package com.aurora.ai.agent.service;

import com.aurora.ai.agent.domain.ChatRun;
import com.aurora.ai.agent.domain.Conversation;
import com.aurora.ai.agent.domain.GenerationTask;
import com.aurora.ai.agent.domain.Message;
import com.aurora.ai.agent.mapper.ChatRunMapper;
import com.aurora.ai.agent.mapper.ConversationMapper;
import com.aurora.ai.domain.SysAiModel;
import com.aurora.ai.dto.AgentSseEvent;
import com.aurora.common.core.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChatRunServiceTest {
    private final ChatRunMapper mapper = mock(ChatRunMapper.class);
    private final ConversationMapper conversations = mock(ConversationMapper.class);
    private final IGenerationTaskService generations = mock(IGenerationTaskService.class);
    private final IMessageService messages = mock(IMessageService.class);
    private final ObjectMapper json = new ObjectMapper();
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final ChatRunService service = new ChatRunService(mapper, conversations, generations, messages, json, transactions);

    @BeforeEach
    void initializeLambdaMetadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ChatRun.class);
        TableInfoHelper.initTableInfo(assistant, Conversation.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(conversations.selectOwnedForUpdate(11L, 7L)).thenReturn(new Conversation());
    }

    @Test
    void duplicateRequestIsReplayedWithoutReservingOrChargingAgain() {
        when(conversations.selectOwnedForUpdate(11L, 7L)).thenReturn(new Conversation());
        ChatRun existing = run("COMPLETED");
        when(mapper.selectOne(any())).thenReturn(existing);
        ChatRunService.Reservation reserved = service.reserve(7L, 11L, "request-1", "hash", model(), 20L, null);
        assertFalse(reserved.created());
        assertSame(existing, reserved.run());
        verify(mapper, never()).insert(any(ChatRun.class));
        verifyNoInteractions(generations);
        assertThrows(ServiceException.class, () -> service.assertSameRequest(existing, "different", 11L));
        assertThrows(ServiceException.class, () -> service.assertSameRequest(existing, "hash", 12L));
    }

    @Test
    void blocksSameConversationWhileImageTasksAreStillRunning() {
        when(conversations.selectOwnedForUpdate(11L, 7L)).thenReturn(new Conversation());
        ChatRun active = run("GENERATING");
        when(mapper.selectOne(any())).thenReturn(null, active);
        GenerationTask pending = new GenerationTask();
        pending.setStatus("RUNNING");
        when(generations.listBySourceMessage(7L, 11L, 21L)).thenReturn(List.of(pending));
        assertThrows(ServiceException.class, () -> service.reserve(7L, 11L, "request-2", "hash-2", model(), 20L, null));
        verify(mapper, never()).insert(any(ChatRun.class));
    }

    @Test
    void differentConversationCanReserveItsOwnRun() {
        when(conversations.selectOwnedForUpdate(12L, 7L)).thenReturn(new Conversation());
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.insert(any(ChatRun.class))).thenAnswer(invocation -> {
            ((ChatRun) invocation.getArgument(0)).setId(2L);
            return 1;
        });
        ChatRunService.Reservation reservation = service.reserve(7L, 12L, "request-2", "hash-2", model(), 20L, null);
        assertTrue(reservation.created());
        assertEquals(12L, reservation.run().getConversationId());
        assertEquals("QUEUED", reservation.run().getStatus());
        verify(conversations).selectOwnedForUpdate(12L, 7L);
    }

    @Test
    void frozenTextRatesSurviveModelPriceChangesAndKeepCachedFallback() {
        when(conversations.selectOwnedForUpdate(11L, 7L)).thenReturn(new Conversation());
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.insert(any(ChatRun.class))).thenAnswer(invocation -> {
            ((ChatRun) invocation.getArgument(0)).setId(1L);
            return 1;
        });
        SysAiModel original = model();
        original.setBeanPerMtokenInputCached(null);
        ChatRun first = service.reserve(7L, 11L, "request-1", "hash", original, 20L, null).run();
        SysAiModel changed = model();
        changed.setBeanPerMtokenInput(999L);
        changed.setBeanPerMtokenOutput(999L);
        SysAiModel snapshot = service.applyBillingSnapshot(first, changed);
        assertEquals(100L, snapshot.getBeanPerMtokenInput());
        assertEquals(500L, snapshot.getBeanPerMtokenOutput());
        assertNull(snapshot.getBeanPerMtokenInputCached());
        assertEquals(999L, changed.getBeanPerMtokenInput());
    }

    @Test
    void selectedResolutionSurvivesDisconnectAndRechargeInsteadOfUsingNewSelection() {
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.insert(any(ChatRun.class))).thenAnswer(invocation -> {
            ((ChatRun) invocation.getArgument(0)).setId(1L);
            return 1;
        });
        ChatRun original = service.reserve(7L, 11L, "resolution-original", "hash",
                model(), 20L, "4K", null).run();
        assertEquals("4K", service.toView(original).getImageResolution());

        ChatRun resumed = service.reserve(7L, 11L, "resolution-resume", "resume-hash",
                model(), 99L, "1K", original).run();
        assertEquals(20L, resumed.getImageModelId());
        assertEquals("4K", service.imageResolution(resumed));
        assertEquals(original.getBillingSnapshot(), resumed.getBillingSnapshot());
        verifyNoInteractions(generations);
    }

    @Test
    void legacyRunWithoutResolutionStillLoads() {
        ChatRun legacy = run("COMPLETED");
        legacy.setBillingSnapshot("{\"input\":100,\"output\":500,\"cached\":null}");
        assertNull(service.imageResolution(legacy));
        assertNull(service.toView(legacy).getImageResolution());
        assertEquals(100L, service.applyBillingSnapshot(legacy, model()).getBeanPerMtokenInput());
    }

    @Test
    void affordablePlanRestoresOriginalImageSettingsWithoutChangingTextBilling() {
        ChatRun current = run("RUNNING");
        current.setImageModelId(99L);
        current.setBillingSnapshot("{\"input\":100,\"output\":500,\"cached\":null,\"imageResolution\":\"1K\"}");
        service.updateImageSelection(current, 20L, "4K");
        assertEquals(20L, service.toView(current).getImageModelId());
        assertEquals("4K", service.toView(current).getImageResolution());
        assertEquals(100L, service.applyBillingSnapshot(current, model()).getBeanPerMtokenInput());
        verify(mapper).updateById(current);
    }

    @Test
    void snapshotIncludesTextEvenWhenNoSubscriberIsPresentAndImagesRemainGenerating() {
        ChatRun run = run("RUNNING");
        service.observe(run, AgentSseEvent.text("第一段"));
        service.observe(run, AgentSseEvent.text("第二段"));
        GenerationTask pending = new GenerationTask();
        pending.setStatus("QUEUED");
        when(generations.listBySourceMessage(7L, 11L, 21L)).thenReturn(List.of(pending));
        service.finish(run, "COMPLETED");
        assertEquals("第一段第二段", run.getText());
        assertEquals("GENERATING", run.getStatus());
        verify(mapper, atLeastOnce()).updateById(run);
    }

    @Test
    void staleTextRunsAreMarkedInterruptedWithoutReplayingModelsOrImageTasks() {
        ChatRun stale = run("RUNNING");
        stale.setHeartbeatTime(LocalDateTime.now().minusMinutes(5));
        when(mapper.selectList(any())).thenReturn(List.of(stale), List.of());
        when(mapper.update(isNull(), any())).thenReturn(1);
        service.maintainRuns();
        assertEquals("INTERRUPTED", stale.getStatus());
        verify(generations).listBySourceMessage(7L, 11L, 21L);
        verifyNoMoreInteractions(generations);
        verifyNoInteractions(messages);
        verify(mapper, never()).insert(any(ChatRun.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "INTERRUPTED", "WAITING_RECHARGE"})
    void failureActionSurvivesReloadWhileAlreadySubmittedImagesKeepRunning(String outcome) {
        ChatRun run = run("RUNNING");
        run.setCompletionStatus(outcome);
        run.setErrorMessage("WAITING_RECHARGE".equals(outcome)
                ? "豆子余额不足" : ChatFailureAdvice.TIMEOUT_MESSAGE);
        GenerationTask task = new GenerationTask();
        task.setTaskId("submitted-before-failure");
        task.setStatus("RUNNING");
        when(generations.listBySourceMessage(7L, 11L, 21L)).thenReturn(List.of(task));
        when(mapper.selectOne(any())).thenReturn(run);

        service.finish(run, outcome);
        var snapshot = service.getOwned(7L, 11L);
        assertEquals("GENERATING", snapshot.getStatus());
        assertEquals("WAITING_RECHARGE".equals(outcome) ? "RECHARGE" : "NEW_CONVERSATION", snapshot.getAction());
        assertEquals("FAILED".equals(outcome) ? "CHAT_MODEL_TIMEOUT"
                : "INTERRUPTED".equals(outcome) ? "CHAT_INTERRUPTED" : "INSUFFICIENT_BEANS", snapshot.getErrorCode());
        assertEquals("submitted-before-failure", snapshot.getGenerationTasks().getFirst().getTaskId());

        task.setStatus("SUCCEEDED");
        var finished = service.getOwned(7L, 11L);
        assertEquals(outcome, finished.getStatus());
        assertEquals(snapshot.getAction(), finished.getAction());
        verify(generations, never()).submitImageBatch(anyLong(), anyLong(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void cancelledClarificationIsNotRestoredAfterOrchestrationFailure() {
        ChatRun run = run("GENERATING");
        run.setCompletionStatus("FAILED");
        run.setErrorMessage(ChatFailureAdvice.FAILED_MESSAGE);
        run.setClarificationJson("{\"scopeId\":\"already-cancelled\",\"question\":\"请选择\"}");
        assertNull(service.toView(run).getClarification());
        run.setStatus("WAITING_CONFIRMATION");
        run.setCompletionStatus("WAITING_CONFIRMATION");
        run.setErrorMessage(null);
        assertEquals("already-cancelled", service.toView(run).getClarification().getScopeId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELLED", "FAILED", "INTERRUPTED", "WAITING_CONFIRMATION"})
    void unfinishedImagesKeepRunBusyAndRestoreOriginalTextOutcomeAfterImagesFinish(String outcome) {
        ChatRun run = run("RUNNING");
        GenerationTask task = new GenerationTask();
        task.setTaskId("task-old");
        task.setStatus("QUEUED");
        when(generations.listBySourceMessage(7L, 11L, 21L)).thenReturn(List.of(task));
        when(mapper.selectOne(any())).thenReturn(run);

        service.finish(run, outcome);
        assertEquals("GENERATING", service.getOwned(7L, 11L).getStatus());
        assertEquals(outcome, run.getCompletionStatus());
        assertEquals("task-old", service.getOwned(7L, 11L).getGenerationTasks().get(0).getTaskId());

        task.setStatus("SUCCEEDED");
        assertEquals(outcome, service.getOwned(7L, 11L).getStatus());
        assertEquals(outcome, run.getCompletionStatus());
    }

    @Test
    void oldTerminalRunCannotBeReplacedWhileItsSubmittedImagesRemainActive() {
        when(conversations.selectOwnedForUpdate(11L, 7L)).thenReturn(new Conversation());
        ChatRun legacy = run("CANCELLED");
        GenerationTask pending = new GenerationTask();
        pending.setStatus("RUNNING");
        when(mapper.selectOne(any())).thenReturn(null, legacy);
        when(generations.listBySourceMessage(7L, 11L, 21L)).thenReturn(List.of(pending));

        assertThrows(ServiceException.class,
                () -> service.reserve(7L, 11L, "request-2", "hash-2", model(), 20L, null));

        assertEquals("GENERATING", legacy.getStatus());
        assertEquals("CANCELLED", legacy.getCompletionStatus());
        verify(mapper, never()).insert(any(ChatRun.class));
    }

    @Test
    void interruptedImagesArePersistedInHistoryBeforeAReplacementRunCanStart() throws Exception {
        ChatRun stale = run("RUNNING");
        stale.setHeartbeatTime(LocalDateTime.now().minusMinutes(5));
        GenerationTask task = new GenerationTask();
        task.setTaskId("task-before-restart");
        task.setN(2);
        task.setStatus("RUNNING");
        when(generations.listBySourceMessage(7L, 11L, 21L)).thenReturn(List.of(task));
        when(mapper.selectList(any())).thenReturn(List.of(stale), List.of(stale));
        when(mapper.update(isNull(), any())).thenReturn(1);
        Message recovered = new Message();
        recovered.setId(22L);
        when(messages.saveMessage(eq(11L), eq(7L), eq("assistant"), anyString(), eq("structured"),
                anyString(), isNull(), isNull())).thenReturn(recovered);

        service.maintainRuns();

        assertEquals("GENERATING", stale.getStatus());
        assertEquals("INTERRUPTED", stale.getCompletionStatus());
        assertEquals(22L, stale.getAssistantMessageId());
        ArgumentCaptor<String> media = ArgumentCaptor.forClass(String.class);
        verify(messages).saveMessage(eq(11L), eq(7L), eq("assistant"), anyString(), eq("structured"),
                media.capture(), isNull(), isNull());
        var savedImages = json.readTree(media.getValue());
        assertEquals("task-before-restart", savedImages.get(0).path("taskId").asText());
        assertEquals(2, savedImages.get(1).path("seq").asInt());

        task.setStatus("SUCCEEDED");
        when(mapper.selectOne(any())).thenReturn(stale);
        assertEquals("INTERRUPTED", service.getOwned(7L, 11L).getStatus());
        when(conversations.selectOwnedForUpdate(11L, 7L)).thenReturn(new Conversation());
        when(mapper.selectOne(any())).thenReturn(null, stale);
        when(mapper.insert(any(ChatRun.class))).thenAnswer(invocation -> {
            ((ChatRun) invocation.getArgument(0)).setId(2L);
            return 1;
        });
        assertTrue(service.reserve(7L, 11L, "request-2", "hash-2", model(), 20L, null).created());
        assertEquals("task-before-restart", json.readTree(media.getValue()).get(1).path("taskId").asText());
        verify(messages, times(1)).saveMessage(anyLong(), anyLong(), anyString(), anyString(),
                anyString(), anyString(), isNull(), isNull());
    }

    @Test
    void unsuccessfulStaleRunClaimDoesNotCreateDuplicateHistory() {
        ChatRun stale = run("RUNNING");
        stale.setHeartbeatTime(LocalDateTime.now().minusMinutes(5));
        GenerationTask task = new GenerationTask();
        task.setStatus("RUNNING");
        when(generations.listBySourceMessage(7L, 11L, 21L)).thenReturn(List.of(task));
        when(mapper.selectList(any())).thenReturn(List.of(stale), List.of());
        when(mapper.update(isNull(), any())).thenReturn(0);

        service.maintainRuns();

        verifyNoInteractions(messages);
        verify(conversations, never()).update(isNull(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void errorEventKeepsConversationReservedUntilWorkerFinalization(boolean needsRecharge) {
        ChatRun active = run("RUNNING");
        AgentSseEvent error = needsRecharge
                ? AgentSseEvent.error("请充值", "INSUFFICIENT_BEANS", "RECHARGE")
                : AgentSseEvent.error("对话出错了");
        String expected = needsRecharge ? "WAITING_RECHARGE" : "FAILED";

        service.observe(active, error);
        assertEquals("RUNNING", active.getStatus());
        assertEquals(expected, active.getCompletionStatus());
        when(mapper.selectOne(any())).thenReturn(active);
        assertEquals("RUNNING", service.getOwned(7L, 11L).getStatus());
        verify(mapper, times(1)).updateById(active);
        when(mapper.selectOne(any())).thenReturn(null, active);
        assertThrows(ServiceException.class,
                () -> service.reserve(7L, 11L, "request-2", "hash-2", model(), 20L, null));
        verify(mapper, never()).insert(any(ChatRun.class));

        service.finish(active, "COMPLETED");
        assertEquals(expected, active.getStatus());
        assertEquals(error.getMessage(), active.getErrorMessage());
    }

    @Test
    void recoveryHistoryFailureRollsBackTheInterruptedRunClaim() {
        ChatRun stale = run("RUNNING");
        stale.setHeartbeatTime(LocalDateTime.now().minusMinutes(5));
        GenerationTask task = new GenerationTask();
        task.setTaskId("task-before-restart");
        task.setStatus("RUNNING");
        when(generations.listBySourceMessage(7L, 11L, 21L)).thenReturn(List.of(task));
        when(mapper.selectList(any())).thenReturn(List.of(stale));
        when(mapper.update(isNull(), any())).thenReturn(1);
        when(messages.saveMessage(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(),
                isNull(), isNull())).thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, service::maintainRuns);

        verify(transactions).rollback(any());
        verify(transactions, never()).commit(any());
        verify(conversations, never()).update(isNull(), any());
    }

    private ChatRun run(String status) {
        ChatRun run = new ChatRun();
        run.setId(1L);
        run.setUserId(7L);
        run.setConversationId(11L);
        run.setMessageId(21L);
        run.setRequestId("request-1");
        run.setRequestFingerprint("hash");
        run.setStatus(status);
        run.setText("");
        run.setThinking("");
        return run;
    }

    private SysAiModel model() {
        SysAiModel model = new SysAiModel();
        model.setId(9L);
        model.setBeanPerMtokenInput(100L);
        model.setBeanPerMtokenInputCached(20L);
        model.setBeanPerMtokenOutput(500L);
        return model;
    }
}
