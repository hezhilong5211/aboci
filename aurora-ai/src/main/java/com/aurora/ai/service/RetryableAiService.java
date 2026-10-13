package com.aurora.ai.service;

import com.aurora.ai.domain.SysAiModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 可重试的 AI 服务
 * 为 AI 调用提供自动重试机制，处理网络抖动、API 限流等场景
 * 
 * 重试策略：
 * - 最大重试次数：3 次（包括首次调用）
 * - 退避策略：指数退避，初始 1 秒，每次翻倍
 * - 支持的异常：Exception（可根据需要细化）
 * 
 * @author aurora
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetryableAiService {

    private final AiFactory aiFactory;

    /**
     * 同步调用 AI（带重试）
     * 
     * @param model  AI 模型配置
     * @param prompt 提示词
     * @return AI 响应内容
     */
    @Retryable(retryFor = { Exception.class }, noRetryFor = { IllegalArgumentException.class }, // 参数错误不重试
            maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000))
    public String callWithRetry(SysAiModel model, String prompt) {
        log.debug("调用 AI [{}]，prompt 长度: {}", model.getModelName(), prompt.length());

        ChatModel chatModel = aiFactory.createChatModel(model);
        return ChatClient.builder(chatModel)
                .build()
                .prompt()
                .user(prompt)
                .call()
                .content();
    }

    /**
     * 同步调用 AI（带系统提示词和重试）
     * 
     * @param model        AI 模型配置
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return AI 响应内容
     */
    @Retryable(retryFor = { Exception.class }, noRetryFor = {
            IllegalArgumentException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000))
    public String callWithSystemPrompt(SysAiModel model, String systemPrompt, String userPrompt) {
        log.debug("调用 AI [{}]，包含系统提示词", model.getModelName());

        ChatModel chatModel = aiFactory.createChatModel(model);
        return ChatClient.builder(chatModel)
                .build()
                .prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
    }

    /**
     * 流式调用 AI（带重试）
     * 注意：流式调用的重试发生在建立连接阶段
     * 
     * @param model  AI 模型配置
     * @param prompt 提示词
     * @return AI 响应流
     */
    @Retryable(retryFor = { Exception.class }, noRetryFor = {
            IllegalArgumentException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000))
    public Flux<String> streamWithRetry(SysAiModel model, String prompt) {
        log.debug("流式调用 AI [{}]", model.getModelName());

        ChatModel chatModel = aiFactory.createChatModel(model);
        return ChatClient.builder(chatModel)
                .build()
                .prompt()
                .user(prompt)
                .stream()
                .content();
    }

    /**
     * 流式调用 AI（带系统提示词和重试）
     * 
     * @param model        AI 模型配置
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return AI 响应流
     */
    @Retryable(retryFor = { Exception.class }, noRetryFor = {
            IllegalArgumentException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000))
    public Flux<String> streamWithSystemPrompt(SysAiModel model, String systemPrompt, String userPrompt) {
        log.debug("流式调用 AI [{}]，包含系统提示词", model.getModelName());

        ChatModel chatModel = aiFactory.createChatModel(model);
        return ChatClient.builder(chatModel)
                .build()
                .prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .stream()
                .content();
    }

    /**
     * 重试失败后的降级处理
     * 当所有重试都失败后，会调用此方法
     */
    @Recover
    public String recoverFromFailure(Exception e, SysAiModel model, String prompt) {
        log.error("AI 调用失败，已重试 3 次。模型: {}，错误: {}", model.getModelName(), e.getMessage());
        throw new RuntimeException("AI 服务暂时不可用，请稍后重试。原因: " + e.getMessage(), e);
    }

    /**
     * 流式调用的降级处理
     */
    @Recover
    public Flux<String> recoverStreamFromFailure(Exception e, SysAiModel model, String prompt) {
        log.error("AI 流式调用失败，已重试 3 次。模型: {}，错误: {}", model.getModelName(), e.getMessage());
        return Flux.error(new RuntimeException("AI 服务暂时不可用，请稍后重试。原因: " + e.getMessage(), e));
    }
}
