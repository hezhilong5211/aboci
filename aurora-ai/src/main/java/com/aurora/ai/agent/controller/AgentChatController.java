package com.aurora.ai.agent.controller;

import com.aurora.ai.agent.domain.AgentSkill;
import com.aurora.ai.agent.domain.Conversation;
import com.aurora.ai.agent.domain.GenerationTask;
import com.aurora.ai.agent.domain.Message;
import com.aurora.ai.agent.domain.UserPrompt;
import com.aurora.ai.agent.billing.InsufficientBeanException;
import com.aurora.ai.agent.dto.AgentChatRequest;
import com.aurora.ai.agent.dto.AgentResumeRequest;
import com.aurora.ai.agent.dto.ConversationVO;
import com.aurora.ai.agent.dto.ChatRunVO;
import com.aurora.ai.agent.dto.GenerateImageRequest;
import com.aurora.ai.agent.dto.GenerationTaskVO;
import com.aurora.ai.agent.dto.GenerationOptionsVO;
import com.aurora.ai.agent.dto.GenerationDownloadVO;
import com.aurora.ai.agent.dto.MessagePageVO;
import com.aurora.ai.agent.dto.MessageVO;
import com.aurora.ai.agent.dto.WorkBatchDetailVO;
import com.aurora.ai.agent.dto.WorkVO;
import com.aurora.ai.agent.dto.WorkItemDetailVO;
import com.aurora.ai.dto.AgentClarification;
import com.aurora.ai.agent.service.AgentService;
import com.aurora.ai.agent.service.IConversationService;
import com.aurora.ai.agent.service.IGenerationTaskService;
import com.aurora.ai.agent.service.GenerationFeaturePolicy;
import com.aurora.ai.agent.service.GeneratedImageDownloadService;
import com.aurora.ai.agent.service.IMessageService;
import com.aurora.ai.agent.service.IUserPromptService;
import com.aurora.ai.mapper.AgentSkillMapper;
import com.aurora.common.core.domain.Result;
import com.aurora.common.core.exception.ServiceException;
import com.aurora.framework.annotation.RateLimiter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 移动端 Agent 对话 Controller(C 端,走 mobile 链,PRD §十)。
 *
 * <p>认证:端点落 {@code /api/mobile/agent/**},由 {@code @Order(0)} 的 mobileSecurityFilterChain
 * + MobileJwtFilter 管辖;当前用户一律取 {@code @RequestAttribute("mobileUserId")},不信任入参 userId(R-DTO)。
 * 非流式返回 {@code Result<T>}(R-RESP),流式对话返回 {@code Flux<String>}(分型 SSE,有意例外)。
 * 归属校验:他人会话/任务返回错误,不泄露数据(R-PERM)。</p>
 *
 * <p>分层(R-LAYER):本类只接请求/校验/包装响应,业务在 AgentService/各 Service;
 * 历史消息 media 里 images 的 taskId→url 解析属响应拼装,调 {@link IGenerationTaskService} 只读接口完成,
 * 不直拼 SQL。skills 只读列表用 {@link AgentSkillMapper} 直查启用 Skill(使用类只读端点)。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/mobile/agent")
@RequiredArgsConstructor
public class AgentChatController {

    private final AgentService agentService;
    private final IConversationService conversationService;
    private final IMessageService messageService;
    private final IGenerationTaskService generationTaskService;
    private final GenerationFeaturePolicy generationFeaturePolicy;
    private final GeneratedImageDownloadService generatedImageDownloadService;
    private final IUserPromptService userPromptService;
    private final AgentSkillMapper agentSkillMapper;
    private final ObjectMapper objectMapper;

    /**
     * 主对话流式接口(分型 SSE)。无 conversationId 则新建会话。
     * 返回 {@code Flux<String>},每个元素为一条分型 SSE 事件 JSON 行(meta/text/tool/result/done/error)。
     *
     * @param req    主对话请求(不含 userId)
     * @param userId 当前登录用户(登录态注入)
     * @return 分型 SSE 事件流
     */
    @PostMapping(value = "/chat/stream", produces = "text/event-stream;charset=UTF-8")
    @RateLimiter(key = "mobile:agent:chat", time = 60, count = 10,
            limitType = RateLimiter.LimitType.USER, message = "对话请求过于频繁，请稍后再试")
    public Flux<String> chat(@RequestBody AgentChatRequest req,
                             @RequestAttribute("mobileUserId") Long userId) {
        return agentService.chatStream(userId, req);
    }

    /** 豆子到账后恢复最后一条已落库但尚未完成的用户消息。 */
    @PostMapping(value = "/chat/resume", produces = "text/event-stream;charset=UTF-8")
    @RateLimiter(key = "mobile:agent:chat", time = 60, count = 10,
            limitType = RateLimiter.LimitType.USER, message = "对话请求过于频繁，请稍后再试")
    public Flux<String> resumeChat(@RequestBody AgentResumeRequest req,
                                   @RequestAttribute("mobileUserId") Long userId) {
        return agentService.resumeChatStream(userId, req);
    }

    /**
     * 显式停止一次仍在执行的对话：排队阶段使用 requestId，消息落库后使用 meta.messageId。
     */
    @PostMapping("/chat/{runId}/stop")
    public Result<Map<String, Boolean>> stopChat(@PathVariable String runId,
                                                  @RequestAttribute("mobileUserId") Long userId) {
        return Result.success(Map.of("stopped", agentService.stopChat(userId, runId)));
    }

    /**
     * 轮询出图任务。校验任务属本人,非本人/不存在返回错误(不泄露他人任务)。
     *
     * @param taskId Redis 队列 taskId
     * @param userId 当前登录用户(登录态注入)
     * @return 出图任务视图
     */
    @GetMapping("/generation/{taskId}")
    public Result<GenerationTaskVO> generation(@PathVariable String taskId,
                                               @RequestAttribute("mobileUserId") Long userId) {
        GenerationTask task = generationTaskService.getOwned(taskId, userId);
        if (task == null) {
            return Result.error(403, "任务不存在或无权访问");
        }
        return Result.success(toGenerationTaskVO(task));
    }

    /**
     * 独立出图(生成页 C 组):文生图 / 图生图(refImages 非空走云雾 image-edit)。
     * 复用预扣 + 失败退还扣豆;立即返回 taskId,前端用 {@code /generation/{taskId}} 轮询出图结果。
     *
     * @param req    出图请求(prompt/aspectRatio/n/refImages,不含 userId)
     * @param userId 当前登录用户(登录态注入)
     * @return {@code {taskId}};参数非法/余额不足返回业务错误码 + 中文文案
     */
    @PostMapping("/generate")
    @RateLimiter(key = "mobile:agent:image", time = 60, count = 10,
            limitType = RateLimiter.LimitType.USER, message = "生成请求过于频繁，请稍后再试")
    public Result<Map<String, Object>> generate(@RequestBody GenerateImageRequest req,
                                                @RequestAttribute("mobileUserId") Long userId) {
        try {
            generationFeaturePolicy.requireStandaloneGenerationEnabled();
        } catch (ServiceException e) {
            return Result.error(e.getMessage());
        }
        if (req == null || !StringUtils.hasText(req.getPrompt())) {
            return Result.error("请输入出图提示词");
        }
        int n = (req.getN() == null) ? 1 : req.getN();
        if (n < 1 || n > 4) {
            return Result.error("张数需为 1~4 张");
        }
        if (req.getRefImages() != null && req.getRefImages().size() > 9) {
            return Result.error("参考图最多9张");
        }
        try {
            // 生成页出图不绑定会话(conversationId=null);预扣 + 入队由 service 统一处理
            String taskId = generationTaskService.submitImageGeneration(
                    userId, null, req.getPrompt(), n, req.getResolution(), req.getAspectRatio(), req.getRefImages(),
                    req.getModelId(), req.getRequestId());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("taskId", taskId);
            return Result.success(data);
        } catch (InsufficientBeanException e) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("errorCode", "INSUFFICIENT_BEANS");
            data.put("action", "RECHARGE");
            data.put("balance", e.getBalance());
            data.put("needed", e.getNeeded());
            Result<Map<String, Object>> result = Result.error(46001, "豆子余额不足,请充值后再生成~");
            result.setData(data);
            return result;
        } catch (ServiceException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 读取后台默认图像模型允许的分辨率选项。 */
    @GetMapping("/generation/options")
    public Result<GenerationOptionsVO> generationOptions(@RequestParam(required = false) Long modelId) {
        return Result.success(generationTaskService.getGenerationOptions(modelId));
    }

    @GetMapping("/conversations/{conversationId}/run")
    public Result<ChatRunVO> conversationRun(@PathVariable Long conversationId,
                                            @RequestAttribute("mobileUserId") Long userId) {
        return Result.success(agentService.getConversationRun(userId, conversationId));
    }

    /** 按当前后台水印配置返回本人任务中指定图片的下载地址。 */
    @GetMapping("/generation/{taskId}/items/{seq}/download-info")
    public Result<GenerationDownloadVO> generationDownloadInfo(
            @PathVariable String taskId,
            @PathVariable int seq,
            @RequestAttribute("mobileUserId") Long userId) {
        try {
            return Result.success(generatedImageDownloadService.resolveDownload(userId, taskId, seq));
        } catch (ServiceException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 本人会话列表,按 last_message_time 倒序分页。
     *
     * @param pageNum  页码(默认 1)
     * @param pageSize 每页条数(默认 10)
     * @param userId   当前登录用户(登录态注入)
     * @return 会话视图分页
     */
    @GetMapping("/conversations")
    public Result<IPage<ConversationVO>> conversations(@RequestParam(defaultValue = "1") int pageNum,
                                                       @RequestParam(defaultValue = "10") int pageSize,
                                                       @RequestAttribute("mobileUserId") Long userId) {
        IPage<Conversation> page = conversationService.pageByUser(userId, pageNum, pageSize);
        IPage<ConversationVO> voPage = page.convert(ConversationVO::from);
        return Result.success(voPage);
    }

    /**
     * 本人「我的作品」:仅 SUCCEEDED 出图任务,按创建时间倒序分页(C 端作品墙)。
     *
     * @param pageNum  页码(默认 1)
     * @param pageSize 每页条数(默认 20)
     * @param sourceType 来源过滤(CREATE_PAGE / AGENT,不传返回全部)
     * @param userId   当前登录用户(登录态注入)
     * @return 作品视图分页
     */
    @GetMapping("/works")
    public Result<IPage<WorkVO>> works(@RequestParam(defaultValue = "1") Integer pageNum,
                                       @RequestParam(defaultValue = "20") Integer pageSize,
                                       @RequestParam(required = false) String sourceType,
                                       @RequestAttribute("mobileUserId") Long userId) {
        try {
            return Result.success(generationTaskService.pageMyWorks(userId, pageNum, pageSize, sourceType));
        } catch (ServiceException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 查询本人某一作品批次的完整计划、提示词、参考图和生成参数。 */
    @GetMapping("/works/{taskId}")
    public Result<WorkBatchDetailVO> workBatchDetail(@PathVariable String taskId,
                                                     @RequestAttribute("mobileUserId") Long userId) {
        try {
            return Result.success(generationTaskService.getWorkBatchDetail(userId, taskId));
        } catch (ServiceException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 查询本人某一张作品的原提示词、实际参考图和生成参数。 */
    @GetMapping("/works/{taskId}/items/{seq}")
    public Result<WorkItemDetailVO> workItemDetail(@PathVariable String taskId,
                                                   @PathVariable int seq,
                                                   @RequestAttribute("mobileUserId") Long userId) {
        try {
            return Result.success(generationTaskService.getWorkItemDetail(userId, taskId, seq));
        } catch (ServiceException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 成功作品按原提示词、参考图和参数重新生成当前一张。 */
    @PostMapping("/works/{taskId}/items/{seq}/regenerate")
    @RateLimiter(key = "mobile:agent:image", time = 60, count = 10,
            limitType = RateLimiter.LimitType.USER, message = "生成请求过于频繁，请稍后再试")
    public Result<Map<String, Object>> regenerateWorkItem(@PathVariable String taskId,
                                                          @PathVariable int seq,
                                                          @RequestAttribute("mobileUserId") Long userId) {
        return replayWorkItem(userId, taskId, seq, false);
    }

    /** 对话结果中的失败图片精准重试，不重跑同任务内已成功图片。 */
    @PostMapping("/generation/{taskId}/items/{seq}/retry")
    @RateLimiter(key = "mobile:agent:image", time = 60, count = 10,
            limitType = RateLimiter.LimitType.USER, message = "生成请求过于频繁，请稍后再试")
    public Result<Map<String, Object>> retryFailedImage(@PathVariable String taskId,
                                                        @PathVariable int seq,
                                                        @RequestAttribute("mobileUserId") Long userId) {
        return replayWorkItem(userId, taskId, seq, true);
    }

    /**
     * 显式新建会话。返回新会话 id。
     *
     * @param req    新建请求(可选 title)
     * @param userId 当前登录用户(登录态注入)
     * @return {@code {conversationId}}
     */
    @PostMapping("/conversations")
    public Result<Map<String, Object>> createConversation(@RequestBody(required = false) CreateConversationRequest req,
                                                          @RequestAttribute("mobileUserId") Long userId) {
        String title = (req == null) ? null : req.getTitle();
        Conversation conversation = conversationService.createConversation(userId, title, null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conversationId", conversation.getId());
        return Result.success(data);
    }

    /**
     * 会话历史消息。归属校验(非本人/不存在返回错误);
     * 消息 media 里 images 引用的 taskId 解析为可访问 url(查 generation_task.result_urls)。
     *
     * @param id     会话 id
     * @param userId 当前登录用户(登录态注入)
     * @return 消息视图列表(media 已补 url)
     */
    @GetMapping("/conversations/{id}/messages")
    public Result<List<MessageVO>> messages(@PathVariable Long id,
                                            @RequestAttribute("mobileUserId") Long userId) {
        Conversation conversation = conversationService.getOwned(id, userId);
        if (conversation == null) {
            return Result.error(403, "会话不存在或无权访问");
        }
        List<Message> list = messageService.listByConversation(id);
        return Result.success(toMessageViews(list));
    }

    /**
     * 会话历史消息游标分页。首次返回最近一页，beforeId 用于继续向前加载。
     * 保留原全量接口，避免后端先部署时影响已发布客户端。
     */
    @GetMapping("/conversations/{id}/messages/page")
    public Result<MessagePageVO> messagePage(
            @PathVariable Long id,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "30") Integer pageSize,
            @RequestAttribute("mobileUserId") Long userId) {
        Conversation conversation = conversationService.getOwned(id, userId);
        if (conversation == null) {
            return Result.error(403, "会话不存在或无权访问");
        }
        int size = Math.max(1, Math.min(pageSize == null ? 30 : pageSize, 50));
        List<Message> ordered = new ArrayList<>(messageService.listBefore(id, beforeId, size + 1));
        boolean hasMore = ordered.size() > size;
        if (hasMore) {
            ordered.remove(0);
        }
        Long nextCursor = hasMore && !ordered.isEmpty() ? ordered.get(0).getId() : null;
        return Result.success(new MessagePageVO(toMessageViews(ordered), nextCursor, hasMore));
    }

    /**
     * 查询本人会话中某条用户消息触发的出图任务，供流式连接断开后恢复图片占位与结果。
     */
    @GetMapping("/conversations/{id}/messages/{messageId}/generations")
    public Result<List<GenerationTaskVO>> messageGenerations(
            @PathVariable Long id,
            @PathVariable Long messageId,
            @RequestAttribute("mobileUserId") Long userId) {
        Conversation conversation = conversationService.getOwned(id, userId);
        if (conversation == null) {
            return Result.error(403, "会话不存在或无权访问");
        }
        List<GenerationTaskVO> result = generationTaskService
                .listBySourceMessage(userId, id, messageId)
                .stream()
                .map(this::toGenerationTaskVO)
                .toList();
        return Result.success(result);
    }

    /** 历史会话恢复时查询仍可回答的精确澄清范围。 */
    @GetMapping("/conversations/{id}/pending-clarification")
    public Result<AgentClarification> pendingClarification(
            @PathVariable Long id,
            @RequestAttribute("mobileUserId") Long userId) {
        Conversation conversation = conversationService.getOwned(id, userId);
        if (conversation == null) {
            return Result.error(403, "会话不存在或无权访问");
        }
        return Result.success(agentService.getPendingClarification(userId, id));
    }

    /**
     * 启用中 Skill 列表(供 @ 选择,不含 injectPrompt)。
     * 取 status='0' 且 del_flag='0' 的启用 Skill,按 skill_id 升序(AgentSkill 只读实体未映射 sort 字段)。
     *
     * @return Skill 精简列表
     */
    @GetMapping("/skills")
    public Result<List<Map<String, Object>>> skills() {
        // @TableLogic 自动过滤 del_flag='0';此处再显式约束 status='0'(仅启用)
        LambdaQueryWrapper<AgentSkill> wrapper = new LambdaQueryWrapper<AgentSkill>()
                .eq(AgentSkill::getStatus, "0")
                .orderByAsc(AgentSkill::getSkillId);
        List<AgentSkill> skills = agentSkillMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>(skills.size());
        for (AgentSkill skill : skills) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("skillId", skill.getSkillId());
            item.put("name", skill.getName());
            item.put("icon", skill.getIcon());
            item.put("description", skill.getDescription());
            item.put("capabilities", skill.getCapabilities());
            result.add(item);
        }
        return Result.success(result);
    }

    /**
     * 取本人个性化提示词内容。无则返回 content 为 null。
     *
     * @param userId 当前登录用户(登录态注入)
     * @return {@code {content}}
     */
    @GetMapping("/user-prompt")
    public Result<Map<String, Object>> getUserPrompt(@RequestAttribute("mobileUserId") Long userId) {
        UserPrompt prompt = userPromptService.getByUserId(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("content", prompt == null ? null : prompt.getContent());
        return Result.success(data);
    }

    /**
     * upsert 本人个性化提示词(本期测试用;C 端 UI 后续)。
     *
     * @param req    个性化提示词请求
     * @param userId 当前登录用户(登录态注入)
     * @return 无数据
     */
    @PostMapping("/user-prompt")
    public Result<Void> upsertUserPrompt(@RequestBody UserPromptRequest req,
                                         @RequestAttribute("mobileUserId") Long userId) {
        userPromptService.upsert(userId, req == null ? null : req.getContent());
        return Result.success();
    }

    // ===== 响应拼装 =====

    /**
     * 出图任务实体转视图:result_urls JSON 串反序列化为字符串列表。
     */
    private GenerationTaskVO toGenerationTaskVO(GenerationTask task) {
        return GenerationTaskVO.from(task, objectMapper);
    }

    private Result<Map<String, Object>> replayWorkItem(Long userId, String taskId, int seq, boolean failedOnly) {
        try {
            String replayTaskId = generationTaskService.replayWorkItem(userId, taskId, seq, failedOnly);
            return Result.success(Map.of("taskId", replayTaskId));
        } catch (InsufficientBeanException e) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("errorCode", "INSUFFICIENT_BEANS");
            data.put("action", "RECHARGE");
            data.put("balance", e.getBalance());
            data.put("needed", e.getNeeded());
            Result<Map<String, Object>> result = Result.error(46001, "豆子余额不足,请充值后再生成~");
            result.setData(data);
            return result;
        } catch (ServiceException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 解析消息 media 里 images 引用的 taskId 为可访问 url。
     *
     * <p>media 两种形态:① 用户上传图为纯 URL 字符串数组(原样返回);
     * ② assistant 产出为 {@code [{taskId,seq}]},逐项查 generation_task.result_urls 取第 seq 张(从 1 起)
     * 补 url 后返回 {@code [{taskId,seq,url}]}。解析失败或非预期结构则原样返回,不阻断历史读取。</p>
     *
     * @param media 原始 media JSON 串(可空)
     * @return 补 url 后的 media JSON 串;无 images 引用则原样返回
     */
    private List<MessageVO> toMessageViews(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        Set<String> taskIds = new LinkedHashSet<>();
        for (Message message : messages) {
            collectMediaTaskIds(message == null ? null : message.getMedia(), taskIds);
        }
        Map<String, GenerationTask> tasks = generationTaskService.mapByTaskIds(taskIds);
        List<MessageVO> result = new ArrayList<>(messages.size());
        for (Message message : messages) {
            MessageVO vo = MessageVO.from(message);
            if (vo != null) {
                vo.setMedia(resolveMediaUrls(vo.getMedia(), tasks));
                result.add(vo);
            }
        }
        return result;
    }

    private void collectMediaTaskIds(String media, Set<String> taskIds) {
        if (!StringUtils.hasText(media)) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(media);
            if (!root.isArray()) {
                return;
            }
            for (JsonNode node : root) {
                String taskId = node.path("taskId").asText(null);
                if (StringUtils.hasText(taskId)) {
                    taskIds.add(taskId);
                }
            }
        } catch (Exception e) {
            log.warn("历史消息 media 任务编号解析失败,跳过批量查询: {}", e.getMessage());
        }
    }

    private String resolveMediaUrls(String media, Map<String, GenerationTask> tasks) {
        if (!StringUtils.hasText(media)) {
            return media;
        }
        try {
            JsonNode root = objectMapper.readTree(media);
            // 仅处理 [{taskId,...}] 形态(产出 images 引用);纯 URL 数组原样返回
            if (!root.isArray() || root.isEmpty() || !root.get(0).has("taskId")) {
                return media;
            }
            List<Map<String, Object>> resolved = new ArrayList<>();
            for (JsonNode node : root) {
                String taskId = node.path("taskId").asText(null);
                int seq = node.path("seq").asInt(1);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("taskId", taskId);
                item.put("seq", seq);
                String url = lookupImageUrl(tasks.get(taskId), seq);
                item.put("url", url);
                item.put("previewUrl", toPreviewUrl(url));
                resolved.add(item);
            }
            return objectMapper.writeValueAsString(resolved);
        } catch (Exception e) {
            log.warn("历史消息 media 解析失败,原样返回: {}", e.getMessage());
            return media;
        }
    }

    /**
     * 从当前页已批量读取的任务中取 result_urls 第 seq 张 URL(seq 从 1 起)。
     * 任务不存在、未出图或序号越界返回 null(前端按 null 显示占位/加载中)。
     */
    private String lookupImageUrl(GenerationTask task, int seq) {
        if (task == null) {
            return null;
        }
        List<String> urls = parseUrlArray(task.getResultUrls());
        int index = seq - 1;
        if (index < 0 || index >= urls.size()) {
            return null;
        }
        return urls.get(index);
    }

    /**
     * 解析 result_urls JSON 数组串为字符串列表;空或解析失败返回空列表。
     */
    private List<String> parseUrlArray(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<String> urls = new ArrayList<>();
            JsonNode root = objectMapper.readTree(json);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    urls.add(node.asText());
                }
            }
            return urls;
        } catch (Exception e) {
            log.warn("result_urls 解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** 新生成文件按固定同名规则派生预览图；旧文件直接回退原图。 */
    private List<String> toPreviewUrls(List<String> originals) {
        List<String> previews = new ArrayList<>(originals.size());
        for (String url : originals) {
            previews.add(toPreviewUrl(url));
        }
        return previews;
    }

    private String toPreviewUrl(String url) {
        if (StringUtils.hasText(url) && url.contains("/profile/generated/") && url.endsWith(".png")) {
            return url.substring(0, url.length() - 4) + ".preview.jpg";
        }
        return url;
    }

    // ===== 入参 DTO(仅本类用,不含 userId,防越权)=====

    /**
     * 新建会话请求(可选 title)。
     */
    @Data
    public static class CreateConversationRequest {
        /** 会话标题(可空,空走「新对话」兜底) */
        private String title;
    }

    /**
     * 个性化提示词 upsert 请求。
     */
    @Data
    public static class UserPromptRequest {
        /** 个性化提示词文本 */
        private String content;
    }
}
