package com.aurora.ai.controller;

import com.aurora.ai.domain.SysAiModel;
import com.aurora.ai.provider.GeminiVisionClient;
import com.aurora.ai.provider.ResponsesApiClient;
import com.aurora.ai.provider.LuxaiImageClient;
import com.aurora.ai.provider.LuxaiImageSize;
import com.aurora.ai.agent.domain.GenerationTask;
import com.aurora.ai.agent.mapper.GenerationTaskMapper;
import com.aurora.ai.service.AiFactory;
import com.aurora.ai.service.impl.AiServiceImpl;
import com.aurora.ai.mapper.SysAiModelMapper;
import com.aurora.common.core.annotation.Log;
import com.aurora.common.core.domain.Result;
import com.aurora.common.core.enums.BusinessType;
import com.aurora.framework.utils.EncryptUtils;
import com.aurora.framework.annotation.RateLimiter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * AI 模型管理控制器
 */
@RestController
@RequestMapping("/ai/model")
@RequiredArgsConstructor
public class SysAiModelController {

    private final SysAiModelMapper sysAiModelMapper;
    private final AiFactory aiFactory;
    private final EncryptUtils encryptUtils;
    private final GeminiVisionClient geminiVisionClient;
    private final ResponsesApiClient responsesApiClient;
    private final LuxaiImageClient luxaiImageClient;
    private final GenerationTaskMapper generationTasks;

    /**
     * 分页查询模型列表
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('ai:model:list')")
    public Result<Page<SysAiModel>> list(@RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String modelName,
            @RequestParam(required = false) String modelType) {
        Page<SysAiModel> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysAiModel> wrapper = new LambdaQueryWrapper<>();
        if (modelName != null && !modelName.isEmpty()) {
            wrapper.like(SysAiModel::getModelName, modelName);
        }
        if (modelType != null && !modelType.isEmpty()) {
            wrapper.eq(SysAiModel::getModelType, modelType);
        }
        wrapper.orderByAsc(SysAiModel::getSortOrder);
        Page<SysAiModel> result = sysAiModelMapper.selectPage(page, wrapper);

        // 对返回结果中的 API Key 进行脱敏显示
        for (SysAiModel model : result.getRecords()) {
            if (model.getApiKey() != null && !model.getApiKey().isEmpty()) {
                // 先解密再脱敏
                String decrypted = encryptUtils.decrypt(model.getApiKey());
                model.setApiKey(EncryptUtils.mask(decrypted));
            }
            // SecretKey（可灵等双密钥模型）同样先解密再脱敏
            if (model.getSecretKey() != null && !model.getSecretKey().isEmpty()) {
                String decryptedSk = encryptUtils.decrypt(model.getSecretKey());
                model.setSecretKey(EncryptUtils.mask(decryptedSk));
            }
        }

        return Result.success(result);
    }

    /**
     * 获取模型详情
     */
    @GetMapping(value = "/{id}")
    @PreAuthorize("@ss.hasPermi('ai:model:query')")
    public Result<SysAiModel> getInfo(@PathVariable("id") Long id) {
        SysAiModel model = sysAiModelMapper.selectById(id);
        if (model != null && model.getApiKey() != null) {
            // 脱敏显示
            String decrypted = encryptUtils.decrypt(model.getApiKey());
            model.setApiKey(EncryptUtils.mask(decrypted));
        }
        if (model != null && model.getSecretKey() != null && !model.getSecretKey().isEmpty()) {
            // SecretKey 脱敏显示
            String decryptedSk = encryptUtils.decrypt(model.getSecretKey());
            model.setSecretKey(EncryptUtils.mask(decryptedSk));
        }
        return Result.success(model);
    }

    /**
     * 新增 AI 模型
     */
    @Log(title = "模型配置", businessType = BusinessType.INSERT, isSaveRequestData = false)
    @PostMapping
    @PreAuthorize("@ss.hasPermi('ai:model:add')")
    public Result<Void> add(@RequestBody SysAiModel sysAiModel) {
        if (sysAiModel.getDisplayTier() == null) sysAiModel.setDisplayTier("中");
        String validationError = validateModelConfiguration(sysAiModel);
        if (validationError != null) {
            return Result.error(validationError);
        }
        if (sysAiModel.getApiKey() == null) sysAiModel.setApiKey("");
        // 加密 API Key 后存储
        if (sysAiModel.getApiKey() != null && !sysAiModel.getApiKey().isEmpty()) {
            sysAiModel.setApiKey(encryptUtils.encrypt(sysAiModel.getApiKey()));
        }
        // SecretKey 同样加密后存储
        if (sysAiModel.getSecretKey() != null && !sysAiModel.getSecretKey().isEmpty()) {
            sysAiModel.setSecretKey(encryptUtils.encrypt(sysAiModel.getSecretKey()));
        }
        sysAiModel.setCreateTime(LocalDateTime.now());
        sysAiModelMapper.insert(sysAiModel);
        aiFactory.clearCache();
        return Result.success();
    }

    /**
     * 修改 AI 模型
     */
    @Log(title = "模型配置", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PutMapping
    @PreAuthorize("@ss.hasPermi('ai:model:edit')")
    public Result<Void> edit(@RequestBody SysAiModel sysAiModel) {
        SysAiModel effective = sysAiModelMapper.selectById(sysAiModel.getId());
        if (effective == null) return Result.error("模型不存在");
        String storedKey = effective.getApiKey();
        BeanWrapperImpl properties = new BeanWrapperImpl(sysAiModel);
        String[] omitted = Arrays.stream(properties.getPropertyDescriptors())
                .filter(property -> properties.getPropertyValue(property.getName()) == null)
                .map(java.beans.PropertyDescriptor::getName).toArray(String[]::new);
        BeanUtils.copyProperties(sysAiModel, effective, omitted);
        if (sysAiModel.getApiKey() != null && sysAiModel.getApiKey().contains("****")) effective.setApiKey(storedKey);
        String validationError = validateModelConfiguration(effective);
        if (validationError != null) {
            return Result.error(validationError);
        }
        if ("gpt-6.1-sol".equalsIgnoreCase(effective.getModelDeployment())) {
            sysAiModel.setApiProtocol(effective.getApiProtocol());
        }
        // 如果 API Key 是脱敏后的格式（包含 ****），则不更新 API Key
        if (sysAiModel.getApiKey() != null && sysAiModel.getApiKey().contains("****")) {
            // 不更新 API Key，保留原值
            sysAiModel.setApiKey(null);
        } else if (sysAiModel.getApiKey() != null && !sysAiModel.getApiKey().isEmpty()) {
            // 新的 API Key，加密后存储
            sysAiModel.setApiKey(encryptUtils.encrypt(sysAiModel.getApiKey()));
        }
        // SecretKey 同款链路：脱敏值跳过更新，否则非空加密后存储
        if (sysAiModel.getSecretKey() != null && sysAiModel.getSecretKey().contains("****")) {
            sysAiModel.setSecretKey(null);
        } else if (sysAiModel.getSecretKey() != null && !sysAiModel.getSecretKey().isEmpty()) {
            sysAiModel.setSecretKey(encryptUtils.encrypt(sysAiModel.getSecretKey()));
        }
        sysAiModel.setUpdateTime(LocalDateTime.now());
        sysAiModelMapper.updateById(sysAiModel);
        aiFactory.clearCache();
        return Result.success();
    }

    /** 图片模型必须为每个已启用分辨率配置单价，默认分辨率也必须属于启用集合。 */
    private String validateImageResolutionBilling(SysAiModel model) {
        if (model == null || !"image".equalsIgnoreCase(model.getModelType())) {
            return null;
        }
        List<String> supported = model.getSupportedResolutions() == null
                ? List.of()
                : Arrays.stream(model.getSupportedResolutions().split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .map(value -> value.toUpperCase(Locale.ROOT))
                        .distinct()
                        .toList();
        if (supported.isEmpty()) {
            return "请至少启用一个分辨率";
        }
        if (model.getDefaultResolution() == null
                || !supported.contains(model.getDefaultResolution().trim().toUpperCase(Locale.ROOT))) {
            return "默认分辨率必须属于已启用选项";
        }
        for (String resolution : supported) {
            if (!List.of("1K", "2K", "4K").contains(resolution)) return "不支持的分辨率: " + resolution;
            Long price = switch (resolution) {
                case "1K" -> model.getBeanPerImage1k();
                case "2K" -> model.getBeanPerImage2k();
                case "4K" -> model.getBeanPerImage4k();
                default -> null;
            };
            if ((price == null && Integer.valueOf(1).equals(model.getIsActive())) || (price != null && price < 0)) {
                return "请配置 " + resolution + " 每张消耗豆子";
            }
        }
        return null;
    }

    private String validateModelConfiguration(SysAiModel model) {
        if (model == null) return "模型配置不能为空";
        if (model.getDisplayTier() != null && !List.of("轻", "中", "高").contains(model.getDisplayTier())) {
            return "小程序档位只能选择轻、中或高";
        }
        if ("gpt-6.1-sol".equalsIgnoreCase(model.getModelDeployment())) {
            if (model.getApiProtocol() == null || model.getApiProtocol().isBlank()) model.setApiProtocol("responses");
            if (!"responses".equalsIgnoreCase(model.getApiProtocol())) return "GPT-6.1 Sol 需使用 Responses 协议";
        }
        if (Integer.valueOf(1).equals(model.getIsActive())) {
            if (model.getApiKey() == null || model.getApiKey().isBlank()) return "启用模型前请配置 API Key";
            if (model.getBaseUrl() == null || model.getBaseUrl().isBlank()
                    || model.getModelDeployment() == null || model.getModelDeployment().isBlank()) return "请配置模型地址和模型 ID";
            if ("chat".equals(model.getModelType()) && (model.getBeanPerMtokenInput() == null
                    || model.getBeanPerMtokenOutput() == null)) return "启用对话模型前请配置输入和输出收费标准";
        }
        for (Long price : Arrays.asList(model.getBeanPerMtokenInput(), model.getBeanPerMtokenInputCached(),
                model.getBeanPerMtokenOutput(), model.getBeanPerImage(), model.getBeanPerImage1k(),
                model.getBeanPerImage2k(), model.getBeanPerImage4k())) {
            if (price != null && price < 0) return "模型收费标准不能为负数";
        }
        String error = validateImageResolutionBilling(model);
        if (error != null) return error;
        if ("image".equals(model.getModelType()) && "luxai".equalsIgnoreCase(model.getProvider())) {
            try {
                model.setImageQuality(LuxaiImageClient.quality(model.getImageQuality()));
                List<String> ratios = com.aurora.ai.agent.service.MobileModelCatalogService.csv(model.getSupportedAspectRatios());
                if (ratios.isEmpty()) return "请配置 Luxai 画幅比例";
                for (String resolution : model.getSupportedResolutions().split(",")) {
                    for (String ratio : ratios) LuxaiImageSize.of(resolution.trim(), ratio);
                }
            } catch (com.aurora.common.core.exception.ServiceException invalid) {
                return invalid.getMessage();
            }
        }
        return null;
    }

    /**
     * 设为默认模型(同 model_type 唯一):先把同类型全部清默认,再把本条置默认。
     * 出图选型(对话内 generate_images 与生成页 /generate)会优先取默认图像模型。
     */
    @Log(title = "模型配置", businessType = BusinessType.UPDATE)
    @PutMapping("/setDefault/{id}")
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("@ss.hasPermi('ai:model:edit')")
    public Result<Void> setDefault(@PathVariable("id") Long id) {
        SysAiModel model = sysAiModelMapper.selectById(id);
        if (model == null) {
            return Result.error("模型不存在");
        }
        if (!Integer.valueOf(1).equals(model.getIsActive())) return Result.error("请先启用并验证模型，再设为默认");
        String validation = validateModelConfiguration(model);
        if (validation != null) return Result.error(validation);
        // 1. 同 model_type 下全部清默认(唯一性)
        LambdaUpdateWrapper<SysAiModel> clear = new LambdaUpdateWrapper<>();
        clear.eq(SysAiModel::getModelType, model.getModelType())
                .set(SysAiModel::getIsDefault, 0);
        sysAiModelMapper.update(null, clear);
        // 2. 本条置默认
        LambdaUpdateWrapper<SysAiModel> set = new LambdaUpdateWrapper<>();
        set.eq(SysAiModel::getId, id)
                .set(SysAiModel::getIsDefault, 1);
        sysAiModelMapper.update(null, set);
        if ("chat".equals(model.getModelType()) && "gpt-6.1-sol".equalsIgnoreCase(model.getModelDeployment())) {
            sysAiModelMapper.update(null, new LambdaUpdateWrapper<SysAiModel>()
                    .eq(SysAiModel::getModelType, "chat").eq(SysAiModel::getModelDeployment, "gpt-5.6-sol")
                    .set(SysAiModel::getIsActive, 0).set(SysAiModel::getIsDefault, 0));
        }
        aiFactory.clearCache();
        return Result.success();
    }

    /**
     * 删除 AI 模型
     */
    @Log(title = "模型配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPermi('ai:model:remove')")
    public Result<Void> remove(@PathVariable Long[] ids) {
        if (generationTasks.selectCount(new LambdaQueryWrapper<GenerationTask>()
                .in(GenerationTask::getModelId, List.of(ids))) > 0) {
            return Result.error("模型关联已有出图任务，请停用模型以保留任务和账务记录");
        }
        sysAiModelMapper.deleteBatchIds(List.of(ids));
        aiFactory.clearCache();
        return Result.success();
    }

    /**
     * 测试模型连通性
     */
    @PostMapping("/test")
    @PreAuthorize("@ss.hasPermi('ai:model:query')")
    @RateLimiter(key = "admin:ai:use", time = 60, count = 20,
            limitType = RateLimiter.LimitType.USER, message = "AI 请求过于频繁，请稍后再试")
    public Result<String> testConnection(@RequestBody SysAiModel sysAiModel) {
        try {
            // 处理 API Key：如果是脱敏格式，从数据库获取并解密
            String apiKey = resolveApiKey(sysAiModel);
            if (apiKey == null || apiKey.isEmpty()) {
                return Result.error("API Key 不能为空");
            }
            // 使用解密后的 API Key 更新模型对象
            sysAiModel.setApiKey(apiKey);

            String type = sysAiModel.getModelType() != null ? sysAiModel.getModelType().toLowerCase() : "chat";

            if ("vision".equals(type)) {
                // 使用与 Agent 相同的 Gemini 原生协议和真实内联图片，避免纯文本 ping 假阳性。
                geminiVisionClient.testConnection(sysAiModel);
                return Result.success("你好，我的 " + sysAiModel.getModelName() + "，多模态图片识别对接成功✅");
            } else if ("chat".equals(type)) {
                if ("responses".equalsIgnoreCase(sysAiModel.getApiProtocol())) {
                    responsesApiClient.testConnection(sysAiModel, apiKey);
                } else {
                    org.springframework.ai.chat.model.ChatModel chatModel = aiFactory.createChatModel(sysAiModel);
                    org.springframework.ai.chat.client.ChatClient.builder(chatModel)
                            .build()
                            .prompt()
                            .user("ping")
                            .call()
                            .content();
                }
                return Result.success("你好，我的 " + sysAiModel.getModelName() + "，模型对接成功✅");
            } else if ("image".equals(type)) {
                String baseUrl = sysAiModel.getBaseUrl();

                if ("luxai".equalsIgnoreCase(sysAiModel.getProvider())) {
                    luxaiImageClient.textToImage(baseUrl, apiKey, sysAiModel.getModelDeployment(),
                            "A plain white ceramic cup on a neutral background", sysAiModel.getDefaultResolution(),
                            "1:1", sysAiModel.getImageQuality());
                    return Result.success("Luxai 图像模型调用成功");
                }

                // 阿里 DashScope 原生处理
                if (baseUrl.contains("dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation")) {
                    org.springframework.web.client.RestClient.create()
                            .post()
                            .uri(baseUrl)
                            .header("Authorization", "Bearer " + apiKey)
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .body(java.util.Map.of(
                                    "model", sysAiModel.getModelDeployment(),
                                    "input", java.util.Map.of(
                                            "messages", java.util.List.of(java.util.Map.of(
                                                    "role", "user",
                                                    "content", java.util.List.of(java.util.Map.of("text", "ping")))))))
                            .retrieve()
                            .toBodilessEntity();
                } else {
                    org.springframework.ai.image.ImageModel imageModel = aiFactory.createImageModel(sysAiModel);
                    // 使用简单的 Prompt 进行连通性测试
                    imageModel.call(new org.springframework.ai.image.ImagePrompt("test ping",
                            org.springframework.ai.openai.OpenAiImageOptions.builder()
                                    .model(sysAiModel.getModelDeployment())
                                    .N(1)
                                    .height(1024)
                                    .width(1024)
                                    .build()));
                }
                return Result.success("图像模型 " + sysAiModel.getModelName() + " 对接成功✅");
            } else if ("video".equals(type)) {
                // 可灵等视频模型用 AccessKey(apiKey) + SecretKey 双密钥现场生成 JWT 鉴权。
                // 视频生成耗时较长，连通性测试只做轻量校验：baseUrl/AK/SK 非空 + JWT 生成不抛异常即视为配置就绪，不真实提交视频任务。
                if (sysAiModel.getBaseUrl() == null || sysAiModel.getBaseUrl().isEmpty()) {
                    return Result.error("Base URL 不能为空");
                }
                String secretKey = resolveSecretKey(sysAiModel);
                if (secretKey == null || secretKey.isEmpty()) {
                    return Result.error("Secret Key 不能为空");
                }
                com.aurora.ai.provider.KlingAuth.INSTANCE.getToken(apiKey, secretKey);
                return Result.success("视频模型 " + sysAiModel.getModelName() + " 配置就绪✅");
            } else {
                return Result.success(sysAiModel.getModelName() + " (" + type + ") 配置已记录");
            }
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null)
                msg = "未知错误";
            return Result.error("部署失败 [终端:" + sysAiModel.getModelDeployment() + "]: " +
                    (msg.length() > 300 ? msg.substring(0, 300) + "..." : msg));
        }
    }

    /**
     * 解析 API Key
     * 1. 如果是脱敏格式（包含 ****），从数据库获取并解密
     * 2. 如果是加密格式（ENC: 开头），直接解密
     * 3. 否则直接返回（新输入的明文 Key）
     */
    private String resolveApiKey(SysAiModel sysAiModel) {
        String apiKey = sysAiModel.getApiKey();

        // 情况1：脱敏格式，需要从数据库获取
        if (apiKey != null && apiKey.contains("****") && sysAiModel.getId() != null) {
            SysAiModel dbModel = sysAiModelMapper.selectById(sysAiModel.getId());
            if (dbModel != null && dbModel.getApiKey() != null) {
                return encryptUtils.decrypt(dbModel.getApiKey());
            }
            return null;
        }

        // 情况2：加密格式（ENC: 旧 CBC / ENC2: 新 GCM），直接解密
        if (apiKey != null && (apiKey.startsWith("ENC:") || apiKey.startsWith("ENC2:"))) {
            return encryptUtils.decrypt(apiKey);
        }

        // 情况3：明文（新输入的 Key）
        return apiKey;
    }

    /**
     * 解析 SecretKey，逻辑与 resolveApiKey 完全对称：
     * 1. 脱敏格式（含 ****）从数据库取出再解密
     * 2. 加密格式（ENC:/ENC2: 前缀）直接解密
     * 3. 否则视为新输入的明文，直接返回
     */
    private String resolveSecretKey(SysAiModel sysAiModel) {
        String secretKey = sysAiModel.getSecretKey();

        if (secretKey != null && secretKey.contains("****") && sysAiModel.getId() != null) {
            SysAiModel dbModel = sysAiModelMapper.selectById(sysAiModel.getId());
            if (dbModel != null && dbModel.getSecretKey() != null) {
                return encryptUtils.decrypt(dbModel.getSecretKey());
            }
            return null;
        }

        if (secretKey != null && (secretKey.startsWith("ENC:") || secretKey.startsWith("ENC2:"))) {
            return encryptUtils.decrypt(secretKey);
        }

        return secretKey;
    }
}
