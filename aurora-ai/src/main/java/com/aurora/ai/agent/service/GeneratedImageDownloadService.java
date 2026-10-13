package com.aurora.ai.agent.service;

import com.aurora.ai.agent.domain.GenerationTask;
import com.aurora.ai.agent.dto.GenerationDownloadVO;
import com.aurora.common.core.exception.ServiceException;
import com.aurora.framework.storage.ProfileStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按当前后台配置返回本人生成图片下载地址。
 * 原图永不覆盖；开启水印时按需生成同名派生 PNG，失败时禁止无水印降级。
 */
@Service
@RequiredArgsConstructor
public class GeneratedImageDownloadService {

    private static final String WATERMARK_SUFFIX = ".watermarked.png";
    private static final String WATERMARK_BASE64 =
            "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";

    private static final BufferedImage WATERMARK = loadWatermark();

    private final IGenerationTaskService generationTaskService;
    private final GenerationFeaturePolicy generationFeaturePolicy;
    private final ProfileStorage profileStorage;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Object> fileLocks = new ConcurrentHashMap<>();

    public GenerationDownloadVO resolveDownload(Long userId, String taskId, int seq) {
        GenerationTask task = generationTaskService.getOwned(taskId, userId);
        if (task == null) {
            throw new ServiceException("任务不存在或无权访问");
        }
        String originalUrl = resultAt(task.getResultUrls(), seq);
        if (originalUrl == null) {
            throw new ServiceException("图片不存在或尚未生成完成");
        }
        if (!generationFeaturePolicy.isDownloadWatermarkEnabled()) {
            return new GenerationDownloadVO(originalUrl, false);
        }

        String watermarkedUrl = toWatermarkedUrl(originalUrl);
        Object lock = fileLocks.computeIfAbsent(watermarkedUrl, key -> new Object());
        try {
            synchronized (lock) {
                if (!exists(watermarkedUrl)) {
                    createWatermarked(originalUrl, watermarkedUrl);
                }
            }
        } finally {
            fileLocks.remove(watermarkedUrl, lock);
        }
        return new GenerationDownloadVO(watermarkedUrl, true);
    }

    private String resultAt(String resultUrls, int seq) {
        if (seq < 1 || resultUrls == null || resultUrls.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(resultUrls);
            if (!root.isArray() || seq > root.size()) {
                return null;
            }
            String value = root.get(seq - 1).asText("");
            return value.isBlank() ? null : value;
        } catch (Exception e) {
            throw new ServiceException("生成图片结果解析失败", e);
        }
    }

    private String toWatermarkedUrl(String originalUrl) {
        String normalized = ProfileStorage.normalizeProfileUrl(originalUrl);
        if (normalized == null || !normalized.startsWith("/profile/generated/")
                || !normalized.endsWith(".png")) {
            throw new ServiceException("生成图片地址不支持安全下载");
        }
        return normalized.substring(0, normalized.length() - 4) + WATERMARK_SUFFIX;
    }

    private boolean exists(String profileUrl) {
        try {
            profileStorage.read(profileUrl);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void createWatermarked(String originalUrl, String watermarkedUrl) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(profileStorage.read(originalUrl)));
            if (source == null) {
                throw new ServiceException("无法解析生成图片");
            }
            BufferedImage output = new BufferedImage(source.getWidth(), source.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = output.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                graphics.drawImage(source, 0, 0, null);

                int targetWidth = Math.max(120, Math.min(320, source.getWidth() / 5));
                int targetHeight = Math.max(39,
                        (int) Math.round((double) targetWidth * WATERMARK.getHeight() / WATERMARK.getWidth()));
                int margin = Math.max(16, Math.min(source.getWidth(), source.getHeight()) / 40);
                graphics.drawImage(WATERMARK,
                        Math.max(0, source.getWidth() - targetWidth - margin),
                        Math.max(0, source.getHeight() - targetHeight - margin),
                        targetWidth, targetHeight, null);
            } finally {
                graphics.dispose();
            }

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                if (!ImageIO.write(output, "png", out)) {
                    throw new ServiceException("运行环境不支持 PNG 编码");
                }
                String relativePath = watermarkedUrl.substring("/profile/".length());
                profileStorage.write(relativePath, out.toByteArray());
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("AI 水印生成失败，请稍后重试", e);
        }
    }

    private static BufferedImage loadWatermark() {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                    Base64.getDecoder().decode(WATERMARK_BASE64)));
            if (image == null) {
                throw new IllegalStateException("AI 水印资源无法解析");
            }
            return image;
        } catch (Exception e) {
            throw new IllegalStateException("AI 水印资源加载失败", e);
        }
    }
}
