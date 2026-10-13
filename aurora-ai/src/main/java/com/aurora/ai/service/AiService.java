package com.aurora.ai.service;

import reactor.core.publisher.Flux;

public interface AiService {
    
    /**
     * 使用指定模型进行对话并保存历史记录
     */
    String chat(String sessionId, Long modelId, String message);

    /**
     * 获取指定会话的历史记录
     */
    java.util.List<com.aurora.ai.domain.SysAiHistory> getHistory(String sessionId);

    /**
     * 根据文本生成图像
     * resolution（分辨率档位，如 1K/2K/4K）与 aspectRatio（宽高比，如 16:9）为可选项，
     * 仅云雾 / GPTS 等多模态供应商使用，传 null 时由各 client 兜底默认
     */
    String generateImage(String sessionId, Long modelId, String prompt, String resolution, String aspectRatio);

    /**
     * 根据文本生成视频（当前对接可灵 Kling）
     * durationSec 视频时长（秒），aspectRatio 画幅比例，均可为 null 由 client 兜底默认
     */
    String generateVideo(String sessionId, Long modelId, String prompt, Integer durationSec, String aspectRatio);

    /**
     * 流式对话（使用默认激活模型）
     */
    Flux<String> streamChat(String message);
}
