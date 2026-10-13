package com.aurora.ai.agent.service.impl;

import com.aurora.ai.agent.billing.BillingCalculator;
import com.aurora.ai.agent.billing.IBeanDeductPort;
import com.aurora.ai.agent.billing.InsufficientBeanException;
import com.aurora.ai.agent.domain.GenerationTask;
import com.aurora.ai.agent.dto.GenerateImageItem;
import com.aurora.ai.agent.service.ImagePromptCompiler;
import com.aurora.ai.agent.dto.GenerationOptionsVO;
import com.aurora.ai.agent.dto.WorkBatchDetailVO;
import com.aurora.ai.agent.dto.WorkBatchItemVO;
import com.aurora.ai.agent.dto.WorkItemDetailVO;
import com.aurora.ai.agent.dto.WorkReferenceVO;
import com.aurora.ai.agent.dto.WorkVO;
import com.aurora.ai.agent.mapper.GenerationTaskMapper;
import com.aurora.ai.agent.service.IGenerationTaskService;
import com.aurora.ai.domain.SysAiModel;
import com.aurora.ai.mapper.SysAiModelMapper;
import com.aurora.common.core.exception.ServiceException;
import com.aurora.framework.mq.TaskProducer;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.time.format.DateTimeFormatter;

/**
 * 出图异步任务 Service 实现(PRD §5.3 / §六 状态机)。
 * 状态/结果以本表为唯一真相源;result_urls 用 ObjectMapper 序列化为 JSON 串存库。
 */
@Service
@RequiredArgsConstructor
public class GenerationTaskServiceImpl extends ServiceImpl<GenerationTaskMapper, GenerationTask>
        implements IGenerationTaskService {

    private final ObjectMapper objectMapper;
    private final TaskProducer taskProducer;
    private final SysAiModelMapper sysAiModelMapper;
    private final BillingCalculator billingCalculator;

    /**
     * 豆子扣减端口(跨模块 SPI,admin 提供 BeanDeductAdapter)。
     * 可选注入(同 GenerationTaskConsumer 范式):为空时预扣降级跳过、不拦截余额,仅 log 由调用方处理。
     */
    @Autowired(required = false)
    private IBeanDeductPort beanDeductPort;

    /** 出图任务队列类型(stream:generation-task),与 GenerationTaskConsumer 一致 */
    private static final String TASK_TYPE = "generation-task";

    private static final DateTimeFormatter WORK_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_PROMPT_CODE_POINTS = 32000;
    private static final int PROMPT_SUMMARY_CODE_POINTS = 90;
    private static final String SOURCE_CREATE_PAGE = "CREATE_PAGE";
    private static final String SOURCE_AGENT = "AGENT";
    private static final String TARGET_MARKER = "【本张唯一执行目标】";
    private static final String ORIGINAL_MARKER = "【完整用户原始要求（仅作为约束背景）】";
    private static final String BOUNDARY_MARKER = "【执行边界】";

    @Override
    public String submitGenerationTask(Long userId, Long conversationId, String prompt, int n,
                                       String resolution, String aspectRatio, Long modelId, String refImages,
                                       String requestItems) {
        // 底层任务上限 9 张；生成页控制器仍单独限制为 1~4 张。
        if (n < 1 || n > 9) {
            throw new ServiceException("单次最多9张");
        }
        validatePrompt(prompt);
        JsonNode snapshots = parseRequestItems(requestItems);
        if (snapshots != null) for (JsonNode item : snapshots) ImagePromptCompiler.requireIndependent(item);
        // modelId 为空时取一个启用的 image 类出图模型(provider 限 yunwu/gptsapi)
        Long resolvedModelId = (modelId != null) ? modelId : resolveImageModel().getId();
        // 清晰度:调用方未指定则走模型默认清晰度(default_resolution)——对话内出图固定走默认,避免出 4K 大图
        String resolvedResolution = resolution;
        if (resolvedResolution == null || resolvedResolution.isBlank()) {
            SysAiModel rm = sysAiModelMapper.selectById(resolvedModelId);
            resolvedResolution = (rm != null) ? rm.getDefaultResolution() : null;
        }
        String taskId = UUID.randomUUID().toString().replace("-", "");
        return persistAndQueueTask(taskId, userId, conversationId, null, prompt, n, resolvedResolution,
                aspectRatio, resolvedModelId, refImages, requestItems);
    }

    @Override
    public String submitImageGeneration(Long userId, Long conversationId, String prompt, int n,
                                        String resolution, String aspectRatio, List<String> refImages) {
        // 张数硬校验(与 submitGenerationTask 一致);图生图参考图最多 9 张(产品口径)
        if (n < 1 || n > 9) {
            throw new ServiceException("单次最多9张");
        }
        validatePrompt(prompt);
        boolean img2img = refImages != null && !refImages.isEmpty();
        if (img2img && refImages.size() > 9) {
            throw new ServiceException("参考图最多9张");
        }
        // 模型解析:图生图固定走云雾(image-edit 无需公网图床);文生图取任一启用 image 模型。
        // 预扣取价的模型即消费者实际出图的模型(显式传 modelId 给 submitGenerationTask),口径一致。
        SysAiModel model = img2img ? resolveYunwuImageModel() : resolveImageModel();
        if (model == null) {
            throw new ServiceException("无可用出图模型");
        }
        String resolvedResolution = resolveResolution(model, resolution);
        // 预扣:按申请张数算应扣豆子;余额不足抛 InsufficientBeanException(携带余额/应扣/单价),不提交任务、不扣豆
        long needed = billingCalculator.imageBeans(model, n, resolvedResolution);
        if (beanDeductPort != null) {
            long balance = beanDeductPort.getBalance(userId);
            if (balance < needed) {
                long beanPerImage = billingCalculator.imagePrice(model, resolvedResolution);
                throw new InsufficientBeanException(balance, needed, beanPerImage);
            }
        }
        return submitPrepaidTask(userId, conversationId, null, prompt, n, resolvedResolution,
                aspectRatio, model.getId(), toRefImagesJson(refImages), null, needed, "AI出图预扣");
    }

    @Override
    public String submitImageBatch(Long userId, Long conversationId, Long sourceMessageId,
                                   List<GenerateImageItem> items,
                                   String resolution, String aspectRatio, List<String> allRefImages) {
        if (items == null || items.isEmpty() || items.size() > 9) {
            throw new ServiceException("批量生图数量需为1~9张");
        }
        List<String> refs = allRefImages == null ? List.of() : List.copyOf(allRefImages);
        if (refs.size() > 9) {
            throw new ServiceException("参考图最多9张");
        }

        boolean img2img = false;
        List<Map<String, Object>> resolvedItems = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            GenerateImageItem item = items.get(i);
            if (item == null || item.getPrompt() == null || item.getPrompt().isBlank()) {
                throw new ServiceException("第" + (i + 1) + "张缺少出图提示词");
            }
            validatePrompt(item.getPrompt());
            if (item.getSeq() != null && item.getSeq() != i + 1) {
                throw new ServiceException("批量图片序号必须连续且与结果顺序一致");
            }
            if (item.getReferenceImageIndexes() != null
                    && new java.util.HashSet<>(item.getReferenceImageIndexes()).size()
                    != item.getReferenceImageIndexes().size()) {
                throw new ServiceException("第" + (i + 1) + "张包含重复的原始图片引用");
            }
            List<String> itemRefs = new ArrayList<>();
            if (item.getReferenceImageIndexes() != null) {
                for (Integer index : item.getReferenceImageIndexes()) {
                    if (index == null || index < 1 || index > refs.size()) {
                        throw new ServiceException("第" + (i + 1) + "张引用了不存在的图片编号");
                    }
                    itemRefs.add(refs.get(index - 1));
                }
            }
            ImagePromptCompiler.requireIndependent(objectMapper.valueToTree(item));
            img2img |= !itemRefs.isEmpty();
            Map<String, Object> stored = new LinkedHashMap<>();
            stored.put("seq", i + 1);
            // 用户已给出的完整提示词保持原文，不在任务层修剪或改写。
            stored.put("prompt", item.getPrompt());
            stored.put("referenceImageIndexes", item.getReferenceImageIndexes() == null
                    ? List.of() : List.copyOf(item.getReferenceImageIndexes()));
            stored.put("refImages", itemRefs);
            if (item.getContractVersion() != null) {
                if ((item.getContractVersion() < 2 || item.getContractVersion() > 5) || item.getReferenceAttachmentIds() == null
                        || item.getReferenceAttachmentIds().size() != refs.size()) {
                    throw new ServiceException("图片参考契约版本或附件快照无效");
                }
                String compiled = item.getContractVersion() <= 3 ? item.getPrompt()
                        : item.getContractVersion() == 5 ? ImagePromptCompiler.compileV5(item.getPlannedPrompt(),
                        item.getReferenceBindings(), item.getReferenceAttachmentIds(),
                        item.getReferenceImageIndexes(), item.getVisibleTexts()) : ImagePromptCompiler.compile(item.getPlannedPrompt(),
                        item.getReferenceBindings(), item.getReferenceAttachmentIds(),
                        item.getReferenceImageIndexes(), item.getVisibleTexts());
                if (!compiled.equals(item.getPrompt())) {
                    throw new ServiceException("执行提示词与实际图片引用不一致，请重新生成计划");
                }
                stored.put("contractVersion", item.getContractVersion());
                stored.put("plannedPrompt", item.getPlannedPrompt());
                stored.put("visibleTexts", item.getVisibleTexts());
                stored.put("sourcePrompt", item.getSourcePrompt());
                stored.put("effectiveRequest", item.getEffectiveRequest());
                stored.put("requestUpdate", item.getRequestUpdate());
                stored.put("referenceAttachmentIds", item.getReferenceAttachmentIds());
                stored.put("referenceBindings", item.getReferenceBindings());
            }
            resolvedItems.add(stored);
        }

        SysAiModel model = img2img ? resolveYunwuImageModel() : resolveImageModel();
        String resolvedResolution = resolveResolution(model, resolution);
        int n = items.size();
        long needed = billingCalculator.imageBeans(model, n, resolvedResolution);
        assertAffordable(userId, model, resolvedResolution, needed);

        return submitPrepaidTask(userId, conversationId, sourceMessageId, items.get(0).getPrompt(), n,
                resolvedResolution, aspectRatio, model.getId(), toRefImagesJson(refs),
                toJsonValue(resolvedItems), needed, "AI出图预扣");
    }

    @Override
    public List<GenerationTask> listBySourceMessage(Long userId, Long conversationId, Long sourceMessageId) {
        if (userId == null || conversationId == null || sourceMessageId == null) {
            return List.of();
        }
        return list(new LambdaQueryWrapper<GenerationTask>()
                .eq(GenerationTask::getUserId, userId)
                .eq(GenerationTask::getConversationId, conversationId)
                .eq(GenerationTask::getMessageId, sourceMessageId)
                .orderByAsc(GenerationTask::getCreateTime)
                .orderByAsc(GenerationTask::getId));
    }

    @Override
    public Map<String, GenerationTask> mapByTaskIds(Collection<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Map.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String taskId : taskIds) {
            if (taskId != null && !taskId.isBlank()) {
                normalized.add(taskId);
            }
        }
        if (normalized.isEmpty()) {
            return Map.of();
        }
        List<GenerationTask> tasks = list(new LambdaQueryWrapper<GenerationTask>()
                .in(GenerationTask::getTaskId, normalized));
        Map<String, GenerationTask> result = new LinkedHashMap<>();
        for (GenerationTask task : tasks) {
            if (task != null && task.getTaskId() != null) {
                result.put(task.getTaskId(), task);
            }
        }
        return result;
    }

    @Override
    public GenerationOptionsVO getGenerationOptions() {
        SysAiModel model = resolveImageModel();
        List<String> supported = parseSupportedResolutions(model.getSupportedResolutions());
        String defaultResolution = resolveResolution(model, null);
        Map<String, Long> beanCosts = new LinkedHashMap<>();
        for (String item : supported) {
            beanCosts.put(item, billingCalculator.imagePrice(model, item));
        }
        return new GenerationOptionsVO(supported, defaultResolution, beanCosts);
    }

    @Override
    public WorkItemDetailVO getWorkItemDetail(Long userId, String taskId, int seq) {
        GenerationTask task = requireOwnedTask(userId, taskId);
        WorkItemSnapshot item = resolveWorkItem(task, seq, false);
        SysAiModel model = sysAiModelMapper.selectById(task.getModelId());
        long beanCost = 0L;
        if (item.resultUrl() != null) {
            int successCount = task.getSuccessCount() == null ? 0 : task.getSuccessCount();
            if (task.getBeanCost() != null && successCount > 0) {
                beanCost = task.getBeanCost() / successCount;
            } else if (model != null) {
                beanCost = billingCalculator.imagePrice(model, task.getResolution());
            }
        }
        String createTime = task.getCreateTime() == null
                ? null : task.getCreateTime().format(WORK_TIME_FORMATTER);
        String unavailableReason = null;
        JsonNode snapshots = parseRequestItems(task.getRequestItems());
        if (snapshots != null && snapshots.isArray() && seq <= snapshots.size()) {
            try {
                ImagePromptCompiler.requireIndependent(snapshots.get(seq - 1));
            } catch (ServiceException e) {
                unavailableReason = e.getMessage();
            }
        }
        return new WorkItemDetailVO(taskId, seq, item.resultUrl(), item.prompt(), item.referenceImages(),
                item.referenceImages().isEmpty() ? "text2img" : "img2img",
                task.getResolution(), task.getAspectRatio(), beanCost, createTime, unavailableReason == null, unavailableReason);
    }

    @Override
    public WorkBatchDetailVO getWorkBatchDetail(Long userId, String taskId) {
        GenerationTask task = requireOwnedTask(userId, taskId);
        int plannedCount = task.getN() == null ? 1 : task.getN();
        List<String> results = parseStringArray(task.getResultUrls());
        JsonNode requestItems = parseRequestItems(task.getRequestItems());
        boolean agentSource = task.getConversationId() != null;
        SysAiModel model = sysAiModelMapper.selectById(task.getModelId());

        int successCount = 0;
        for (int i = 0; i < plannedCount; i++) {
            if (hasText(resultAt(results, i))) {
                successCount++;
            }
        }
        long beanPerSuccess = resolveHistoricalBeanCost(task, model, successCount);
        long totalBeanCost = task.getBeanCost() == null
                ? beanPerSuccess * successCount : Math.max(0L, task.getBeanCost().longValue());

        String batchPrompt = agentSource ? null : task.getPrompt();
        List<WorkBatchItemVO> items = new ArrayList<>(plannedCount);
        for (int i = 0; i < plannedCount; i++) {
            int seq = i + 1;
            JsonNode requestItem = requestItems != null && i < requestItems.size() ? requestItems.get(i) : null;
            String fullPrompt = requestItem == null ? task.getPrompt() : requestItem.path("prompt").asText(task.getPrompt());
            PromptParts promptParts = agentSource
                    ? parseAgentPrompt(fullPrompt) : new PromptParts(null, null);
            if (agentSource && !hasText(batchPrompt) && hasText(promptParts.userPrompt())) {
                batchPrompt = promptParts.userPrompt();
            }
            List<WorkReferenceVO> references = resolveWorkReferences(task, requestItem, results);
            String imageUrl = resultAt(results, i);
            boolean succeeded = hasText(imageUrl);
            items.add(new WorkBatchItemVO(
                    seq,
                    resolveItemStatus(task.getStatus(), succeeded),
                    succeeded ? imageUrl : null,
                    succeeded ? toPreviewUrl(imageUrl) : null,
                    promptParts.executionTarget(),
                    promptParts.userPrompt(),
                    fullPrompt,
                    references,
                    references.isEmpty() ? "text2img" : "img2img",
                    succeeded ? beanPerSuccess : 0L));
        }

        String createTime = task.getCreateTime() == null
                ? null : task.getCreateTime().format(WORK_TIME_FORMATTER);
        return new WorkBatchDetailVO(
                taskId,
                agentSource ? SOURCE_AGENT : SOURCE_CREATE_PAGE,
                batchPrompt,
                plannedCount,
                successCount,
                Math.max(0, plannedCount - successCount),
                task.getResolution(),
                task.getAspectRatio(),
                totalBeanCost,
                createTime,
                items);
    }

    @Override
    public String replayWorkItem(Long userId, String taskId, int seq, boolean failedOnly) {
        GenerationTask source = requireOwnedTask(userId, taskId);
        if (!List.of("SUCCEEDED", "FAILED", "CANCELLED").contains(source.getStatus())) {
            throw new ServiceException("原任务尚未结束，暂不能重新生成");
        }
        WorkItemSnapshot item = resolveWorkItem(source, seq, true);
        if (failedOnly && item.resultUrl() != null && !item.resultUrl().isBlank()) {
            throw new ServiceException("该图片已经生成成功，无需重试");
        }
        if (!failedOnly && item.resultUrl() == null) {
            throw new ServiceException("该作品未生成成功，请使用失败重试");
        }

        SysAiModel model = sysAiModelMapper.selectById(source.getModelId());
        if (model == null) {
            throw new ServiceException("原出图模型不存在，无法精确重新生成");
        }
        if (!item.referenceImages().isEmpty() && !"yunwu".equalsIgnoreCase(model.getProvider())) {
            throw new ServiceException("原模型不支持图生图，无法精确重新生成");
        }
        String resolution = source.getResolution();
        if (resolution == null || resolution.isBlank()) {
            resolution = resolveResolution(model, null);
        }
        long needed = billingCalculator.imageBeans(model, 1, resolution);
        assertAffordable(userId, model, resolution, needed);

        return submitPrepaidTask(userId, source.getConversationId(), null, item.prompt(), 1,
                resolution, source.getAspectRatio(), source.getModelId(),
                toRefImagesJson(item.referenceImages()), null, needed, "AI单张重新生成预扣");
    }

    /** 先完成原子预扣再发布任务；扣减失败时不会落库或进入图片队列。 */
    private String submitPrepaidTask(Long userId, Long conversationId, Long sourceMessageId,
                                     String prompt, int n,
                                     String resolution, String aspectRatio, Long modelId,
                                     String refImages, String requestItems, long needed, String remark) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        boolean deducted = beanDeductPort != null && needed > 0;
        if (deducted) {
            beanDeductPort.deduct(userId, needed, "img:" + taskId, remark);
        }
        try {
            return persistAndQueueTask(taskId, userId, conversationId, sourceMessageId, prompt, n, resolution,
                    aspectRatio, modelId, refImages, requestItems);
        } catch (RuntimeException e) {
            try {
                markStatus(taskId, "FAILED", null, 0, "任务提交失败");
            } catch (RuntimeException statusError) {
                e.addSuppressed(statusError);
            }
            if (deducted) {
                try {
                    beanDeductPort.refund(userId, needed, "img:" + taskId, "任务提交失败退还");
                } catch (RuntimeException refundError) {
                    e.addSuppressed(refundError);
                }
            }
            throw e;
        }
    }

    /** 先提交任务行和 QUEUED 状态，再发布 Redis 消息，保证消费者一定能读取完整任务。 */
    private String persistAndQueueTask(String taskId, Long userId, Long conversationId, Long sourceMessageId,
                                       String prompt, int n,
                                       String resolution, String aspectRatio, Long modelId,
                                       String refImages, String requestItems) {
        GenerationTask task = createTask(taskId, userId, conversationId, sourceMessageId,
                modelId, prompt, n, resolution, aspectRatio, refImages, requestItems);
        markStatus(taskId, "QUEUED", null, null, null);
        taskProducer.send(TASK_TYPE, Map.of("generationTaskId", task.getId()));
        return taskId;
    }

    /**
     * 取一个启用的云雾(yunwu)image 类出图模型 id(图生图 image-edit 用,按 sort_order 升序取第一条)。
     * 无可用云雾出图模型抛业务异常。
     */
    private SysAiModel resolveYunwuImageModel() {
        LambdaQueryWrapper<SysAiModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiModel::getModelType, "image")
                .eq(SysAiModel::getIsActive, 1)
                .eq(SysAiModel::getProvider, "yunwu")
                // 默认图像模型优先(is_default=1),否则按 sort_order 取第一条
                .orderByDesc(SysAiModel::getIsDefault)
                .orderByAsc(SysAiModel::getSortOrder)
                .last("LIMIT 1");
        SysAiModel model = sysAiModelMapper.selectOne(wrapper);
        if (model == null) {
            throw new ServiceException("无可用图生图模型(需云雾出图模型)");
        }
        return model;
    }

    /**
     * 参考图 URL 列表序列化为 JSON 数组串;空列表返回 null(即文生图,不落 ref_images)。
     */
    private String toRefImagesJson(List<String> refImages) {
        if (refImages == null || refImages.isEmpty()) {
            return null;
        }
        return toJson(refImages);
    }

    /**
     * 取一个启用的 image 类出图模型 id(provider 限 yunwu/gptsapi,按 sort_order 升序取第一条)。
     * 无可用模型抛业务异常。
     */
    private SysAiModel resolveImageModel() {
        LambdaQueryWrapper<SysAiModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiModel::getModelType, "image")
                .eq(SysAiModel::getIsActive, 1)
                .in(SysAiModel::getProvider, "yunwu", "gptsapi")
                // 默认图像模型优先(is_default=1),否则按 sort_order 取第一条
                .orderByDesc(SysAiModel::getIsDefault)
                .orderByAsc(SysAiModel::getSortOrder)
                .last("LIMIT 1");
        SysAiModel model = sysAiModelMapper.selectOne(wrapper);
        if (model == null) {
            throw new ServiceException("无可用出图模型");
        }
        return model;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GenerationTask createTask(String taskId, Long userId, Long conversationId, Long messageId,
                                     Long modelId, String prompt, Integer n, String resolution, String aspectRatio,
                                     String refImages, String requestItems) {
        GenerationTask task = new GenerationTask();
        task.setTaskId(taskId);
        task.setUserId(userId);
        task.setConversationId(conversationId);
        task.setMessageId(messageId);
        task.setTaskType("image");
        task.setModelId(modelId);
        task.setPrompt(prompt);
        task.setN(n);
        task.setResolution(resolution);
        task.setAspectRatio(aspectRatio);
        task.setRefImages(refImages);
        task.setRequestItems(requestItems);
        task.setStatus("CREATED");
        task.setSuccessCount(0);
        save(task);
        return task;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markStatus(String taskId, String status, List<String> resultUrls, Integer successCount, String errorMsg) {
        LambdaUpdateWrapper<GenerationTask> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(GenerationTask::getTaskId, taskId)
                .set(GenerationTask::getStatus, status);
        // resultUrls 不为空时序列化为 JSON 串写 result_urls 列;为 null 表示本次不更新该列
        if (resultUrls != null) {
            wrapper.set(GenerationTask::getResultUrls, toJson(resultUrls));
        }
        if (successCount != null) {
            wrapper.set(GenerationTask::getSuccessCount, successCount);
        }
        if (errorMsg != null) {
            wrapper.set(GenerationTask::getErrorMsg, errorMsg);
        }
        update(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setBeanCost(String taskId, long beanCost) {
        // 仅回写 bean_cost,不触碰状态/结果列;与 markStatus 分离,出图结算时单独调用
        LambdaUpdateWrapper<GenerationTask> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(GenerationTask::getTaskId, taskId)
                .set(GenerationTask::getBeanCost, beanCost);
        update(wrapper);
    }

    @Override
    public GenerationTask getByTaskId(String taskId) {
        return getOne(new LambdaQueryWrapper<GenerationTask>().eq(GenerationTask::getTaskId, taskId));
    }

    @Override
    public GenerationTask getOwned(String taskId, Long userId) {
        return getOne(new LambdaQueryWrapper<GenerationTask>()
                .eq(GenerationTask::getTaskId, taskId)
                .eq(GenerationTask::getUserId, userId));
    }

    @Override
    public IPage<WorkVO> pageMyWorks(Long userId, Integer pageNum, Integer pageSize, String sourceType) {
        // 仅本人成功出图任务,按创建时间倒序(最新作品在前)
        LambdaQueryWrapper<GenerationTask> wrapper = new LambdaQueryWrapper<GenerationTask>()
                .eq(GenerationTask::getUserId, userId)
                .eq(GenerationTask::getStatus, "SUCCEEDED");
        if (sourceType != null && !sourceType.isBlank()) {
            switch (sourceType.trim().toUpperCase(Locale.ROOT)) {
                case SOURCE_CREATE_PAGE -> wrapper.isNull(GenerationTask::getConversationId);
                case SOURCE_AGENT -> wrapper.isNotNull(GenerationTask::getConversationId);
                default -> throw new ServiceException("作品来源参数无效");
            }
        }
        wrapper.orderByDesc(GenerationTask::getCreateTime);
        IPage<GenerationTask> taskPage = page(new Page<>(pageNum, pageSize), wrapper);
        return taskPage.convert(this::toWorkVO);
    }

    private WorkVO toWorkVO(GenerationTask task) {
        int plannedCount = task.getN() == null ? 1 : task.getN();
        List<String> results = parseStringArray(task.getResultUrls());
        int successCount = 0;
        for (int i = 0; i < plannedCount; i++) {
            if (hasText(resultAt(results, i))) {
                successCount++;
            }
        }
        JsonNode requestItems = parseRequestItems(task.getRequestItems());
        boolean agentSource = task.getConversationId() != null;
        String summary = buildPromptSummary(task, requestItems, agentSource);

        WorkVO vo = new WorkVO();
        vo.setId(task.getId());
        vo.setTaskId(task.getTaskId());
        vo.setPrompt(summary);
        vo.setPromptSummary(summary);
        vo.setSourceType(agentSource ? SOURCE_AGENT : SOURCE_CREATE_PAGE);
        vo.setResultUrls(results);
        vo.setPreviewUrls(results.stream().map(this::toPreviewUrl).toList());
        vo.setSuccessCount(successCount);
        vo.setPlannedCount(plannedCount);
        vo.setFailedCount(Math.max(0, plannedCount - successCount));
        vo.setHasReferences(hasReferences(task, requestItems));
        vo.setCreateTime(task.getCreateTime() == null
                ? null : task.getCreateTime().format(WORK_TIME_FORMATTER));
        return vo;
    }

    /**
     * 将成功 URL 列表序列化为 JSON 数组串;序列化失败抛业务异常。
     */
    private String toJson(List<String> resultUrls) {
        try {
            return objectMapper.writeValueAsString(resultUrls);
        } catch (JsonProcessingException e) {
            throw new ServiceException("出图结果序列化失败", e);
        }
    }

    private String toJsonValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ServiceException("批量出图任务序列化失败", e);
        }
    }

    private void assertAffordable(Long userId, SysAiModel model, String resolution, long needed) {
        if (beanDeductPort == null) {
            return;
        }
        long balance = beanDeductPort.getBalance(userId);
        if (balance < needed) {
            long beanPerImage = billingCalculator.imagePrice(model, resolution);
            throw new InsufficientBeanException(balance, needed, beanPerImage);
        }
    }

    private void validatePrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new ServiceException("提示词不能为空");
        }
        if (prompt.codePointCount(0, prompt.length()) > MAX_PROMPT_CODE_POINTS) {
            throw new ServiceException("提示词最多32000个字符");
        }
    }

    private String resolveResolution(SysAiModel model, String requested) {
        List<String> supported = parseSupportedResolutions(model.getSupportedResolutions());
        if (requested == null || requested.isBlank()) {
            String configured = model.getDefaultResolution();
            if (configured == null || configured.isBlank()) {
                return supported.isEmpty() ? null : supported.get(0);
            }
            if (supported.isEmpty()) {
                return configured.trim();
            }
            String matched = matchResolution(configured, supported);
            if (matched == null) {
                throw new ServiceException("后台默认分辨率未包含在已启用选项中");
            }
            return matched;
        }
        if (supported.isEmpty()) {
            throw new ServiceException("后台未配置可用分辨率");
        }
        String matched = matchResolution(requested, supported);
        if (matched == null) {
            throw new ServiceException("所选分辨率未在后台启用");
        }
        return matched;
    }

    private String matchResolution(String value, List<String> supported) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (String item : supported) {
            if (item.toUpperCase(Locale.ROOT).equals(normalized)) {
                return item;
            }
        }
        return null;
    }

    private List<String> parseSupportedResolutions(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty() && result.stream().noneMatch(v -> v.equalsIgnoreCase(trimmed))) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private GenerationTask requireOwnedTask(Long userId, String taskId) {
        GenerationTask task = getOwned(taskId, userId);
        if (task == null) {
            throw new ServiceException("任务不存在或无权访问");
        }
        return task;
    }

    private long resolveHistoricalBeanCost(GenerationTask task, SysAiModel model, int successCount) {
        if (successCount <= 0) {
            return 0L;
        }
        if (task.getBeanCost() != null) {
            return Math.max(0L, task.getBeanCost().longValue()) / successCount;
        }
        return model == null ? 0L : billingCalculator.imagePrice(model, task.getResolution());
    }

    private List<WorkReferenceVO> resolveWorkReferences(GenerationTask task, JsonNode requestItem,
                                                         List<String> results) {
        List<WorkReferenceVO> references = new ArrayList<>();
        if (requestItem != null) {
            for (JsonNode generatedSeq : requestItem.path("generatedReferenceSeqs")) {
                int sourceSeq = generatedSeq.asInt(0);
                String sourceUrl = sourceSeq >= 1 ? resultAt(results, sourceSeq - 1) : null;
                references.add(new WorkReferenceVO(
                        references.size() + 1, "GENERATED", sourceSeq > 0 ? sourceSeq : null,
                        hasText(sourceUrl) ? sourceUrl : null));
            }
            for (String url : parseStringArray(requestItem.path("refImages"))) {
                if (hasText(url)) {
                    references.add(new WorkReferenceVO(references.size() + 1, "UPLOADED", null, url));
                }
            }
            return List.copyOf(references);
        }
        for (String url : parseStringArray(task.getRefImages())) {
            if (hasText(url)) {
                references.add(new WorkReferenceVO(references.size() + 1, "REFERENCE", null, url));
            }
        }
        return List.copyOf(references);
    }

    private String resolveItemStatus(String taskStatus, boolean succeeded) {
        if (succeeded) {
            return "SUCCEEDED";
        }
        return List.of("CREATED", "QUEUED", "RUNNING").contains(taskStatus) ? "GENERATING" : "FAILED";
    }

    private String buildPromptSummary(GenerationTask task, JsonNode requestItems, boolean agentSource) {
        if (!agentSource) {
            return truncatePrompt(task.getPrompt());
        }
        List<String> targets = new ArrayList<>();
        if (requestItems != null) {
            for (JsonNode item : requestItems) {
                PromptParts parts = parseAgentPrompt(item.path("prompt").asText(null));
                if (hasText(parts.executionTarget())) {
                    targets.add(parts.executionTarget());
                }
            }
        }
        if (targets.isEmpty()) {
            targets.add(parseAgentPrompt(task.getPrompt()).executionTarget());
        }
        List<String> validTargets = targets.stream().filter(this::hasText).toList();
        if (validTargets.isEmpty()) {
            return "Agent 生图任务";
        }
        String summary = String.join("；", validTargets.subList(0, Math.min(2, validTargets.size())));
        if (validTargets.size() > 2) {
            summary += " 等" + validTargets.size() + "张";
        }
        return truncatePrompt(summary);
    }

    private boolean hasReferences(GenerationTask task, JsonNode requestItems) {
        if (requestItems != null) {
            for (JsonNode item : requestItems) {
                if (item.path("generatedReferenceSeqs").size() > 0 || item.path("refImages").size() > 0) {
                    return true;
                }
            }
        }
        return parseStringArray(task.getRefImages()).stream().anyMatch(this::hasText);
    }

    private JsonNode parseRequestItems(String json) {
        if (!hasText(json)) {
            return null;
        }
        try {
            JsonNode items = objectMapper.readTree(json);
            return items.isArray() ? items : null;
        } catch (Exception e) {
            return null;
        }
    }

    private PromptParts parseAgentPrompt(String prompt) {
        if (!hasText(prompt)) {
            return new PromptParts(null, null);
        }
        int targetMarker = prompt.indexOf(TARGET_MARKER);
        int originalMarker = prompt.indexOf(ORIGINAL_MARKER);
        int boundaryMarker = prompt.indexOf(BOUNDARY_MARKER);
        if (targetMarker >= 0 && originalMarker > targetMarker && boundaryMarker > originalMarker) {
            String target = prompt.substring(targetMarker + TARGET_MARKER.length(), originalMarker).trim();
            String original = prompt.substring(originalMarker + ORIGINAL_MARKER.length(), boundaryMarker).trim();
            return new PromptParts(emptyToNull(target), emptyToNull(original));
        }
        return new PromptParts(prompt.trim(), null);
    }

    private String truncatePrompt(String value) {
        if (!hasText(value)) {
            return "未命名作品";
        }
        String trimmed = value.trim();
        int codePoints = trimmed.codePointCount(0, trimmed.length());
        if (codePoints <= PROMPT_SUMMARY_CODE_POINTS) {
            return trimmed;
        }
        int end = trimmed.offsetByCodePoints(0, PROMPT_SUMMARY_CODE_POINTS);
        return trimmed.substring(0, end) + "…";
    }

    private String toPreviewUrl(String url) {
        if (hasText(url) && url.contains("/profile/generated/") && url.endsWith(".png")) {
            return url.substring(0, url.length() - 4) + ".preview.jpg";
        }
        return url;
    }

    private String resultAt(List<String> results, int index) {
        return index >= 0 && index < results.size() ? results.get(index) : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String emptyToNull(String value) {
        return hasText(value) ? value : null;
    }

    /** 解析单张执行快照；旧任务内依赖只允许展示，不允许自动重生。 */
    private WorkItemSnapshot resolveWorkItem(GenerationTask task, int seq, boolean forExecution) {
        int n = task.getN() == null ? 1 : task.getN();
        if (seq < 1 || seq > n) {
            throw new ServiceException("图片序号不存在");
        }
        List<String> results = parseStringArray(task.getResultUrls());
        String resultUrl = seq <= results.size() ? results.get(seq - 1) : null;
        if (resultUrl != null && resultUrl.isBlank()) {
            resultUrl = null;
        }

        if (task.getRequestItems() == null || task.getRequestItems().isBlank()) {
            return new WorkItemSnapshot(task.getPrompt(), parseStringArray(task.getRefImages()), resultUrl);
        }
        try {
            JsonNode items = objectMapper.readTree(task.getRequestItems());
            if (!items.isArray() || seq > items.size()) {
                throw new ServiceException("原任务项不存在");
            }
            JsonNode item = items.get(seq - 1);
            String prompt = item.path("prompt").asText(null);
            if (prompt == null || prompt.isBlank()) {
                throw new ServiceException("原任务提示词不存在");
            }
            List<String> references = new ArrayList<>();
            if (forExecution) {
                ImagePromptCompiler.requireIndependent(item);
            } else {
                // 历史展示如实呈现当时素材，不参与新任务执行。
                for (JsonNode generatedSeq : item.path("generatedReferenceSeqs")) {
                    int sourceSeq = generatedSeq.asInt(0);
                    String historicalUrl = sourceSeq > 0 && sourceSeq <= results.size()
                            ? results.get(sourceSeq - 1) : null;
                    if (hasText(historicalUrl)) references.add(historicalUrl);
                }
            }
            references.addAll(parseStringArray(item.path("refImages")));
            if (references.size() > 9) {
                throw new ServiceException("原任务参考图超过9张");
            }
            return new WorkItemSnapshot(prompt, List.copyOf(references), resultUrl);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("原任务数据解析失败", e);
        }
    }

    private List<String> parseStringArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return parseStringArray(objectMapper.readTree(json));
        } catch (Exception e) {
            throw new ServiceException("原任务图片数据解析失败", e);
        }
    }

    private List<String> parseStringArray(JsonNode array) {
        if (array == null || !array.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode node : array) {
            values.add(node.asText(""));
        }
        return values;
    }

    private record WorkItemSnapshot(String prompt, List<String> referenceImages, String resultUrl) {
    }

    private record PromptParts(String executionTarget, String userPrompt) {
    }
}
