package com.aurora.ai.service;

import com.aurora.ai.domain.SysAiModel;
import com.aurora.framework.utils.EncryptUtils;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 模型工厂
 * 根据配置动态创建 ChatModel 实例
 */
@Service
public class AiFactory {

    private final Map<String, ChatModel> modelCache = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private EncryptUtils encryptUtils;

    /**
     * 根据配置创建或获取 ChatModel，并处理 API Key
     * API Key 使用 AES 加密存储，在使用时解密
     */
    public ChatModel createChatModel(SysAiModel config) {
        String cacheKey = config.getId() + "-" + config.getUpdateTime();
        if (modelCache.containsKey(cacheKey)) {
            return modelCache.get(cacheKey);
        }

        // 解密 API Key
        String apiKey = decryptApiKey(config.getApiKey());
        String baseUrl = config.getBaseUrl();

        // Simple validation
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(baseUrl)) {
            throw new IllegalArgumentException("API Key and Base URL are required");
        }

        // 默认路径配置
        String completionsPath = "/v1/chat/completions";

        boolean isVersioned = baseUrl.matches(".*\\/v\\d+.*") || baseUrl.contains("/api/");

        // 智能路径处理：如果 baseUrl 已经包含了路径，则传递空字符串或相对路径以避免重复
        if (baseUrl.contains("/chat/completions")) {
            completionsPath = "";
        } else if (isVersioned) {
            completionsPath = "/chat/completions";
        }

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .completionsPath(completionsPath)
                .build();

        // 使用 Builders 创建配置选项
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(config.getModelDeployment())
                .temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens())
                .build();

        // 使用 Builder 创建 ChatModel
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();

        modelCache.put(cacheKey, chatModel);
        return chatModel;
    }

    /**
     * 创建图像模型 (ImageModel)，用于文生图
     */
    public org.springframework.ai.image.ImageModel createImageModel(SysAiModel config) {
        String apiKey = decryptApiKey(config.getApiKey());
        String baseUrl = config.getBaseUrl();

        // 图像生成默认路径
        String imagesPath = "/v1/images/generations";
        if (baseUrl.contains("/images/generations") || baseUrl.contains("/generation")) {
            imagesPath = "";
        } else if (baseUrl.matches(".*\\/v\\d+.*") || baseUrl.contains("/api/")) {
            imagesPath = "/images/generations";
        }

        org.springframework.ai.openai.api.OpenAiImageApi imageApi = org.springframework.ai.openai.api.OpenAiImageApi
                .builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .imagesPath(imagesPath)
                .build();

        final String finalBaseUrl = baseUrl;
        final String finalApiKey = apiKey;
        final String finalImagesPath = imagesPath;

        // 对非 OpenAI 官方的模型（如豆包、智谱等）自动应用去水印逻辑
        boolean isNonStandardOpenAI = !finalBaseUrl.contains("api.openai.com");

        if (isNonStandardOpenAI) {
            return new org.springframework.ai.image.ImageModel() {
                @Override
                public org.springframework.ai.image.ImageResponse call(
                        org.springframework.ai.image.ImagePrompt prompt) {
                    try {
                        String fullUri = finalBaseUrl.endsWith("/") ? finalBaseUrl + finalImagesPath
                                : finalBaseUrl + "/" + finalImagesPath;
                        if (finalBaseUrl.contains("/images/generations"))
                            fullUri = finalBaseUrl;

                        java.util.Map<String, Object> body = new java.util.HashMap<>();
                        body.put("model", config.getModelDeployment());
                        body.put("prompt", prompt.getInstructions().get(0).getText());

                        // 针对不同供应商（豆包、智谱等）广泛设置去水印参数
                        body.put("logo_info", java.util.Map.of("add_logo", false));
                        body.put("no_watermark", true);

                        var response = org.springframework.web.client.RestClient.create()
                                .post()
                                .uri(fullUri)
                                .header("Authorization", "Bearer " + finalApiKey)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .body(body)
                                .retrieve()
                                .body(java.util.Map.class);

                        if (response != null && response.containsKey("data")) {
                            var data = (java.util.List<java.util.Map<String, Object>>) response.get("data");
                            if (!data.isEmpty()) {
                                String url = (String) data.get(0).get("url");
                                var generation = new org.springframework.ai.image.ImageGeneration(
                                        new org.springframework.ai.image.Image(url, null));
                                return new org.springframework.ai.image.ImageResponse(java.util.List.of(generation));
                            }
                        }
                        throw new RuntimeException("Unexpected response from API: " + response);
                    } catch (Exception e) {
                        throw new RuntimeException("图像生成失败: " + e.getMessage(), e);
                    }
                }
            };
        }

        org.springframework.ai.openai.OpenAiImageOptions options = org.springframework.ai.openai.OpenAiImageOptions
                .builder()
                .model(config.getModelDeployment())
                .build();

        return new org.springframework.ai.openai.OpenAiImageModel(imageApi, options,
                new org.springframework.retry.support.RetryTemplate());
    }

    public void clearCache() {
        modelCache.clear();
    }

    /**
     * 解密 API Key
     * 如果加密工具不可用或解密失败，返回原值
     */
    private String decryptApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return apiKey;
        }
        if (encryptUtils != null) {
            return encryptUtils.decrypt(apiKey);
        }
        return apiKey;
    }
}
