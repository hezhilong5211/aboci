package com.aurora.ai.mq;

import com.aurora.ai.agent.billing.BillingCalculator;
import com.aurora.ai.agent.billing.IBeanDeductPort;
import com.aurora.ai.agent.domain.GenerationTask;
import com.aurora.ai.agent.service.IGenerationTaskService;
import com.aurora.ai.agent.service.ImagePromptCompiler;
import com.aurora.ai.domain.SysAiModel;
import com.aurora.ai.mapper.SysAiModelMapper;
import com.aurora.ai.provider.GptsImageClient;
import com.aurora.ai.provider.YunwuImageClient;
import com.aurora.ai.provider.support.ImageConcurrencyGate;
import com.aurora.ai.provider.support.RecoverableApiException;
import com.aurora.common.core.exception.ServiceException;
import com.aurora.framework.mq.TaskConsumer;
import com.aurora.framework.mq.TaskMessage;
import com.aurora.framework.mq.TaskProducer;
import com.aurora.framework.storage.ProfileStorage;
import com.aurora.framework.utils.EncryptUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 出图异步任务消费者(PRD §六 状态机)。
 *
 * <p>从 Redis Streams 消费出图任务:读 generation_task → 置 RUNNING → 按 provider 分派出图 client
 * 循环出图 n 张 → 落盘 /profile/*.png → 回写状态/结果。</p>
 *
 * <p>容错口径(PRD §六/§十三-2):单张异常在循环内自行兜住并累计；零成功时置 FAILED 并退款后正常确认消息，
 * 不再触发通用队列整批重投，确保上游技术失败最多只重试一次。部分成功直接置 SUCCEEDED。</p>
 *
 * 任务类型:generation-task;队列:stream:generation-task;消费者组:generation-task-group。
 */
@Slf4j
@Component
public class GenerationTaskConsumer extends TaskConsumer {

    private final IGenerationTaskService generationTaskService;
    private final YunwuImageClient yunwuImageClient;
    private final GptsImageClient gptsImageClient;
    private final SysAiModelMapper sysAiModelMapper;
    private final EncryptUtils encryptUtils;
    private final BillingCalculator billingCalculator;
    /** 出图并发闸:按 provider 限同时在途请求数,防触发中转站并发上限 */
    private final ImageConcurrencyGate imageConcurrencyGate;
    /** 解析 ref_images JSON 数组串(图生图参考图 /profile URL) */
    private final ObjectMapper objectMapper;
    private final ProfileStorage profileStorage;

    /** 单张出图对超时、连接异常、408/429/5xx 最多自动重试一次。 */
    private static final int PROVIDER_MAX_RETRY = 1;

    /**
     * 出图并行消费者数(默认 30,与云雾并发闸对齐;可经 {@code -Dai.image.parallelism=} 或
     * 环境变量 {@code AI_IMAGE_PARALLELISM} 覆盖)。构造期解析,故用静态方法而非 @Value。
     */
    private static int imageParallelism() {
        String v = System.getProperty("ai.image.parallelism");
        if (v == null) {
            v = System.getenv("AI_IMAGE_PARALLELISM");
        }
        try {
            return v == null ? 30 : Math.max(1, Integer.parseInt(v.trim()));
        } catch (NumberFormatException e) {
            return 30;
        }
    }

    /**
     * 豆子扣减端口(跨模块 SPI,admin 提供 BeanDeductAdapter)。
     * 可选注入:理论上 admin 必加载,为空时出图结算退还降级跳过并 log.warn。
     */
    @Autowired(required = false)
    private IBeanDeductPort beanDeductPort;

    public GenerationTaskConsumer(StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            TaskProducer taskProducer,
            IGenerationTaskService generationTaskService,
            YunwuImageClient yunwuImageClient,
            GptsImageClient gptsImageClient,
            SysAiModelMapper sysAiModelMapper,
            EncryptUtils encryptUtils,
            BillingCalculator billingCalculator,
            ImageConcurrencyGate imageConcurrencyGate,
            ProfileStorage profileStorage) {
        super(redisTemplate, objectMapper, taskProducer,
                "stream:generation-task", // 队列名称
                "generation-task-group", // 消费者组名称
                imageParallelism()); // 并行消费者数(与云雾并发闸对齐,默认 30)
        this.generationTaskService = generationTaskService;
        this.yunwuImageClient = yunwuImageClient;
        this.gptsImageClient = gptsImageClient;
        this.sysAiModelMapper = sysAiModelMapper;
        this.encryptUtils = encryptUtils;
        this.billingCalculator = billingCalculator;
        this.imageConcurrencyGate = imageConcurrencyGate;
        this.objectMapper = objectMapper;
        this.profileStorage = profileStorage;
    }

    @Override
    protected void processTask(TaskMessage message) throws Exception {
        // payload 用自增 id,数字字段用 toString()+Long.valueOf 兜底(可能反序列化为 Integer/Long)
        Long generationTaskId = Long.valueOf(message.getPayload().get("generationTaskId").toString());

        GenerationTask task = generationTaskService.getById(generationTaskId);
        if (task == null) {
            // 任务行不存在,重试无意义但交由基类计数后入死信,记录便于排查
            log.error("❌ 出图任务不存在: generationTaskId={}", generationTaskId);
            throw new ServiceException("出图任务不存在: " + generationTaskId);
        }
        String taskId = task.getTaskId();
        log.info("🎨 开始出图任务: taskId={}, n={}, modelId={}", taskId, task.getN(), task.getModelId());

        // 置处理中
        generationTaskService.markStatus(taskId, "RUNNING", null, null, null);

        // 取出图模型配置(模型缺失/provider 不支持视为任务级失败,走零成功分支)
        SysAiModel model = sysAiModelMapper.selectById(task.getModelId());
        if (model == null) {
            generationTaskService.markStatus(taskId, "FAILED", null, 0, "出图模型不存在");
            throw new ServiceException("出图模型不存在: modelId=" + task.getModelId());
        }
        String provider = model.getProvider();
        if (!"yunwu".equalsIgnoreCase(provider) && !"gptsapi".equalsIgnoreCase(provider)) {
            // provider 配置错误:出图未发起,零成功结算 → 全额退还预扣
            generationTaskService.markStatus(taskId, "FAILED", null, 0, "出图模型供应商不支持");
            settleRefund(task, model, 0);
            log.error("出图模型供应商不支持: taskId={}, provider={}", taskId, provider);
            return;
        }
        String apiKey = encryptUtils.decrypt(model.getApiKey());

        List<GenerationCallItem> callItems;
        try {
            callItems = resolveCallItems(task);
        } catch (ServiceException e) {
            // 已冻结计划损坏属于不可恢复输入错误，不得通过整批重投重新解释或扣费。
            generationTaskService.markStatus(taskId, "FAILED", null, 0, e.getMessage());
            settleRefund(task, model, 0);
            log.error("出图输入计划无效: taskId={}, error={}", taskId, e.getMessage());
            return;
        }
        int n = callItems.size();
        boolean img2img = callItems.stream().anyMatch(item ->
                !item.originalReferences().isEmpty());
        if (img2img && !"yunwu".equalsIgnoreCase(provider)) {
            // 图生图模型非云雾:出图未发起,零成功结算 → 全额退还预扣
            generationTaskService.markStatus(taskId, "FAILED", null, 0, "图生图仅支持云雾出图模型");
            settleRefund(task, model, 0);
            log.error("图生图模型供应商不支持: taskId={}, provider={}", taskId, provider);
            return;
        }

        // 结果数组按请求序号固定长度，失败项保留空串，避免后续成功图片前移导致 seq 对错图。
        List<String> resultUrls = new ArrayList<>(Collections.nCopies(n, ""));
        int failCount = 0;
        String referenceFailure = null;

        // 所有任务项独立提交，不等待任何成品；并发仍由供应商闸限制。
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            ExecutorCompletionService<JobResult> completion = new ExecutorCompletionService<>(executor);
            int submitted = 0;
            for (int index = 0; index < n; index++) {
                GenerationCallItem item = callItems.get(index);
                final int outputIndex = index;
                try {
                    List<byte[]> references = resolveReferences(taskId, index + 1, item);
                    completion.submit(() -> generateOne(outputIndex, taskId, task, model,
                            apiKey, provider, item.prompt(), references));
                    submitted++;
                } catch (Exception e) {
                    failCount++;
                    if (referenceFailure == null) {
                        referenceFailure = "第" + (index + 1) + "张参考图不可用，请检查素材后重试";
                    }
                    log.error("出图参考图读取失败: taskId={}, seq={}, error={}", taskId, index + 1, e.getMessage());
                }
            }
            for (int i = 0; i < submitted; i++) {
                JobResult result = completion.take().get();
                if (result.url() != null) {
                    resultUrls.set(result.index(), result.url());
                    generationTaskService.markStatus(taskId, "RUNNING", resultUrls,
                            countSuccessful(resultUrls), null);
                } else {
                    failCount++;
                    log.error("出图单张失败: taskId={}, seq={}/{}, error={}",
                            taskId, result.index() + 1, n, result.error());
                }
            }
        }

        int successCount = countSuccessful(resultUrls);
        if (successCount >= 1) {
            // 部分失败也算 SUCCEEDED,error_msg 记失败张数(脱敏,不透传堆栈)
            String errorMsg = (failCount > 0) ? ("部分失败:成功" + successCount + "张,失败" + failCount + "张") : null;
            generationTaskService.markStatus(taskId, "SUCCEEDED", resultUrls, successCount, errorMsg);
            // 结算:按实际成功张数退还差额(部分失败退差额,全成功不退),写 bean_cost
            settleRefund(task, model, successCount);
            log.info("✅ 出图任务完成: taskId={}, success={}, fail={}", taskId, successCount, failCount);
        } else {
            // 零成功:置 FAILED 并按 0 成功结算(全额退还预扣)。
            // provider 已在单张调用内最多重试一次，此处正常返回让队列 ACK，禁止通用消费者整批重投。
            generationTaskService.markStatus(taskId, "FAILED", null, 0,
                    referenceFailure != null ? referenceFailure : "出图全部失败,请稍后重试");
            settleRefund(task, model, 0);
            log.error("❌ 出图任务全部失败: taskId={}, n={}", taskId, n);
        }
    }

    /**
     * 出图单张调用 + 退避重试:仅对 {@link RecoverableApiException}(超时、连接异常、408/429/5xx)退避重试
     * (等待 2 秒,最多 {@link #PROVIDER_MAX_RETRY} 次),重试期间仍持并发令牌避免立即再撞限流;
     * 不可恢复异常(如 400 参数错)立即抛出交由调用方计失败。
     *
     * @param taskId 任务号(仅日志用)
     * @param call   provider 出图调用
     * @return 图片字节
     * @throws Exception 重试耗尽或不可恢复异常
     */
    private byte[] callWithBackoff(String taskId, java.util.concurrent.Callable<byte[]> call) throws Exception {
        int attempt = 0;
        while (true) {
            try {
                return call.call();
            } catch (RecoverableApiException e) {
                if (attempt >= PROVIDER_MAX_RETRY) {
                    throw e;
                }
                long backoffMs = 1000L * (1L << (attempt + 1)); // 2s / 4s / 8s
                attempt++;
                log.warn("出图可恢复错误(status={}),退避重试 {}/{},等待 {}ms: taskId={}",
                        e.getStatusCode(), attempt, PROVIDER_MAX_RETRY, backoffMs, taskId);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    // 退避被中断(优雅关闭):恢复中断标志并向上抛,不吞中断信号
                    Thread.currentThread().interrupt();
                    throw ie;
                }
            }
        }
    }

    /** 按冻结编号组装提交前素材，任何缺失都不能前移编号。 */
    private List<byte[]> resolveReferences(String taskId, int outputSeq, GenerationCallItem item) {
        List<ReferenceInput> inputs = item.originalReferences();
        if (inputs.size() > 9) {
            throw new ServiceException("参考图总数超过9张");
        }
        List<byte[]> references = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            references.add(readRequiredReference(inputs.get(i), i + 1));
        }
        // 仅记录完整装配成功、即将提交的输入清单；不记录文件URL、图片内容或凭据。
        log.info("出图输入清单: taskId={}, seq={}, count={}", taskId, outputSeq, inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            ReferenceInput input = inputs.get(i);
            byte[] bytes = references.get(i);
            log.info("出图实际输入: taskId={}, seq={}, input={}, source={}, sourceIndex={}, sha256={}, bytes={}",
                    taskId, outputSeq, i + 1, input.sourceType(), input.sourceIndex(), sha256(bytes), bytes.length);
        }
        return List.copyOf(references);
    }

    private JobResult generateOne(int index, String taskId, GenerationTask task, SysAiModel model,
                                  String apiKey, String provider, String prompt, List<byte[]> references) {
        boolean acquired = false;
        try {
            imageConcurrencyGate.acquire(provider);
            acquired = true;
            byte[] image = callWithBackoff(taskId, () -> {
                if (!references.isEmpty()) {
                    return yunwuImageClient.imageToImage(model.getBaseUrl(), apiKey,
                            model.getModelDeployment(), prompt, task.getResolution(),
                            task.getAspectRatio(), references);
                }
                if ("yunwu".equalsIgnoreCase(provider)) {
                    return yunwuImageClient.textToImage(model.getBaseUrl(), apiKey,
                            model.getModelDeployment(), prompt, task.getResolution(), task.getAspectRatio());
                }
                return gptsImageClient.textToImage(model.getBaseUrl(), apiKey,
                        model.getModelDeployment(), prompt, task.getResolution(), task.getAspectRatio());
            });
            return new JobResult(index, saveImageToProfile(image), null);
        } catch (Exception e) {
            return new JobResult(index, null, e.getMessage());
        } finally {
            if (acquired) {
                imageConcurrencyGate.release(provider);
            }
        }
    }

    /**
     * 出图结算退还 + 写 bean_cost(对齐 PRD §五:图片 = 预扣 + 结算退还)。
     *
     * <p>预扣已由 AgentService 在提交任务前按 n 扣减(bizNo="img:"+taskId);此处按<b>实际成功张数</b>结算:
     * <ul>
     *   <li>{@code needed = imageBeans(model, n)}:预扣额(按申请张数);</li>
     *   <li>{@code actualCost = imageBeans(model, successCount)}:实扣额(按成功张数,零成功为 0);</li>
     *   <li>{@code refundAmt = needed − actualCost}:应退差额;{@code >0} 且 port 可用时退还。</li>
     * </ul>
     * 写 {@code generation_task.bean_cost = actualCost}。
     *
     * <p><b>防重(关键)</b>:虽然明确失败后的正常路径会直接确认队列消息，仍以 {@code bean_cost}
     * 作结算幂等哨兵，防止进程在结算临界点异常后重复消费并再次退款。首次结算后即写入
     * bean_cost(0 也算已结算)，重入时读到非 null 即不再退。
     *
     * <p>{@code beanDeductPort == null}(理论不会,admin 必加载适配器)时跳过退还仅 log.warn,不阻断任务流转。
     *
     * @param task         出图任务(取 taskId/userId/n/beanCost)
     * @param model        出图模型(取 beanPerImage)
     * @param successCount 实际成功张数(零成功传 0)
     */
    private void settleRefund(GenerationTask task, SysAiModel model, int successCount) {
        // 幂等哨兵:已结算过(bean_cost 非 null)则跳过,防零成功重试重复退款
        if (task.getBeanCost() != null) {
            return;
        }
        String taskId = task.getTaskId();
        int n = (task.getN() == null) ? 1 : task.getN();
        long needed = billingCalculator.imageBeans(model, n, task.getResolution());
        long actualCost = billingCalculator.imageBeans(model, successCount, task.getResolution());
        long refundAmt = needed - actualCost;

        if (refundAmt > 0) {
            if (beanDeductPort != null) {
                beanDeductPort.refund(task.getUserId(), refundAmt, "img:" + taskId, "出图失败退还");
                log.info("出图结算退还: taskId={}, userId={}, needed={}, actualCost={}, refund={}",
                        taskId, task.getUserId(), needed, actualCost, refundAmt);
            } else {
                // 适配器未注入:降级跳过退还,记录便于人工对账补退
                log.warn("豆子扣减端口未注入,出图退还跳过: taskId={}, refund={}", taskId, refundAmt);
            }
        }
        // 写实扣豆子(0 也写,作为已结算哨兵)
        generationTaskService.setBeanCost(taskId, actualCost);
    }

    /** 只解析冻结的URL顺序；文件读取留在单项执行阶段，缺图不会影响无关任务项。 */
    private List<String> parseReferenceUrls(String refImagesJson) {
        if (refImagesJson == null || refImagesJson.isBlank()) {
            return List.of();
        }
        try {
            return parseReferenceUrls(objectMapper.readTree(refImagesJson));
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("出图参考图列表格式错误");
        }
    }

    private List<String> parseReferenceUrls(JsonNode array) {
        if (array == null || array.isMissingNode() || array.isNull()) {
            return List.of();
        }
        if (!array.isArray() || array.size() > 9) {
            throw new ServiceException("出图参考图列表格式错误");
        }
        List<String> urls = new ArrayList<>(array.size());
        for (JsonNode node : array) {
            if (!node.isTextual() || node.asText().isBlank()) {
                throw new ServiceException("出图参考图列表包含空或非法地址");
            }
            urls.add(node.asText());
        }
        return List.copyOf(urls);
    }

    /** 新快照核对原始编号，旧任务和单张重试按已保存的完整实际顺序兼容读取。 */
    private List<ReferenceInput> originalReferences(JsonNode indexes, List<String> itemUrls,
                                                     List<String> allUrls) {
        boolean hasIndexes = indexes != null && !indexes.isMissingNode() && !indexes.isNull();
        if (hasIndexes && (!indexes.isArray() || indexes.size() != itemUrls.size())) {
            throw new ServiceException("出图参考图编号与地址数量不一致");
        }
        List<ReferenceInput> inputs = new ArrayList<>(itemUrls.size());
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < itemUrls.size(); i++) {
            int sourceIndex = i + 1;
            if (hasIndexes) {
                JsonNode value = indexes.get(i);
                if (!value.isIntegralNumber() || !value.canConvertToInt()) {
                    throw new ServiceException("出图参考图编号非法");
                }
                sourceIndex = value.asInt();
                if (sourceIndex < 1 || sourceIndex > allUrls.size() || !seen.add(sourceIndex)
                        || !itemUrls.get(i).equals(allUrls.get(sourceIndex - 1))) {
                    throw new ServiceException("出图参考图编号与冻结素材不一致");
                }
            }
            inputs.add(new ReferenceInput(itemUrls.get(i), hasIndexes ? "UPLOADED" : "REFERENCE", sourceIndex));
        }
        return List.copyOf(inputs);
    }

    /** 解析批量任务项；旧任务没有 request_items 时按原 prompt+n 生成同款任务项。 */
    private List<GenerationCallItem> resolveCallItems(GenerationTask task) {
        List<String> allUrls = parseReferenceUrls(task.getRefImages());
        if (task.getRequestItems() == null || task.getRequestItems().isBlank()) {
            int n = task.getN() == null ? 1 : task.getN();
            if (n < 1 || n > 9 || task.getPrompt() == null || task.getPrompt().isBlank()) {
                throw new ServiceException("出图任务数量或提示词无效");
            }
            List<ReferenceInput> refs = originalReferences(null, allUrls, allUrls);
            List<GenerationCallItem> legacy = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                legacy.add(new GenerationCallItem(task.getPrompt(), refs));
            }
            return legacy;
        }
        try {
            JsonNode root = objectMapper.readTree(task.getRequestItems());
            if (!root.isArray() || root.isEmpty() || root.size() > 9
                    || (task.getN() != null && task.getN() != root.size())) {
                throw new ServiceException("批量生图任务项数量无效");
            }
            List<GenerationCallItem> items = new ArrayList<>();
            for (JsonNode node : root) {
                JsonNode outputSeq = node.path("seq");
                if (!outputSeq.isMissingNode() && (!outputSeq.isIntegralNumber()
                        || !outputSeq.canConvertToInt() || outputSeq.asInt() != items.size() + 1)) {
                    throw new ServiceException("批量生图任务序号与结果位置不一致");
                }
                String prompt = node.path("prompt").asText(null);
                if (prompt == null || prompt.isBlank()) {
                    throw new ServiceException("批量生图任务缺少提示词");
                }
                ImagePromptCompiler.requireIndependent(node);
                List<ReferenceInput> refs = originalReferences(node.path("referenceImageIndexes"),
                        parseReferenceUrls(node.path("refImages")), allUrls);
                if (refs.size() > 9) {
                    throw new ServiceException("批量生图任务参考图超过9张");
                }
                items.add(new GenerationCallItem(prompt, refs));
            }
            return items;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("批量生图任务解析失败", e);
        }
    }

    private int countSuccessful(List<String> urls) {
        return (int) urls.stream().filter(url -> url != null && !url.isBlank()).count();
    }

    /** 保持既有profile路径边界，返回完整素材或失败；异常信息不得泄露路径。 */
    private byte[] readRequiredReference(ReferenceInput input, int inputIndex) {
        String url = input.url();
        if (url == null || !url.startsWith("/profile/") || url.contains("..")) {
            throw new ServiceException("本任务输入图" + inputIndex + "地址无效");
        }
        try {
            byte[] bytes = profileStorage.read(url);
            if (bytes == null || bytes.length == 0) {
                throw new ServiceException("参考素材为空");
            }
            return bytes;
        } catch (Exception e) {
            log.warn("出图必需素材读取失败: input={}, source={}, sourceIndex={}, errorType={}",
                    inputIndex, input.sourceType(), input.sourceIndex(), e.getClass().getSimpleName());
            throw new ServiceException("本任务输入图" + inputIndex + "读取失败或为空");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256不可用", e);
        }
    }

    /**
     * 将图片字节落盘到上传目录,返回经 /profile/** 静态映射可访问的相对 URL(复刻 AiServiceImpl 逻辑)。
     */
    private String saveImageToProfile(byte[] image) {
        String id = UUID.randomUUID().toString();
        String fileName = "generated/" + id + ".png";
        try {
            String originalUrl = profileStorage.write(fileName, image);
            try {
                profileStorage.write("generated/" + id + ".preview.jpg", createPreviewJpeg(image));
            } catch (Exception e) {
                // 预览图失败不影响原图交付，接口按原图回退。
                log.warn("生成图片预览图派生失败，回退原图: url={}, err={}", originalUrl, e.getMessage());
            }
            return originalUrl;
        } catch (Exception e) {
            throw new ServiceException("图片落盘失败:" + e.getMessage(), e);
        }
    }

    /** 生成长边不超过 768px 的 JPEG 预览图，原图继续用于全屏预览和保存。 */
    private byte[] createPreviewJpeg(byte[] imageBytes) throws Exception {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (source == null) {
            throw new ServiceException("无法解析生成图片");
        }
        int maxEdge = 768;
        double scale = Math.min(1D, (double) maxEdge / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage preview = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = preview.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new ServiceException("运行环境不支持 JPEG 编码");
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ImageOutputStream imageOut = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(imageOut);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.8F);
            }
            writer.write(null, new IIOImage(preview, null, null), param);
            return out.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private record ReferenceInput(String url, String sourceType, int sourceIndex) {
    }

    private record GenerationCallItem(String prompt, List<ReferenceInput> originalReferences) {
    }

    private record JobResult(int index, String url, String error) {
    }
}
