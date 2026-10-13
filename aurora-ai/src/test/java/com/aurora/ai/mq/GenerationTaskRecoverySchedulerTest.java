package com.aurora.ai.mq;

import com.aurora.ai.agent.domain.GenerationTask;
import com.aurora.ai.agent.service.IGenerationTaskService;
import com.aurora.framework.mq.TaskProducer;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GenerationTaskRecoverySchedulerTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void preservesSavedOutputsAndRequeuesOnlyUnsettledOrUnstartedRowsAfterRestart() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GenerationTask.class);
        IGenerationTaskService tasks = mock(IGenerationTaskService.class);
        TaskProducer queue = mock(TaskProducer.class);
        GenerationTask oldRunning = new GenerationTask();
        oldRunning.setId(1L);
        oldRunning.setStatus("RUNNING");
        oldRunning.setSuccessCount(2);
        oldRunning.setResultUrls("[\"/profile/one.png\",\"/profile/two.png\",\"\"]");
        GenerationTask unsettled = new GenerationTask();
        unsettled.setId(1L);
        unsettled.setStatus("SUCCEEDED");
        GenerationTask queued = new GenerationTask();
        queued.setId(2L);
        queued.setStatus("QUEUED");
        when(tasks.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(oldRunning), List.of(unsettled, queued));
        GenerationTaskRecoveryScheduler recovery = new GenerationTaskRecoveryScheduler(tasks, queue);
        recovery.republishPending();
        verifyNoInteractions(queue, tasks);

        recovery.recoverInterrupted();

        ArgumentCaptor<LambdaUpdateWrapper> updates = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(tasks, times(3)).update(updates.capture());
        LambdaUpdateWrapper interruption = updates.getAllValues().getFirst();
        assertThat(interruption.getSqlSet()).contains("status", "success_count").doesNotContain("result_urls", "bean_cost");
        assertThat(interruption.getParamNameValuePairs().values()).contains("SUCCEEDED", 2);
        verify(queue).send("generation-task", Map.of("generationTaskId", 1L));
        verify(queue).send("generation-task", Map.of("generationTaskId", 2L));
        ArgumentCaptor<LambdaQueryWrapper> queries = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(tasks, times(2)).list(queries.capture());
        LambdaQueryWrapper republish = queries.getAllValues().get(1);
        republish.getSqlSegment();
        assertThat(republish.getParamNameValuePairs().values()).contains("CREATED", "QUEUED", "SUCCEEDED", "FAILED", "CANCELLED")
                .doesNotContain("RUNNING");
    }

    @Test
    void queueOutageLeavesDurableTasksAvailableForNextRecovery() {
        IGenerationTaskService tasks = mock(IGenerationTaskService.class);
        TaskProducer queue = mock(TaskProducer.class);
        GenerationTask queued = new GenerationTask();
        queued.setId(2L);
        queued.setStatus("QUEUED");
        when(tasks.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(), List.of(queued), List.of(queued));
        when(queue.send(anyString(), anyMap())).thenThrow(new IllegalStateException("offline")).thenReturn(null);
        GenerationTaskRecoveryScheduler recovery = new GenerationTaskRecoveryScheduler(tasks, queue);
        recovery.recoverInterrupted();
        verify(tasks, never()).update(any(LambdaUpdateWrapper.class));
        recovery.republishPending();
        verify(queue, times(2)).send("generation-task", Map.of("generationTaskId", 2L));
        verify(tasks).update(any(LambdaUpdateWrapper.class));
    }
}
