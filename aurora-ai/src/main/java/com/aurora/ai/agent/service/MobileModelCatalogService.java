package com.aurora.ai.agent.service;

import com.aurora.ai.agent.billing.BillingCalculator;
import com.aurora.ai.agent.dto.MobileModelVO;
import com.aurora.ai.domain.SysAiModel;
import com.aurora.ai.mapper.SysAiModelMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MobileModelCatalogService {
    private final SysAiModelMapper models;
    private final BillingCalculator billing;

    public Map<String, List<MobileModelVO>> catalog() {
        List<SysAiModel> active = models.selectList(new LambdaQueryWrapper<SysAiModel>()
                .eq(SysAiModel::getIsActive, 1).in(SysAiModel::getModelType, "chat", "image")
                .orderByDesc(SysAiModel::getIsDefault).orderByAsc(SysAiModel::getSortOrder)
                .orderByAsc(SysAiModel::getId));
        List<MobileModelVO> available = active.stream().filter(MobileModelCatalogService::configured)
                .filter(model -> !"image".equals(model.getModelType()) || supportsImageProvider(model.getProvider()))
                .map(this::toPublicModel).toList();
        return Map.of("chatModels", available.stream().filter(model -> "chat".equals(model.getModelType())).toList(),
                "imageModels", available.stream().filter(model -> "image".equals(model.getModelType())).toList());
    }

    public static boolean supportsImageProvider(String provider) {
        return provider != null && Set.of("yunwu", "gptsapi", "luxai").contains(provider.toLowerCase(java.util.Locale.ROOT));
    }

    public static boolean supportsReferences(String provider) {
        return "yunwu".equalsIgnoreCase(provider) || "luxai".equalsIgnoreCase(provider);
    }

    public static List<String> csv(String value) {
        return value == null || value.isBlank() ? List.of() : Arrays.stream(value.split(","))
                .map(String::trim).filter(item -> !item.isBlank()).distinct().toList();
    }

    private static boolean configured(SysAiModel model) {
        if (model.getApiKey() == null || model.getApiKey().isBlank()
                || model.getBaseUrl() == null || model.getBaseUrl().isBlank()
                || model.getModelDeployment() == null || model.getModelDeployment().isBlank()) return false;
        if ("chat".equals(model.getModelType())) {
            return model.getBeanPerMtokenInput() != null && model.getBeanPerMtokenInput() >= 0
                    && model.getBeanPerMtokenOutput() != null && model.getBeanPerMtokenOutput() >= 0;
        }
        List<String> resolutions = csv(model.getSupportedResolutions());
        return !resolutions.isEmpty() && resolutions.stream().allMatch(resolution -> {
            Long price = switch (resolution.toUpperCase(java.util.Locale.ROOT)) {
                case "1K" -> model.getBeanPerImage1k();
                case "2K" -> model.getBeanPerImage2k();
                case "4K" -> model.getBeanPerImage4k();
                default -> null;
            };
            if (price == null) price = model.getBeanPerImage();
            return price != null && price >= 0;
        });
    }

    private MobileModelVO toPublicModel(SysAiModel model) {
        MobileModelVO result = new MobileModelVO();
        result.setModelId(model.getId());
        result.setModelName(model.getModelName());
        result.setModelDeployment(model.getModelDeployment());
        result.setProvider(model.getProvider());
        result.setModelType(model.getModelType());
        result.setIsDefault(Integer.valueOf(1).equals(model.getIsDefault()));
        result.setResolutions(csv(model.getSupportedResolutions()));
        result.setAspectRatios(csv(model.getSupportedAspectRatios()));
        result.setDefaultResolution(model.getDefaultResolution());
        Map<String, Long> prices = new LinkedHashMap<>();
        if ("image".equals(model.getModelType())) {
            for (String resolution : result.getResolutions()) prices.put(resolution, billing.imagePrice(model, resolution));
        }
        result.setBeanCosts(prices);
        result.setBeanPerMtokenInput(model.getBeanPerMtokenInput());
        result.setBeanPerMtokenInputCached(model.getBeanPerMtokenInputCached() == null
                ? model.getBeanPerMtokenInput() : model.getBeanPerMtokenInputCached());
        result.setBeanPerMtokenOutput(model.getBeanPerMtokenOutput());
        result.setSupportsReferenceImages("image".equals(model.getModelType()) && supportsReferences(model.getProvider()));
        result.setMaxReferenceImages(result.isSupportsReferenceImages() ? 9 : 0);
        return result;
    }
}
