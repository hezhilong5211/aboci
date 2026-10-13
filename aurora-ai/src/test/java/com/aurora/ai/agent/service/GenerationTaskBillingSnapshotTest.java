package com.aurora.ai.agent.service;

import com.aurora.ai.agent.billing.BillingCalculator;
import com.aurora.ai.agent.billing.IBeanDeductPort;
import com.aurora.ai.agent.domain.GenerationTask;
import com.aurora.ai.agent.mapper.GenerationTaskMapper;
import com.aurora.ai.agent.service.impl.GenerationTaskServiceImpl;
import com.aurora.ai.domain.SysAiModel;
import com.aurora.ai.mapper.SysAiModelMapper;
import com.aurora.common.core.exception.ServiceException;
import com.aurora.framework.mq.TaskProducer;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GenerationTaskBillingSnapshotTest {
    private GenerationTaskServiceImpl service;
    private final GenerationTaskMapper taskMapper = mock(GenerationTaskMapper.class);
    private final SysAiModelMapper models = mock(SysAiModelMapper.class);
    private final IBeanDeductPort beans = mock(IBeanDeductPort.class);
    private final TaskProducer queue = mock(TaskProducer.class);
    private SysAiModel model;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GenerationTask.class);
        service = spy(new GenerationTaskServiceImpl(new ObjectMapper(), queue, models, new BillingCalculator()));
        ReflectionTestUtils.setField(service, "baseMapper", taskMapper);
        ReflectionTestUtils.setField(service, "beanDeductPort", beans);
        model = new SysAiModel();
        model.setId(14L);
        model.setModelType("image");
        model.setProvider("luxai");
        model.setIsActive(1);
        model.setApiKey("encrypted");
        model.setModelName("GPT Image 2.5 Flare");
        model.setModelDeployment("gpt-image-2.5-flare");
        model.setBaseUrl("https://api.openlux.ai/v1");
        model.setSupportedResolutions("1K,2K");
        model.setSupportedAspectRatios("1:1,16:9");
        model.setDefaultResolution("1K");
        model.setBeanPerImage1k(8L);
        model.setBeanPerImage2k(15L);
        model.setImageQuality("high");
        when(models.selectById(14L)).thenReturn(model);
        when(beans.getBalance(7L)).thenReturn(1000L);
        when(taskMapper.insert(any(GenerationTask.class))).thenAnswer(invocation -> {
            GenerationTask task = invocation.getArgument(0);
            task.setId(101L);
            return 1;
        });
        when(taskMapper.updateById(any(GenerationTask.class))).thenReturn(1);
        doReturn(null).when(service).getOne(any(LambdaQueryWrapper.class));
        doNothing().when(service).markStatus(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void freezesSelectedModelPriceQualityAndReferenceOrderBeforeDebit() {
        String taskId = service.submitImageGeneration(7L, null, "draw", 2, "2K", "16:9",
                List.of("/profile/second.png", "/profile/first.png"), 14L, "stable-1");
        ArgumentCaptor<GenerationTask> captured = ArgumentCaptor.forClass(GenerationTask.class);
        verify(taskMapper).insert(captured.capture());
        GenerationTask task = captured.getValue();
        model.setBeanPerImage2k(999L);
        model.setImageQuality("low");
        assertThat(task.getModelId()).isEqualTo(14L);
        assertThat(task.getBeanPerImageSnapshot()).isEqualTo(15L);
        assertThat(task.getPrepaidBeans()).isEqualTo(30L);
        assertThat(task.getImageQualitySnapshot()).isEqualTo("high");
        assertThat(task.getRequestId()).isEqualTo("stable-1");
        assertThat(task.getAspectRatio()).isEqualTo("16:9");
        assertThat(task.getRefImages()).isEqualTo("[\"/profile/second.png\",\"/profile/first.png\"]");
        verify(beans).deduct(7L, 30L, "img:" + taskId, "AI出图预扣");
    }

    @Test
    void sameRequestReusesTaskEvenWhenModelPricesOrActivationHaveChanged() {
        GenerationTask existing = new GenerationTask();
        existing.setTaskId("existing-task");
        existing.setUserId(7L);
        existing.setModelId(14L);
        existing.setPrompt("draw");
        existing.setN(2);
        existing.setResolution("2K");
        existing.setAspectRatio("16:9");
        existing.setRequestId("stable-1");
        doReturn(existing).when(service).getOne(any(LambdaQueryWrapper.class));
        model.setIsActive(0);
        model.setBeanPerImage2k(999L);
        assertThat(service.submitImageGeneration(7L, null, "draw", 2, "2K", "16:9", List.of(), 14L, "stable-1"))
                .isEqualTo("existing-task");
        verifyNoInteractions(beans, queue, taskMapper);
    }

    @Test
    void requestIdCannotBeReusedForChangedPromptAndDisabledModelsCannotBeSelected() {
        GenerationTask existing = new GenerationTask();
        existing.setPrompt("original");
        existing.setN(1);
        doReturn(existing).when(service).getOne(any(LambdaQueryWrapper.class));
        assertThatThrownBy(() -> service.submitImageGeneration(7L, null, "changed", 1, null, null, List.of(), 14L, "stable-1"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("不同的出图内容");
        doReturn(null).when(service).getOne(any(LambdaQueryWrapper.class));
        model.setIsActive(0);
        assertThatThrownBy(() -> service.submitImageGeneration(7L, null, "draw", 1, null, null, List.of(), 14L, "stable-2"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("未启用");
        verifyNoInteractions(beans, queue, taskMapper);
    }
}
