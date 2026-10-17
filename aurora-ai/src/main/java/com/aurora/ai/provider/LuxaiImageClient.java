package com.aurora.ai.provider;

import com.aurora.ai.provider.support.AiHttpUtil;
import com.aurora.ai.provider.support.RecoverableApiException;
import com.aurora.common.core.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class LuxaiImageClient {
    public static final long MAX_REFERENCE_BYTES = 50L * 1024 * 1024;
    private static final int TIMEOUT_SECONDS = 1000;
    private static final Set<String> QUALITIES = Set.of("auto", "low", "medium", "high", "xhigh", "max");
    private static final ObjectMapper IMAGE_JSON = new ObjectMapper(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxStringLength(100_000_000).build()).build());

    public static String quality(String configured) {
        String quality = configured == null || configured.isBlank() ? "auto" : configured.trim();
        if (!QUALITIES.contains(quality)) throw new ServiceException("Luxai 图片质量配置无效");
        return quality;
    }

    public byte[] textToImage(String baseUrl, String apiKey, String model, String prompt,
                              String resolution, String aspectRatio, String quality) {
        validatePrompt(prompt);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("model", model);
        fields.put("prompt", prompt);
        fields.put("n", 1);
        fields.put("size", LuxaiImageSize.of(resolution, aspectRatio));
        fields.put("quality", quality(quality));
        fields.put("output_format", "png");
        try {
            return parseImage(AiHttpUtil.postJsonRaw(endpoint(baseUrl, "generations"),
                    AiHttpUtil.M.writeValueAsString(fields), apiKey, TIMEOUT_SECONDS));
        } catch (Exception error) {
            throw requestFailure(error);
        }
    }

    public byte[] imageToImage(String baseUrl, String apiKey, String model, String prompt,
                               String resolution, String aspectRatio, String quality, List<byte[]> references) {
        validatePrompt(prompt);
        if (references == null || references.isEmpty() || references.size() > 9) {
            throw new ServiceException("Luxai 图生图需1~9张参考图");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("model", model);
        fields.put("prompt", prompt);
        fields.put("n", "1");
        fields.put("size", LuxaiImageSize.of(resolution, aspectRatio));
        fields.put("quality", quality(quality));
        fields.put("output_format", "png");
        List<AiHttpUtil.FilePart> files = new ArrayList<>();
        long totalBytes = 0;
        String field = references.size() == 1 ? "image" : "image[]";
        for (int index = 0; index < references.size(); index++) {
            byte[] bytes = references.get(index);
            if (bytes == null || bytes.length < 4) throw new ServiceException("参考图为空或格式无效");
            totalBytes += bytes.length;
            if (totalBytes > MAX_REFERENCE_BYTES) throw new ServiceException("参考图总大小不能超过50MB");
            String contentType;
            String extension;
            if ((bytes[0] & 255) == 137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71) {
                contentType = "image/png";
                extension = "png";
            } else if ((bytes[0] & 255) == 255 && (bytes[1] & 255) == 216) {
                contentType = "image/jpeg";
                extension = "jpg";
            } else if (bytes.length >= 12 && bytes[0] == 82 && bytes[1] == 73 && bytes[2] == 70
                    && bytes[3] == 70 && bytes[8] == 87 && bytes[9] == 69 && bytes[10] == 66 && bytes[11] == 80) {
                contentType = "image/webp";
                extension = "webp";
            } else throw new ServiceException("参考图仅支持PNG、JPEG、WebP");
            files.add(new AiHttpUtil.FilePart(field, "ref" + (index + 1) + "." + extension, contentType, bytes));
        }
        try {
            return parseImage(AiHttpUtil.postMultipartRaw(endpoint(baseUrl, "edits"), apiKey,
                    TIMEOUT_SECONDS, fields, files));
        } catch (Exception error) {
            throw requestFailure(error);
        }
    }

    private void validatePrompt(String prompt) {
        if (prompt == null || prompt.isBlank() || prompt.codePointCount(0, prompt.length()) > 32000) {
            throw new ServiceException("Luxai 提示词需1~32000个字符");
        }
    }

    private String endpoint(String baseUrl, String operation) {
        if (baseUrl == null || baseUrl.isBlank()) throw new ServiceException("Luxai API地址未配置");
        String base = baseUrl.replaceAll("/+$", "");
        return base + (base.endsWith("/v1") ? "" : "/v1") + "/images/" + operation;
    }

    private byte[] parseImage(byte[] response) throws Exception {
        JsonNode image = IMAGE_JSON.readTree(response).path("data").path(0);
        String base64 = image.path("b64_json").asText();
        if (!base64.isBlank()) return Base64.getDecoder().decode(base64);
        String url = image.path("url").asText();
        if (!url.isBlank()) {
            URI uri = URI.create(url);
            if (!Set.of("http", "https").contains(uri.getScheme())) {
                throw new ServiceException("Luxai 返回的图片地址无效");
            }
            return AiHttpUtil.download(url);
        }
        throw new ServiceException("Luxai 未返回有效图片");
    }

    private RuntimeException requestFailure(Exception error) {
        if (error instanceof RecoverableApiException recoverable) {
            return new RecoverableApiException(recoverable.getStatusCode(), "Luxai 出图服务暂时不可用");
        }
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                return new ServiceException("Luxai 出图请求被中断");
            }
            if (cause instanceof IOException) return new RecoverableApiException(0, "Luxai 出图网络异常或超时");
        }
        return new ServiceException("Luxai 出图失败，请检查模型配置、参数和渠道权限");
    }
}
