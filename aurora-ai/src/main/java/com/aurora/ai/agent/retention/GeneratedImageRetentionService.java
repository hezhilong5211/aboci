package com.aurora.ai.agent.retention;

import com.aurora.ai.agent.domain.GenerationTask;
import com.aurora.ai.agent.domain.Message;
import com.aurora.ai.agent.domain.MessageAttachment;
import com.aurora.ai.agent.service.IGenerationTaskService;
import com.aurora.ai.agent.service.IMessageAttachmentService;
import com.aurora.ai.agent.service.IMessageService;
import com.aurora.ai.agent.service.IReferenceScopeService;
import com.aurora.framework.storage.ProfileStorage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 生成图片 30 天保留清理服务。
 *
 * <p>只处理 {@code generation_task.task_type=image} 的结果文件和数据库流水；案例库等上层业务通过
 * {@link GeneratedMediaProtectionPort} 声明长期保护 URL。清理顺序为“文件成功删除（或受保护）→
 * 事务内清除 message 图片引用并删除 generation_task”，文件删除失败时保留数据库行供下次重试。</p>
 */
@Slf4j
@Service
public class GeneratedImageRetentionService {

    private final IGenerationTaskService generationTaskService;
    private final IMessageService messageService;
    private final IMessageAttachmentService messageAttachmentService;
    private final IReferenceScopeService referenceScopeService;
    private final ProfileStorage profileStorage;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final List<GeneratedMediaProtectionPort> protectionPorts;
    private final int retentionDays;
    private final int maxTasksPerRun;

    public GeneratedImageRetentionService(
            IGenerationTaskService generationTaskService,
            IMessageService messageService,
            IMessageAttachmentService messageAttachmentService,
            IReferenceScopeService referenceScopeService,
            ProfileStorage profileStorage,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate,
            List<GeneratedMediaProtectionPort> protectionPorts,
            @Value("${ai.generated-image.retention-days:30}") int retentionDays,
            @Value("${ai.generated-image.cleanup.max-tasks-per-run:2000}") int maxTasksPerRun) {
        this.generationTaskService = generationTaskService;
        this.messageService = messageService;
        this.messageAttachmentService = messageAttachmentService;
        this.referenceScopeService = referenceScopeService;
        this.profileStorage = profileStorage;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.protectionPorts = protectionPorts;
        this.retentionDays = Math.max(1, retentionDays);
        this.maxTasksPerRun = Math.max(1, maxTasksPerRun);
    }

    /**
     * 执行一次过期清理。
     *
     * @return 成功清理的 generation_task 数量
     */
    public int cleanupExpired() {
        Set<String> protectedUrls;
        try {
            protectedUrls = loadProtectedUrls();
        } catch (Exception e) {
            // 保护清单加载失败时宁可不清，也不能误删案例图片。
            log.error("生成图片清理已中止：案例保护清单加载失败", e);
            return 0;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        List<GenerationTask> tasks = generationTaskService.list(new LambdaQueryWrapper<GenerationTask>()
                .eq(GenerationTask::getTaskType, "image")
                .le(GenerationTask::getCreateTime, cutoff)
                .notExists("SELECT 1 FROM conversation c WHERE c.id = generation_task.conversation_id "
                        + "AND c.del_flag = '0' AND c.expire_time > NOW()")
                .notExists("SELECT 1 FROM message_attachment a JOIN conversation c ON c.id = a.conversation_id "
                        + "WHERE a.source_task_id = generation_task.task_id AND c.del_flag = '0' AND c.expire_time > NOW()")
                .orderByAsc(GenerationTask::getCreateTime)
                .last("LIMIT " + maxTasksPerRun));

        int cleaned = 0;
        int failed = 0;
        int protectedFileCount = 0;
        for (GenerationTask task : tasks) {
            FileCleanupResult fileResult = cleanupResultFiles(task, protectedUrls);
            protectedFileCount += fileResult.protectedCount;
            if (!fileResult.success) {
                failed++;
                continue;
            }
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    scrubMessageReferences(task.getTaskId());
                    List<MessageAttachment> attachments = messageAttachmentService.listBySourceTask(task.getTaskId());
                    referenceScopeService.cancelByAttachmentIds(attachments.stream()
                            .map(MessageAttachment::getAttachmentId)
                            .toList());
                    messageAttachmentService.removeBySourceTask(task.getTaskId());
                    generationTaskService.removeById(task.getId());
                });
                cleaned++;
            } catch (Exception e) {
                failed++;
                log.error("生成图片数据库清理失败，保留任务待下次重试: taskId={}", task.getTaskId(), e);
            }
        }
        if (!tasks.isEmpty()) {
            log.info("生成图片保留期清理完成: cutoff={}, scanned={}, cleaned={}, failed={}, protectedFiles={}",
                    cutoff, tasks.size(), cleaned, failed, protectedFileCount);
        }
        return cleaned;
    }

    private Set<String> loadProtectedUrls() {
        Set<String> protectedUrls = new HashSet<>();
        for (GeneratedMediaProtectionPort port : protectionPorts) {
            Set<String> urls = port.protectedProfileUrls();
            if (urls == null) {
                continue;
            }
            for (String url : urls) {
                String normalized = ProfileStorage.normalizeProfileUrl(url);
                if (normalized != null) {
                    protectedUrls.add(normalized);
                }
            }
        }
        return protectedUrls;
    }

    private FileCleanupResult cleanupResultFiles(GenerationTask task, Set<String> protectedUrls) {
        List<String> resultUrls;
        try {
            resultUrls = parseResultUrls(task.getResultUrls());
        } catch (Exception e) {
            log.error("生成图片结果地址解析失败，保留任务待人工检查: taskId={}, resultUrls={}",
                    task.getTaskId(), task.getResultUrls(), e);
            return new FileCleanupResult(false, 0);
        }

        int protectedCount = 0;
        for (String url : resultUrls) {
            String normalized = ProfileStorage.normalizeProfileUrl(url);
            if (normalized == null) {
                // 外部地址不属于本机磁盘，数据库流水仍可按保留期清理。
                continue;
            }
            if (protectedUrls.contains(normalized)) {
                protectedCount++;
                continue;
            }
            try {
                profileStorage.deleteEverywhere(normalized);
                String previewUrl = toPreviewUrl(normalized);
                if (previewUrl != null) {
                    profileStorage.deleteEverywhere(previewUrl);
                }
                String watermarkedUrl = toWatermarkedUrl(normalized);
                if (watermarkedUrl != null) {
                    profileStorage.deleteEverywhere(watermarkedUrl);
                }
            } catch (Exception e) {
                log.error("生成图片文件删除失败，保留任务待下次重试: taskId={}, url={}",
                        task.getTaskId(), normalized, e);
                return new FileCleanupResult(false, protectedCount);
            }
        }
        return new FileCleanupResult(true, protectedCount);
    }

    /** 新生成图片使用固定同名预览图；旧路径无预览图时返回 null。 */
    private String toPreviewUrl(String originalUrl) {
        if (originalUrl != null && originalUrl.startsWith("/profile/generated/") && originalUrl.endsWith(".png")) {
            return originalUrl.substring(0, originalUrl.length() - 4) + ".preview.jpg";
        }
        return null;
    }

    /** 新生成图片的按需水印派生图；旧路径没有派生图时删除仍按成功处理。 */
    private String toWatermarkedUrl(String originalUrl) {
        if (originalUrl != null && originalUrl.startsWith("/profile/generated/") && originalUrl.endsWith(".png")) {
            return originalUrl.substring(0, originalUrl.length() - 4) + ".watermarked.png";
        }
        return null;
    }

    private List<String> parseResultUrls(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        JsonNode root = objectMapper.readTree(json);
        if (!root.isArray()) {
            throw new IllegalArgumentException("result_urls 不是数组");
        }
        java.util.ArrayList<String> urls = new java.util.ArrayList<>();
        for (JsonNode node : root) {
            if (node.isTextual() && !node.asText().isBlank()) {
                urls.add(node.asText());
            }
        }
        return urls;
    }

    /** 删除 message.media/content 中指向过期 taskId 的图片引用，保留对话文字。 */
    private void scrubMessageReferences(String taskId) {
        List<Message> messages = messageService.list(new LambdaQueryWrapper<Message>()
                .and(wrapper -> wrapper.like(Message::getMedia, taskId)
                        .or()
                        .like(Message::getContent, taskId)));
        for (Message message : messages) {
            String media = scrubMedia(message.getMedia(), taskId);
            String content = scrubStructuredContent(message.getContent(), message.getContentType(), taskId);
            boolean mediaChanged = !java.util.Objects.equals(media, message.getMedia());
            boolean contentChanged = !java.util.Objects.equals(content, message.getContent());
            if (!mediaChanged && !contentChanged) {
                continue;
            }
            LambdaUpdateWrapper<Message> update = new LambdaUpdateWrapper<Message>()
                    .eq(Message::getId, message.getId());
            if (mediaChanged) {
                update.set(Message::getMedia, media);
            }
            if (contentChanged) {
                update.set(Message::getContent, content);
            }
            messageService.update(update);
        }
    }

    String scrubMedia(String json, String taskId) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!(root instanceof ArrayNode array)) {
                return json;
            }
            boolean changed = removeTaskRefs(array, taskId);
            if (!changed) {
                return json;
            }
            return array.isEmpty() ? null : objectMapper.writeValueAsString(array);
        } catch (Exception e) {
            log.warn("消息媒体引用解析失败，跳过清理: taskId={}, err={}", taskId, e.getMessage());
            return json;
        }
    }

    String scrubStructuredContent(String json, String contentType, String taskId) {
        if (!"structured".equals(contentType) || json == null || json.isBlank()) {
            return json;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!(root instanceof ObjectNode object)) {
                return json;
            }
            JsonNode images = object.get("images");
            if (!(images instanceof ArrayNode imageArray) || !removeTaskRefs(imageArray, taskId)) {
                return json;
            }
            if (imageArray.isEmpty()) {
                object.remove("images");
            }
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            log.warn("结构化消息引用解析失败，跳过清理: taskId={}, err={}", taskId, e.getMessage());
            return json;
        }
    }

    private boolean removeTaskRefs(ArrayNode array, String taskId) {
        int before = array.size();
        for (int i = array.size() - 1; i >= 0; i--) {
            JsonNode node = array.get(i);
            if (node.isObject() && taskId.equals(node.path("taskId").asText(null))) {
                array.remove(i);
            }
        }
        return before != array.size();
    }

    private record FileCleanupResult(boolean success, int protectedCount) {
    }
}
