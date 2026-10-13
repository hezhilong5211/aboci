package com.aurora.ai.controller;

import com.aurora.ai.domain.SysAiHistory;
import com.aurora.ai.service.AiService;
import com.aurora.common.core.domain.Result;
import com.aurora.framework.annotation.RateLimiter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin
@RequestMapping("/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * AI 对话接口（支持历史记录持久化）
     * Body: { sessionId: "...", modelId: 1, message: "..." }
     */
    @PostMapping("/chat")
    @RateLimiter(key = "admin:ai:use", time = 60, count = 20,
            limitType = RateLimiter.LimitType.USER, message = "AI 请求过于频繁，请稍后再试")
    public Result<String> chat(@RequestBody Map<String, Object> params) {
        String sessionId = (String) params.getOrDefault("sessionId", "default");
        Long modelId = params.get("modelId") != null ? Long.valueOf(params.get("modelId").toString()) : null;
        String message = (String) params.get("message");
        return Result.success(aiService.chat(sessionId, modelId, message));
    }

    /**
     * 文字生成图片接口
     * Body: { sessionId: "...", modelId: 1, prompt: "...", resolution?: "1K", aspectRatio?: "16:9" }
     * 权限例外：生成使用类端点，所有登录用户均可使用，故用 isAuthenticated() 而非 @ss.hasPermi
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/image/t2i")
    @RateLimiter(key = "admin:ai:use", time = 60, count = 20,
            limitType = RateLimiter.LimitType.USER, message = "AI 请求过于频繁，请稍后再试")
    public Result<String> textToImage(@RequestBody Map<String, Object> params) {
        String sessionId = (String) params.getOrDefault("sessionId", "default");
        Long modelId = params.get("modelId") != null ? Long.valueOf(params.get("modelId").toString()) : null;
        String prompt = (String) params.get("prompt");
        String resolution = (String) params.get("resolution");
        String aspectRatio = (String) params.get("aspectRatio");
        return Result.success(aiService.generateImage(sessionId, modelId, prompt, resolution, aspectRatio));
    }

    /**
     * 文字生成视频接口（当前对接可灵 Kling）
     * Body: { sessionId: "...", modelId: 1, prompt: "...", duration?: 5, aspectRatio?: "16:9" }
     * 权限例外：生成使用类端点，所有登录用户均可使用，故用 isAuthenticated() 而非 @ss.hasPermi
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/video/t2v")
    @RateLimiter(key = "admin:ai:use", time = 60, count = 20,
            limitType = RateLimiter.LimitType.USER, message = "AI 请求过于频繁，请稍后再试")
    public Result<String> textToVideo(@RequestBody Map<String, Object> params) {
        String sessionId = (String) params.getOrDefault("sessionId", "default");
        Long modelId = params.get("modelId") != null ? Long.valueOf(params.get("modelId").toString()) : null;
        String prompt = (String) params.get("prompt");
        Integer duration = params.get("duration") != null ? Integer.valueOf(params.get("duration").toString()) : null;
        String aspectRatio = (String) params.get("aspectRatio");
        return Result.success(aiService.generateVideo(sessionId, modelId, prompt, duration, aspectRatio));
    }

    /**
     * 获取对话历史记录
     */
    @GetMapping("/history")
    public Result<List<SysAiHistory>> getHistory(@RequestParam String sessionId) {
        return Result.success(aiService.getHistory(sessionId));
    }

    /**
     * 基础对话测试接口（仅用于快速验证）
     */
    @GetMapping("/test/chat")
    @RateLimiter(key = "admin:ai:use", time = 60, count = 20,
            limitType = RateLimiter.LimitType.USER, message = "AI 请求过于频繁，请稍后再试")
    public Result<String> simpleChat(@RequestParam(defaultValue = "ping") String message) {
        return Result.success(aiService.chat("test", null, message));
    }

    /**
     * 流式对话接口
     */
    @GetMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    @RateLimiter(key = "admin:ai:use", time = 60, count = 20,
            limitType = RateLimiter.LimitType.USER, message = "AI 请求过于频繁，请稍后再试")
    public Flux<String> streamChat(@RequestParam(defaultValue = "ping") String message) {
        return aiService.streamChat(message);
    }
}
