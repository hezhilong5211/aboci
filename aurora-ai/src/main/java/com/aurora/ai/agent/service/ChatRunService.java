package com.aurora.ai.agent.service;

import com.aurora.ai.agent.domain.ChatRun;
import com.aurora.ai.agent.domain.Conversation;
import com.aurora.ai.agent.domain.GenerationTask;
import com.aurora.ai.agent.domain.Message;
import com.aurora.ai.agent.dto.ChatRunVO;
import com.aurora.ai.agent.dto.GenerationTaskVO;
import com.aurora.ai.agent.mapper.ChatRunMapper;
import com.aurora.ai.agent.mapper.ConversationMapper;
import com.aurora.ai.domain.SysAiModel;
import com.aurora.ai.dto.AgentClarification;
import com.aurora.ai.dto.AgentResult;
import com.aurora.ai.dto.AgentSseEvent;
import com.aurora.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Durable request identity and snapshots. Transport disconnection never cancels a run. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRunService {
    private static final Set<String> BUSY = Set.of("QUEUED", "RUNNING", "GENERATING");
    private final ChatRunMapper mapper;
    private final ConversationMapper conversationMapper;
    private final IGenerationTaskService generationTaskService;
    private final IMessageService messageService;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;
    private final Map<Long, ChatRun> localRuns = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastFlush = new ConcurrentHashMap<>();

    public ChatRun findRequest(Long userId, String requestId) {
        return mapper.selectOne(new LambdaQueryWrapper<ChatRun>()
                .eq(ChatRun::getUserId, userId).eq(ChatRun::getRequestId, requestId));
    }

    public ChatRun findSource(Long userId, Long messageId) {
        if (messageId == null) return null;
        return mapper.selectOne(new LambdaQueryWrapper<ChatRun>()
                .eq(ChatRun::getUserId, userId).eq(ChatRun::getMessageId, messageId)
                .orderByDesc(ChatRun::getId).last("LIMIT 1"));
    }

    @Transactional(rollbackFor = Exception.class)
    public Reservation reserve(Long userId, Long conversationId, String requestId,
                           String fingerprint, SysAiModel model, Long imageModelId, ChatRun resumed) {
        return reserve(userId, conversationId, requestId, fingerprint, model, imageModelId, null, resumed);
    }

    @Transactional(rollbackFor = Exception.class)
    public Reservation reserve(Long userId, Long conversationId, String requestId,
                               String fingerprint, SysAiModel model, Long imageModelId,
                               String imageResolution, ChatRun resumed) {
        Conversation owned = conversationMapper.selectOwnedForUpdate(conversationId, userId);
        if (owned == null) throw new ServiceException("会话不存在或无权访问");
        ChatRun existing = findRequest(userId, requestId);
        if (existing != null) {
            assertSameRequest(existing, fingerprint, conversationId);
            return new Reservation(existing, false);
        }
        ChatRun latest = latest(userId, conversationId);
        if (latest != null && BUSY.contains(refresh(latest).getStatus())) {
            throw new ServiceException("该会话正在处理中，请等待完成或先停止");
        }
        ChatRun run = new ChatRun();
        run.setUserId(userId);
        run.setConversationId(conversationId);
        run.setRequestId(requestId);
        run.setRequestFingerprint(fingerprint);
        run.setModelId(model.getId());
        run.setImageModelId(resumed == null ? imageModelId : resumed.getImageModelId());
        run.setBillingSnapshot(resumed == null ? billingSnapshot(model, imageResolution) : resumed.getBillingSnapshot());
        run.setStatus("QUEUED");
        run.setText("");
        run.setThinking("");
        LocalDateTime now = LocalDateTime.now();
        run.setCreateTime(now);
        run.setUpdateTime(now);
        run.setHeartbeatTime(now);
        mapper.insert(run);
        updateConversation(run);
        localRuns.put(run.getId(), run);
        return new Reservation(run, true);
    }

    public void assertSameRequest(ChatRun run, String fingerprint, Long conversationId) {
        if (!run.getRequestFingerprint().equals(fingerprint)
                || (conversationId != null && !conversationId.equals(run.getConversationId()))) {
            throw new ServiceException("请求编号已用于其他内容，请重新发送");
        }
    }

    public void bindMessage(ChatRun run, Long messageId) {
        if (run == null) return;
        run.setMessageId(messageId);
        run.setStatus("RUNNING");
        flush(run);
        updateConversation(run);
    }

    public void bindAssistant(ChatRun run, Long assistantMessageId) {
        if (run == null) return;
        run.setAssistantMessageId(assistantMessageId);
        flush(run);
    }

    public void observe(ChatRun run, AgentSseEvent event) {
        if (run == null) return;
        synchronized (run) {
            if ("text".equals(event.getType())) run.setText(run.getText() + nullToEmpty(event.getContent()));
            if ("thinking".equals(event.getType())) run.setThinking(run.getThinking() + nullToEmpty(event.getContent()));
            if ("result".equals(event.getType())) run.setResultJson(json(event.getData()));
            if ("clarification".equals(event.getType())) run.setClarificationJson(json(event.getClarification()));
            if ("error".equals(event.getType())) {
                run.setErrorMessage(event.getMessage());
                run.setCompletionStatus("INSUFFICIENT_BEANS".equals(event.getErrorCode())
                        ? "WAITING_RECHARGE" : "FAILED");
            }
            long now = System.currentTimeMillis();
            if (now - lastFlush.getOrDefault(run.getId(), 0L) >= 500 || !Set.of("text", "thinking").contains(event.getType())) {
                flush(run);
            }
        }
    }

    public void finish(ChatRun run, String status) {
        if (run == null) return;
        synchronized (run) {
            String observedOutcome = run.getCompletionStatus() == null ? run.getStatus() : run.getCompletionStatus();
            if ("COMPLETED".equals(status) && Set.of("FAILED", "WAITING_RECHARGE").contains(observedOutcome)) {
                status = observedOutcome;
            }
            run.setStatus(status);
            if (!BUSY.contains(status)) run.setCompletionStatus(status);
            refreshGenerationState(run);
            flush(run);
            updateConversation(run);
        }
        localRuns.remove(run.getId());
        lastFlush.remove(run.getId());
    }

    public ChatRunVO getOwned(Long userId, Long conversationId) {
        ChatRun run = latest(userId, conversationId);
        return run == null ? null : toView(refresh(run));
    }

    public ChatRunVO toView(ChatRun run) {
        ChatRunVO vo = new ChatRunVO();
        vo.setRunId(run.getRequestId());
        vo.setRequestId(run.getRequestId());
        vo.setConversationId(run.getConversationId());
        vo.setMessageId(run.getMessageId());
        vo.setAssistantMessageId(run.getAssistantMessageId());
        vo.setModelId(run.getModelId());
        vo.setImageModelId(run.getImageModelId());
        vo.setImageResolution(imageResolution(run));
        vo.setStatus(run.getStatus());
        vo.setText(run.getText());
        vo.setThinking(run.getThinking());
        vo.setResult(parse(run.getResultJson(), AgentResult.class));
        vo.setClarification(parse(run.getClarificationJson(), AgentClarification.class));
        vo.setErrorMessage(run.getErrorMessage());
        vo.setUpdatedTime(run.getUpdateTime());
        vo.setGenerationTasks(tasks(run).stream().map(task -> GenerationTaskVO.from(task, objectMapper)).toList());
        return vo;
    }

    public SysAiModel applyBillingSnapshot(ChatRun run, SysAiModel model) {
        if (run == null || run.getBillingSnapshot() == null) return model;
        try {
            var rates = objectMapper.readTree(run.getBillingSnapshot());
            SysAiModel snapshot = new SysAiModel();
            org.springframework.beans.BeanUtils.copyProperties(model, snapshot);
            snapshot.setBeanPerMtokenInput(rates.path("input").asLong(0));
            snapshot.setBeanPerMtokenInputCached(rates.path("cached").isNull()
                    ? null : rates.path("cached").asLong(0));
            snapshot.setBeanPerMtokenOutput(rates.path("output").asLong(0));
            if (rates.has("deployment")) snapshot.setModelDeployment(rates.path("deployment").asText(null));
            if (rates.has("protocol")) snapshot.setApiProtocol(rates.path("protocol").asText(null));
            if (rates.has("baseUrl")) snapshot.setBaseUrl(rates.path("baseUrl").asText(null));
            if (rates.has("maxTokens")) snapshot.setMaxTokens(rates.path("maxTokens").isNull()
                    ? null : rates.path("maxTokens").asInt());
            if (rates.has("temperature")) snapshot.setTemperature(rates.path("temperature").isNull()
                    ? null : rates.path("temperature").asDouble());
            return snapshot;
        } catch (Exception e) {
            throw new ServiceException("原任务计费快照不可用，请联系管理员");
        }
    }

    @Scheduled(fixedDelayString = "${ai.chat-run.heartbeat-ms:20000}")
    public void maintainRuns() {
        localRuns.values().forEach(run -> {
            synchronized (run) { flush(run); }
        });
        LocalDateTime stale = LocalDateTime.now().minusSeconds(90);
        List<ChatRun> abandoned = mapper.selectList(new LambdaQueryWrapper<ChatRun>()
                .in(ChatRun::getStatus, "QUEUED", "RUNNING")
                .lt(ChatRun::getHeartbeatTime, stale).last("LIMIT 200"));
        for (ChatRun run : abandoned) {
            new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
                // Reservation and recovery acquire the conversation lock before the run lock.
                if (conversationMapper.selectOwnedForUpdate(run.getConversationId(), run.getUserId()) == null) return;
                run.setStatus("INTERRUPTED");
                run.setCompletionStatus("INTERRUPTED");
                run.setErrorMessage("服务重启导致本轮中断，已提交的图片任务仍可查询；请重新发起对话");
                List<GenerationTask> submitted = tasks(run);
                refreshGenerationState(run, submitted);
                int changed = mapper.update(null, new LambdaUpdateWrapper<ChatRun>()
                        .eq(ChatRun::getId, run.getId()).in(ChatRun::getStatus, "QUEUED", "RUNNING")
                        .lt(ChatRun::getHeartbeatTime, stale)
                        .set(ChatRun::getStatus, run.getStatus()).set(ChatRun::getCompletionStatus, run.getCompletionStatus())
                        .set(ChatRun::getErrorMessage, run.getErrorMessage())
                        .set(ChatRun::getUpdateTime, LocalDateTime.now()));
                if (changed == 1) {
                    persistInterruptedImages(run, submitted);
                    updateConversation(run);
                }
            });
        }
        mapper.selectList(new LambdaQueryWrapper<ChatRun>().eq(ChatRun::getStatus, "GENERATING")
                .orderByAsc(ChatRun::getUpdateTime).last("LIMIT 200")).forEach(this::refresh);
    }

    private ChatRun latest(Long userId, Long conversationId) {
        return mapper.selectOne(new LambdaQueryWrapper<ChatRun>()
                .eq(ChatRun::getUserId, userId).eq(ChatRun::getConversationId, conversationId)
                .orderByDesc(ChatRun::getId).last("LIMIT 1"));
    }

    private ChatRun refresh(ChatRun run) {
        if (Set.of("QUEUED", "RUNNING").contains(run.getStatus())) return run;
        String previous = run.getStatus();
        if (run.getCompletionStatus() == null) {
            run.setCompletionStatus("GENERATING".equals(previous) ? "COMPLETED" : previous);
        }
        refreshGenerationState(run);
        if (!previous.equals(run.getStatus())) {
            flush(run);
            updateConversation(run);
        }
        return run;
    }

    private void refreshGenerationState(ChatRun run) {
        refreshGenerationState(run, tasks(run));
    }

    private void refreshGenerationState(ChatRun run, List<GenerationTask> tasks) {
        if (tasks.isEmpty()) return;
        boolean pending = tasks.stream().anyMatch(task -> Set.of("CREATED", "QUEUED", "RUNNING").contains(task.getStatus()));
        boolean failed = tasks.stream().anyMatch(task -> Set.of("FAILED", "CANCELLED").contains(task.getStatus()));
        if (pending) {
            run.setStatus("GENERATING");
        } else {
            String completion = run.getCompletionStatus();
            run.setStatus(completion != null && !"COMPLETED".equals(completion)
                    ? completion : failed ? "FAILED" : "COMPLETED");
        }
    }

    private void persistInterruptedImages(ChatRun run, List<GenerationTask> tasks) {
        if (run.getAssistantMessageId() != null || tasks.isEmpty()) return;
        List<AgentResult.ImageRef> images = new ArrayList<>();
        for (GenerationTask task : tasks) {
            for (int seq = 1; seq <= (task.getN() == null ? 1 : task.getN()); seq++) {
                AgentResult.ImageRef image = new AgentResult.ImageRef();
                image.setTaskId(task.getTaskId());
                image.setSeq(seq);
                images.add(image);
            }
        }
        AgentResult result = new AgentResult();
        result.setBody(run.getErrorMessage());
        result.setImages(images);
        Message recovered = messageService.saveMessage(run.getConversationId(), run.getUserId(),
                "assistant", json(result), "structured", json(images), null, null);
        run.setAssistantMessageId(recovered.getId());
        run.setResultJson(json(result));
        mapper.update(null, new LambdaUpdateWrapper<ChatRun>().eq(ChatRun::getId, run.getId())
                .set(ChatRun::getAssistantMessageId, recovered.getId()).set(ChatRun::getResultJson, run.getResultJson()));
    }

    private List<GenerationTask> tasks(ChatRun run) {
        return run.getMessageId() == null ? List.of()
                : generationTaskService.listBySourceMessage(run.getUserId(), run.getConversationId(), run.getMessageId());
    }

    private void flush(ChatRun run) {
        LocalDateTime now = LocalDateTime.now();
        run.setUpdateTime(now);
        run.setHeartbeatTime(now);
        mapper.updateById(run);
        lastFlush.put(run.getId(), System.currentTimeMillis());
    }

    private void updateConversation(ChatRun run) {
        LambdaUpdateWrapper<Conversation> update = new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, run.getConversationId())
                .set(Conversation::getRunId, run.getRequestId()).set(Conversation::getRunStatus, run.getStatus());
        if (!"QUEUED".equals(run.getStatus())) update.eq(Conversation::getRunId, run.getRequestId());
        conversationMapper.update(null, update);
    }

    /** Resolution travels with the durable run, including a recharge continuation. */
    public String imageResolution(ChatRun run) {
        if (run == null || run.getBillingSnapshot() == null) return null;
        try {
            return objectMapper.readTree(run.getBillingSnapshot()).path("imageResolution").asText(null);
        } catch (Exception e) {
            throw new ServiceException("原任务出图设置不可用，请联系管理员");
        }
    }

    /** A balance confirmation continues its original image settings, even after a picker change. */
    public void updateImageSelection(ChatRun run, Long imageModelId, String imageResolution) {
        if (run == null) return;
        synchronized (run) {
            try {
                ObjectNode snapshot = (ObjectNode) objectMapper.readTree(run.getBillingSnapshot());
                snapshot.put("imageResolution", imageResolution);
                run.setImageModelId(imageModelId);
                run.setBillingSnapshot(snapshot.toString());
                flush(run);
            } catch (Exception e) {
                throw new ServiceException("原任务出图设置不可用，请联系管理员");
            }
        }
    }

    private String billingSnapshot(SysAiModel model, String imageResolution) {
        ObjectNode rates = objectMapper.createObjectNode();
        rates.put("input", model.getBeanPerMtokenInput());
        rates.put("cached", model.getBeanPerMtokenInputCached());
        rates.put("output", model.getBeanPerMtokenOutput());
        rates.put("deployment", model.getModelDeployment());
        rates.put("protocol", model.getApiProtocol());
        rates.put("baseUrl", model.getBaseUrl());
        rates.put("maxTokens", model.getMaxTokens());
        rates.put("temperature", model.getTemperature());
        rates.put("imageResolution", imageResolution);
        return rates.toString();
    }

    private String json(Object value) {
        try { return value == null ? null : objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("运行快照保存失败", e); }
    }

    private <T> T parse(String json, Class<T> type) {
        try { return json == null ? null : objectMapper.readValue(json, type); }
        catch (Exception e) { log.warn("运行快照解析失败: {}", type.getSimpleName()); return null; }
    }

    private static String nullToEmpty(String value) { return value == null ? "" : value; }

    public record Reservation(ChatRun run, boolean created) {}
}
