package com.aurora.ai.agent.retention;

import com.aurora.ai.agent.domain.GenerationTask;
import com.aurora.ai.agent.domain.Message;
import com.aurora.ai.agent.service.IGenerationTaskService;
import com.aurora.ai.agent.service.IMessageService;
import com.aurora.framework.storage.ProfileStorage;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeneratedImageRetentionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void deletesExpiredGeneratedFileButKeepsCaseProtectedFile() throws Exception {
        Path configured = tempDir.resolve("uploads");
        ProfileStorage storage = new ProfileStorage(configured.toString(), tempDir.resolve("home").toString());
        storage.write("generated/delete.png", "delete".getBytes());
        storage.write("generated/delete.preview.jpg", "preview".getBytes());
        storage.write("keep.png", "keep".getBytes());

        GenerationTask deleteTask = task(1L, "delete-task", "[\"/profile/generated/delete.png\"]");
        GenerationTask keepTask = task(2L, "keep-task", "[\"/profile/keep.png\"]");
        IGenerationTaskService taskService = mock(IGenerationTaskService.class);
        IMessageService messageService = mock(IMessageService.class);
        when(taskService.list(any(Wrapper.class))).thenReturn(List.of(deleteTask, keepTask));
        when(messageService.list(any(Wrapper.class))).thenReturn(List.of());

        TransactionTemplate tx = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(tx).executeWithoutResult(any());

        GeneratedMediaProtectionPort protection = () -> Set.of("/profile/keep.png");
        GeneratedImageRetentionService service = new GeneratedImageRetentionService(
                taskService, messageService,
                mock(com.aurora.ai.agent.service.IMessageAttachmentService.class),
                mock(com.aurora.ai.agent.service.IReferenceScopeService.class),
                storage, new ObjectMapper(), tx,
                List.of(protection), 30, 100);

        assertThat(service.cleanupExpired()).isEqualTo(2);
        assertThat(configured.resolve("generated/delete.png")).doesNotExist();
        assertThat(configured.resolve("generated/delete.preview.jpg")).doesNotExist();
        assertThat(configured.resolve("keep.png")).exists();
        verify(taskService).removeById(1L);
        verify(taskService).removeById(2L);
    }

    @Test
    void scrubsOnlyMatchingTaskReferences() {
        GeneratedImageRetentionService service = new GeneratedImageRetentionService(
                mock(IGenerationTaskService.class), mock(IMessageService.class),
                mock(com.aurora.ai.agent.service.IMessageAttachmentService.class),
                mock(com.aurora.ai.agent.service.IReferenceScopeService.class),
                new ProfileStorage(tempDir.resolve("uploads").toString(), tempDir.resolve("home").toString()),
                new ObjectMapper(), mock(TransactionTemplate.class), List.of(), 30, 100);

        String media = service.scrubMedia(
                "[{\"taskId\":\"old\",\"seq\":1},{\"taskId\":\"new\",\"seq\":1}]", "old");
        String content = service.scrubStructuredContent(
                "{\"title\":\"保留文字\",\"images\":[{\"taskId\":\"old\",\"seq\":1}]}",
                "structured", "old");

        assertThat(media).isEqualTo("[{\"taskId\":\"new\",\"seq\":1}]");
        assertThat(content).isEqualTo("{\"title\":\"保留文字\"}");
    }

    private GenerationTask task(Long id, String taskId, String resultUrls) {
        GenerationTask task = new GenerationTask();
        task.setId(id);
        task.setTaskId(taskId);
        task.setTaskType("image");
        task.setResultUrls(resultUrls);
        task.setCreateTime(LocalDateTime.now().minusDays(31));
        return task;
    }
}
