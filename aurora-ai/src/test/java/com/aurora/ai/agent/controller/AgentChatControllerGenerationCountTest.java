package com.aurora.ai.agent.controller;

import com.aurora.ai.agent.dto.GenerateImageRequest;
import com.aurora.ai.agent.service.*;
import com.aurora.ai.mapper.AgentSkillMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AgentChatControllerGenerationCountTest {
    private final IGenerationTaskService generations = mock(IGenerationTaskService.class);
    private final AgentChatController controller = new AgentChatController(mock(AgentService.class),
            mock(IConversationService.class), mock(IMessageService.class), generations,
            mock(GenerationFeaturePolicy.class), mock(GeneratedImageDownloadService.class),
            mock(IUserPromptService.class), mock(AgentSkillMapper.class), new ObjectMapper());

    @ParameterizedTest
    @ValueSource(ints = {1, 9})
    void acceptsFullCountRangeAndForwardsResolutionAndIdempotencyKey(int count) {
        GenerateImageRequest request = request(count);
        when(generations.submitImageGeneration(7L, null, "draw", count, "2K", "3:4",
                List.of(), 17L, "nine-images")).thenReturn("task-nine");
        var result = controller.generate(request, 7L);
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsEntry("taskId", "task-nine");
        verify(generations).submitImageGeneration(7L, null, "draw", count, "2K", "3:4",
                List.of(), 17L, "nine-images");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10})
    void rejectsCountsOutsideRangeWithoutSubmitting(int count) {
        assertThat(controller.generate(request(count), 7L).getCode()).isNotEqualTo(200);
        verifyNoInteractions(generations);
    }

    private GenerateImageRequest request(int count) {
        GenerateImageRequest request = new GenerateImageRequest();
        request.setPrompt("draw"); request.setN(count); request.setResolution("2K");
        request.setAspectRatio("3:4"); request.setRefImages(List.of());
        request.setModelId(17L); request.setRequestId("nine-images");
        return request;
    }
}
