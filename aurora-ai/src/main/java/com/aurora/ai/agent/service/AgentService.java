package com.aurora.ai.agent.service;

import com.aurora.ai.agent.billing.BillingCalculator;
import com.aurora.ai.agent.billing.IBeanDeductPort;
import com.aurora.ai.agent.billing.InsufficientBeanException;
import com.aurora.ai.agent.domain.Conversation;
import com.aurora.ai.agent.domain.ChatRun;
import com.aurora.ai.agent.domain.GenerationTask;
import com.aurora.ai.agent.domain.MessageAttachment;
import com.aurora.ai.agent.domain.Message;
import com.aurora.ai.agent.dto.ClaimedReferenceScope;
import com.aurora.ai.agent.dto.ChatRunVO;
import com.aurora.ai.agent.dto.AgentChatRequest;
import com.aurora.ai.agent.dto.AgentResumeRequest;
import com.aurora.ai.agent.dto.AgentImageInput;
import com.aurora.ai.agent.dto.GenerateImageItem;
import com.aurora.ai.agent.dto.GenerationOptionsVO;
import com.aurora.ai.agent.dto.ImageReferenceBinding;
import com.aurora.ai.agent.dto.MessageAttachmentDraft;
import com.aurora.ai.agent.security.ContentCheckResult;
import com.aurora.ai.agent.security.IContentSecurityPort;
import com.aurora.ai.domain.SysAiModel;
import com.aurora.ai.dto.AgentResult;
import com.aurora.ai.dto.AgentClarification;
import com.aurora.ai.dto.AgentSseEvent;
import com.aurora.ai.mapper.SysAiModelMapper;
import com.aurora.ai.provider.ResponsesApiClient;
import com.aurora.common.core.exception.ServiceException;
import com.aurora.framework.mq.ApiRateLimiter;
import com.aurora.framework.storage.ProfileStorage;
import com.aurora.framework.utils.EncryptUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;
import reactor.core.scheduler.Scheduler;

import java.math.BigDecimal;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Agent 编排核心服务(PRD §八/§九,路径 B 裸 HTTP 自管多轮工具循环)。
 *
 * <p>职责:接收一次主对话请求,组装三层提示词,直打默认多模态对话模型
 * {@code /chat/completions}(stream=true + tools),自管 tool_calls 累积与回灌
 * ({@code generate_images} 批量异步出图),循环至模型不再要求调工具,
 * 以分型 SSE 事件({@link AgentSseEvent})流式产出,并持久化 conversation/message。</p>
 *
 * <p>流式模型:用 {@link Sinks.Many} 单播缓冲;阻塞的 JDK HttpClient 逐行读放在 {@link Schedulers#boundedElastic()}
 * 执行,边读边 {@code sink.tryEmitNext(event.toJsonLine())},结束 {@code tryEmitComplete()}。
 * 任意异常 → emit error 友好文案后正常收尾,不落脏数据。</p>
 */
@Slf4j
@Service
public class AgentService {

    /** 跨轮上下文回灌:取最近 N 条历史消息(user/assistant 文本)重建 messages 前缀 */
    private static final int RECENT_CONTEXT_LIMIT = 10;

    /** 工具循环最大轮数,防模型无限要求调工具导致死循环 */
    private static final int MAX_TOOL_ROUNDS = 8;

    /** 对话模型流式请求超时(秒):工具循环单轮请求 */
    private static final int STREAM_TIMEOUT_SEC = 120;

    /** 单次对话最多接收 9 张图片,顺序即图片编号 */
    private static final int MAX_INPUT_IMAGES = 9;

    /** Agent 单次批量出图最多 9 张 */
    private static final int MAX_BATCH_IMAGES = 9;

    /** 普通用户澄清最多两轮，同一问题类型只能问一次。 */
    private static final int MAX_CLARIFICATION_ROUNDS = 2;

    /** 澄清范围一天后失效，避免旧任务在未来消息中复活。 */
    private static final int CLARIFICATION_TTL_HOURS = 24;

    private static final Set<String> CLARIFICATION_KEYS = Set.of(
            "OUTPUT_COUNT", "REFERENCE_MAPPING", "COMBINATION_POLICY", "SUBJECT_IDENTITY",
            "EXACT_TEXT", "MISSING_REFERENCE", "HARD_CONFLICT", "INSUFFICIENT_BALANCE");

    /** 余额不足确认卡及服务端保存计划使用的稳定标识。 */
    private static final String INSUFFICIENT_BALANCE_KEY = "INSUFFICIENT_BALANCE";
    private static final String AFFORDABLE_PLAN_CONTEXT_KEY = "affordablePlan";
    private static final String GENERATE_AFFORDABLE_OPTION = "generate_affordable";
    private static final String GENERATE_REQUESTED_OPTION = "generate_requested";

    /** 末尾结构化产出 fenced 代码块匹配:```aurora-result {json} ```(DOTALL 跨行;
     *  贪婪取到最后一个收尾围栏——非贪婪在 JSON 值意外含 "}```" 形态时会截断捕获) */
    private static final Pattern RESULT_BLOCK_PATTERN =
            Pattern.compile("```aurora-result\\s*(\\{.*})\\s*```", Pattern.DOTALL);

    private final SysAiModelMapper aiModelMapper;
    private final IConversationService conversationService;
    private final IMessageService messageService;
    private final IMessageAttachmentService messageAttachmentService;
    private final IReferenceScopeService referenceScopeService;
    private final IGenerationTaskService generationTaskService;
    private final PromptAssembler promptAssembler;
    private final ProfileStorage profileStorage;
    private final EncryptUtils encryptUtils;
    private final ObjectMapper objectMapper;
    private final BillingCalculator billingCalculator;
    private final ResponsesApiClient responsesApiClient;
    private final ApiRateLimiter apiRateLimiter;

    /** 活跃对话按本次用户消息 id 登记，供客户端显式停止。 */
    private final ConcurrentMap<Long, ActiveRun> activeRuns = new ConcurrentHashMap<>();
    /** 活跃对话按客户端请求 id 登记，覆盖用户消息落库前的排队阶段。 */
    private final ConcurrentMap<String, ActiveRun> requestRuns = new ConcurrentHashMap<>();
    private final ConcurrentMap<Sinks.Many<String>, ActiveRun> sinkRuns = new ConcurrentHashMap<>();
    private Scheduler orchestrationScheduler = Schedulers.boundedElastic();

    @Autowired(required = false)
    private ChatRunService chatRunService;

    @Autowired(required = false)
    private ConversationMemoryService memoryService;

    @Autowired(required = false)
    private ConversationCapacityService conversationCapacityService;

    /**
     * 豆子扣减端口(跨模块 SPI):由 aurora-admin 的 BeanDeductAdapter 实现。
     * 可选注入:为 null 时计费降级跳过(仅 log.warn),不阻断对话/出图。
     */
    @Autowired(required = false)
    private IBeanDeductPort beanDeductPort;

    /**
     * 文本内容安全审核端口(跨模块 SPI):由 aurora-admin 的适配器实现。
     * 可选注入:为 null 时降级放行(admin 未提供适配器则不拦截)。
     */
    @Autowired(required = false)
    private IContentSecurityPort contentSecurityPort;

    /** 文本对话最低门槛(豆子):只拦截零余额,图片费用仍由出图任务按张精确预扣。 */
    @Value("${ai.billing.min-text-beans:1}")
    private long minTextBeans;

    public AgentService(SysAiModelMapper aiModelMapper,
                        IConversationService conversationService,
                        IMessageService messageService,
                        IMessageAttachmentService messageAttachmentService,
                        IReferenceScopeService referenceScopeService,
                        IGenerationTaskService generationTaskService,
                        PromptAssembler promptAssembler,
                        ProfileStorage profileStorage,
                        EncryptUtils encryptUtils,
                        ObjectMapper objectMapper,
                        BillingCalculator billingCalculator,
                        ResponsesApiClient responsesApiClient,
                        ApiRateLimiter apiRateLimiter) {
        this.aiModelMapper = aiModelMapper;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.messageAttachmentService = messageAttachmentService;
        this.referenceScopeService = referenceScopeService;
        this.generationTaskService = generationTaskService;
        this.promptAssembler = promptAssembler;
        this.profileStorage = profileStorage;
        this.encryptUtils = encryptUtils;
        this.objectMapper = objectMapper;
        this.billingCalculator = billingCalculator;
        this.responsesApiClient = responsesApiClient;
        this.apiRateLimiter = apiRateLimiter;
    }

    /**
     * 主对话流式编排入口。每个 Flux 元素为一条分型 SSE 事件的 JSON 行(由 {@link AgentSseEvent#toJsonLine()} 产出)。
     *
     * @param userId 当前登录移动端用户(取自登录态,非入参)
     * @param req    主对话请求
     * @return 分型 SSE 事件流
     */
    public Flux<String> chatStream(Long userId, AgentChatRequest req) {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        ActiveRun activeRun = new ActiveRun(userId);
        if (chatRunService != null) {
            try {
                if (req == null) throw new ServiceException("对话请求不能为空");
                String requestId = StringUtils.hasText(req.getRequestId())
                        ? req.getRequestId().trim() : UUID.randomUUID().toString();
                if (requestId.length() > 64) throw new ServiceException("请求编号过长");
                req.setRequestId(requestId);
                String fingerprint = requestFingerprint(req);
                ChatRun existing = chatRunService.findRequest(userId, requestId);
                if (existing != null) {
                    chatRunService.assertSameRequest(existing, fingerprint, req.getConversationId());
                    return replayRun(existing);
                }
                Conversation conversation = resolveConversation(userId, req);
                req.setConversationId(conversation.getId());
                // Existing request replay above remains available; new work on an oversized chat is rejected
                // before reserving a run, saving another message, calling a model or charging beans.
                if (conversationCapacityService != null) {
                    conversationCapacityService.requireCapacity(userId, conversation.getId());
                }
                ChatRun resumed = chatRunService.findSource(userId, req.getResumeMessageId());
                SysAiModel model = resumed == null ? resolveChatModel(req.getModelId())
                        : aiModelMapper.selectById(resumed.getModelId());
                if (model == null) throw new ServiceException("原任务对话模型已不可用");
                Long imageModelId = resumed == null ? req.getImageModelId() : resumed.getImageModelId();
                String requestedResolution = resumed == null ? req.getImageResolution()
                        : chatRunService.imageResolution(resumed);
                GenerationOptionsVO imageOptions = resolveChatImageOptions(imageModelId, requestedResolution);
                String imageResolution = resolveChatImageResolution(imageOptions, requestedResolution);
                if (imageOptions != null) imageModelId = imageOptions.getModelId();
                ChatRunService.Reservation reservation = chatRunService.reserve(userId, conversation.getId(), requestId,
                        fingerprint, model, imageModelId, imageResolution, resumed);
                if (!reservation.created()) return replayRun(reservation.run());
                activeRun.persisted = reservation.run();
                activeRun.chatModel = chatRunService.applyBillingSnapshot(activeRun.persisted, model);
                req.setModelId(activeRun.persisted.getModelId());
                req.setImageModelId(activeRun.persisted.getImageModelId());
                req.setImageResolution(chatRunService.imageResolution(activeRun.persisted));
            } catch (ConversationLimitException e) {
                return Flux.just(AgentSseEvent.error(e.getMessage(), ConversationLimitException.CODE,
                        ConversationLimitException.ACTION).toJsonLine());
            } catch (ServiceException e) {
                return Flux.just(AgentSseEvent.error(e.getMessage()).toJsonLine());
            }
        }
        sinkRuns.put(sink, activeRun);
        String requestRunKey = requestRunKey(userId, req == null ? null : req.getRequestId());
        activeRun.onQueuedCancellation = () -> {
            cleanupRun(sink, activeRun, requestRunKey);
            sink.tryEmitComplete();
        };
        if (requestRunKey != null) {
            if (requestRuns.putIfAbsent(requestRunKey, activeRun) != null) {
                sinkRuns.remove(sink);
                return Flux.just(AgentSseEvent.error("原任务已在处理中，请勿重复提交").toJsonLine());
            }
        }
        // 阻塞编排(HTTP 流式读 + 落库)放 boundedElastic,避免占用响应式线程
        Disposable worker = orchestrationScheduler
                .schedule(() -> runQueuedOrchestration(userId, req, sink, activeRun, requestRunKey));
        activeRun.attach(worker);
        return sink.asFlux();
    }

    public ChatRunVO getConversationRun(Long userId, Long conversationId) {
        if (conversationService.getOwned(conversationId, userId) == null) {
            throw new ServiceException("会话不存在或无权访问");
        }
        return chatRunService == null ? null : chatRunService.getOwned(userId, conversationId);
    }

    private Flux<String> replayRun(ChatRun run) {
        if (conversationService.getOwned(run.getConversationId(), run.getUserId()) == null) {
            throw new ServiceException("会话不存在或无权访问");
        }
        ChatRunVO snapshot = chatRunService.toView(run);
        List<String> events = new ArrayList<>();
        AgentSseEvent meta = AgentSseEvent.meta(snapshot.getConversationId(), snapshot.getMessageId());
        meta.setRunId(snapshot.getRunId());
        meta.setRequestId(snapshot.getRequestId());
        meta.setStatus(snapshot.getStatus());
        events.add(meta.toJsonLine());
        if (StringUtils.hasText(snapshot.getThinking())) events.add(AgentSseEvent.thinking(snapshot.getThinking()).toJsonLine());
        if (StringUtils.hasText(snapshot.getText())) events.add(AgentSseEvent.text(snapshot.getText()).toJsonLine());
        if (snapshot.getResult() != null) events.add(AgentSseEvent.result(snapshot.getResult()).toJsonLine());
        if (snapshot.getClarification() != null) events.add(AgentSseEvent.clarification(snapshot.getClarification()).toJsonLine());
        if (StringUtils.hasText(snapshot.getErrorMessage())) events.add(AgentSseEvent.error(snapshot.getErrorMessage()).toJsonLine());
        events.add(AgentSseEvent.done().toJsonLine());
        return Flux.fromIterable(events);
    }

    private String requestFingerprint(AgentChatRequest req) {
        try {
            ObjectNode value = objectMapper.valueToTree(req);
            value.remove(List.of("requestId", "conversationId"));
            // Keep pre-resolution client fingerprints replayable across this release.
            if (req.getImageResolution() == null) value.remove("imageResolution");
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new ServiceException("对话请求无法保存");
        }
    }

    private GenerationOptionsVO resolveChatImageOptions(Long imageModelId, String requestedResolution) {
        try {
            return imageModelId == null ? generationTaskService.getGenerationOptions()
                    : generationTaskService.getGenerationOptions(imageModelId);
        } catch (ServiceException error) {
            // A text-only request with no explicit image settings may work without an image channel.
            if (imageModelId != null || StringUtils.hasText(requestedResolution)) throw error;
            return null;
        }
    }

    private String resolveChatImageResolution(GenerationOptionsVO options, String requested) {
        if (!StringUtils.hasText(requested)) return options == null ? null : options.getDefaultResolution();
        if (options == null || options.getSupportedResolutions() == null) {
            throw new ServiceException("后台未配置可用分辨率");
        }
        return options.getSupportedResolutions().stream()
                .filter(value -> value.equalsIgnoreCase(requested.trim())).findFirst()
                .orElseThrow(() -> new ServiceException("所选分辨率未在后台启用"));
    }

    /** 恢复已通过安全审核并落库、但因豆子不足尚未产生助手回复的最后一条用户消息。 */
    public Flux<String> resumeChatStream(Long userId, AgentResumeRequest resume) {
        AgentChatRequest request = new AgentChatRequest();
        if (resume != null) {
            request.setRequestId(resume.getRequestId());
            request.setConversationId(resume.getConversationId());
            request.setResumeMessageId(resume.getSourceMessageId());
            request.setSkill(resume.getSkill());
            request.setClarificationScopeId(resume.getClarificationScopeId());
            request.setClarificationOptionId(resume.getClarificationOptionId());
        }
        return chatStream(userId, request);
    }

    /** 先进入全局对话并发闸，再执行编排；等待阶段不落库、不计费、不调用模型。 */
    private void runQueuedOrchestration(Long userId, AgentChatRequest req, Sinks.Many<String> sink,
                                        ActiveRun activeRun, String requestRunKey) {
        if (!activeRun.beginExecution()) {
            cleanupRun(sink, activeRun, requestRunKey);
            sink.tryEmitComplete();
            return;
        }
        boolean acquired = false;
        try {
            ensureActive(activeRun.cancelled);
            acquired = apiRateLimiter.acquireWithWaitNotice(waiting ->
                    emit(sink, AgentSseEvent.queue("当前排队人数较多，请耐心等待")));
            if (!acquired) {
                emit(sink, AgentSseEvent.error("系统繁忙，请稍后重试"));
                sink.tryEmitComplete();
                return;
            }
            ensureActive(activeRun.cancelled);
            runOrchestration(userId, req, sink, activeRun);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sink.tryEmitComplete();
        } catch (CancellationException e) {
            sink.tryEmitComplete();
        } finally {
            cleanupRun(sink, activeRun, requestRunKey);
            if (acquired) {
                apiRateLimiter.release("chat");
            }
        }
    }

    private void cleanupRun(Sinks.Many<String> sink, ActiveRun run, String requestRunKey) {
        if (!run.cleaned.compareAndSet(false, true)) return;
        if (chatRunService != null && run.persisted != null) {
            String status = run.cancelled.get() ? "CANCELLED" : run.persisted.getStatus();
            if ("QUEUED".equals(status) || "RUNNING".equals(status)) {
                status = run.persisted.getCompletionStatus() == null ? "FAILED" : run.persisted.getCompletionStatus();
            }
            try { chatRunService.finish(run.persisted, status); }
            catch (Exception e) { log.error("运行状态收尾失败: runId={}", run.persisted.getId(), e); }
        }
        sinkRuns.remove(sink);
        if (requestRunKey != null) requestRuns.remove(requestRunKey, run);
    }

    /**
     * 显式停止当前用户的一次对话。返回 false 表示任务已结束、不存在或不属于当前用户。
     */
    public boolean stopChat(Long userId, Long messageId) {
        if (userId == null || messageId == null) {
            return false;
        }
        ActiveRun activeRun = activeRuns.get(messageId);
        if (activeRun == null || !userId.equals(activeRun.userId)) {
            return false;
        }
        return activeRun.cancel();
    }

    /** 排队阶段按 requestId 停止，消息落库后仍兼容按 messageId 停止。 */
    public boolean stopChat(Long userId, String runId) {
        if (userId == null || !StringUtils.hasText(runId)) {
            return false;
        }
        String key = requestRunKey(userId, runId);
        if (key != null) {
            ActiveRun requestRun = requestRuns.get(key);
            if (requestRun != null) {
                return requestRun.cancel();
            }
        }
        try {
            return stopChat(userId, Long.valueOf(runId));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String requestRunKey(Long userId, String requestId) {
        if (userId == null || !StringUtils.hasText(requestId)) {
            return null;
        }
        String normalized = requestId.trim();
        if (normalized.length() > 64) {
            return null;
        }
        return userId + ":" + normalized;
    }

    /** 查询会话中仍可回答的结构化澄清，供历史会话恢复输入状态。 */
    public AgentClarification getPendingClarification(Long userId, Long conversationId) {
        if (userId == null || conversationId == null) {
            return null;
        }
        com.aurora.ai.agent.domain.ReferenceScope scope =
                referenceScopeService.getOpen(conversationId, userId);
        if (scope == null) {
            return null;
        }
        JsonNode pending = parseClarificationContext(scope.getContextJson()).path("pending");
        if (!pending.isObject()) {
            return null;
        }
        try {
            AgentClarification clarification = objectMapper.treeToValue(pending, AgentClarification.class);
            clarification.setScopeId(scope.getScopeId());
            clarification.setRound(scope.getRoundNo() == null ? 1 : scope.getRoundNo());
            return clarification;
        } catch (Exception e) {
            log.warn("恢复待回答澄清失败: scopeId={}, err={}", scope.getScopeId(), e.getMessage());
            return null;
        }
    }

    /**
     * 编排主流程(阻塞执行)。异常统一转 error 事件,不抛出、不落脏 assistant 数据。
     */
    private void runOrchestration(Long userId, AgentChatRequest req, Sinks.Many<String> sink,
                                  ActiveRun activeRun) {
        Conversation conversation = null;
        // 累计 token 用量提到 try 外:中断/失败时也按已产生 token 结算(无产出不扣)
        TokenUsage usage = new TokenUsage();
        SysAiModel chatModel = null;
        Long messageId = null;
        boolean settled = false;
        List<AgentResult.ImageRef> collectedImages = new ArrayList<>();
        ToolExecutionState toolState = new ToolExecutionState();
        toolState.imageModelId = req.getImageModelId();
        toolState.imageResolution = req.getImageResolution();
        toolState.requestId = req.getRequestId();
        try {
            ensureActive(activeRun.cancelled);
            Message resumedUserMessage = resolveResumedUserMessage(userId, req);
            if (resumedUserMessage != null) {
                req.setMessage(resumedUserMessage.getContent());
                req.setImageInputs(toResumeImageInputs(resumedUserMessage.getId()));
            }
            List<ResolvedInputImage> inputImages;
            try {
                inputImages = resolveInputImages(userId, req);
            } catch (ServiceException e) {
                emit(sink, AgentSseEvent.error(e.getMessage()));
                sink.tryEmitComplete();
                return;
            }
            if (!validateInputImages(inputImages.stream().map(ResolvedInputImage::url).toList(), sink)) {
                return;
            }
            // 1. 会话:无 conversationId 则新建;否则校验归属
            conversation = resolveConversation(userId, req);
            Long conversationId = conversation.getId();

            // 文本内容安全审核(主流程最前):在 user 入库、余额校验/扣豆、对话模型调用之前。
            // port 为空则降级放行;命中拦截则 emit error 后正常收尾,不入库、不扣豆、不调模型。
            if (contentSecurityPort != null) {
                ContentCheckResult check = contentSecurityPort.checkText(userId, req.getMessage(), "对话");
                if (check != null && !check.isPass()) {
                    String reason = StringUtils.hasText(check.getReason())
                            ? check.getReason() : "内容可能违规,请调整后再试";
                    emit(sink, AgentSseEvent.error(reason));
                    sink.tryEmitComplete();
                    return;
                }
            }

            // 用户消息与稳定附件身份同事务落库；media 保留 URL 数组兼容旧客户端。
            String userMedia = toJsonOrNull(inputImages.stream()
                    .map(ResolvedInputImage::toMediaRef)
                    .toList());
            List<MessageAttachmentDraft> attachmentDrafts = inputImages.stream()
                    .map(ResolvedInputImage::toDraft)
                    .toList();
            Message userMessage = resumedUserMessage != null
                    ? resumedUserMessage
                    : messageService.saveUserMessage(
                            conversationId, userId, req.getMessage(), userMedia, attachmentDrafts);
            // 用户消息一旦落库即视为本次使用，失败/余额不足也不能沿用旧到期时间。
            conversationService.touch(conversationId);
            messageId = userMessage.getId();
            if (chatRunService != null) chatRunService.bindMessage(activeRun.persisted, messageId);
            ActiveRun existingRun = activeRuns.putIfAbsent(messageId, activeRun);
            if (existingRun != null) {
                emit(sink, AgentSseEvent.error("原任务已在继续处理中，请勿重复提交"));
                sink.tryEmitComplete();
                return;
            }
            ensureActive(activeRun.cancelled);
            emit(sink, AgentSseEvent.meta(conversationId, userMessage.getId()));

            // 文本对话发起前余额门槛拦截:余额低于门槛直接 error,不发起模型调用(port 为空则不拦截)
            if (beanDeductPort != null && beanDeductPort.getBalance(userId) < minTextBeans) {
                emit(sink, AgentSseEvent.error(
                        "豆子余额不足,请充值后再聊~", "INSUFFICIENT_BEANS", "RECHARGE"));
                sink.tryEmitComplete();
                return;
            }

            // 图片不从普通历史消息自动恢复；只使用当前消息，或消费上轮模型显式创建的一次性澄清范围。
            ReferenceContext referenceContext = resolveReferenceContext(
                    conversationId, userId, userMessage, inputImages, req);
            toolState.answeredClarificationKeys.addAll(referenceContext.askedClarificationKeys());

            // 余额确认卡的继续生成直接复用服务端保存的计划，不再次调用对话模型。
            if (continueAffordablePlan(sink, req, userId, conversationId, messageId,
                    referenceContext, collectedImages, toolState)) {
                AgentResult directResult = mergeImages(null, collectedImages);
                if (directResult != null) {
                    emit(sink, AgentSseEvent.result(directResult));
                }
                ensureActive(activeRun.cancelled);
                if (!activeRun.beginFinalization()) {
                    throw new CancellationException("用户停止对话");
                }
                Message directMessage = persistAssistant(conversationId, userId, "", directResult, collectedImages, toolState);
                if (chatRunService != null && directMessage != null) {
                    chatRunService.bindAssistant(activeRun.persisted, directMessage.getId());
                    chatRunService.finish(activeRun.persisted, toolState.clarificationRequested
                            ? "WAITING_CONFIRMATION" : "COMPLETED");
                }
                conversationService.touch(conversationId);
                emit(sink, AgentSseEvent.done());
                sink.tryEmitComplete();
                return;
            }

            // 2. 系统提示词:三层 + 运行时上下文(直接生成规则 + 当前图片编号)
            // 当前 user 已先入库，额外取一条后再按 id 排除，确保真正保留最多 10 条历史消息。
            List<Message> recent = referenceContext.continued()
                    ? List.of()
                    : messageService.recentForContext(conversationId, RECENT_CONTEXT_LIMIT + 1);
            String runtimeContext = buildRuntimeContext(referenceContext);
            chatModel = activeRun.chatModel != null ? activeRun.chatModel : resolveChatModel(req.getModelId());
            if (memoryService != null) {
                ConversationMemoryService.Context memory = memoryService.prepare(userId, conversationId, messageId, chatModel);
                recent = memory.recentMessages();
                runtimeContext = runtimeContext + "\n" + memory.memoryPrompt();
            }
            String effectiveSkill = StringUtils.hasText(req.getSkill())
                    ? req.getSkill() : referenceContext.skillName();
            PromptAssembler.AssembledPrompt assembled = promptAssembler.assembleSystemPrompt(
                    userId, effectiveSkill, runtimeContext);
            String systemPrompt = assembled.text();
            // 调试:打印组装后的 system 提示词(含全局+Skill+个性化+产出契约+运行时),便于核验各层是否拼接到位
            log.info("Agent system 提示词组装: userId={}, skill={}, 长度={}\n----- system prompt 开始 -----\n{}\n----- system prompt 结束 -----",
                    userId, effectiveSkill, systemPrompt == null ? 0 : systemPrompt.length(), systemPrompt);

            // 3. messages 前缀:system + 历史文本 + 当前 user 真正的多模态内容块
            ArrayNode messages = objectMapper.createArrayNode();
            if (StringUtils.hasText(systemPrompt)) {
                messages.add(textMessage("system", systemPrompt));
            }
            // 追问续接已由精确 scope 携带原需求、全部答案和图片，
            // 不再混入普通最近消息，避免旧任务的人物或映射污染当前计划。
            if (!referenceContext.continued()) {
                appendHistory(messages, recent, userMessage.getId());
            }
            messages.add(userMessage(req.getMessage(), referenceContext));

            // 4. 解析默认多模态对话模型并执行多轮工具循环
            // The selected model and rates are fixed before queueing for persisted runs.

            // 收集 generate_images 产生的 taskId 及其递增 seq,供末尾 result.images 覆盖/补全;
            // submittedResults 收 submit_result 工具提交的结构化产出(主通道)
            List<AgentResult> submittedResults = new ArrayList<>();
            String finalText = runToolLoop(sink, messages, chatModel, userId, conversationId, messageId,
                    referenceContext, collectedImages, usage,
                    assembled.contractStrong(), submittedResults, toolState, activeRun.cancelled);
            if (!StringUtils.hasText(finalText) && StringUtils.hasText(toolState.clarificationQuestion)) {
                finalText = toolState.clarificationQuestion;
                emit(sink, AgentSseEvent.text(finalText));
            }
            ensureActive(activeRun.cancelled);

            // 5. 结构化产出:主通道=submit_result 工具(多次以最后一次为准);
            // 兜底1=正文围栏块解析(模型未调工具却手写了块);
            // 兜底2=自修复——强契约没拿到产出、或正文出现坏块时,补一轮纠正(前端无感,仅一次)
            AgentResult block = submittedResults.isEmpty()
                    ? tryParseBlock(finalText)
                    : submittedResults.get(submittedResults.size() - 1);
            boolean blockExpected = !toolState.clarificationRequested
                    && (assembled.contractStrong() || finalText.contains(RESULT_FENCE));
            if (block == null && blockExpected) {
                String patched = retryResultBlock(messages, chatModel, usage);
                if (StringUtils.hasText(patched)) {
                    finalText = finalText + "\n" + patched;
                    block = tryParseBlock(finalText);
                }
            }
            // images 用后端收集的 task_id 覆盖/补全(模型自填的不可信)
            AgentResult agentResult = mergeImages(block, collectedImages);
            if (agentResult != null) {
                emit(sink, AgentSseEvent.result(agentResult));
            }

            // 模型与工具调用已经结束；此后落库、结算必须作为一个不可中断收尾段执行。
            if (!activeRun.beginFinalization()) {
                throw new CancellationException("用户停止对话");
            }

            // 落 assistant message:有结构化产出存其 JSON(structured),否则存纯文本(text)
            Message assistantMessage = persistAssistant(
                    conversationId, userId, finalText, agentResult, collectedImages, toolState);
            if (chatRunService != null && assistantMessage != null) {
                chatRunService.bindAssistant(activeRun.persisted, assistantMessage.getId());
            }

            // 文本事后结算:正常结束按累计 token 算费扣豆,并把 token_usage/bean_cost 写回 assistant 消息
            // 先关闭本次重试入口；结算内若“扣豆成功、消息回写失败”，不得在外层再次扣费。
            settled = true;
            if (isInsufficientBalanceClarification(toolState)) {
                recordUnbilledUsage(assistantMessage, usage);
            } else {
                settleText(userId, chatModel, usage, assistantMessage, messageId);
            }

            // 更新会话最近消息时间
            conversationService.touch(conversationId);

            if (chatRunService != null) chatRunService.finish(activeRun.persisted,
                    toolState.clarificationRequested ? "WAITING_CONFIRMATION" : "COMPLETED");

            emit(sink, AgentSseEvent.done());
            sink.tryEmitComplete();
        } catch (Exception e) {
            if (isCancelled(activeRun.cancelled, e)) {
                // worker.dispose() 可能留下中断标记；清除后才能可靠保存停止状态和已产生用量。
                Thread.interrupted();
                cancelOpenedScope(toolState);
                activeRun.beginFinalization();
                log.info("Agent 对话已由用户停止: userId={}, conversationId={}, 已提交图片={}",
                        userId, conversation == null ? null : conversation.getId(), collectedImages.size());
                if (conversation != null && messageId != null) {
                    Message stoppedMessage = persistAssistant(conversation.getId(), userId,
                            "对话已停止", terminationResult("对话已停止", collectedImages), collectedImages, null);
                    if (chatRunService != null && stoppedMessage != null) {
                        chatRunService.bindAssistant(activeRun.persisted, stoppedMessage.getId());
                    }
                    if (!settled && chatModel != null && usage.hasAny()) {
                        settled = true;
                        try {
                            settleText(userId, chatModel, usage, stoppedMessage, messageId);
                        } catch (Exception ex) {
                            log.warn("停止对话结算扣豆失败: userId={}, messageId={}, err={}",
                                    userId, messageId, ex.getMessage());
                        }
                    }
                    conversationService.touch(conversation.getId());
                }
                sink.tryEmitComplete();
                return;
            }
            cancelOpenedScope(toolState);
            // SSE 可能早已断开；普通失败必须可从历史恢复，不能只发送实时错误。
            log.error("Agent 编排失败: userId={}, conversationId={}", userId,
                    conversation == null ? null : conversation.getId(), e);
            String failureMessage = e instanceof ServiceException && StringUtils.hasText(e.getMessage())
                    ? e.getMessage() : "对话出错了,请稍后重试";
            // 余额不足仍沿用“最后一条为 user”的充值续跑契约。
            if (!settled && !(e instanceof InsufficientBeanException) && conversation != null && messageId != null) {
                try {
                    Message failure = persistAssistant(conversation.getId(), userId, failureMessage,
                            terminationResult(failureMessage, collectedImages), collectedImages, null);
                    if (chatRunService != null && failure != null) {
                        chatRunService.bindAssistant(activeRun.persisted, failure.getId());
                    }
                    conversationService.touch(conversation.getId());
                } catch (Exception persistError) {
                    log.error("保存对话失败结果异常: conversationId={}, messageId={}",
                            conversation.getId(), messageId, persistError);
                }
            }
            // 中断/失败:若已产生 token 仍按已产生量结算(无 token 不扣)，不计为生图费用。
            if (!settled && chatModel != null && usage.hasAny()) {
                settled = true;
                try {
                    settleText(userId, chatModel, usage, null, messageId);
                } catch (Exception ex) {
                    log.warn("中断结算扣豆失败: userId={}, messageId={}, err={}", userId, messageId, ex.getMessage());
                }
            }
            if (e instanceof ConversationLimitException) {
                emit(sink, AgentSseEvent.error(failureMessage, ConversationLimitException.CODE,
                        ConversationLimitException.ACTION));
            } else if (e instanceof InsufficientBeanException) {
                emit(sink, AgentSseEvent.error(
                        "豆子余额不足,充值后可返回继续生成~",
                        "INSUFFICIENT_BEANS", "RECHARGE"));
            } else {
                emit(sink, AgentSseEvent.error(failureMessage));
            }
            sink.tryEmitComplete();
        } finally {
            if (messageId != null) {
                activeRuns.remove(messageId, activeRun);
            }
        }
    }

    private Message resolveResumedUserMessage(Long userId, AgentChatRequest request) {
        if (request == null || request.getResumeMessageId() == null) {
            return null;
        }
        if (request.getConversationId() == null) {
            throw new ServiceException("待恢复会话不能为空");
        }
        Message message = messageService.getById(request.getResumeMessageId());
        if (message == null || !userId.equals(message.getUserId())
                || !request.getConversationId().equals(message.getConversationId())
                || !"user".equals(message.getRole())) {
            throw new ServiceException("待恢复任务不存在或无权访问");
        }
        List<Message> conversationMessages = messageService.listByConversation(request.getConversationId());
        Message last = conversationMessages.isEmpty()
                ? null : conversationMessages.get(conversationMessages.size() - 1);
        if (last == null || !message.getId().equals(last.getId()) || !"user".equals(last.getRole())) {
            throw new ServiceException("原任务已完成或已有后续消息");
        }
        return message;
    }

    private List<AgentImageInput> toResumeImageInputs(Long messageId) {
        List<MessageAttachment> attachments = messageAttachmentService.listByMessage(messageId);
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        List<AgentImageInput> inputs = new ArrayList<>(attachments.size());
        for (MessageAttachment attachment : attachments) {
            AgentImageInput input = new AgentImageInput();
            if ("GENERATED".equals(attachment.getSourceType())) {
                input.setType("generated");
                input.setTaskId(attachment.getSourceTaskId());
                input.setSeq(attachment.getSourceSeq());
            } else {
                input.setType("upload");
                input.setUrl(attachment.getProfileUrl());
            }
            inputs.add(input);
        }
        return inputs;
    }

    /**
     * 文本事后结算:按多模态对话模型用量计算应扣豆子,取 min(应扣,余额) 扣至不透支,
     * 并把总 token 与实扣豆子写回 assistant 消息(message 为 null 时仅扣费、不写库)。
     *
     * <p>port 为空(admin 未提供适配器)则计费降级:仅 log.warn,不扣费、不写库。</p>
     *
     * @param userId         当前用户
     * @param chatModel      多模态对话模型(取文本三档单价)
     * @param usage          本次累计 token 用量
     * @param message        承载 token_usage/bean_cost 的 assistant 消息(可空:中断/失败时无消息)
     * @param fallbackMsgId  message 为空时 bizNo 兜底用的消息 id(取当前 user 消息 id)
     */
    private void settleText(Long userId, SysAiModel chatModel, TokenUsage usage,
                            Message message, Long fallbackMsgId) {
        if (!usage.hasAny()) {
            return;
        }
        if (beanDeductPort == null) {
            log.warn("豆子扣减端口未注入,文本计费降级跳过: userId={}, totalToken={}", userId, usage.total());
            return;
        }
        // total=总输入、cached=命中、output=输出(BillingCalculator 内按「未命中=总输入−命中」分档)
        BigDecimal chatRaw = billingCalculator.textRaw(chatModel,
                usage.promptTokens, usage.cachedTokens, usage.completionTokens);
        long beans = billingCalculator.ceilBeans(chatRaw);
        log.info("Agent 文本用量: userId={}, inputTokens={}, cachedTokens={}, outputTokens={}, beans={}",
                userId, usage.promptTokens, usage.cachedTokens, usage.completionTokens, beans);
        long actual = 0;
        if (beans > 0) {
            Long bizMsgId = (message != null) ? message.getId() : fallbackMsgId;
            actual = beanDeductPort.deductUpToBalance(userId, beans, "msg:" + bizMsgId, "AI对话");
        }
        // 写回总 token 与实扣豆子(无 assistant 消息时跳过)
        if (message != null) {
            message.setTokenUsage((int) usage.total());
            message.setBeanCost((int) actual);
            messageService.updateById(message);
        }
    }

    /** 出图余额确认不扣本次计划解析费用，但保留 token 用量，避免展示余额与可生成张数在流结束后失真。 */
    private void recordUnbilledUsage(Message message, TokenUsage usage) {
        if (message == null || !usage.hasAny()) {
            return;
        }
        message.setTokenUsage((int) usage.total());
        message.setBeanCost(0);
        messageService.updateById(message);
    }

    private boolean isInsufficientBalanceClarification(ToolExecutionState state) {
        return state != null && state.clarificationRequested && state.clarification != null
                && INSUFFICIENT_BALANCE_KEY.equals(state.clarification.getKey());
    }

    /**
     * 解析或新建会话。conversationId 为空则新建;否则校验归属,非本人/不存在抛业务异常。
     */
    private Conversation resolveConversation(Long userId, AgentChatRequest req) {
        if (req.getConversationId() == null) {
            String title = summarizeTitle(req.getMessage());
            return conversationService.createConversation(userId, title, req.getModelId());
        }
        Conversation conversation = conversationService.getOwned(req.getConversationId(), userId);
        if (conversation == null) {
            throw new ServiceException("会话不存在或无权访问");
        }
        return conversation;
    }

    /** 强契约下,每轮工具回灌后追加的防遗忘提醒(多轮长上下文中模型易丢失结构化提交要求) */
    private static final String CONTRACT_REMINDER =
            "提醒:综合工具结果继续。产出完成后必须先调用 submit_result 工具提交 title/body/tags,"
                    + "再用一句简短引导作为正文收尾,不要在正文重复产出内容。";

    /**
     * 多轮工具循环:裸 HTTP 流式请求默认对话模型,累积正文与 tool_calls,执行工具回灌后再请求,
     * 直至 finish_reason != tool_calls 或达到最大轮数。
     *
     * @param contractStrong   是否注入了强产出契约(命中 Skill);true 时每轮工具回灌后追加防遗忘提醒
     * @param submittedResults 模型经 submit_result 工具提交的结构化产出(出参,多次以最后一次为准)
     * @return 最终 assistant 文本(用于兜底解析围栏块与落库)
     */
    private String runToolLoop(Sinks.Many<String> sink, ArrayNode messages, SysAiModel model,
                               Long userId, Long conversationId, Long sourceMessageId,
                               ReferenceContext referenceContext,
                               List<AgentResult.ImageRef> collectedImages, TokenUsage usage,
                               boolean contractStrong, List<AgentResult> submittedResults,
                               ToolExecutionState toolState,
                               AtomicBoolean cancelled) throws Exception {
        String bearer = encryptUtils.decrypt(model.getApiKey());
        if (isResponsesProtocol(model)) {
            return runResponsesToolLoop(sink, messages, model, bearer, userId, conversationId,
                    sourceMessageId, referenceContext, collectedImages, usage,
                    contractStrong, submittedResults, toolState, cancelled);
        }
        String url = buildCompletionsUrl(model.getBaseUrl());

        String lastAssistantText = "";
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            ensureActive(cancelled);
            // 单轮:发起流式请求,逐行解析,产出 text 事件并累积本轮 assistant 内容与 tool_calls
            RoundResult roundResult = streamOneRound(sink, url, bearer, model, messages);
            lastAssistantText = roundResult.content;
            // 多轮工具循环按轮累加 token(整轮总和,供末尾合并算费)
            usage.add(roundResult.promptTokens, roundResult.cachedTokens, roundResult.completionTokens);

            // 把本轮 assistant 消息追加进 messages(含 tool_calls 原样,供下一轮回灌)
            messages.add(buildAssistantMessage(roundResult));

            if (!"tool_calls".equals(roundResult.finishReason) || roundResult.toolCalls.isEmpty()) {
                // 不再要求调工具:本轮文本即最终文本
                return roundResult.content;
            }

            // 执行每个 tool_call,结果以 role:tool(带 tool_call_id)追加进 messages
            for (ToolCall toolCall : roundResult.toolCalls) {
                ensureActive(cancelled);
                String result = executeTool(sink, toolCall, userId, conversationId, sourceMessageId,
                        referenceContext,
                        collectedImages, toolState, submittedResults);
                ObjectNode toolMsg = objectMapper.createObjectNode();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", toolCall.id);
                toolMsg.put("content", result);
                messages.add(toolMsg);
                if (toolState.clarificationRequested) {
                    break;
                }
            }
            if (toolState.clarificationRequested) {
                return toolState.clarificationQuestion;
            }

            appendSelectedHistory(messages, toolState, false);

            // 强契约防遗忘:工具回灌后补一条 system 提醒,抵消多轮长上下文对末尾结构化要求的稀释
            if (contractStrong) {
                messages.add(textMessage("system", CONTRACT_REMINDER));
            }
        }
        // 达到最大轮数仍未收敛:返回最后一轮文本,避免无限循环
        log.warn("Agent 工具循环达到最大轮数 {},强制结束: conversationId={}", MAX_TOOL_ROUNDS, conversationId);
        return lastAssistantText;
    }

    /**
     * Responses 多轮工具循环：把历史消息转换为 input，回灌 function_call 与 function_call_output。
     */
    private String runResponsesToolLoop(Sinks.Many<String> sink, ArrayNode messages, SysAiModel model,
                                        String bearer, Long userId, Long conversationId,
                                        Long sourceMessageId,
                                        ReferenceContext referenceContext,
                                        List<AgentResult.ImageRef> collectedImages,
                                        TokenUsage usage, boolean contractStrong,
                                        List<AgentResult> submittedResults,
                                        ToolExecutionState toolState,
                                        AtomicBoolean cancelled) throws Exception {
        ArrayNode input = toResponsesInput(messages);
        String lastAssistantText = "";

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            ensureActive(cancelled);
            ResultBlockStreamFilter textFilter = new ResultBlockStreamFilter();
            ResponsesApiClient.Round response = responsesApiClient.streamRound(
                    model, bearer, input, buildResponsesTools(),
                    text -> textFilter.feed(sink, text),
                    thinking -> emit(sink, AgentSseEvent.thinking(thinking)));
            textFilter.flush(sink);
            lastAssistantText = response.content();
            usage.add(response.inputTokens(), response.cachedTokens(), response.outputTokens());

            for (JsonNode outputItem : response.outputItems()) {
                input.add(outputItem.deepCopy());
            }
            if (response.toolCalls().isEmpty()) {
                return lastAssistantText;
            }

            for (ResponsesApiClient.ToolCall responseCall : response.toolCalls()) {
                ensureActive(cancelled);
                ToolCall toolCall = new ToolCall();
                toolCall.id = responseCall.callId();
                toolCall.name = responseCall.name();
                toolCall.arguments = responseCall.arguments();
                String result = executeTool(sink, toolCall, userId, conversationId, sourceMessageId,
                        referenceContext,
                        collectedImages, toolState, submittedResults);
                ObjectNode toolOutput = input.addObject();
                toolOutput.put("type", "function_call_output");
                toolOutput.put("call_id", responseCall.callId());
                toolOutput.put("output", result);
                if (toolState.clarificationRequested) {
                    break;
                }
            }
            if (toolState.clarificationRequested) {
                return toolState.clarificationQuestion;
            }

            appendSelectedHistory(input, toolState, true);

            if (contractStrong) {
                ObjectNode reminder = input.addObject();
                reminder.put("role", "developer");
                reminder.put("content", CONTRACT_REMINDER);
            }
        }
        log.warn("Responses 工具循环达到最大轮数 {},强制结束: conversationId={}", MAX_TOOL_ROUNDS, conversationId);
        return lastAssistantText;
    }

    /** 用户取消时在下一处安全边界终止模型与工具循环。 */
    private void ensureActive(AtomicBoolean cancelled) {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("用户停止对话");
        }
    }

    /** 识别显式取消与中断包装异常，取消不作为业务错误下发。 */
    private boolean isCancelled(AtomicBoolean cancelled, Throwable error) {
        if (cancelled.get() || error instanceof CancellationException || error instanceof InterruptedException) {
            return true;
        }
        Throwable cause = error.getCause();
        while (cause != null) {
            if (cause instanceof CancellationException || cause instanceof InterruptedException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /** 本轮未正常完成时撤销已创建但尚未向用户完整输出的澄清范围。 */
    private void cancelOpenedScope(ToolExecutionState toolState) {
        if (!StringUtils.hasText(toolState.openedScopeId)) {
            return;
        }
        try {
            referenceScopeService.cancel(toolState.openedScopeId);
        } catch (Exception e) {
            log.warn("撤销未完成的澄清范围失败: scopeId={}, err={}",
                    toolState.openedScopeId, e.getMessage());
        }
        toolState.openedScopeId = null;
    }

    /**
     * 发起一轮流式请求并逐行解析。
     *
     * <p>读 SSE:行以 "data: " 开头去前缀;"[DONE]" 忽略;否则 JSON 解析。
     * delta.content 非空 → emit text;delta.tool_calls 按 index 累积 function.name 与 arguments 片段。</p>
     */
    private RoundResult streamOneRound(Sinks.Many<String> sink, String url, String bearer,
                                       SysAiModel model, ArrayNode messages) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model.getModelDeployment());
        body.set("messages", messages);
        body.put("stream", true);
        // 开启 usage 回传:流式响应默认不一定带 usage,需显式声明,末尾多一个 chunk 携带 usage(choices 为空)
        body.putObject("stream_options").put("include_usage", true);
        body.set("tools", buildTools());

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(STREAM_TIMEOUT_SEC))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + bearer)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        // 复用 AiHttpUtil.CLIENT 共享 HttpClient;按行读取 SSE 流
        HttpResponse<Stream<String>> response = com.aurora.ai.provider.support.AiHttpUtil.CLIENT
                .send(request, HttpResponse.BodyHandlers.ofLines());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String errBody = response.body().reduce("", (a, b) -> a + b);
            log.error("对话模型请求失败 HTTP {} body={}", response.statusCode(), errBody);
            throw new ServiceException("对话模型流式请求失败 HTTP " + response.statusCode());
        }

        StringBuilder content = new StringBuilder();
        // 结构化块流式过滤(PRD §9.3):emit 侧过滤,content 仍累积全文供末尾解析与落库
        ResultBlockStreamFilter textFilter = new ResultBlockStreamFilter();
        // 按 index 累积 tool_call;TreeMap 保证按 index 有序遍历
        TreeMap<Integer, ToolCallBuilder> toolBuilders = new TreeMap<>();
        String finishReason = null;
        // 末尾 usage chunk 携带本轮 token 用量(choices 可能为空),仅取 usage,不当 text/tool 处理
        long promptTokens = 0;
        long completionTokens = 0;
        long cachedTokens = 0;

        try (Stream<String> lines = response.body()) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring("data:".length()).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) {
                    continue;
                }
                JsonNode root = objectMapper.readTree(data);
                JsonNode choice = root.path("choices").path(0);
                JsonNode delta = choice.path("delta");

                JsonNode contentNode = delta.path("content");
                if (!contentNode.isMissingNode() && !contentNode.isNull() && !contentNode.asText().isEmpty()) {
                    String text = contentNode.asText();
                    content.append(text);
                    textFilter.feed(sink, text);
                }

                // 思考增量:reasoner 模型把推理过程放在 delta.reasoning_content,有则推 thinking 事件供前端渲染折叠思考块。
                // 思考内容仅流式呈现,不拼入正文 content、不入库、不参与工具循环;
                // 计费:reasoning token 已计入 completion_tokens(OpenAI 兼容口径),见 settleText,无需单独叠加。
                JsonNode reasoningNode = delta.path("reasoning_content");
                if (!reasoningNode.isMissingNode() && !reasoningNode.isNull() && !reasoningNode.asText().isEmpty()) {
                    emit(sink, AgentSseEvent.thinking(reasoningNode.asText()));
                }

                JsonNode toolCalls = delta.path("tool_calls");
                if (toolCalls.isArray()) {
                    for (JsonNode tc : toolCalls) {
                        accumulateToolCall(toolBuilders, tc);
                    }
                }

                String fr = choice.path("finish_reason").asText(null);
                if (StringUtils.hasText(fr)) {
                    finishReason = fr;
                }

                // usage 通常仅在末尾 chunk 出现(choices 空数组,上面 path(0) 为 MissingNode 自然跳过 text/tool)
                JsonNode usage = root.path("usage");
                if (usage.isObject()) {
                    promptTokens = usage.path("prompt_tokens").asLong(0);
                    completionTokens = usage.path("completion_tokens").asLong(0);
                    // 命中缓存 token:优先旧版 prompt_cache_hit_tokens,兼容新版 prompt_tokens_details.cached_tokens
                    JsonNode hit = usage.path("prompt_cache_hit_tokens");
                    if (hit.isNumber()) {
                        cachedTokens = hit.asLong(0);
                    } else {
                        cachedTokens = usage.path("prompt_tokens_details").path("cached_tokens").asLong(0);
                    }
                }
            }
        }
        // 流结束:补发滞后窗里剩余的真实正文(命中过 fence 则已全部吞掉)
        textFilter.flush(sink);

        RoundResult result = new RoundResult();
        result.content = content.toString();
        result.finishReason = finishReason;
        result.promptTokens = promptTokens;
        result.completionTokens = completionTokens;
        result.cachedTokens = cachedTokens;
        for (ToolCallBuilder b : toolBuilders.values()) {
            ToolCall tc = b.build();
            if (tc != null) {
                result.toolCalls.add(tc);
            }
        }
        return result;
    }

    /** 结构化块起始 fence(流式过滤用,与 RESULT_BLOCK_PATTERN 口径一致) */
    private static final String RESULT_FENCE = "```aurora-result";

    /**
     * 强契约缺块自修复:追加纠正指令后补一轮请求,要求模型仅输出 aurora-result 代码块。
     * 输出走独立丢弃 sink(不向前端漏文本);token 用量照常累计参与计费。
     *
     * @return 纠正轮全文(期望即为块本身);异常返回空串,由调用方降级
     */
    private String retryResultBlock(ArrayNode messages, SysAiModel model, TokenUsage usage) {
        try {
            messages.add(textMessage("system",
                    "你上一条回复的 ```aurora-result 代码块缺失或 JSON 不合法。请现在仅重新输出该代码块:"
                            + "以 ```aurora-result 开头、以 ``` 结尾,块内 JSON 按系统要求包含 title/body/tags(如适用);"
                            + "必须是严格合法的 JSON——字符串值内不要出现未转义的英文双引号(需要引号用中文「」);"
                            + "不要调用工具,不要输出任何其他文字。"));
            String bearer = encryptUtils.decrypt(model.getApiKey());
            if (isResponsesProtocol(model)) {
                ResponsesApiClient.Round round = responsesApiClient.streamRound(
                        model, bearer, toResponsesInput(messages), objectMapper.createArrayNode(),
                        text -> { }, thinking -> { });
                usage.add(round.inputTokens(), round.cachedTokens(), round.outputTokens());
                return round.content();
            }
            String url = buildCompletionsUrl(model.getBaseUrl());
            // 丢弃 sink:纠正轮内容只用于解析,不推给前端
            Sinks.Many<String> discard = Sinks.many().unicast().onBackpressureBuffer();
            RoundResult round = streamOneRound(discard, url, bearer, model, messages);
            usage.add(round.promptTokens, round.cachedTokens, round.completionTokens);
            log.info("aurora-result 缺块自修复完成: 补块文本长度={}", round.content == null ? 0 : round.content.length());
            return round.content == null ? "" : round.content;
        } catch (Exception e) {
            log.warn("aurora-result 缺块自修复失败,降级 images-only: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 结构化块流式过滤器(PRD §9.3):向前端发 text 时保留一个 fence 长度的滞后窗,
     * 检测到 {@code ```aurora-result} 起始即停发——块内容只经末尾 result 事件下发,正文流里不出现裸 JSON。
     * 滞后窗保证 fence 跨 delta 半包也不会漏发块头;仅影响 emit,不影响 content 全文累积。
     */
    private class ResultBlockStreamFilter {
        private final StringBuilder pending = new StringBuilder();
        private boolean fenceHit = false;

        /** 收到一个正文增量:滞后判定后把可安全外发的部分推给前端 */
        void feed(Sinks.Many<String> sink, String text) {
            if (fenceHit) {
                return;
            }
            pending.append(text);
            int idx = pending.indexOf(RESULT_FENCE);
            if (idx >= 0) {
                fenceHit = true;
                if (idx > 0) {
                    emit(sink, AgentSseEvent.text(pending.substring(0, idx)));
                }
                pending.setLength(0);
                return;
            }
            int safeLen = pending.length() - RESULT_FENCE.length();
            if (safeLen > 0) {
                emit(sink, AgentSseEvent.text(pending.substring(0, safeLen)));
                pending.delete(0, safeLen);
            }
        }

        /** 流结束:补发滞后窗里剩余的真实正文(命中过 fence 则无剩余) */
        void flush(Sinks.Many<String> sink) {
            if (!fenceHit && pending.length() > 0) {
                emit(sink, AgentSseEvent.text(pending.toString()));
                pending.setLength(0);
            }
        }
    }

    /**
     * 累积一个 tool_call 片段:按 index 定位,合并 id / function.name / function.arguments(arguments 跨块拼接)。
     */
    private void accumulateToolCall(TreeMap<Integer, ToolCallBuilder> builders, JsonNode tc) {
        int index = tc.path("index").asInt(0);
        ToolCallBuilder builder = builders.computeIfAbsent(index, k -> new ToolCallBuilder());
        if (tc.hasNonNull("id")) {
            builder.id = tc.get("id").asText();
        }
        JsonNode function = tc.path("function");
        if (function.hasNonNull("name")) {
            builder.name = function.get("name").asText();
        }
        if (function.hasNonNull("arguments")) {
            builder.arguments.append(function.get("arguments").asText());
        }
    }

    /**
     * 执行单个工具调用,返回回灌给模型的 tool 结果 JSON 串。
     * 工具调用前后 emit tool(running/done)状态条;submit_result 静默执行(不发状态条)。
     */
    private String executeTool(Sinks.Many<String> sink, ToolCall toolCall,
                               Long userId, Long conversationId, Long sourceMessageId,
                               ReferenceContext referenceContext,
                               List<AgentResult.ImageRef> collectedImages, ToolExecutionState toolState,
                               List<AgentResult> submittedResults) {
        JsonNode args = parseArguments(toolCall.arguments);
        if (toolState.selectedHistory != null) referenceContext = toolState.selectedHistory;
        if ("select_history_images".equals(toolCall.name)) {
            return selectHistoryImages(args, userId, conversationId, sourceMessageId, referenceContext, toolState);
        }
        if ("submit_result".equals(toolCall.name)) {
            if (toolState.clarificationRequested) {
                return toolError("本轮已请求澄清，请直接结束并等待用户回复");
            }
            return doSubmitResult(args, submittedResults);
        }
        if ("request_clarification".equals(toolCall.name)) {
            String result = doRequestClarification(args, userId, conversationId, referenceContext, toolState);
            if (toolState.clarification != null) {
                emit(sink, AgentSseEvent.clarification(toolState.clarification));
            }
            return result;
        }
        if ("generate_images".equals(toolCall.name)) {
            if (toolState.historyPending) {
                return toolError("历史素材已精确加载，下一轮先查看图片和附件ID，再提交完整生成计划");
            }
            toolState.toolCallId = toolCall.id;
            int n = args.path("jobs").size();
            emit(sink, AgentSseEvent.tool("generate_images", "running", "正在提交出图"));
            String result;
            try {
                result = doGenerateImages(args, userId, conversationId, sourceMessageId, referenceContext,
                        collectedImages, toolState);
            } catch (InsufficientBeanException e) {
                emit(sink, AgentSseEvent.tool("generate_images", "failed", "豆子余额不足"));
                throw e;
            } catch (RecoverableImagePlanException e) {
                if (++toolState.planCorrections > 2) {
                    throw new ServiceException("图片引用计划连续校验失败，本次未提交图片，请重试。");
                }
                log.warn("图片引用计划待修正: sourceMessageId={}, correction={}, reason={}",
                        sourceMessageId, toolState.planCorrections, e.getMessage());
                emit(sink, AgentSseEvent.tool("generate_images", "running", "正在校对图片引用"));
                return toolError(e.getMessage() + "。本次未提交、未扣生图费用；修正全部jobs后重新调用generate_images，不向用户追问内部引用格式。");
            } catch (ServiceException e) {
                log.warn("生图计划未提交: userId={}, conversationId={}, sourceMessageId={}, reason={}",
                        userId, conversationId, sourceMessageId, e.getMessage());
                emit(sink, AgentSseEvent.tool("generate_images", "failed", "生图计划未通过校验，未提交图片"));
                throw new ServiceException("生图计划未通过校验，本次未提交图片，请重试。");
            }
            JsonNode resultNode = parseArguments(result);
            boolean submitted = resultNode.hasNonNull("task_id");
            boolean clarificationRequired = resultNode.path("clarification_required").asBoolean(false);
            if (clarificationRequired && toolState.clarification != null) {
                emit(sink, AgentSseEvent.clarification(toolState.clarification));
            }
            String status = submitted || clarificationRequired ? "done" : "failed";
            int submittedCount = resultNode.path("n").asInt(n);
            boolean balanceConfirmation = clarificationRequired && toolState.clarification != null
                    && INSUFFICIENT_BALANCE_KEY.equals(toolState.clarification.getKey());
            String message = submitted ? "已提交 " + submittedCount + " 张"
                    : balanceConfirmation ? "等待确认余额方案"
                    : clarificationRequired ? "等待确认生成数量"
                    : "出图计划暂未提交，请重试";
            if ("done".equals(status)) {
                String taskId = resultNode.path("task_id").asText(null);
                if (StringUtils.hasText(taskId)) {
                    emit(sink, AgentSseEvent.tool("generate_images", status, message, taskId, submittedCount));
                } else {
                    emit(sink, AgentSseEvent.tool("generate_images", status, message));
                }
            } else {
                emit(sink, AgentSseEvent.tool("generate_images", status, message));
            }
            return result;
        }
        // 未知工具:回灌错误让模型自行降级
        log.warn("Agent 收到未知工具调用: name={}", toolCall.name);
        return "{\"error\":\"不支持的工具\"}";
    }

    /**
     * submit_result:模型提交结构化产出(title/body/tags)。参数直接反序列化为 AgentResult 暂存,
     * images 由后端收集覆盖(模型传了也忽略);多次调用以最后一次为准。解析失败回灌错误让模型修正。
     */
    private String doSubmitResult(JsonNode args, List<AgentResult> submittedResults) {
        try {
            AgentResult result = objectMapper.treeToValue(args, AgentResult.class);
            result.setTitle(normalizeStructuredTitle(result.getTitle()));
            result.setImages(null);
            submittedResults.add(result);
            log.info("submit_result 收到结构化产出: title={}, bodyLen={}, tags={}",
                    result.getTitle(),
                    result.getBody() == null ? 0 : result.getBody().length(),
                    result.getTags() == null ? 0 : result.getTags().size());
            return "{\"ok\":true}";
        } catch (Exception e) {
            log.warn("submit_result 参数解析失败: {}", e.getMessage());
            return "{\"error\":\"参数不是合法的 JSON 对象,请修正后重新调用 submit_result\"}";
        }
    }

    private String selectHistoryImages(JsonNode args, Long userId, Long conversationId, Long messageId,
                                        ReferenceContext current, ToolExecutionState state) {
        if (state.submitted || state.clarificationRequested || state.selectedHistory != null) {
            return toolError("本轮素材范围已确定，不能再次添加历史图片");
        }
        Message source = messageService.getById(messageId);
        if (source == null || !userId.equals(source.getUserId()) || !conversationId.equals(source.getConversationId())) {
            throw new ServiceException("消息不存在或无权访问");
        }
        String request = source.getContent() == null ? "" : source.getContent();
        boolean mentionsHistory = Pattern.compile("之前|前面|以前|上次|上一|刚才|历史|第.{0,3}张|previous|earlier|last", Pattern.CASE_INSENSITIVE)
                .matcher(request).find();
        boolean mentionsImage = Pattern.compile("图|照片|海报|作品|成品|素材|那张|这张|image|photo|picture", Pattern.CASE_INSENSITIVE)
                .matcher(request).find();
        if (!mentionsHistory || !mentionsImage) return toolError("用户本轮没有明确引用历史图片，不能自动加载旧素材");
        JsonNode generated = args.path("generated_images");
        JsonNode ids = args.path("attachment_ids");
        if ((!generated.isMissingNode() && !generated.isArray()) || (!ids.isMissingNode() && !ids.isArray())) {
            return toolError("历史素材必须按task_id/seq或attachment_ids数组精确指定");
        }
        int count = generated.size() + ids.size();
        if (count < 1 || count + current.images().size() > MAX_INPUT_IMAGES) {
            return toolError("请选择1至9张明确的历史素材，连同当前素材合计不能超过9张");
        }
        List<ReferenceImage> selected = new ArrayList<>(current.images());
        List<MessageAttachmentDraft> drafts = new ArrayList<>();
        ArrayNode media = objectMapper.createArrayNode();
        try {
            if (StringUtils.hasText(source.getMedia())) {
                JsonNode original = objectMapper.readTree(source.getMedia());
                if (original.isArray()) original.forEach(item -> media.add(item.deepCopy()));
            }
            Set<String> selectedSources = new HashSet<>();
            for (JsonNode image : generated) {
                String taskId = image.path("task_id").asText(null);
                int seq = image.path("seq").asInt(0);
                if (!StringUtils.hasText(taskId) || seq < 1 || !selectedSources.add(taskId + ":" + seq)) {
                    return toolError("历史作品编号或顺序无效，请按索引选择，不能重复");
                }
                GenerationTask task = generationTaskService.getOwned(taskId, userId);
                if (task == null || !conversationId.equals(task.getConversationId())
                        || (task.getMessageId() != null && task.getMessageId() >= messageId)
                        || !"SUCCEEDED".equals(task.getStatus())) {
                    return toolError("历史作品不存在、尚未完成或不属于本会话");
                }
                JsonNode urls = objectMapper.readTree(task.getResultUrls());
                if (!urls.isArray() || seq > urls.size() || !urls.get(seq - 1).isTextual()
                        || normalizeProfileUrl(urls.get(seq - 1).asText()) == null) {
                    return toolError("该序号是失败或已失效的图片，不能移动编号或替换为其他图");
                }
                String url = normalizeProfileUrl(urls.get(seq - 1).asText());
                if (selected.stream().anyMatch(item -> item.url().equals(url))) return toolError("该历史图片已在本轮素材中");
                String attachmentId = UUID.randomUUID().toString().replace("-", "");
                selected.add(new ReferenceImage(attachmentId, url));
                drafts.add(new MessageAttachmentDraft(attachmentId, selected.size(), "GENERATED", url, taskId, seq));
                media.addObject().put("type", "generated").put("url", url).put("taskId", taskId).put("seq", seq);
            }
            List<String> attachmentIds = new ArrayList<>();
            for (JsonNode id : ids) {
                if (!id.isTextual() || !selectedSources.add(id.asText())) return toolError("历史附件ID无效或重复");
                attachmentIds.add(id.asText());
            }
            if (!attachmentIds.isEmpty()) {
                List<MessageAttachment> owned = messageAttachmentService.listOwnedOrdered(conversationId, userId, attachmentIds);
                if (owned.size() != attachmentIds.size()) return toolError("历史附件不存在或不属于本会话");
                for (MessageAttachment attachment : owned) {
                    if (attachment.getMessageId() >= messageId || selected.stream().anyMatch(item ->
                            item.attachmentId().equals(attachment.getAttachmentId()) || item.url().equals(attachment.getProfileUrl()))) {
                        return toolError("只能选择尚未提供的历史附件");
                    }
                    String attachmentId = UUID.randomUUID().toString().replace("-", "");
                    selected.add(new ReferenceImage(attachmentId, attachment.getProfileUrl()));
                    drafts.add(new MessageAttachmentDraft(attachmentId, selected.size(), attachment.getSourceType(),
                            attachment.getProfileUrl(), attachment.getSourceTaskId(), attachment.getSourceSeq()));
                    ObjectNode mediaRef = media.addObject().put("type", "GENERATED".equals(attachment.getSourceType()) ? "generated" : "upload")
                            .put("url", attachment.getProfileUrl()).put("sourceAttachmentId", attachment.getAttachmentId());
                    if (attachment.getSourceTaskId() != null) mediaRef.put("taskId", attachment.getSourceTaskId()).put("seq", attachment.getSourceSeq());
                }
            }
            // Load only selected media before freezing the scope; unreadable files cannot become references.
            for (ReferenceImage image : selected) toMultimodalImageUrl(image.url());
            messageService.appendUserAttachments(userId, messageId, media.toString(), drafts);
            state.selectedHistory = new ReferenceContext(current.sourceMessageId(), current.sourcePrompt(), List.copyOf(selected),
                    current.continued(), current.clarificationContextJson(), current.clarificationRound(),
                    current.askedClarificationKeys(), current.clarificationAnswerSummary(), current.skillName());
            state.historyPending = true;
            ObjectNode result = objectMapper.createObjectNode().put("status", "SELECTED");
            result.set("selection", args.deepCopy());
            ArrayNode refs = result.putArray("references");
            for (ReferenceImage image : selected) refs.addObject().put("attachment_id", image.attachmentId());
            result.put("instruction", "已加载精确选定的历史图片，下一轮查看后再生成；不能把未选择的历史素材加入任务");
            return result.toString();
        } catch (ServiceException e) {
            return toolError(e.getMessage());
        } catch (Exception e) {
            return toolError("历史素材读取失败，请确认图片尚在本会话中");
        }
    }

    private void appendSelectedHistory(ArrayNode messages, ToolExecutionState state, boolean responses) {
        if (!state.historyPending) return;
        ObjectNode selected = userMessage("【用户本轮明确引用的历史图片，已由后端按稳定ID校验并加载】", state.selectedHistory);
        if (responses) {
            ArrayNode wrapper = objectMapper.createArrayNode().add(selected);
            toResponsesInput(wrapper).forEach(messages::add);
        } else messages.add(selected);
        state.historyPending = false;
    }

    /**
     * 显式澄清工具：只能锁定本轮不可变引用范围内的附件，且仅供下一条用户消息消费一次。
     */
    private String doRequestClarification(JsonNode args, Long userId, Long conversationId,
                                          ReferenceContext referenceContext,
                                          ToolExecutionState toolState) {
        if (toolState.submitted) {
            return toolError("本轮已提交出图计划，不能再请求澄清");
        }
        if (toolState.clarificationRequested) {
            return toolError("本轮已经请求过澄清，请勿重复调用");
        }
        String key = normalizeClarificationKey(args.path("key").asText(null));
        if (referenceContext.askedClarificationKeys().contains(key)
                || toolState.answeredClarificationKeys.contains(key)) {
            return toolError("该问题已经回答，不得重复追问；请采用已有答案或推荐方案继续执行");
        }
        int round = referenceContext.clarificationRound() + 1;
        if (round > MAX_CLARIFICATION_ROUNDS) {
            return toolError("已达到追问上限；除安全、付费数量和必须准确的文字外，请采用合理默认值继续执行");
        }
        String question = args.path("question").asText(null);
        if (!StringUtils.hasText(question) || question.length() > 500) {
            return toolError("澄清问题为空或过长");
        }

        if (referenceContext.images().size() > MAX_INPUT_IMAGES) {
            List<String> selected = parseAllowedAttachmentIds(args.path("reference_attachment_ids"), referenceContext);
            if (selected == null || selected.isEmpty()) {
                return toolError("新旧候选素材超过9张，请在reference_attachment_ids中保留本次澄清需要的至多9张附件，不得默认混用旧整组");
            }
            Set<String> keep = new LinkedHashSet<>(selected);
            referenceContext = new ReferenceContext(referenceContext.sourceMessageId(), referenceContext.sourcePrompt(),
                    referenceContext.images().stream().filter(image -> keep.contains(image.attachmentId())).toList(),
                    referenceContext.continued(), referenceContext.clarificationContextJson(),
                    referenceContext.clarificationRound(), referenceContext.askedClarificationKeys(),
                    referenceContext.clarificationAnswerSummary(), referenceContext.skillName());
        }
        AgentClarification clarification = buildClarification(args, key, question.trim(), round, referenceContext);
        List<String> attachmentIds = referenceContext.images().stream()
                .map(ReferenceImage::attachmentId)
                .toList();

        toolState.clarificationQuestion = clarification.getQuestion();
        toolState.clarificationRequested = true;
        String contextJson = buildClarificationContext(referenceContext, clarification);
        boolean allowAdditionalImages = !"KEEP".equals(clarification.getImageUpdatePolicy());
        com.aurora.ai.agent.domain.ReferenceScope scope = referenceScopeService.open(
                conversationId, userId, referenceContext.sourceMessageId(), attachmentIds,
                toolState.clarificationQuestion, allowAdditionalImages,
                contextJson, round, LocalDateTime.now().plusHours(CLARIFICATION_TTL_HOURS));
        toolState.openedScopeId = scope.getScopeId();
        clarification.setScopeId(scope.getScopeId());
        toolState.clarification = clarification;

        ObjectNode result = objectMapper.createObjectNode();
        result.put("ok", true);
        result.put("pinned_attachment_count", attachmentIds.size());
        result.put("scope_id", scope.getScopeId());
        result.put("instruction", "澄清卡片已由后端展示，请结束本轮，不得重复提问或调用其他工具");
        return result.toString();
    }

    private String normalizeClarificationKey(String key) {
        if (!StringUtils.hasText(key)) {
            return "HARD_CONFLICT";
        }
        String normalized = key.trim().toUpperCase();
        return CLARIFICATION_KEYS.contains(normalized) ? normalized : "HARD_CONFLICT";
    }

    private AgentClarification buildClarification(JsonNode args, String key, String question,
                                                  int round, ReferenceContext referenceContext) {
        AgentClarification clarification = new AgentClarification();
        clarification.setKey(key);
        clarification.setQuestion(question);
        clarification.setRound(round);
        List<AgentClarification.Option> options = parseClarificationOptions(args.path("options"));
        clarification.setOptions(options);
        clarification.setAllowFreeText(options.isEmpty()
                || args.path("allow_free_text").asBoolean(true));
        String recommendedOptionId = args.path("recommended_option_id").asText(null);
        boolean recommendedExists = false;
        for (AgentClarification.Option option : options) {
            recommendedExists |= option.getId().equals(recommendedOptionId);
        }
        if (!options.isEmpty() && !recommendedExists) {
            recommendedOptionId = options.get(0).getId();
        }
        clarification.setRecommendedOptionId(recommendedOptionId);
        for (AgentClarification.Option option : options) {
            option.setRecommended(option.getId().equals(recommendedOptionId));
        }

        String policy = args.path("image_update_policy").asText("KEEP_APPEND").toUpperCase();
        if (!"REPLACE".equals(policy) && !"KEEP_APPEND".equals(policy) && !"KEEP".equals(policy)) {
            policy = "KEEP_APPEND";
        }
        String replaceAttachmentId = args.path("replace_attachment_id").asText(null);
        if ("REPLACE".equals(policy)) {
            boolean validTarget = false;
            for (ReferenceImage image : referenceContext.images()) {
                validTarget |= image.attachmentId().equals(replaceAttachmentId);
            }
            if (!validTarget) {
                policy = "KEEP_APPEND";
                replaceAttachmentId = null;
            }
        }
        clarification.setImageUpdatePolicy(policy);
        clarification.setReplaceAttachmentId(replaceAttachmentId);
        return clarification;
    }

    private List<AgentClarification.Option> parseClarificationOptions(JsonNode optionsNode) {
        if (!optionsNode.isArray() || optionsNode.isEmpty()) {
            return List.of();
        }
        List<AgentClarification.Option> options = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode node : optionsNode) {
            if (options.size() >= 3) {
                break;
            }
            String id = node.path("id").asText(null);
            String label = node.path("label").asText(null);
            if (!StringUtils.hasText(id) || id.length() > 40 || !ids.add(id)
                    || !StringUtils.hasText(label) || label.length() > 80) {
                continue;
            }
            AgentClarification.Option option = new AgentClarification.Option();
            option.setId(id);
            option.setLabel(label.trim());
            options.add(option);
        }
        return List.copyOf(options);
    }

    private String buildClarificationContext(ReferenceContext referenceContext,
                                             AgentClarification clarification) {
        ObjectNode root = parseClarificationContext(referenceContext.clarificationContextJson());
        if (StringUtils.hasText(referenceContext.skillName())) {
            root.put("skillName", referenceContext.skillName());
        }
        ArrayNode askedKeys = root.withArray("askedKeys");
        boolean exists = false;
        for (JsonNode node : askedKeys) {
            exists |= clarification.getKey().equals(node.asText());
        }
        if (!exists) {
            askedKeys.add(clarification.getKey());
        }
        JsonNode pending = objectMapper.valueToTree(clarification);
        if (pending.isObject()) {
            ((ObjectNode) pending).remove("scopeId");
        }
        root.set("pending", pending);
        return root.toString();
    }

    /**
     * generate_images:一次提交完整批量计划,数量按用户要求决定（支持单张），每个任务项生成一张图。
     * 图片引用只接受稳定附件ID，由后端映射到提交前已确定的素材，模型不能自行提供URL。
     */
    private String doGenerateImages(JsonNode args, Long userId, Long conversationId,
                                    Long sourceMessageId,
                                    ReferenceContext referenceContext,
                                         List<AgentResult.ImageRef> collectedImages,
                                    ToolExecutionState toolState) {
        if (toolState.submitted) {
            return "{\"error\":\"本轮批量出图计划已经提交,请勿重复调用\"}";
        }
        if (toolState.clarificationRequested) {
            return toolError("本轮已请求澄清，请等待用户回复后再生成");
        }

        String requestUpdate = args.path("request_update").asText("");
        String effectiveRequest = args.path("effective_request").asText("");
        if (!Set.of("REPLACE", "AMEND", "CONFIRM").contains(requestUpdate)
                || !StringUtils.hasText(effectiveRequest)) {
            throw invalidPlan("请补充request_update（REPLACE完整重述/AMEND局部修改/CONFIRM确认）与effective_request当前有效要求，不能只保存‘开始’或遗漏最新回答");
        }
        if (effectiveRequest.codePointCount(0, effectiveRequest.length()) > 32000) {
            throw invalidPlan("当前有效要求超过32000字符，请保留本任务的必要条件");
        }
        String sourceSnapshot = buildSourceSnapshot(referenceContext);
        String referenceUpdate = args.path("reference_update").asText("REPLACE");
        if (!Set.of("REPLACE", "MERGE").contains(referenceUpdate)) {
            throw invalidPlan("reference_update只能是REPLACE整组替换或MERGE明确保留旧素材");
        }
        referenceContext = selectPlanReferenceContext(referenceContext, requestUpdate, referenceUpdate);
        // 图片总数由模型结合完整需求、最新回答和逐张输出计划决定。
        // 后端仅验证结构化契约，禁止从原文、effective_request或追问回答截取数字覆盖计划。
        JsonNode jobsNode = args.path("jobs");
        JsonNode expectedCountNode = args.path("expected_count");
        if (!expectedCountNode.isIntegralNumber() || !expectedCountNode.canConvertToInt()) {
            throw invalidPlan("expected_count必须为1～9的整数，本次未提交");
        }
        int expectedCount = expectedCountNode.intValue();
        if (!jobsNode.isArray() || expectedCount < 1 || expectedCount > MAX_BATCH_IMAGES
                || jobsNode.isEmpty() || jobsNode.size() > MAX_BATCH_IMAGES) {
            throw invalidPlan("生图计划必须包含1～9个独立任务项，本次未提交");
        }
        int requiredCount = jobsNode.size();
        if (expectedCount != requiredCount) {
            // 内部计划自相矛盾不等于用户需求不明确，不把模型错误变成用户的数量追问。
            throw invalidPlan("计划声明" + expectedCount + "张，但包含" + requiredCount + "个任务项，本次未提交");
        }
        try {
            referenceContext = compactPlanReferences(referenceContext, jobsNode);
        } catch (ServiceException e) {
            throw invalidPlan(e.getMessage());
        }

        List<GenerateImageItem> items = new ArrayList<>();
        for (int i = 0; i < requiredCount; i++) {
            JsonNode job = jobsNode.get(i);
            int seq = i + 1;
            String prompt = job == null ? null : job.path("prompt").asText(null);
            if (!StringUtils.hasText(prompt)) {
                throw invalidPlan("第 " + seq + " 项缺少出图提示词");
            }

            List<Integer> referenceIndexes = job == null
                    ? allReferenceIndexes(referenceContext)
                    : parseReferenceAttachmentIds(job, referenceContext);
            if (referenceIndexes == null) {
                throw invalidPlan("第 " + seq + " 项包含不存在或重复的参考图附件ID");
            }
            try {
                ImagePromptCompiler.requireIndependent(job);
            } catch (ServiceException e) {
                throw invalidPlan(e.getMessage());
            }
            if (referenceIndexes.size() > MAX_INPUT_IMAGES) {
                throw invalidPlan("每张参考图总数不能超过9张");
            }

            GenerateImageItem item = new GenerateImageItem();
            item.setSeq(seq);
            try {
                List<ImageReferenceBinding> bindings = parseReferenceBindings(job);
                List<String> attachmentIds = referenceContext.images().stream()
                        .map(ReferenceImage::attachmentId).toList();
                List<String> visibleTexts = new ArrayList<>();
                JsonNode textNodes = job.path("visible_text");
                if (!textNodes.isMissingNode() && !textNodes.isArray()) {
                    throw new ServiceException("visible_text必须是画面文字数组");
                }
                for (JsonNode node : textNodes) {
                    if (!node.isTextual()) throw new ServiceException("画面文字必须是字符串");
                    visibleTexts.add(node.asText());
                }
                item.setPrompt(ImagePromptCompiler.compileV6(prompt, bindings, attachmentIds,
                        referenceIndexes, visibleTexts));
                item.setContractVersion(6);
                item.setPlannedPrompt(prompt);
                item.setVisibleTexts(List.copyOf(visibleTexts));
                item.setReferenceBindings(bindings);
                item.setReferenceAttachmentIds(attachmentIds);
                item.setSourcePrompt(sourceSnapshot);
                item.setEffectiveRequest(effectiveRequest);
                item.setRequestUpdate(requestUpdate);
            } catch (ServiceException e) {
                throw new RecoverableImagePlanException("第" + seq + "项参考契约无效：" + e.getMessage());
            }
            item.setReferenceImageIndexes(referenceIndexes);
            items.add(item);
        }

        List<String> normalizedImages = normalizeUploadedImages(referenceContext.urls());
        if (normalizedImages == null) {
            throw invalidPlan("本轮参考图地址不合法,只能使用用户上传的 /profile 图片");
        }

        String aspectRatio = args.path("aspect_ratio").asText(null);
        try {
            // 清晰度由用户设置及运行快照确定，不允许规划模型覆盖。
            String taskId = submitSelectedImageBatch(userId, conversationId, sourceMessageId, items,
                    emptyToNull(aspectRatio), normalizedImages, toolState);
            toolState.submitted = true;

            for (int i = 0; i < items.size(); i++) {
                AgentResult.ImageRef ref = new AgentResult.ImageRef();
                ref.setTaskId(taskId);
                ref.setSeq(i + 1);
                collectedImages.add(ref);
            }

            ObjectNode result = objectMapper.createObjectNode();
            result.put("task_id", taskId);
            result.put("n", items.size());
            result.put("status", "QUEUED");
            return result.toString();
        } catch (ServiceException e) {
            if (e instanceof InsufficientBeanException insufficient) {
                return requestAffordablePlanConfirmation(
                        userId, conversationId, referenceContext, toolState,
                        items, emptyToNull(aspectRatio), insufficient);
            }
            return toolError(e.getMessage());
        }
    }

    /**
     * 消费余额确认卡时直接提交服务端保存的出图计划。
     * generate_affordable 取原计划前 N 项，generate_requested 用于充值到账后恢复完整计划。
     */
    private boolean continueAffordablePlan(Sinks.Many<String> sink, AgentChatRequest request,
                                           Long userId, Long conversationId, Long sourceMessageId,
                                           ReferenceContext referenceContext,
                                           List<AgentResult.ImageRef> collectedImages,
                                           ToolExecutionState state) {
        String selectedOption = request == null ? null : request.getClarificationOptionId();
        if (!GENERATE_AFFORDABLE_OPTION.equals(selectedOption)
                && !GENERATE_REQUESTED_OPTION.equals(selectedOption)) {
            return false;
        }
        ObjectNode context = parseClarificationContext(referenceContext.clarificationContextJson());
        JsonNode answers = context.path("answers");
        JsonNode lastAnswer = answers.isArray() && !answers.isEmpty()
                ? answers.get(answers.size() - 1) : objectMapper.nullNode();
        if (!INSUFFICIENT_BALANCE_KEY.equals(lastAnswer.path("key").asText())
                || !selectedOption.equals(lastAnswer.path("optionId").asText())) {
            return false;
        }

        JsonNode plan = context.path(AFFORDABLE_PLAN_CONTEXT_KEY);
        JsonNode itemsNode = plan.path("items");
        if (!plan.isObject() || !itemsNode.isArray()
                || itemsNode.isEmpty() || itemsNode.size() > MAX_BATCH_IMAGES) {
            throw new ServiceException("刚才的出图计划已失效，请重新发送完整需求");
        }
        List<GenerateImageItem> fullPlan = new ArrayList<>(itemsNode.size());
        try {
            for (int i = 0; i < itemsNode.size(); i++) {
                ImagePromptCompiler.requireIndependent(itemsNode.get(i));
                GenerateImageItem item = objectMapper.treeToValue(itemsNode.get(i), GenerateImageItem.class);
                if (item == null || item.getSeq() == null || item.getSeq() != i + 1
                        || !StringUtils.hasText(item.getPrompt())) {
                    throw new ServiceException("刚才的出图计划已失效，请重新发送完整需求");
                }
                fullPlan.add(item);
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("刚才的出图计划已失效，请重新发送完整需求");
        }

        int targetCount = GENERATE_REQUESTED_OPTION.equals(selectedOption)
                ? fullPlan.size()
                : Math.min(plan.path("affordableCount").asInt(0), fullPlan.size());
        if (targetCount < 1) {
            throw new ServiceException("当前余额不足以生成图片，请先充值");
        }
        List<GenerateImageItem> selectedItems = List.copyOf(fullPlan.subList(0, targetCount));
        List<String> normalizedImages = normalizeUploadedImages(referenceContext.urls());
        if (normalizedImages == null) {
            throw new ServiceException("原任务中的参考图已失效，请重新上传后继续");
        }
        String aspectRatio = emptyToNull(plan.path("aspectRatio").asText(null));
        // Confirmation continues the quoted plan even if the user has since changed the picker.
        if (plan.has("imageModelId")) {
            state.imageModelId = plan.path("imageModelId").isNull() ? null : plan.path("imageModelId").longValue();
        }
        if (plan.has("imageResolution")) state.imageResolution = plan.path("imageResolution").asText(null);
        ActiveRun activeRun = sinkRuns.get(sink);
        if (activeRun != null && activeRun.persisted != null && chatRunService != null) {
            chatRunService.updateImageSelection(activeRun.persisted, state.imageModelId, state.imageResolution);
        }

        emit(sink, AgentSseEvent.tool("generate_images", "running", "正在提交出图"));
        try {
            state.toolCallId = "balance-plan";
            String taskId = submitSelectedImageBatch(userId, conversationId, sourceMessageId, selectedItems,
                    aspectRatio, normalizedImages, state);
            state.submitted = true;
            for (int i = 0; i < selectedItems.size(); i++) {
                AgentResult.ImageRef ref = new AgentResult.ImageRef();
                ref.setTaskId(taskId);
                ref.setSeq(i + 1);
                collectedImages.add(ref);
            }
            emit(sink, AgentSseEvent.tool("generate_images", "done",
                    "已提交 " + selectedItems.size() + " 张", taskId, selectedItems.size()));
        } catch (InsufficientBeanException insufficient) {
            requestAffordablePlanConfirmation(
                    userId, conversationId, referenceContext, state,
                    fullPlan, aspectRatio, insufficient);
            emit(sink, AgentSseEvent.clarification(state.clarification));
            emit(sink, AgentSseEvent.tool(
                    "generate_images", "done", "余额已变化，请重新确认"));
        }
        return true;
    }

    /** 将已解析计划和精确计费快照保存为一次性确认范围。 */
    private String requestAffordablePlanConfirmation(Long userId, Long conversationId,
                                                     ReferenceContext referenceContext,
                                                     ToolExecutionState state,
                                                     List<GenerateImageItem> items,
                                                     String aspectRatio,
                                                     InsufficientBeanException insufficient) {
        long balance = Math.max(0L, insufficient.getBalance());
        long needed = Math.max(0L, insufficient.getNeeded());
        long beanPerImage = Math.max(0L, insufficient.getBeanPerImage());
        int affordableCount = beanPerImage <= 0L
                ? 0 : (int) Math.min(items.size(), balance / beanPerImage);
        String question = affordableCount > 0
                ? "当前余额 " + balance + " 个豆子；本次 " + items.size() + " 张共需 "
                        + needed + " 个，每张 " + beanPerImage + " 个。当前最多可生成 "
                        + affordableCount + " 张。"
                : "当前余额 " + balance + " 个豆子；本次 " + items.size() + " 张共需 "
                        + needed + " 个，每张 " + beanPerImage + " 个。当前余额不足以生成 1 张图片。";

        AgentClarification clarification = new AgentClarification();
        clarification.setKey(INSUFFICIENT_BALANCE_KEY);
        clarification.setQuestion(question);
        List<AgentClarification.Option> options = new ArrayList<>();
        if (affordableCount > 0) {
            AgentClarification.Option generate = new AgentClarification.Option();
            generate.setId(GENERATE_AFFORDABLE_OPTION);
            generate.setLabel("生成 " + affordableCount + " 张");
            generate.setRecommended(true);
            options.add(generate);
            clarification.setRecommendedOptionId(GENERATE_AFFORDABLE_OPTION);
        }
        AgentClarification.Option recharge = new AgentClarification.Option();
        recharge.setId("recharge");
        recharge.setLabel("去充值");
        recharge.setRecommended(affordableCount == 0);
        options.add(recharge);
        if (affordableCount == 0) {
            clarification.setRecommendedOptionId("recharge");
        }
        clarification.setOptions(List.copyOf(options));
        clarification.setAllowFreeText(false);
        clarification.setImageUpdatePolicy("KEEP");
        int round = Math.max(1, referenceContext.clarificationRound() + 1);
        clarification.setRound(round);

        ObjectNode context = parseClarificationContext(
                buildClarificationContext(referenceContext, clarification));
        ObjectNode plan = context.putObject(AFFORDABLE_PLAN_CONTEXT_KEY);
        plan.put("requestedCount", items.size());
        plan.put("affordableCount", affordableCount);
        plan.put("balance", balance);
        plan.put("needed", needed);
        plan.put("beanPerImage", beanPerImage);
        plan.put("imageModelId", state.imageModelId);
        plan.put("imageResolution", state.imageResolution);
        if (StringUtils.hasText(aspectRatio)) {
            plan.put("aspectRatio", aspectRatio);
        }
        plan.set("items", objectMapper.valueToTree(items));

        List<String> attachmentIds = referenceContext.images().stream()
                .map(ReferenceImage::attachmentId)
                .toList();
        com.aurora.ai.agent.domain.ReferenceScope scope = referenceScopeService.open(
                conversationId, userId, referenceContext.sourceMessageId(),
                attachmentIds, question, false, context.toString(), round,
                LocalDateTime.now().plusHours(CLARIFICATION_TTL_HOURS));
        clarification.setScopeId(scope.getScopeId());
        state.clarificationQuestion = question;
        state.clarificationRequested = true;
        state.clarification = clarification;
        state.openedScopeId = scope.getScopeId();
        log.info("Agent 出图余额不足改为可执行确认: userId={}, conversationId={}, balance={},"
                        + " needed={}, unit={}, requested={}, affordable={}",
                userId, conversationId, balance, needed, beanPerImage, items.size(), affordableCount);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("clarification_required", true);
        result.put("question", question);
        result.put("affordable_count", affordableCount);
        result.put("instruction", "余额方案已由后端展示，请结束本轮");
        return result.toString();
    }

    private List<Integer> allReferenceIndexes(ReferenceContext referenceContext) {
        List<Integer> indexes = new ArrayList<>(referenceContext.images().size());
        for (int i = 0; i < referenceContext.images().size(); i++) {
            indexes.add(i + 1);
        }
        return indexes;
    }

    private ServiceException invalidPlan(String reason) {
        return new ServiceException("生图计划校验失败：" + reason);
    }

    private static final class RecoverableImagePlanException extends ServiceException {
        private RecoverableImagePlanException(String message) {
            super(message);
        }
    }

    private List<ImageReferenceBinding> parseReferenceBindings(JsonNode job) {
        JsonNode nodes = job == null ? objectMapper.nullNode() : job.path("reference_bindings");
        if (!nodes.isArray() || nodes.size() > 27) {
            throw new ServiceException("必须提供reference_bindings数组（文生图传空数组）");
        }
        List<ImageReferenceBinding> bindings = new ArrayList<>();
        for (JsonNode node : nodes) {
            JsonNode attrs = node.path("attributes");
            if (!attrs.isArray() || attrs.isEmpty() || attrs.size() > 16) {
                throw new ServiceException("每个参考职责必须包含1至16个属性");
            }
            List<String> attributes = new ArrayList<>();
            for (JsonNode attr : attrs) {
                if (!attr.isTextual()) throw new ServiceException("参考属性必须是字符串");
                attributes.add(attr.asText());
            }
            bindings.add(new ImageReferenceBinding(node.path("source_type").asText(null),
                    node.path("source_id").asText(null), attributes, node.path("usage").asText(null)));
        }
        return List.copyOf(bindings);
    }

    private String currentRequestText(ReferenceContext context) {
        return parseClarificationContext(context.clarificationContextJson())
                .path("currentPrompt").asText(context.sourcePrompt());
    }

    private String buildSourceSnapshot(ReferenceContext context) {
        String source = context.sourcePrompt() == null ? "" : context.sourcePrompt();
        JsonNode answers = parseClarificationContext(context.clarificationContextJson()).path("answers");
        if (!answers.isArray() || answers.isEmpty()) return source;
        StringBuilder snapshot = new StringBuilder("【原始要求】\n").append(source);
        for (JsonNode answer : answers) {
            snapshot.append("\n\n【澄清问题】\n").append(answer.path("question").asText(""))
                    .append("\n【用户回答原文，后者优先】\n").append(answer.path("answer").asText(""));
            if (answer.hasNonNull("optionId")) {
                snapshot.append("\n选择项：").append(answer.path("optionId").asText());
                snapshot.append("\n选项解释：").append(answer.path("summary").asText(""));
            }
        }
        return snapshot.toString();
    }

    /** 候选可含上一轮与本轮素材；提交前只冻结计划实际选中的至多9张原始图。 */
    private ReferenceContext compactPlanReferences(ReferenceContext context, JsonNode jobs) {
        if (!jobs.isArray()) return context;
        Set<String> selected = new LinkedHashSet<>();
        for (JsonNode job : jobs) {
            if (!job.path("reference_attachment_ids").isArray()) {
                throw new ServiceException("每项必须明确提供reference_attachment_ids数组，不引用时传[]，不得按格式错误退化为文生图");
            }
            List<String> ids = parseAllowedAttachmentIds(job.path("reference_attachment_ids"), context);
            if (ids == null) throw new ServiceException("计划引用了本轮范围以外的附件ID");
            selected.addAll(ids);
        }
        if (selected.size() > MAX_INPUT_IMAGES) {
            throw new ServiceException("整批实际选用的原始素材不能超过9张，请按本次要求选择必要素材，不得静默丢弃必需图");
        }
        List<ReferenceImage> images = context.images().stream()
                .filter(image -> selected.contains(image.attachmentId())).toList();
        return new ReferenceContext(context.sourceMessageId(), context.sourcePrompt(), images,
                context.continued(), context.clarificationContextJson(), context.clarificationRound(),
                context.askedClarificationKeys(), context.clarificationAnswerSummary(),
                context.skillName());
    }

    /** 只有模型明确判定完整重述才重建附件空间；不按字数或附图数量猜测业务意图。 */
    private ReferenceContext selectPlanReferenceContext(ReferenceContext context, String update) {
        return selectPlanReferenceContext(context, update, "REPLACE");
    }

    private ReferenceContext selectPlanReferenceContext(ReferenceContext context, String update, String referenceUpdate) {
        if (!context.continued() || !"REPLACE".equals(update)) return context;
        JsonNode json = parseClarificationContext(context.clarificationContextJson());
        JsonNode attachments = json.path("currentAttachments");
        List<ReferenceImage> images = new ArrayList<>();
        if (attachments.isArray()) {
            for (JsonNode attachment : attachments) {
                images.add(new ReferenceImage(attachment.path("attachmentId").asText(),
                        attachment.path("url").asText()));
            }
        }
        // 纯文字完整重述可继续使用已明确保留的素材；提交新整组则只使用新编号空间。
        if ("MERGE".equals(referenceUpdate)) images = new ArrayList<>(context.images());
        else if (images.isEmpty()) images.addAll(context.images());
        return new ReferenceContext(context.sourceMessageId(), currentRequestText(context),
                List.copyOf(images), true, context.clarificationContextJson(), context.clarificationRound(),
                context.askedClarificationKeys(), context.clarificationAnswerSummary(),
                context.skillName());
    }

    private String fallbackJobPrompt(int seq) {
        return "生成整体需求中的第 " + seq
                + " 张图片，保持统一人物与风格，并与其他图片在构图、机位或内容重点上有明确差异";
    }

    /** 校验并保留模型给出的参考图编号顺序;缺省表示文生图。 */
    private List<Integer> parseReferenceIndexes(JsonNode indexesNode, int uploadedImageCount) {
        if (!indexesNode.isArray() || indexesNode.isEmpty()) {
            return List.of();
        }
        if (indexesNode.size() > MAX_INPUT_IMAGES) {
            return null;
        }
        List<Integer> indexes = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (JsonNode node : indexesNode) {
            if (!node.isIntegralNumber()) {
                return null;
            }
            int index = node.asInt();
            if (index < 1 || index > uploadedImageCount || !seen.add(index)) {
                return null;
            }
            indexes.add(index);
        }
        return indexes;
    }

    /** 把模型提交的稳定附件ID映射为生成服务使用的 1 起始序号。 */
    private List<Integer> parseReferenceAttachmentIds(JsonNode job, ReferenceContext referenceContext) {
        JsonNode idsNode = job.path("reference_attachment_ids");
        if (!idsNode.isMissingNode() && !idsNode.isNull()) {
            List<String> ids = parseAllowedAttachmentIds(idsNode, referenceContext);
            if (ids == null) {
                return null;
            }
            Map<String, Integer> indexes = referenceContext.indexesByAttachmentId();
            return ids.stream().map(indexes::get).toList();
        }
        // 兼容已在进行中的旧模型调用形态；编号仍只能指向本轮不可变引用范围。
        return parseReferenceIndexes(job.path("reference_image_indexes"), referenceContext.images().size());
    }

    /** 校验附件ID是否完全属于本轮不可变引用范围，并保留模型给出的顺序。 */
    private List<String> parseAllowedAttachmentIds(JsonNode idsNode, ReferenceContext referenceContext) {
        if (!idsNode.isArray() || idsNode.isEmpty()) {
            return List.of();
        }
        if (idsNode.size() > MAX_INPUT_IMAGES) {
            return null;
        }
        Map<String, Integer> allowed = referenceContext.indexesByAttachmentId();
        List<String> ids = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode node : idsNode) {
            if (!node.isTextual()) {
                return null;
            }
            String attachmentId = node.asText();
            if (!allowed.containsKey(attachmentId) || !seen.add(attachmentId)) {
                return null;
            }
            ids.add(attachmentId);
        }
        return ids;
    }

    /** 将本轮图片统一归一化为共享生成服务可读取的 /profile 地址,保持原数组顺序。 */
    private List<String> normalizeUploadedImages(List<String> uploadedImages) {
        if (uploadedImages == null || uploadedImages.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String image : uploadedImages) {
            String url = normalizeProfileUrl(image);
            if (url == null) {
                return null;
            }
            normalized.add(url);
        }
        return normalized;
    }

    /** 构造工具错误 JSON,避免手工拼接用户或服务端错误文案造成 JSON 转义问题。 */
    private String toolError(String message) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("error", StringUtils.hasText(message) ? message : "出图计划校验失败");
        return result.toString();
    }

    /**
     * 解析文本里的 fenced ```aurora-result``` JSON 块为 {@link AgentResult}。
     * 未命中或 JSON 不合法返回 null(由调用方决定是否触发自修复);images 合并见 {@link #mergeImages}。
     */
    private AgentResult tryParseBlock(String finalText) {
        if (!StringUtils.hasText(finalText)) {
            return null;
        }
        Matcher matcher = RESULT_BLOCK_PATTERN.matcher(finalText);
        if (!matcher.find()) {
            if (finalText.contains("```")) {
                // 疑似应有块却未命中:留尾部样本定位是模型未输出还是格式漂移
                log.warn("aurora-result 块未命中, finalText 长度={}, 尾部300字: {}",
                        finalText.length(),
                        finalText.substring(Math.max(0, finalText.length() - 300)));
            }
            return null;
        }
        try {
            return objectMapper.readValue(matcher.group(1), AgentResult.class);
        } catch (Exception e) {
            // 常见成因:模型在字符串值里用了未转义英文双引号 → JSON 截断;由调用方触发自修复
            log.warn("aurora-result 块 JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 合并出图引用:有出图任务则用后端收集的 images 覆盖/补全(模型自填的 taskId 不可信);
     * block 为空且无图时返回 null(纯文案对话不发 result)。
     */
    private AgentResult mergeImages(AgentResult block, List<AgentResult.ImageRef> collectedImages) {
        AgentResult result = block;
        if (!collectedImages.isEmpty()) {
            if (result == null) {
                result = new AgentResult();
            }
            result.setImages(collectedImages);
        }
        return result;
    }

    private AgentResult terminationResult(String message, List<AgentResult.ImageRef> images) {
        AgentResult result = mergeImages(null, images);
        if (result != null) result.setBody(message);
        return result;
    }

    /**
     * 落 assistant message:有结构化产出存其 JSON 串(structured)、media 存 images 引用 JSON;
     * 否则存最终纯文本(text)。空文本且无结构化时不落库,避免空白 assistant 行。
     */
    private Message persistAssistant(Long conversationId, Long userId, String finalText,
                                     AgentResult agentResult, List<AgentResult.ImageRef> collectedImages,
                                     ToolExecutionState toolState) {
        if (toolState != null && toolState.clarificationRequested && toolState.clarification != null) {
            return messageService.saveMessage(conversationId, userId, "assistant",
                    toolState.clarification.getQuestion(), "clarification",
                    toJsonOrNull(toolState.clarification), null, null);
        }
        boolean hasText = StringUtils.hasText(finalText);
        if (!hasText && agentResult == null) {
            return null;
        }
        String content;
        String contentType;
        if (agentResult != null) {
            content = toJsonOrNull(agentResult);
            contentType = "structured";
        } else {
            content = finalText;
            contentType = "text";
        }
        String media = collectedImages.isEmpty() ? null : toJsonOrNull(collectedImages);
        return messageService.saveMessage(conversationId, userId, "assistant", content, contentType, media, null, null);
    }

    // ===== 模型解析 =====

    /**
     * 解析对话模型:req.modelId 优先;否则在启用的 chat 模型中优先取 is_default=1,
     * 再按 sort_order 升序兜底。不得硬编码 provider 或部署名。
     */
    private SysAiModel resolveChatModel(Long modelId) {
        if (modelId != null) {
            SysAiModel model = aiModelMapper.selectById(modelId);
            if (model == null) {
                throw new ServiceException("指定的对话模型不存在");
            }
            if (!"chat".equals(model.getModelType()) || !Integer.valueOf(1).equals(model.getIsActive())) {
                throw new ServiceException("指定的对话模型未启用或类型不正确");
            }
            return model;
        }
        SysAiModel model = aiModelMapper.selectOne(new LambdaQueryWrapper<SysAiModel>()
                .eq(SysAiModel::getModelType, "chat")
                .eq(SysAiModel::getIsActive, 1)
                .orderByDesc(SysAiModel::getIsDefault)
                .orderByAsc(SysAiModel::getSortOrder)
                .last("LIMIT 1"));
        if (model == null) {
            throw new ServiceException("无可用对话模型");
        }
        return model;
    }

    // ===== messages 构造 =====

    /** 构造运行时上下文:明确多模态看图、直接生图与批量计划规则。 */
    private String buildRuntimeContext(ReferenceContext referenceContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("【本轮最高优先级】\n")
                .append("用户本轮明确要求和最新澄清高于已激活 Skill；Skill 只补充用户没有说明的内容。")
                .append("若 Skill 与用户要求的图片角色、数量、步骤、文案或参数冲突，以用户当前要求为准。\n")
                .append("【生图决策规则】\n")
                .append("1. 你已能直接看到用户消息中的图片,不得要求独立识图或调用识图工具。\n")
                .append("2. 用户明确要求生成或编辑图片且给出完整可执行提示词时,直接规划并调用 generate_images；完整原文留审计，单张执行目标不得遗漏当前适用的条件。\n")
                .append("3. 当前上传编号与附件ID一一对应。用户明确指定图片职责时必须服从，禁止按画面内容或Skill擅自调换；只有用户未指定用途时，才根据内容识别主体、商品、服装、背景、风格、构图等通用属性。\n")
                .append("3.1. 只有缺少不可替代的主题、存在多个人物主体冲突、必须准确的文案缺失或同一层级条件互相矛盾时才追问。缺图但可安全合成时提供推荐兜底，不得直接拒绝。\n")
                .append("4. 光线、镜头、默认画幅等可合理补齐的信息不得触发追问。\n")
                .append("5. 图片总数必须依据完整提示词、最新修改及澄清回答的语义确定，先列齐本轮所有独立图片产出，再填写expected_count并确保与jobs数量一致，每项一个job，支持1～9张；参考图数量、年龄、尺寸、分组小计和标题/正文/标签数量不作为出图总数。用户未明确总数时按所列图片产出逐项计数；有总数和分组时核对二者一致，不能只取其中一个分组。\n")
				.append("5.1. 输出清单可唯一确定时直接生成；用户需求确实矛盾或无法确定产出项时才调用request_clarification追问。提交前自行核对expected_count与jobs一致，不得把内部漏项或计算错误作为用户需求不明确的理由。\n")
                .append("6. effective_request写出当前全部有效要求；request_update区分REPLACE完整新要求、AMEND局部修改、CONFIRM确认执行，依据语义而非字数判断。新要求覆盖旧要求，最新数值和角色不可沿用历史值。用户原文与回答另存审计；job.prompt必须包含所有本张适用的目标、属性、数值、镜头和差异，不包含其他输出的命令。区分全局、分组、单张要求及例外，完整展开本张适用条件；effective_request只留存审计，不会自动传给图片模型，不能用它代替job.prompt。\n")
                .append("7. 每个job只引用本次提交前已确定的素材，使用reference_attachment_ids。各张独立生成，不存在首图锚点、前序成品引用或分阶段执行；禁止通过其他工具或新任务绕过这一限制。\n")
                .append("7.0. job.prompt中所有图片引用必须使用[[ref:附件ID]]，例如[[ref:abc]]提供材质，附件ID原样取自可用素材。不得写原图号、图片序号、输入图号或猜测附图位置；后端依据reference_attachment_ids实际顺序统一替换标记并生成职责表。各张请求互不共享上下文，禁止用“与成品1一致”“同上一张”代替完整要求。\n")
                .append("7.01. 先从用户指定参考图提取需要保持的关键可见特征，形成共同描述，例如服装颜色、版型、领口与发型长度、轮廓；在所有适用job中原样完整复用，不得只写同一服装或同一发型。不得从其他图片补入该属性，不编造不可见细节，不改变用户指定职责。只供局部属性的图片不能带入其他外观，例如参考发色不等于继承其发型或服装。前后对比或左右区域必须分别展开共同特征、人物服装来源与各区域例外，不能把改造前属性扩散到改造后。提交前对照原始要求核对每个job的遗漏、冲突和例外；重要冲突先按用户明确优先级处理，仍无法消解再澄清。\n")
                .append("7.1. 用户主动添加的历史作品是本次素材；未选择的历史图片和本任务尚未生成的结果不得作为输入。多角度系列直接复用相同的原始人物、发型、服装和环境依据。\n")
                .append("7.11. 用户本轮明确引用之前的图片时，可用select_history_images按历史索引中的task_id/seq或attachment_id精确加载；仅加载语义明确的目标。多个候选无法唯一确定时先澄清，不能默认全部加载。工具加载后必须下一轮查看实际图片和新附件ID再规划生成。历史用途描述是生成请求摘要，不证明图片实际内容。\n")
                .append("7.2. 用户明确要求精确引用本任务首张、先生成改造前单图再拼接成品等依赖流程时，必须如实说明当前不支持自动接续；可请用户将已有成品主动添加为新任务素材。直接独立生成不能视为同等流程，不得默默省略中间产物或承诺相同效果；仅在用户明确同意直接生成替代方案后执行。没有人物素材时仅能统一文字描述，不承诺严格同脸。\n")
                .append("7.3. 每个job必须提供reference_bindings覆盖每张实际引用图。source_type固定original，source_id为附件ID；attributes为通用属性数组，同一对象的同一属性只有一个primary，其余为detail。多主体使用subject_a.identity等对象作用域，前后对比使用before.clothing与after.clothing等区域作用域。bindings必须覆盖prompt声明从图片提取的每个属性及适用区域，不得只声明改造前属性却在文字中也用于改造后。用户明确职责优先于图片内容和Skill，不得擅自混用；未指定时才根据画面识别用途。\n")
                .append("7.5. 有新完整要求和完整新图组时使用REPLACE并按本条新上传编号绑定；AMEND只覆盖明确修改的内容，CONFIRM不重写已确认要求。删除或增加输出时，effective_request必须完整表达保留、删除和新增后的产出清单与总数，不要求用户使用固定措辞。无关历史对话不能覆盖当前契约。每项执行条件必须完整，禁止引用其他输出。\n")
                .append("7.6. 合照、多个商品或多个对象用属性作用域区分：subject_a.identity与subject_b.identity、product_a.shape与product_b.shape可各有一个primary；job.prompt用相同对象名说明位置与关系，不把不同对象误判为同一属性冲突。\n")
                .append("7.7. 海报、商品标签等需要逐字出现在画面中的文案放入visible_text数组，job.prompt只描述排版位置，不混入文案里的‘图1’‘Image 2’等字样；这些字样是画面文字，不是参考图坐标。\n")
                .append("7.8. 需求重述与素材更新分开判断：完整新要求且上传完整新图组时reference_update=REPLACE；即使需求完整重述，若明确沿用旧商品图并加入新背景等，则reference_update=MERGE，按附件ID显式选取新旧素材，再收口最多9张实际原图。\n")
                .append("8. 追问必须使用稳定 key 且只问一个会改变核心结果的问题；给出 2~3 个普通用户能理解的选项并标记推荐项。用户回答‘随便/按推荐/你决定’时直接采用推荐项。\n")
                .append("8.1. 已经问过的 key 不得再次询问；清晰后立即执行，不得继续追问光线、镜头、画幅等可默认内容。\n")
                .append("9. 不进行自动审美质量检查,不得为主观质量自行重复提交。\n");
        if (!referenceContext.askedClarificationKeys().isEmpty()) {
            sb.append("【已完成澄清】\n已问问题类型：")
                    .append(String.join("、", referenceContext.askedClarificationKeys()))
                    .append("。这些问题不得再次询问。\n");
        }
        if (StringUtils.hasText(referenceContext.clarificationAnswerSummary())) {
            sb.append("【最近一次澄清回答】\n")
                    .append(referenceContext.clarificationAnswerSummary()).append("\n");
        }
        JsonNode currentAttachments = parseClarificationContext(referenceContext.clarificationContextJson())
                .path("currentAttachments");
        if (currentAttachments.isArray() && !currentAttachments.isEmpty()) {
            sb.append("【本条新上传图片：REPLACE时采用这里的编号空间】\n");
            for (int i = 0; i < currentAttachments.size(); i++) {
                sb.append("本条上传图").append(i + 1).append(" = ")
                        .append(currentAttachments.get(i).path("attachmentId").asText()).append("\n");
            }
        }
        if (!referenceContext.images().isEmpty()) {
            sb.append("【本轮可用图片】\n")
                    .append("图片已按以下编号作为多模态内容块提供;generate_images 仅通过 reference_attachment_ids 引用:\n");
            for (int i = 0; i < referenceContext.images().size(); i++) {
                ReferenceImage image = referenceContext.images().get(i);
                sb.append("图片").append(i + 1).append(" = ").append(image.attachmentId()).append("\n");
            }
        }
        return sb.toString().trim();
    }

    /**
     * 把最近历史(user/assistant 文本)拼成 messages 前缀。
     * 排除本轮刚落库的当前 user 消息(excludeId)与 tool 消息(tool 不跨轮回灌,降复杂度)。
     * 历史只回灌文本；图片必须来自当前消息或已消费的一次性澄清范围。
     */
    private void appendHistory(ArrayNode messages, List<Message> recent, Long excludeId) {
        for (Message m : recent) {
            if (m.getId().equals(excludeId)) {
                continue;
            }
            String role = m.getRole();
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            if (!StringUtils.hasText(m.getContent())) {
                continue;
            }
            messages.add(textMessage(role, m.getContent()));
        }
    }

    /**
     * 构造当前 user 多模态消息:文本块后按“图片N文本块 + image_url内容块”交错排列。
     * /profile 图片内联为 data URL,避免云雾无法访问本机或内网地址。
     */
    private ObjectNode userMessage(String message, ReferenceContext referenceContext) {
        String text = message == null ? "" : message;
        if (referenceContext.continued() && StringUtils.hasText(referenceContext.sourcePrompt())) {
            text = text + "\n\n【历史待澄清要求，仅用于判断保留或替换；本条完整新要求优先】\n" + referenceContext.sourcePrompt();
        }
        if (StringUtils.hasText(referenceContext.clarificationAnswerSummary())) {
            text = text + "\n\n【上次问题与本轮回答】\n"
                    + referenceContext.clarificationAnswerSummary()
                    + "\n该问题视为已经回答；若用户表示不确定或按推荐，采用上次问题的推荐项，不得再次追问同一问题。";
        }
        if (referenceContext.images().isEmpty()) {
            return textMessage("user", text);
        }
        ObjectNode node = objectMapper.createObjectNode();
        node.put("role", "user");
        ArrayNode content = node.putArray("content");
        if (StringUtils.hasText(text)) {
            content.addObject().put("type", "text").put("text", text);
        }
        for (int i = 0; i < referenceContext.images().size(); i++) {
            ReferenceImage image = referenceContext.images().get(i);
            content.addObject().put("type", "text")
                    .put("text", "图片" + (i + 1) + "（附件ID：" + image.attachmentId() + "）");
            ObjectNode imageUrl = content.addObject().put("type", "image_url").putObject("image_url");
            imageUrl.put("url", toMultimodalImageUrl(image.url()));
        }
        return node;
    }

    /** 把 Chat Completions 消息形态转换为 Responses input 形态。 */
    private ArrayNode toResponsesInput(ArrayNode messages) {
        ArrayNode input = objectMapper.createArrayNode();
        for (JsonNode message : messages) {
            String role = message.path("role").asText("user");
            if ("tool".equals(role)) {
                continue;
            }
            ObjectNode converted = input.addObject();
            converted.put("role", "system".equals(role) ? "developer" : role);
            JsonNode content = message.path("content");
            if (!content.isArray()) {
                converted.put("content", content.asText(""));
                continue;
            }
            ArrayNode convertedContent = converted.putArray("content");
            for (JsonNode part : content) {
                String type = part.path("type").asText();
                if ("text".equals(type)) {
                    convertedContent.addObject()
                            .put("type", "input_text")
                            .put("text", part.path("text").asText(""));
                } else if ("image_url".equals(type)) {
                    convertedContent.addObject()
                            .put("type", "input_image")
                            .put("image_url", part.path("image_url").path("url").asText(""));
                }
            }
        }
        return input;
    }

    /** 将本地 profile 图片内联为 data URL;公网 http(s) 图片保持原地址。 */
    private String toMultimodalImageUrl(String imageUrl) {
        String normalized = normalizeProfileUrl(imageUrl);
        if (normalized != null) {
            try {
                byte[] bytes = profileStorage.read(normalized);
                return "data:" + guessImageMimeType(bytes) + ";base64,"
                        + Base64.getEncoder().encodeToString(bytes);
            } catch (Exception e) {
                if (!isHttpUrl(imageUrl)) {
                    throw new ServiceException("图片读取失败: " + normalized, e);
                }
                log.warn("本地 profile 图片读取失败,降级使用公网地址: image={}, err={}", normalized, e.getMessage());
            }
        }
        if (isHttpUrl(imageUrl)) {
            return imageUrl.trim();
        }
        throw new ServiceException("图片地址不合法");
    }

    /** 校验本轮图片数量和地址,失败时直接结束 SSE,不创建会话或落消息。 */
    private boolean validateInputImages(List<String> images, Sinks.Many<String> sink) {
        if (images == null || images.isEmpty()) {
            return true;
        }
        if (images.size() > MAX_INPUT_IMAGES) {
            emit(sink, AgentSseEvent.error("每次最多上传 9 张图片"));
            sink.tryEmitComplete();
            return false;
        }
        for (String image : images) {
            if (!StringUtils.hasText(image)
                    || (normalizeProfileUrl(image) == null && !isHttpUrl(image))) {
                emit(sink, AgentSseEvent.error("上传图片地址不合法"));
                sink.tryEmitComplete();
                return false;
            }
        }
        return true;
    }

    /**
     * 解析新旧图片入参。已生成图必须属于当前用户且对应序号已有成功 URL，禁止信任客户端裸地址。
     */
    private List<ResolvedInputImage> resolveInputImages(Long userId, AgentChatRequest request) {
        List<AgentImageInput> typedInputs = request.getImageInputs();
        if (typedInputs == null || typedInputs.isEmpty()) {
            if (request.getImages() == null || request.getImages().isEmpty()) {
                return List.of();
            }
            List<ResolvedInputImage> legacy = new ArrayList<>(request.getImages().size());
            for (int i = 0; i < request.getImages().size(); i++) {
                legacy.add(new ResolvedInputImage(newAttachmentId(), i + 1, "UPLOAD",
                        resolveOwnedUploadImage(userId, request.getImages().get(i), i + 1), null, null));
            }
            return legacy;
        }
        if (typedInputs.size() > MAX_INPUT_IMAGES) {
            throw new ServiceException("每次最多上传 9 张图片");
        }

        List<ResolvedInputImage> resolved = new ArrayList<>(typedInputs.size());
        for (int i = 0; i < typedInputs.size(); i++) {
            AgentImageInput input = typedInputs.get(i);
            if (input == null || !StringUtils.hasText(input.getType())) {
                throw new ServiceException("图片" + (i + 1) + "参数不完整");
            }
            if ("upload".equalsIgnoreCase(input.getType())) {
                if (!StringUtils.hasText(input.getUrl())) {
                    throw new ServiceException("图片" + (i + 1) + "地址为空");
                }
                resolved.add(new ResolvedInputImage(newAttachmentId(), i + 1, "UPLOAD",
                        resolveOwnedUploadImage(userId, input.getUrl(), i + 1), null, null));
            } else if ("generated".equalsIgnoreCase(input.getType())) {
                resolved.add(new ResolvedInputImage(newAttachmentId(), i + 1, "GENERATED",
                        resolveOwnedGeneratedImage(userId, input, i + 1), input.getTaskId(), input.getSeq()));
            } else {
                throw new ServiceException("图片" + (i + 1) + "类型不支持");
            }
        }
        return resolved;
    }

    private String newAttachmentId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 上传图只允许当前用户自己的分目录。历史平铺头像路径无用户目录，
     * 仅当它曾出现在该用户自己的旧消息中时兼容放行。
     */
    private String resolveOwnedUploadImage(Long userId, String imageUrl, int displayIndex) {
        String normalized = normalizeProfileUrl(imageUrl);
        if (normalized == null) {
            throw new ServiceException("图片" + displayIndex + "不是可用的上传图");
        }
        String agentPrefix = "/profile/agent_reference/" + userId + "/";
        String avatarPrefix = "/profile/mobile_avatar/" + userId + "/";
        if (normalized.startsWith(agentPrefix) || normalized.startsWith(avatarPrefix)) {
            return normalized;
        }
        if (normalized.startsWith("/profile/mobile_avatar/")) {
            long ownedHistoryCount = messageService.count(new LambdaQueryWrapper<Message>()
                    .eq(Message::getUserId, userId)
                    .eq(Message::getRole, "user")
                    .like(Message::getMedia, normalized));
            if (ownedHistoryCount > 0) {
                return normalized;
            }
        }
        if (normalized.startsWith("/profile/generated/")) {
            long ownedTaskCount = generationTaskService.count(
                    new LambdaQueryWrapper<com.aurora.ai.agent.domain.GenerationTask>()
                            .eq(com.aurora.ai.agent.domain.GenerationTask::getUserId, userId)
                            .like(com.aurora.ai.agent.domain.GenerationTask::getResultUrls, normalized));
            if (ownedTaskCount > 0) {
                return normalized;
            }
        }
        throw new ServiceException("图片" + displayIndex + "不属于当前用户");
    }

    /**
     * 构建本轮唯一可用图片集。当前消息有新图时默认覆盖旧范围；
     * 只有上轮澄清明确允许追加图片时，才会合并一次。
     */
    private ReferenceContext resolveReferenceContext(Long conversationId, Long userId,
                                                     Message currentMessage,
                                                     List<ResolvedInputImage> currentInputs,
                                                     AgentChatRequest request) {
        List<ReferenceImage> currentImages = currentInputs.stream()
                .map(input -> new ReferenceImage(input.attachmentId(), input.url()))
                .toList();
        String scopeId = request == null ? null : request.getClarificationScopeId();
        if (!StringUtils.hasText(scopeId)) {
            // 没有精确范围ID即视为新任务；旧追问不得因“恰好是下一条消息”而复活。
            referenceScopeService.cancelOpen(conversationId, userId);
            return new ReferenceContext(currentMessage.getId(), currentMessage.getContent(),
                    currentImages, false, null, 0, Set.of(), null,
                    request == null ? null : request.getSkill());
        }

        ClaimedReferenceScope claimed = referenceScopeService.claim(
                scopeId, conversationId, userId, currentMessage.getId());
        if (claimed == null) {
            throw new ServiceException("刚才的问题已失效，请重新发送完整需求");
        }

        Message sourceMessage = messageService.getById(claimed.scope().getSourceMessageId());
        if (sourceMessage == null
                || !conversationId.equals(sourceMessage.getConversationId())
                || !userId.equals(sourceMessage.getUserId())
                || !"user".equals(sourceMessage.getRole())) {
            log.warn("一次性图片范围的原始消息无效: scopeId={}, sourceMessageId={}",
                    claimed.scope().getScopeId(), claimed.scope().getSourceMessageId());
            throw new ServiceException("原任务上下文已失效，请重新发送完整需求");
        }

        List<MessageAttachment> scopedRows = messageAttachmentService.listOwnedOrdered(
                conversationId, userId, claimed.attachmentIds());
        if (scopedRows.size() != claimed.attachmentIds().size()) {
            log.warn("一次性图片范围包含已失效附件: scopeId={}, expected={}, actual={}",
                    claimed.scope().getScopeId(), claimed.attachmentIds().size(), scopedRows.size());
            throw new ServiceException("原任务中的参考图已失效，请重新上传后继续");
        }

        List<ReferenceImage> scopedImages = new ArrayList<>(scopedRows.size());
        for (MessageAttachment attachment : scopedRows) {
            scopedImages.add(new ReferenceImage(attachment.getAttachmentId(), attachment.getProfileUrl()));
        }
        JsonNode previousContext = parseClarificationContext(claimed.scope().getContextJson());
        JsonNode pending = previousContext.path("pending");
        String imageUpdatePolicy = pending.path("imageUpdatePolicy").asText("KEEP_APPEND");
        String replaceAttachmentId = pending.path("replaceAttachmentId").asText(null);
        List<ReferenceImage> images = mergeClarificationImages(
                scopedImages, currentImages, imageUpdatePolicy, replaceAttachmentId);
        if (images.size() > MAX_INPUT_IMAGES * 2) {
            throw new ServiceException("本轮候选素材范围无效，请重新发送完整需求");
        }

        String pendingKey = pending.path("key").asText(null);
        String pendingQuestion = pending.path("question").asText(claimed.scope().getQuestion());
        String selectedOption = request == null ? null : request.getClarificationOptionId();
        String answer = currentMessage.getContent();
        String currentAnswerSummary = buildClarificationAnswerSummary(
                pendingQuestion, answer, selectedOption, pending.path("recommendedOptionId").asText(null),
                pending.path("options"));
        String advancedContext = advanceClarificationContext(
                previousContext, pendingKey, pendingQuestion, answer, selectedOption,
                currentAnswerSummary);
        ObjectNode currentContext = parseClarificationContext(advancedContext);
        currentContext.put("currentMessageId", currentMessage.getId());
        currentContext.put("currentPrompt", answer == null ? "" : answer);
        ArrayNode currentAttachmentIds = currentContext.putArray("currentAttachmentIds");
        currentImages.forEach(image -> currentAttachmentIds.add(image.attachmentId()));
        // 完整重述时模型可以选择本轮新图；保留被去重的同URL新附件ID供准确映射。
        ArrayNode currentAttachments = currentContext.putArray("currentAttachments");
        currentImages.forEach(image -> currentAttachments.addObject()
                .put("attachmentId", image.attachmentId()).put("url", image.url()));
        advancedContext = currentContext.toString();
        Set<String> askedKeys = readAskedClarificationKeys(parseClarificationContext(advancedContext));
        String answerSummary = buildClarificationHistorySummary(parseClarificationContext(advancedContext));
        int round = claimed.scope().getRoundNo() == null ? 1 : claimed.scope().getRoundNo();
        String skillName = parseClarificationContext(advancedContext).path("skillName").asText(null);
        return new ReferenceContext(sourceMessage.getId(), sourceMessage.getContent(), List.copyOf(images), true,
                advancedContext, round, askedKeys, answerSummary, skillName);
    }

    /** 回答追问时新图片默认追加；只有问题明确要求替换时才替换目标位置。 */
    private List<ReferenceImage> mergeClarificationImages(List<ReferenceImage> scopedImages,
                                                           List<ReferenceImage> currentImages,
                                                           String policy,
                                                           String replaceAttachmentId) {
        List<ReferenceImage> merged = new ArrayList<>(scopedImages);
        int currentStart = 0;
        if ("REPLACE".equals(policy) && StringUtils.hasText(replaceAttachmentId)
                && !currentImages.isEmpty()) {
            for (int i = 0; i < merged.size(); i++) {
                if (replaceAttachmentId.equals(merged.get(i).attachmentId())) {
                    merged.set(i, currentImages.get(0));
                    currentStart = 1;
                    break;
                }
            }
        }
        Set<String> attachmentIds = new LinkedHashSet<>();
        for (ReferenceImage image : merged) attachmentIds.add(image.attachmentId());
        for (int i = currentStart; i < currentImages.size(); i++) {
            ReferenceImage image = currentImages.get(i);
            if (attachmentIds.add(image.attachmentId())) merged.add(image);
        }
        return merged;
    }

    private ObjectNode parseClarificationContext(String json) {
        if (!StringUtils.hasText(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node.isObject() ? (ObjectNode) node : objectMapper.createObjectNode();
        } catch (Exception e) {
            log.warn("澄清上下文解析失败，按空上下文继续: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private String advanceClarificationContext(JsonNode previousContext, String key, String question,
                                               String answer, String optionId, String summary) {
        ObjectNode root = previousContext != null && previousContext.isObject()
                ? ((ObjectNode) previousContext).deepCopy() : objectMapper.createObjectNode();
        ArrayNode answers = root.withArray("answers");
        ObjectNode item = answers.addObject();
        item.put("key", key == null ? "HARD_CONFLICT" : key);
        item.put("question", question == null ? "" : question);
        item.put("answer", answer == null ? "" : answer);
        if (StringUtils.hasText(optionId)) {
            item.put("optionId", optionId);
        }
        if (StringUtils.hasText(summary)) {
            item.put("summary", summary);
        }
        root.remove("pending");
        return root.toString();
    }

    /** 把两轮内已确认的答案按原顺序完整回灌，防止后一轮覆盖前一轮映射。 */
    private String buildClarificationHistorySummary(JsonNode context) {
        JsonNode answers = context.path("answers");
        if (!answers.isArray() || answers.isEmpty()) {
            return null;
        }
        List<String> summaries = new ArrayList<>();
        for (JsonNode item : answers) {
            String summary = item.path("summary").asText(null);
            if (!StringUtils.hasText(summary)) {
                summary = "问题：" + item.path("question").asText("")
                        + "\n用户已回答：" + item.path("answer").asText("按推荐方案处理");
            }
            summaries.add(summary);
        }
        return String.join("\n\n", summaries);
    }

    private Set<String> readAskedClarificationKeys(JsonNode context) {
        Set<String> keys = new LinkedHashSet<>();
        JsonNode array = context.path("askedKeys");
        if (array.isArray()) {
            for (JsonNode node : array) {
                if (node.isTextual() && StringUtils.hasText(node.asText())) {
                    keys.add(node.asText());
                }
            }
        }
        return Set.copyOf(keys);
    }

    private String buildClarificationAnswerSummary(String question, String answer, String optionId,
                                                   String recommendedOptionId, JsonNode options) {
        String effective = answer == null ? "" : answer.trim();
        String effectiveOptionId = optionId;
        if (!StringUtils.hasText(effectiveOptionId) && prefersRecommended(effective)) {
            effectiveOptionId = recommendedOptionId;
        }
        if (StringUtils.hasText(effectiveOptionId) && options.isArray()) {
            for (JsonNode option : options) {
                if (effectiveOptionId.equals(option.path("id").asText())) {
                    String label = option.path("label").asText(effective);
                    if (!effective.equals(label) && StringUtils.hasText(effective)) {
                        effective = label + "\n用户补充原文：" + effective;
                    } else {
                        effective = label;
                    }
                    break;
                }
            }
        }
        return "问题：" + (question == null ? "" : question)
                + "\n用户已回答：" + (StringUtils.hasText(effective) ? effective : "按推荐方案处理");
    }

    private boolean prefersRecommended(String answer) {
        if (!StringUtils.hasText(answer)) {
            return false;
        }
        String normalized = answer.replace(" ", "");
        return normalized.contains("随便") || normalized.contains("按推荐")
                || normalized.contains("你决定") || normalized.contains("你看着办")
                || normalized.contains("都可以");
    }

    private String resolveOwnedGeneratedImage(Long userId, AgentImageInput input, int displayIndex) {
        if (!StringUtils.hasText(input.getTaskId()) || input.getSeq() == null || input.getSeq() < 1) {
            throw new ServiceException("图片" + displayIndex + "生成引用不完整");
        }
        com.aurora.ai.agent.domain.GenerationTask task =
                generationTaskService.getOwned(input.getTaskId(), userId);
        if (task == null || !StringUtils.hasText(task.getResultUrls())) {
            throw new ServiceException("图片" + displayIndex + "不存在或无权访问");
        }
        try {
            JsonNode urls = objectMapper.readTree(task.getResultUrls());
            int index = input.getSeq() - 1;
            if (!urls.isArray() || index >= urls.size() || !StringUtils.hasText(urls.path(index).asText())) {
                throw new ServiceException("图片" + displayIndex + "尚未生成成功");
            }
            return urls.path(index).asText();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("图片" + displayIndex + "生成引用解析失败", e);
        }
    }

    private boolean isHttpUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim().toLowerCase();
        return trimmed.startsWith("https://") || trimmed.startsWith("http://");
    }

    /** 按图片文件头识别常见 MIME 类型。 */
    private String guessImageMimeType(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return "image/png";
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return "image/png";
        }
        if (bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46) {
            return "image/webp";
        }
        if (bytes[0] == 0x47 && bytes[1] == 0x49 && bytes[2] == 0x46) {
            return "image/gif";
        }
        return "image/png";
    }

    /**
     * 构造纯文本 message 节点(role + content)。
     */
    private ObjectNode textMessage(String role, String content) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("role", role);
        node.put("content", content);
        return node;
    }

    /**
     * 构造本轮 assistant 消息节点。content 为本轮文本(可空字符串),tool_calls 原样回填供下一轮。
     */
    private ObjectNode buildAssistantMessage(RoundResult round) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("role", "assistant");
        node.put("content", round.content == null ? "" : round.content);
        if (!round.toolCalls.isEmpty()) {
            ArrayNode toolCalls = node.putArray("tool_calls");
            for (ToolCall tc : round.toolCalls) {
                ObjectNode tcNode = toolCalls.addObject();
                tcNode.put("id", tc.id);
                tcNode.put("type", "function");
                ObjectNode function = tcNode.putObject("function");
                function.put("name", tc.name);
                function.put("arguments", tc.arguments);
            }
        }
        return node;
    }

    // ===== 工具定义(OpenAI 兼容 tools 协议,标准 JSON Schema)=====

    /**
     * 构造 tools 数组:generate_images(批量异步出图)+ submit_result(结构化产出)。
     * GPT-5.6 直接接收图片内容块,不再暴露独立识图工具。
     */
    private ArrayNode buildTools() {
        ArrayNode tools = objectMapper.createArrayNode();

        ObjectNode history = tools.addObject().put("type", "function");
        ObjectNode historyFn = history.putObject("function").put("name", "select_history_images");
        historyFn.put("description", "仅在用户本轮明确引用历史图片时按稳定ID选择本会话已经存在的素材；有歧义先澄清，不自动回带所有历史图。加载后下一轮查看图片再生成。");
        ObjectNode historyParams = historyFn.putObject("parameters").put("type", "object");
        ObjectNode historyProps = historyParams.putObject("properties");
        ObjectNode generated = historyProps.putObject("generated_images").put("type", "array").put("maxItems", 9);
        ObjectNode generatedItem = generated.putObject("items").put("type", "object");
        ObjectNode generatedProps = generatedItem.putObject("properties");
        generatedProps.putObject("task_id").put("type", "string");
        generatedProps.putObject("seq").put("type", "integer").put("minimum", 1).put("maximum", 9);
        generatedItem.putArray("required").add("task_id").add("seq");
        generatedItem.put("additionalProperties", false);
        ObjectNode oldAttachments = historyProps.putObject("attachment_ids").put("type", "array").put("maxItems", 9);
        oldAttachments.putObject("items").put("type", "string");
        historyParams.put("additionalProperties", false);

        // generate_images
        ObjectNode generate = tools.addObject();
        generate.put("type", "function");
        ObjectNode generateFn = generate.putObject("function");
        generateFn.put("name", "generate_images");
        generateFn.put("description",
                "用户明确要生成或编辑图片时,一次性提交全部任务项,最多 9 张,数量按用户提示词或 Skill 判断。"
                        + "用户完整原文由后端自动保留;每个 prompt 只描述本张唯一目标,每张图一个 job。"
                        + "参考图只传本轮已确定的附件ID，不得传URL；各张独立执行，不支持本任务生成图引用");
        ObjectNode generateParams = generateFn.putObject("parameters");
        generateParams.put("type", "object");
        ObjectNode generateProps = generateParams.putObject("properties");
        ObjectNode expectedCount = generateProps.putObject("expected_count");
        expectedCount.put("type", "integer");
        expectedCount.put("description", "结合完整需求、最新修改和澄清回答逐项规划出的图片总数，必须与jobs长度一致；不计参考图、分组小计及文本产出");
        expectedCount.put("minimum", 1);
        expectedCount.put("maximum", MAX_BATCH_IMAGES);
        ObjectNode jobs = generateProps.putObject("jobs");
        jobs.put("type", "array");
        jobs.put("description", "完整批量计划,每个元素只生成一张图,顺序即最终图片顺序");
        jobs.put("minItems", 1);
        jobs.put("maxItems", MAX_BATCH_IMAGES);
        ObjectNode jobSchema = jobs.putObject("items");
        jobSchema.put("type", "object");
        ObjectNode jobProps = jobSchema.putObject("properties");
        ObjectNode seq = jobProps.putObject("seq");
        seq.put("type", "integer");
        seq.put("description", "结果顺序,从 1 开始连续递增");
        seq.put("minimum", 1);
        seq.put("maximum", MAX_BATCH_IMAGES);
        jobProps.putObject("prompt")
                .put("type", "string")
                .put("description", "独立可执行的本张完整要求：展开适用的全局、分组条件和本张差异，保留具体数值与指定参考的可见特征；所有图片引用只能用[[ref:附件ID]]，禁止原图号、输入图号及与上一张一致等指代；后端自动转换为实际附图位置。其他成品要求不进入本项，画面文字放visible_text");
        ObjectNode referenceAttachmentIds = jobProps.putObject("reference_attachment_ids");
        ObjectNode visibleText = jobProps.putObject("visible_text");
        visibleText.put("type", "array");
        visibleText.put("maxItems", 20);
        visibleText.put("description", "需要逐字出现在画面中的文案或商品文字，和图片引用分开；无文字时省略或空数组");
        visibleText.putObject("items").put("type", "string").put("maxLength", 4000);
        referenceAttachmentIds.put("type", "array");
        referenceAttachmentIds.put("description",
                "本张图使用的参考图附件ID,必须从本轮可用图片列表原样选择;空数组表示文生图");
        referenceAttachmentIds.put("maxItems", MAX_INPUT_IMAGES);
        referenceAttachmentIds.putObject("items").put("type", "string");
        ObjectNode bindings = jobProps.putObject("reference_bindings");
        bindings.put("type", "array");
        bindings.put("description", "覆盖全部引用图的属性职责，实际附图顺序由后端编译，不能继承未声明属性");
        bindings.put("maxItems", 27);
        ObjectNode binding = bindings.putObject("items");
        binding.put("type", "object");
        ObjectNode bindingProps = binding.putObject("properties");
        bindingProps.putObject("source_type").put("type", "string")
                .putArray("enum").add("original");
        bindingProps.putObject("source_id").put("type", "string")
                .put("description", "本次提交前已确定的素材附件ID");
        ObjectNode attributes = bindingProps.putObject("attributes");
        attributes.put("type", "array");
        attributes.put("minItems", 1);
        attributes.put("maxItems", 16);
        attributes.putObject("items").put("type", "string");
        bindingProps.putObject("usage").put("type", "string")
                .putArray("enum").add("primary").add("detail");
        binding.putArray("required").add("source_type").add("source_id").add("attributes").add("usage");
        jobSchema.putArray("required").add("prompt")
                .add("reference_attachment_ids").add("reference_bindings");
        // 分辨率来自用户选择或后台默认值，不向规划模型暴露可覆盖的参数。
        generateProps.putObject("aspect_ratio")
                .put("type", "string")
                .put("description", "画幅比例,可空(如 1:1 / 16:9 / 9:16 / 3:4)");
        generateProps.putObject("request_update").put("type", "string")
                .putArray("enum").add("REPLACE").add("AMEND").add("CONFIRM");
        generateProps.putObject("reference_update").put("type", "string")
                .putArray("enum").add("REPLACE").add("MERGE");
        generateProps.putObject("effective_request").put("type", "string")
                .put("description", "按最新明确要求与澄清整理的完整有效需求，保留准确数量、数值、素材职责及各输出变化；不能只写‘开始’");
        generateParams.putArray("required").add("expected_count").add("jobs")
                .add("request_update").add("effective_request");

        // request_clarification：只在信息确实冲突或缺失时使用，显式锁定下一轮可继续使用的图片。
        ObjectNode clarification = tools.addObject();
        clarification.put("type", "function");
        ObjectNode clarificationFn = clarification.putObject("function");
        clarificationFn.put("name", "request_clarification");
        clarificationFn.put("description",
                "只有核心结果所需的信息确实缺失或同层级条件互相矛盾时调用。"
                        + "每次只问一个问题并给普通用户推荐选项;后端自动保留全部当前图片。"
                        + "已回答问题、可合理默认的镜头光线画幅不得追问;工具成功后结束本轮");
        ObjectNode clarificationParams = clarificationFn.putObject("parameters");
        clarificationParams.put("type", "object");
        ObjectNode clarificationProps = clarificationParams.putObject("properties");
        ObjectNode clarificationRefs = clarificationProps.putObject("reference_attachment_ids");
        clarificationRefs.put("type", "array");
        clarificationRefs.put("maxItems", MAX_INPUT_IMAGES);
        clarificationRefs.put("description", "新旧候选图合计超过9张时，显式选择本轮澄清应保留的附件；其他情况可省略");
        clarificationRefs.putObject("items").put("type", "string");
        clarificationProps.putObject("question")
                .put("type", "string")
                .put("description", "面向普通用户的一句话问题,不要使用附件ID或要求用户自己写组合");
        ObjectNode clarificationKey = clarificationProps.putObject("key");
        clarificationKey.put("type", "string");
        ArrayNode keyEnum = clarificationKey.putArray("enum");
        CLARIFICATION_KEYS.forEach(keyEnum::add);
        clarificationKey.put("description", "稳定问题类型,同一类型最多询问一次");
        ObjectNode options = clarificationProps.putObject("options");
        options.put("type", "array");
        options.put("maxItems", 3);
        options.put("description", "0~3个快捷选项;有合理默认时推荐项放第一");
        ObjectNode optionItem = options.putObject("items");
        optionItem.put("type", "object");
        ObjectNode optionProps = optionItem.putObject("properties");
        optionProps.putObject("id").put("type", "string");
        optionProps.putObject("label").put("type", "string");
        optionItem.putArray("required").add("id").add("label");
        clarificationProps.putObject("recommended_option_id")
                .put("type", "string")
                .put("description", "推荐选项id;用户说随便或按推荐时采用");
        clarificationProps.putObject("allow_free_text")
                .put("type", "boolean")
                .put("description", "是否允许用户自由输入,默认true");
        ObjectNode imagePolicy = clarificationProps.putObject("image_update_policy");
        imagePolicy.put("type", "string");
        imagePolicy.putArray("enum").add("KEEP").add("KEEP_APPEND").add("REPLACE");
        imagePolicy.put("description", "KEEP保持原图;KEEP_APPEND允许补图且默认追加;REPLACE用新图替换指定附件");
        clarificationProps.putObject("replace_attachment_id")
                .put("type", "string")
                .put("description", "仅REPLACE时填写被替换的当前附件ID");
        clarificationParams.putArray("required").add("key").add("question");

        // submit_result:结构化产出提交(标题/正文/标签),模型按契约/用户要求自主决策调用
        ObjectNode submit = tools.addObject();
        submit.put("type", "function");
        ObjectNode submitFn = submit.putObject("function");
        submitFn.put("name", "submit_result");
        submitFn.put("description",
                "提交结构化产出(标题/正文/标签)。按 Skill 产出契约创作、或用户明确要求分段/可分别复制的内容时,"
                        + "在给出最终回复前调用;普通对话不要调用。title 只放标题,body 只放正文,tags 只放标签数组,"
                        + "不得把标签段重复写入 title。整套产出只调用一次,重复调用以最后一次为准");
        ObjectNode submitParams = submitFn.putObject("parameters");
        submitParams.put("type", "object");
        ObjectNode submitProps = submitParams.putObject("properties");
        submitProps.putObject("title")
                .put("type", "string")
                .put("description", "仅标题区;用户要求多类或多条标题时按封面标题、笔记标题等分组完整输出,严禁包含正文或话题标签");
        submitProps.putObject("body")
                .put("type", "string")
                .put("description", "正文全文");
        ObjectNode submitTags = submitProps.putObject("tags");
        submitTags.put("type", "array");
        submitTags.put("description", "仅标签列表,不带#号;不要再把标签文本写入 title");
        submitTags.putObject("items").put("type", "string");

        return tools;
    }

    /** Responses 函数工具定义没有 Chat Completions 的 function 包装层。 */
    private ArrayNode buildResponsesTools() {
        ArrayNode responsesTools = objectMapper.createArrayNode();
        for (JsonNode chatTool : buildTools()) {
            JsonNode function = chatTool.path("function");
            ObjectNode tool = responsesTools.addObject();
            tool.put("type", "function");
            tool.put("name", function.path("name").asText());
            tool.put("description", function.path("description").asText());
            tool.set("parameters", function.path("parameters").deepCopy());
        }
        return responsesTools;
    }

    // ===== 通用工具方法 =====

    /**
     * 归一化图生图参考图 URL 为 /profile 相对路径(消费端只认 /profile/ 前缀,防路径穿越):
     * 模型可能回传完整地址(http://host/profile/x.png),截取 /profile/ 起的相对部分;
     * 不含 /profile/ 或带 .. 的返回 null(调用方整体报错回灌)。
     */
    private String normalizeProfileUrl(String url) {
        if (!StringUtils.hasText(url) || url.contains("..")) {
            return null;
        }
        if (url.startsWith("/profile/")) {
            return url;
        }
        int idx = url.indexOf("/profile/");
        return idx > 0 ? url.substring(idx) : null;
    }

    /**
     * 拼接 chat/completions 完整地址(对齐 AiFactory.completionsPath 逻辑)。
     */
    private String buildCompletionsUrl(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        if (base.contains("/chat/completions")) {
            return base;
        }
        boolean isVersioned = base.matches(".*\\/v\\d+.*") || base.contains("/api/");
        return isVersioned ? base + "/chat/completions" : base + "/v1/chat/completions";
    }

    private boolean isResponsesProtocol(SysAiModel model) {
        return model != null && "responses".equalsIgnoreCase(model.getApiProtocol());
    }

    /**
     * 解析 tool_call.arguments 字符串为 JsonNode;为空或非法 JSON 时返回空对象节点(降级,不抛)。
     */
    private JsonNode parseArguments(String arguments) {
        if (!StringUtils.hasText(arguments)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(arguments);
        } catch (Exception e) {
            log.warn("工具参数 JSON 解析失败,降级空参: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    /**
     * 取首条消息文本前 50 字符作会话标题;空则兜底「新对话」(交由 Service 兜底,此处仅截断)。
     */
    private String summarizeTitle(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String trimmed = message.trim();
        return trimmed.length() <= 50 ? trimmed : trimmed.substring(0, 50);
    }

    /**
     * 结构化标题只保留标题分组；模型偶尔把话题标签段重复塞入 title 时在服务端截断。
     */
    static String normalizeStructuredTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return title;
        }
        int cut = title.length();
        for (String marker : List.of("\n话题标签：", "\n话题标签:", "\n【话题标签】", "\n标签：", "\n标签:")) {
            int index = title.indexOf(marker);
            if (index >= 0 && index < cut) {
                cut = index;
            }
        }
        return title.substring(0, cut).trim();
    }

    /**
     * 空串转 null(出图分辨率/画幅可空,空串按未传处理走模型默认)。
     */
    private String emptyToNull(String s) {
        return StringUtils.hasText(s) ? s : null;
    }

    /**
     * 对象序列化为 JSON 串;null 或序列化失败返回 null。
     */
    private String toJsonOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("JSON 序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 向 sink 推一条 SSE 事件(单行 JSON);失败仅告警,不中断编排。
     */
    private void emit(Sinks.Many<String> sink, AgentSseEvent event) {
        ActiveRun run = sinkRuns.get(sink);
        if (run != null && run.persisted != null && chatRunService != null) {
            event.setRunId(run.persisted.getRequestId());
            event.setRequestId(run.persisted.getRequestId());
            chatRunService.observe(run.persisted, event);
        }
        Sinks.EmitResult emitResult = sink.tryEmitNext(event.toJsonLine());
        if (emitResult.isFailure()) {
            log.warn("SSE 事件下发失败: type={}, result={}", event.getType(), emitResult);
        }
    }

    private String submitSelectedImageBatch(Long userId, Long conversationId, Long sourceMessageId,
                                            List<GenerateImageItem> items, String aspectRatio,
                                            List<String> images, ToolExecutionState state) {
        if (state.imageModelId == null && !StringUtils.hasText(state.requestId)) {
            return generationTaskService.submitImageBatch(userId, conversationId, sourceMessageId,
                    items, state.imageResolution, aspectRatio, images);
        }
        String requestId = null;
        if (StringUtils.hasText(state.requestId)) {
            requestId = UUID.nameUUIDFromBytes((state.requestId + ":" + state.toolCallId)
                    .getBytes(StandardCharsets.UTF_8)).toString();
        }
        return generationTaskService.submitImageBatch(userId, conversationId, sourceMessageId,
                items, state.imageResolution, aspectRatio, images, state.imageModelId, requestId);
    }

    // ===== 内部累积结构 =====

    /** 一次活跃编排的取消状态；模型/工具阶段可中断，落库结算阶段不可中断。 */
    private static final class ActiveRun {
        private final Long userId;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean cleaned = new AtomicBoolean(false);
        private boolean interruptible = true;
        private boolean started;
        private Runnable onQueuedCancellation;
        private volatile Disposable worker;
        private ChatRun persisted;
        private SysAiModel chatModel;

        private ActiveRun(Long userId) {
            this.userId = userId;
        }

        private synchronized void attach(Disposable worker) {
            this.worker = worker;
            if (cancelled.get()) {
                worker.dispose();
            }
        }

        private synchronized boolean cancel() {
            if (!interruptible) {
                return false;
            }
            cancelled.set(true);
            interruptible = false;
            Disposable current = worker;
            if (current != null) {
                current.dispose();
            }
            if (!started && onQueuedCancellation != null) onQueuedCancellation.run();
            return true;
        }

        private synchronized boolean beginExecution() {
            if (cancelled.get()) return false;
            started = true;
            return true;
        }

        private synchronized boolean beginFinalization() {
            if (cancelled.get()) {
                return false;
            }
            interruptible = false;
            return true;
        }
    }

    /**
     * 一轮流式请求的累积结果。
     */
    private static class RoundResult {
        /** 本轮 assistant 正文文本(增量拼接) */
        String content = "";
        /** 本轮结束原因(stop / tool_calls / length 等) */
        String finishReason;
        /** 本轮模型要求调用的工具(按 index 有序) */
        final List<ToolCall> toolCalls = new ArrayList<>();
        /** 本轮模型 usage:总输入 token(prompt_tokens) */
        long promptTokens;
        /** 本轮模型 usage:输出 token(completion_tokens) */
        long completionTokens;
        /** 本轮模型 usage:输入命中缓存 token(prompt_cache_hit_tokens / prompt_tokens_details.cached_tokens) */
        long cachedTokens;
    }

    /** 一次对话的累计 token 用量,多模态图片 token 已包含在对话模型 prompt_tokens 中。 */
    private static class TokenUsage {
        long promptTokens;
        long cachedTokens;
        long completionTokens;

        void add(long prompt, long cached, long completion) {
            promptTokens += prompt;
            cachedTokens += cached;
            completionTokens += completion;
        }

        /** 是否已产生任何 token(用于中断/失败时判断是否需结算) */
        boolean hasAny() {
            return promptTokens > 0 || completionTokens > 0;
        }

        /** 总 token(写入 message.token_usage) */
        long total() {
            return promptTokens + completionTokens;
        }
    }

    /** 本轮工具状态：出图与澄清互斥，参考表达错误只在提交前有限修正。 */
    private static class ToolExecutionState {
        ReferenceContext selectedHistory;
        boolean historyPending;
        Long imageModelId;
        String imageResolution;
        String requestId;
        String toolCallId;
        private int planCorrections;
        boolean submitted;
        boolean clarificationRequested;
        String openedScopeId;
        String clarificationQuestion;
        AgentClarification clarification;
        final Set<String> answeredClarificationKeys = new LinkedHashSet<>();
    }

    /** 从客户端入参完成归属校验后的图片，在落库前即分配稳定附件ID。 */
    private record ResolvedInputImage(String attachmentId, int position, String sourceType,
                                      String url, String sourceTaskId, Integer sourceSeq) {
        MessageAttachmentDraft toDraft() {
            return new MessageAttachmentDraft(attachmentId, position, sourceType,
                    url, sourceTaskId, sourceSeq);
        }

        Map<String, Object> toMediaRef() {
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("type", "GENERATED".equals(sourceType) ? "generated" : "upload");
            ref.put("url", url);
            if ("GENERATED".equals(sourceType)) {
                ref.put("taskId", sourceTaskId);
                ref.put("seq", sourceSeq);
            }
            return ref;
        }
    }

    /** 本轮模型可见、可被工具引用的图片。 */
    private record ReferenceImage(String attachmentId, String url) {
    }

    /** 本轮不可变的图片引用与原始需求上下文。 */
    private record ReferenceContext(Long sourceMessageId, String sourcePrompt,
                                    List<ReferenceImage> images, boolean continued,
                                    String clarificationContextJson, int clarificationRound,
                                    Set<String> askedClarificationKeys,
                                    String clarificationAnswerSummary,
                                    String skillName) {
        List<String> urls() {
            return images.stream().map(ReferenceImage::url).toList();
        }

        Map<String, Integer> indexesByAttachmentId() {
            Map<String, Integer> indexes = new LinkedHashMap<>();
            for (int i = 0; i < images.size(); i++) {
                indexes.put(images.get(i).attachmentId(), i + 1);
            }
            return indexes;
        }
    }

    /**
     * 单个 tool_call 累积器(按 index 合并跨块片段)。
     */
    private static class ToolCallBuilder {
        String id;
        String name;
        final StringBuilder arguments = new StringBuilder();

        ToolCall build() {
            // 无 name 视为无效片段,跳过
            if (!StringUtils.hasText(name)) {
                return null;
            }
            ToolCall tc = new ToolCall();
            tc.id = id;
            tc.name = name;
            tc.arguments = arguments.toString();
            return tc;
        }
    }

    /**
     * 一个完整的工具调用。
     */
    private static class ToolCall {
        String id;
        String name;
        String arguments;
    }
}
