package com.aurora.admin.mobile.retention;

import com.aurora.admin.mobile.domain.MobileUser;
import com.aurora.admin.mobile.service.IMobileUserService;
import com.aurora.ai.agent.domain.GenerationTask;
import com.aurora.ai.agent.domain.Message;
import com.aurora.ai.agent.service.IGenerationTaskService;
import com.aurora.ai.agent.service.IMessageService;
import com.aurora.framework.storage.ProfileStorage;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UploadedReferenceRetentionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void deletesOnlyExpiredUnreferencedAgentFiles() throws Exception {
        initTableInfo(GenerationTask.class);
        initTableInfo(Message.class);
        initTableInfo(MobileUser.class);

        Path uploads = tempDir.resolve("uploads");
        ProfileStorage storage = new ProfileStorage(uploads.toString(), tempDir.resolve("home").toString());
        storage.write("agent_reference/5/unreferenced.png", "delete".getBytes());
        storage.write("agent_reference/5/task-reference.png", "keep-task".getBytes());
        storage.write("agent_reference/5/message-reference.png", "keep-message".getBytes());
        storage.write("agent_reference/5/recent.png", "keep-recent".getBytes());
        storage.write("mobile_avatar/5/avatar.png", "keep-avatar".getBytes());

        Instant old = Instant.now().minus(31, ChronoUnit.DAYS);
        for (String filename : List.of("unreferenced.png", "task-reference.png", "message-reference.png")) {
            Files.setLastModifiedTime(uploads.resolve("agent_reference/5/" + filename), FileTime.from(old));
        }
        Files.setLastModifiedTime(uploads.resolve("mobile_avatar/5/avatar.png"), FileTime.from(old));

        GenerationTask task = new GenerationTask();
        task.setRefImages("[\"/profile/agent_reference/5/task-reference.png\"]");
        Message message = new Message();
        message.setMedia("[\"https://example.com/profile/agent_reference/5/message-reference.png?token=1\"]");

        IGenerationTaskService generationTaskService = mock(IGenerationTaskService.class);
        IMessageService messageService = mock(IMessageService.class);
        IMobileUserService mobileUserService = mock(IMobileUserService.class);
        when(generationTaskService.list(any(Wrapper.class))).thenReturn(List.of(task));
        when(messageService.list(any(Wrapper.class))).thenReturn(List.of(message));
        when(mobileUserService.list(any(Wrapper.class))).thenReturn(List.of());

        UploadedReferenceRetentionService service = new UploadedReferenceRetentionService(
                storage, generationTaskService, messageService,
                mock(com.aurora.ai.agent.service.IMessageAttachmentService.class), mobileUserService,
                new ObjectMapper(), 30, 100);

        assertThat(service.cleanupExpired()).isEqualTo(1);
        assertThat(uploads.resolve("agent_reference/5/unreferenced.png")).doesNotExist();
        assertThat(uploads.resolve("agent_reference/5/task-reference.png")).exists();
        assertThat(uploads.resolve("agent_reference/5/message-reference.png")).exists();
        assertThat(uploads.resolve("agent_reference/5/recent.png")).exists();
        assertThat(uploads.resolve("mobile_avatar/5/avatar.png")).exists();
    }

    private void initTableInfo(Class<?> entityType) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
