package com.aurora.admin.mobile.adapter;

import com.aurora.admin.mobile.domain.Template;
import com.aurora.admin.mobile.service.ITemplateService;
import com.aurora.ai.agent.retention.GeneratedMediaProtectionPort;
import com.aurora.framework.storage.ProfileStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * 案例库媒体长期保护适配器。
 *
 * <p>所有未逻辑删除的案例（含已下架）均由管理后台统一管理，其封面、效果图、参考图不参与
 * generation_task 的 30 天物理文件清理。</p>
 */
@Component
@RequiredArgsConstructor
public class TemplateGeneratedMediaProtectionAdapter implements GeneratedMediaProtectionPort {

    private final ITemplateService templateService;
    private final ObjectMapper objectMapper;

    @Override
    public Set<String> protectedProfileUrls() {
        Set<String> urls = new HashSet<>();
        for (Template template : templateService.list()) {
            addDirectUrl(template.getCoverUrl(), urls);
            addJsonUrls(template.getResultMedia(), urls);
            addJsonUrls(template.getReferenceImages(), urls);
        }
        return urls;
    }

    private void addDirectUrl(String value, Set<String> urls) {
        String normalized = ProfileStorage.normalizeProfileUrl(value);
        if (normalized != null) {
            urls.add(normalized);
        }
    }

    private void addJsonUrls(String json, Set<String> urls) {
        if (!StringUtils.hasText(json)) {
            return;
        }
        try {
            collectTextUrls(objectMapper.readTree(json), urls);
        } catch (Exception e) {
            // 保护字段异常时抛出，由清理服务整体中止，避免误删案例图片。
            throw new IllegalStateException("案例媒体字段不是合法 JSON", e);
        }
    }

    private void collectTextUrls(JsonNode node, Set<String> urls) {
        if (node == null) {
            return;
        }
        if (node.isTextual()) {
            addDirectUrl(node.asText(), urls);
            return;
        }
        if (node.isContainerNode()) {
            node.elements().forEachRemaining(child -> collectTextUrls(child, urls));
        }
    }
}
