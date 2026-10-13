package com.aurora.admin.mobile.retention;

import com.aurora.admin.mobile.domain.MobileUser;
import com.aurora.admin.mobile.service.IMobileUserService;
import com.aurora.ai.agent.domain.GenerationTask;
import com.aurora.ai.agent.domain.Message;
import com.aurora.ai.agent.domain.MessageAttachment;
import com.aurora.ai.agent.service.IGenerationTaskService;
import com.aurora.ai.agent.service.IMessageAttachmentService;
import com.aurora.ai.agent.service.IMessageService;
import com.aurora.framework.storage.ProfileStorage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 清理超过保留期且已无数据库引用的用户上传参考图。 */
@Slf4j
@Service
public class UploadedReferenceRetentionService {

    private static final String REFERENCE_DIRECTORY = "agent_reference";
    private static final String REFERENCE_PREFIX = "/profile/agent_reference/";

    private final ProfileStorage profileStorage;
    private final IGenerationTaskService generationTaskService;
    private final IMessageService messageService;
    private final IMessageAttachmentService messageAttachmentService;
    private final IMobileUserService mobileUserService;
    private final ObjectMapper objectMapper;
    private final int retentionDays;
    private final int maxFilesPerRun;

    public UploadedReferenceRetentionService(
            ProfileStorage profileStorage,
            IGenerationTaskService generationTaskService,
            IMessageService messageService,
            IMessageAttachmentService messageAttachmentService,
            IMobileUserService mobileUserService,
            ObjectMapper objectMapper,
            @Value("${ai.uploaded-reference.retention-days:30}") int retentionDays,
            @Value("${ai.uploaded-reference.cleanup.max-files-per-run:2000}") int maxFilesPerRun) {
        this.profileStorage = profileStorage;
        this.generationTaskService = generationTaskService;
        this.messageService = messageService;
        this.messageAttachmentService = messageAttachmentService;
        this.mobileUserService = mobileUserService;
        this.objectMapper = objectMapper;
        this.retentionDays = Math.max(1, retentionDays);
        this.maxFilesPerRun = Math.max(1, maxFilesPerRun);
    }

    /**
     * 执行一次参考图清理。引用清单读取失败时整体中止，宁可多留文件也不误删。
     *
     * @return 成功删除的参考图数量
     */
    public int cleanupExpired() {
        Set<String> referencedUrls;
        List<String> candidates;
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        try {
            referencedUrls = loadReferencedUrls();
            candidates = profileStorage.listConfiguredProfileUrls(
                    REFERENCE_DIRECTORY, cutoff, maxFilesPerRun);
        } catch (Exception e) {
            log.error("上传参考图清理已中止：引用清单或文件清单读取失败", e);
            return 0;
        }

        int cleaned = 0;
        int protectedCount = 0;
        int failed = 0;
        for (String candidate : candidates) {
            if (referencedUrls.contains(candidate)) {
                protectedCount++;
                continue;
            }
            try {
                if (profileStorage.deleteEverywhere(candidate)) {
                    cleaned++;
                }
            } catch (Exception e) {
                failed++;
                log.error("上传参考图删除失败，保留待下次重试: url={}", candidate, e);
            }
        }
        if (!candidates.isEmpty()) {
            log.info("上传参考图保留期清理完成: cutoff={}, scanned={}, cleaned={}, protected={}, failed={}",
                    cutoff, candidates.size(), cleaned, protectedCount, failed);
        }
        return cleaned;
    }

    private Set<String> loadReferencedUrls() {
        Set<String> urls = new HashSet<>();

        List<GenerationTask> tasks = generationTaskService.list(new LambdaQueryWrapper<GenerationTask>()
                .select(GenerationTask::getRefImages, GenerationTask::getRequestItems)
                .and(wrapper -> wrapper.like(GenerationTask::getRefImages, REFERENCE_PREFIX)
                        .or()
                        .like(GenerationTask::getRequestItems, REFERENCE_PREFIX)));
        for (GenerationTask task : tasks) {
            collectJsonUrls(task.getRefImages(), urls);
            collectJsonUrls(task.getRequestItems(), urls);
        }

        List<Message> messages = messageService.list(new LambdaQueryWrapper<Message>()
                .select(Message::getMedia)
                .like(Message::getMedia, REFERENCE_PREFIX));
        for (Message message : messages) {
            collectJsonUrls(message.getMedia(), urls);
        }

        List<MessageAttachment> attachments = messageAttachmentService.list(
                new QueryWrapper<MessageAttachment>()
                        .select("profile_url")
                        .like("profile_url", REFERENCE_PREFIX));
        for (MessageAttachment attachment : attachments) {
            addNormalized(attachment.getProfileUrl(), urls);
        }

        List<MobileUser> users = mobileUserService.list(new LambdaQueryWrapper<MobileUser>()
                .select(MobileUser::getAvatarUrl)
                .like(MobileUser::getAvatarUrl, REFERENCE_PREFIX));
        for (MobileUser user : users) {
            addNormalized(user.getAvatarUrl(), urls);
        }
        return urls;
    }

    private void collectJsonUrls(String json, Set<String> urls) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            collectNodeUrls(objectMapper.readTree(json), urls);
        } catch (Exception e) {
            // 非 JSON 历史值仅按单个 URL 识别；无法识别时保留数据库，跳过该值。
            addNormalized(json, urls);
        }
    }

    private void collectNodeUrls(JsonNode node, Set<String> urls) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            addNormalized(node.asText(), urls);
            return;
        }
        if (node.isContainerNode()) {
            node.forEach(child -> collectNodeUrls(child, urls));
        }
    }

    private void addNormalized(String value, Set<String> urls) {
        String normalized = ProfileStorage.normalizeProfileUrl(value);
        if (normalized != null && normalized.startsWith(REFERENCE_PREFIX)) {
            urls.add(normalized);
        }
    }
}
