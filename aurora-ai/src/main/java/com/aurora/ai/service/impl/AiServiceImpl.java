package com.aurora.ai.service.impl;

import com.aurora.ai.domain.SysAiHistory;
import com.aurora.ai.domain.SysAiModel;
import com.aurora.ai.mapper.SysAiHistoryMapper;
import com.aurora.ai.mapper.SysAiModelMapper;
import com.aurora.ai.provider.GptsImageClient;
import com.aurora.ai.provider.KlingVideoClient;
import com.aurora.ai.provider.YunwuImageClient;
import com.aurora.ai.provider.support.ImageConcurrencyGate;
import com.aurora.ai.service.AiFactory;
import com.aurora.ai.service.AiService;
import com.aurora.ai.service.RetryableAiService;
import com.aurora.common.core.exception.ServiceException;
import com.aurora.framework.mq.ApiRateLimiter;
import com.aurora.framework.utils.EncryptUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final AiFactory aiFactory;
    private final SysAiModelMapper sysAiModelMapper;
    private final SysAiHistoryMapper sysAiHistoryMapper;
    private final RetryableAiService retryableAiService;
    private final YunwuImageClient yunwuImageClient;
    private final GptsImageClient gptsImageClient;
    private final KlingVideoClient klingVideoClient;
    private final EncryptUtils encryptUtils;
    private final ApiRateLimiter apiRateLimiter;
    private final ImageConcurrencyGate imageConcurrencyGate;

    /** 上传目录，落盘后经 /profile/** 静态映射对外访问，与 application.yml 一致 */
    @Value("${spring.profile.upload-path:./uploadPath}")
    private String uploadPath;

    private SysAiModel getModelConfig(Long modelId) {
        if (modelId != null) {
            return sysAiModelMapper.selectById(modelId);
        }
        // 如果未指定 modelId，则回退到默认激活模型
        LambdaQueryWrapper<SysAiModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiModel::getIsActive, 1)
               .orderByAsc(SysAiModel::getSortOrder)
               .last("LIMIT 1");
        return sysAiModelMapper.selectOne(wrapper);
    }

    /**
     * 保存对话/图像生成历史记录
     */
    private void saveHistory(String sessionId, SysAiModel model, String role, String content, String msgType, String prompt) {
        SysAiHistory history = new SysAiHistory();
        history.setSessionId(sessionId);
        if (model != null) {
            history.setModelId(model.getId());
            history.setModelName(model.getModelName());
        }
        history.setRole(role);
        history.setContent(content);
        history.setMsgType(msgType);
        history.setPrompt(prompt);
        history.setCreateTime(LocalDateTime.now());
        sysAiHistoryMapper.insert(history);
    }

    @Override
    public String chat(String sessionId, Long modelId, String message) {
        SysAiModel config = getModelConfig(modelId);
        if (config == null) throw new RuntimeException("No AI model configured");

        if ("image".equalsIgnoreCase(config.getModelType())) {
            throw new RuntimeException("所选模型 [" + config.getModelName() + "] 是图像模型，不支持文本对话。请点击下方图片图标切换模式。");
        }

        acquireChatPermit();
        try {
            // 保存用户消息
            saveHistory(sessionId, config, "user", message, "text", null);

            // 经 RetryableAiService 调用，自动重试网络抖动/限流，重试耗尽由其 @Recover 降级（TD-AI-03）
            String response = retryableAiService.callWithRetry(config, message);

            // 保存助手消息
            saveHistory(sessionId, config, "assistant", response, "text", null);
            return response;
        } finally {
            apiRateLimiter.release("chat");
        }
    }

    @Override
    public List<SysAiHistory> getHistory(String sessionId) {
        LambdaQueryWrapper<SysAiHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiHistory::getSessionId, sessionId)
               .orderByAsc(SysAiHistory::getCreateTime);
        return sysAiHistoryMapper.selectList(wrapper);
    }

    @Override
    public String generateImage(String sessionId, Long modelId, String prompt, String resolution, String aspectRatio) {
        SysAiModel config = getModelConfig(modelId);
        if (config == null) throw new RuntimeException("No AI model configured");

        if (!"image".equalsIgnoreCase(config.getModelType())) {
            throw new RuntimeException("所选模型 [" + config.getModelName() + "] 不支持图像生成。请切换至具备 Draw/Image 类型的模型。");
        }

        // 按模型配置的允许集校验画质/画幅(未配置则不限制，沿用原值由 client 兜底)
        // 画质：移动端不传时优先用模型配置的默认分辨率，其次允许集首项
        resolution = resolveResolution(resolution, config.getSupportedResolutions(), config.getDefaultResolution());
        aspectRatio = resolveAllowedValue("画幅比例", aspectRatio, config.getSupportedAspectRatios());

        String provider = config.getProvider();
        acquireImagePermit(provider);
        try {
            // 保存用户请求
            saveHistory(sessionId, config, "user", prompt, "text", null);

            String imageUrl;
            // 云雾 / GPTS 返回图片字节，需落盘换取可访问 URL；其它供应商（openai 等）走原有 createImageModel 路径直接返回 url
            if ("yunwu".equalsIgnoreCase(provider)) {
                String apiKey = encryptUtils.decrypt(config.getApiKey());
                byte[] image = yunwuImageClient.textToImage(config.getBaseUrl(), apiKey,
                        config.getModelDeployment(), prompt, resolution, aspectRatio);
                imageUrl = saveImageToProfile(image);
            } else if ("gptsapi".equalsIgnoreCase(provider)) {
                String apiKey = encryptUtils.decrypt(config.getApiKey());
                byte[] image = gptsImageClient.textToImage(config.getBaseUrl(), apiKey,
                        config.getModelDeployment(), prompt, resolution, aspectRatio);
                imageUrl = saveImageToProfile(image);
            } else {
                ImageModel imageModel = aiFactory.createImageModel(config);
                ImageResponse response = imageModel.call(new ImagePrompt(prompt));
                imageUrl = response.getResult().getOutput().getUrl();
            }

            // 保存图像生成结果
            saveHistory(sessionId, config, "assistant", imageUrl, "image", prompt);
            return imageUrl;
        } finally {
            imageConcurrencyGate.release(provider);
        }
    }

    /**
     * 按模型配置的允许集校验图像生成参数。
     *
     * <p>规则：allowConfig 为逗号分隔字符串，去空白取非空项为允许集 allow。
     * allow 为空(未配置)时不校验，原样返回 value;allow 非空时：
     * value 为空白则回退为 allow 第一项作默认;否则 value 必须落在 allow 内，不在则抛 {@link ServiceException}。</p>
     *
     * @param label       参数中文名(画质 / 画幅比例)，用于错误提示
     * @param value       入参值
     * @param allowConfig 模型配置的允许集(逗号分隔，可空)
     * @return 校验通过(或回退默认)后的值
     */
    private String resolveAllowedValue(String label, String value, String allowConfig) {
        if (allowConfig == null || allowConfig.trim().isEmpty()) {
            return value;
        }
        List<String> allow = Arrays.stream(allowConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (allow.isEmpty()) {
            return value;
        }
        if (value == null || value.trim().isEmpty()) {
            return allow.get(0);
        }
        if (!allow.contains(value.trim())) {
            throw new ServiceException(label + " [" + value + "] 不在该模型允许范围内，允许值：" + allowConfig);
        }
        return value;
    }

    /**
     * 解析图像生成分辨率：入参为空时优先用模型配置的默认分辨率，其次允许集首项。
     *
     * <p>allow 为空(未配允许集)时不校验：入参非空原样返回，入参空则返回 defaultValue;
     * allow 非空时：入参空则优先取 defaultValue，无默认再取 allow 首项;入参非空必须命中 allow，否则抛异常。</p>
     *
     * @param value        入参分辨率(移动端可不传)
     * @param allowConfig  允许集(逗号分隔，可空)
     * @param defaultValue 模型配置的默认分辨率(可空)
     * @return 最终分辨率
     */
    private String resolveResolution(String value, String allowConfig, String defaultValue) {
        List<String> allow = (allowConfig == null || allowConfig.trim().isEmpty())
                ? List.of()
                : Arrays.stream(allowConfig.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        boolean blank = value == null || value.trim().isEmpty();
        if (allow.isEmpty()) {
            return blank ? defaultValue : value;
        }
        if (blank) {
            return (defaultValue != null && !defaultValue.trim().isEmpty()) ? defaultValue.trim() : allow.get(0);
        }
        if (!allow.contains(value.trim())) {
            throw new ServiceException("画质 [" + value + "] 不在该模型允许范围内，允许值：" + allowConfig);
        }
        return value;
    }

    @Override
    public String generateVideo(String sessionId, Long modelId, String prompt, Integer durationSec, String aspectRatio) {
        SysAiModel config = getModelConfig(modelId);
        if (config == null) throw new ServiceException("未配置 AI 模型");

        if (!"video".equalsIgnoreCase(config.getModelType())) {
            throw new ServiceException("所选模型 [" + config.getModelName() + "] 不是视频模型，无法生成视频。");
        }

        // 保存用户请求
        saveHistory(sessionId, config, "user", prompt, "text", null);

        // 可灵采用 AK/SK 双密钥：apiKey 存 AccessKey、secretKey 存 SecretKey，均加密入库，使用时解密
        String accessKey = encryptUtils.decrypt(config.getApiKey());
        String secretKey = encryptUtils.decrypt(config.getSecretKey());
        // durationSec 为空时传 0，由 client 兜底默认时长
        int duration = durationSec != null ? durationSec : 0;
        String videoUrl = klingVideoClient.textToVideo(config.getBaseUrl(), accessKey, secretKey,
                config.getModelDeployment(), prompt, duration, aspectRatio, null);

        // 保存视频生成结果
        saveHistory(sessionId, config, "assistant", videoUrl, "video", prompt);

        return videoUrl;
    }

    /**
     * 将图片字节落盘到上传目录，返回经 /profile/** 静态映射可访问的相对 URL。
     */
    private String saveImageToProfile(byte[] image) {
        File dir = new File(uploadPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String fileName = UUID.randomUUID() + ".png";
        File dest = new File(dir, fileName);
        try {
            Files.write(dest.toPath(), image);
        } catch (Exception e) {
            throw new ServiceException("图片落盘失败：" + e.getMessage(), e);
        }
        return "/profile/" + fileName;
    }

    @Override
    public Flux<String> streamChat(String message) {
        // 查找流式对话的默认激活模型（通常不需要在每一块返回时保存历史记录）
        LambdaQueryWrapper<SysAiModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiModel::getIsActive, 1)
               .orderByAsc(SysAiModel::getSortOrder)
               .last("LIMIT 1");
        SysAiModel config = sysAiModelMapper.selectOne(wrapper);
        if (config == null) throw new RuntimeException("No active AI model configured");

        return Flux.using(
                () -> {
                    acquireChatPermit();
                    return Boolean.TRUE;
                },
                ignored -> Flux.defer(() -> {
                        ChatModel chatModel = aiFactory.createChatModel(config);
                        return ChatClient.builder(chatModel).build().prompt()
                                .user(message)
                                .stream()
                                .content();
                }),
                ignored -> apiRateLimiter.release("chat"))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void acquireChatPermit() {
        try {
            if (!apiRateLimiter.acquireWithWaitNotice(null)) {
                throw new ServiceException("系统繁忙，请稍后重试", 503);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("请求已停止", 499, e);
        }
    }

    private void acquireImagePermit(String provider) {
        try {
            imageConcurrencyGate.acquire(provider);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("图片生成已停止", 499, e);
        }
    }
}
