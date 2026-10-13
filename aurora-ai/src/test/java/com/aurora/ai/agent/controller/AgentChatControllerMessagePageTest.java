package com.aurora.ai.agent.controller;

import com.aurora.ai.agent.domain.Conversation;
import com.aurora.ai.agent.domain.GenerationTask;
import com.aurora.ai.agent.domain.Message;
import com.aurora.ai.agent.dto.MessagePageVO;
import com.aurora.ai.agent.service.AgentService;
import com.aurora.ai.agent.service.GeneratedImageDownloadService;
import com.aurora.ai.agent.service.GenerationFeaturePolicy;
import com.aurora.ai.agent.service.IConversationService;
import com.aurora.ai.agent.service.IGenerationTaskService;
import com.aurora.ai.agent.service.IMessageService;
import com.aurora.ai.agent.service.IUserPromptService;
import com.aurora.ai.mapper.AgentSkillMapper;
import com.aurora.common.core.domain.Result;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentChatControllerMessagePageTest {

    private final IConversationService conversationService = mock(IConversationService.class);
    private final IMessageService messageService = mock(IMessageService.class);
    private final IGenerationTaskService generationTaskService = mock(IGenerationTaskService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private AgentChatController controller;

    @BeforeEach
    void setUp() {
        controller = new AgentChatController(
                mock(AgentService.class),
                conversationService,
                messageService,
                generationTaskService,
                mock(GenerationFeaturePolicy.class),
                mock(GeneratedImageDownloadService.class),
                mock(IUserPromptService.class),
                mock(AgentSkillMapper.class),
                objectMapper);
    }

    @Test
    void shouldReturnCursorPageAndResolveGeneratedImagesInOneBatch() throws Exception {
        when(conversationService.getOwned(33L, 7L)).thenReturn(new Conversation());
        Message oldestExtra = message(1L, "user", null);
        Message user = message(2L, "user", null);
        Message assistant = message(3L, "assistant", "[{\"taskId\":\"task-a\",\"seq\":1}]");
        when(messageService.listBefore(33L, null, 3))
                .thenReturn(List.of(oldestExtra, user, assistant));
        GenerationTask task = new GenerationTask();
        task.setTaskId("task-a");
        task.setResultUrls("[\"/profile/generated/a.png\"]");
        when(generationTaskService.mapByTaskIds(anySet()))
                .thenReturn(Map.of("task-a", task));

        Result<MessagePageVO> result = controller.messagePage(33L, null, 2, 7L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().isHasMore()).isTrue();
        assertThat(result.getData().getNextCursor()).isEqualTo(2L);
        assertThat(result.getData().getRecords()).extracting(item -> item.getId())
                .containsExactly(2L, 3L);
        JsonNode media = objectMapper.readTree(result.getData().getRecords().get(1).getMedia());
        assertThat(media.get(0).path("url").asText()).isEqualTo("/profile/generated/a.png");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> taskIds = ArgumentCaptor.forClass(Set.class);
        verify(generationTaskService).mapByTaskIds(taskIds.capture());
        assertThat(taskIds.getValue()).containsExactly("task-a");
    }

    @Test
    void shouldRejectConversationNotOwnedByCurrentUser() {
        when(conversationService.getOwned(33L, 7L)).thenReturn(null);

        Result<MessagePageVO> result = controller.messagePage(33L, null, 30, 7L);

        assertThat(result.getCode()).isEqualTo(403);
        verify(messageService, never()).listBefore(33L, null, 31);
    }

    @Test
    void priorTerminatedRunImagesRemainVisibleAfterANewRunBecomesLatest() throws Exception {
        Conversation conversation = new Conversation();
        conversation.setRunId("new-request");
        conversation.setRunStatus("COMPLETED");
        when(conversationService.getOwned(33L, 7L)).thenReturn(conversation);
        Message stopped = message(22L, "assistant", "[{\"taskId\":\"task-old\",\"seq\":2}]");
        stopped.setContentType("structured");
        stopped.setContent("{\"body\":\"对话已停止\",\"images\":[{\"taskId\":\"task-old\",\"seq\":2}]}");
        Message later = message(32L, "assistant", null);
        when(messageService.listBefore(33L, null, 31)).thenReturn(List.of(stopped, later));
        GenerationTask task = new GenerationTask();
        task.setTaskId("task-old");
        task.setResultUrls("[null,\"/profile/generated/old-second.png\"]");
        when(generationTaskService.mapByTaskIds(anySet())).thenReturn(Map.of("task-old", task));

        Result<MessagePageVO> result = controller.messagePage(33L, null, 30, 7L);

        assertThat(result.getData().getRecords()).hasSize(2);
        assertThat(objectMapper.readTree(result.getData().getRecords().get(0).getContent()).path("body").asText())
                .isEqualTo("对话已停止");
        JsonNode oldMedia = objectMapper.readTree(result.getData().getRecords().get(0).getMedia());
        assertThat(oldMedia.get(0).path("seq").asInt()).isEqualTo(2);
        assertThat(oldMedia.get(0).path("url").asText()).isEqualTo("/profile/generated/old-second.png");
    }

    private Message message(Long id, String role, String media) {
        Message message = new Message();
        message.setId(id);
        message.setRole(role);
        message.setContent("内容" + id);
        message.setContentType("text");
        message.setMedia(media);
        return message;
    }
}
