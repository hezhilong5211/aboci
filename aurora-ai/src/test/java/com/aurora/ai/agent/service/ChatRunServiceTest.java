package com.aurora.ai.agent.service;

import com.aurora.ai.agent.domain.ChatRun;
import com.aurora.ai.agent.domain.Conversation;
import com.aurora.ai.agent.domain.GenerationTask;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChatRunServiceTest {
    private final ChatRunMapper mapper = mock(ChatRunMapper.class);
    private final ConversationMapper conversations = mock(ConversationMapper.class);
    private final IGenerationTaskService generations = mock(IGenerationTaskService.class);
    private final ChatRunService service = new ChatRunService(mapper, conversations, generations, new ObjectMapper());

    @BeforeEach
    void initializeLambdaMetadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ChatRun.class);
        TableInfoHelper.initTableInfo(assistant, Conversation.class);
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
        verifyNoInteractions(generations);
        verify(mapper, never()).insert(any(ChatRun.class));
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
