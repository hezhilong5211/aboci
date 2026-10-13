package com.aurora.ai.mq;

import com.aurora.ai.agent.billing.BillingCalculator;
import com.aurora.ai.agent.billing.IBeanDeductPort;
import com.aurora.ai.agent.domain.GenerationTask;
import com.aurora.ai.agent.service.IGenerationTaskService;
import com.aurora.ai.domain.SysAiModel;
import com.aurora.ai.mapper.SysAiModelMapper;
import com.aurora.ai.provider.GptsImageClient;
import com.aurora.ai.provider.YunwuImageClient;
import com.aurora.ai.provider.support.ImageConcurrencyGate;
import com.aurora.framework.mq.TaskMessage;
import com.aurora.framework.mq.TaskProducer;
import com.aurora.framework.storage.ProfileStorage;
import com.aurora.framework.utils.EncryptUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GenerationTaskConsumerBillingTest {
    private final IGenerationTaskService tasks = mock(IGenerationTaskService.class);
    private final SysAiModelMapper models = mock(SysAiModelMapper.class);
    private final IBeanDeductPort beans = mock(IBeanDeductPort.class);
    private final YunwuImageClient yunwu = mock(YunwuImageClient.class);
    private final GptsImageClient gpts = mock(GptsImageClient.class);
    private GenerationTaskConsumer consumer;
    private GenerationTask task;

    @BeforeEach
    void setUp() {
        consumer = new GenerationTaskConsumer(mock(StringRedisTemplate.class), new ObjectMapper(),
                mock(TaskProducer.class), tasks, yunwu, gpts, models, mock(EncryptUtils.class),
                new BillingCalculator(), mock(ImageConcurrencyGate.class), mock(ProfileStorage.class));
        ReflectionTestUtils.setField(consumer, "beanDeductPort", beans);
        task = new GenerationTask();
        task.setId(101L);
        task.setUserId(7L);
        task.setModelId(14L);
        task.setTaskId("snapshot-task");
        task.setStatus("QUEUED");
        task.setN(3);
        task.setBeanPerImageSnapshot(10L);
        task.setPrepaidBeans(30L);
        when(tasks.getById(101L)).thenReturn(task);
        when(tasks.tryStart("snapshot-task")).thenReturn(true);
    }

    @Test
    void missingModelRefundsOriginalPrepaidAmountWithoutCallingSupplier() throws Exception {
        run();
        verify(beans).refund(7L, 30L, "img:snapshot-task", "出图失败退还");
        verify(tasks).setBeanCost("snapshot-task", 0L);
        verifyNoInteractions(yunwu, gpts);
    }

    @Test
    void terminalPartiallySuccessfulTaskSettlesFromSnapshotAfterModelPriceChange() throws Exception {
        task.setStatus("SUCCEEDED");
        task.setSuccessCount(2);
        SysAiModel model = new SysAiModel();
        model.setBeanPerImage(999L);
        when(models.selectById(14L)).thenReturn(model);
        run();
        verify(beans).refund(7L, 10L, "img:snapshot-task", "出图失败退还");
        verify(tasks).setBeanCost("snapshot-task", 20L);
        verify(tasks, never()).tryStart(anyString());
        verifyNoInteractions(yunwu, gpts);
    }

    @Test
    void terminalSettledDeliveryCannotRegenerateOrRefundAgain() throws Exception {
        task.setStatus("SUCCEEDED");
        task.setBeanCost(20);
        run();
        verifyNoInteractions(beans, yunwu, gpts);
        verify(tasks, never()).tryStart(anyString());
        verify(tasks, never()).markStatus(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void concurrentDuplicateDeliveryCannotCallSupplier() throws Exception {
        when(tasks.tryStart("snapshot-task")).thenReturn(false);
        run();
        verifyNoInteractions(beans, yunwu, gpts, models);
    }

    private void run() throws Exception {
        consumer.processTask(TaskMessage.create("generation-task", Map.of("generationTaskId", 101L)));
    }
}
