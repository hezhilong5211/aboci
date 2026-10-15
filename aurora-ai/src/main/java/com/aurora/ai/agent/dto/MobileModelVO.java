package com.aurora.ai.agent.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/** Public catalogue: contains tier labels, never internal model names, suppliers or credentials. */
@Data
public class MobileModelVO {
    private Long modelId;
    /** Public tier label: 轻 / 中 / 高. */
    private String modelName;
    private String displayTier;
    private String modelType;
    private Boolean isDefault;
    private List<String> resolutions;
    private List<String> aspectRatios;
    private String defaultResolution;
    private Map<String, Long> beanCosts;
    private Long beanPerMtokenInput;
    private Long beanPerMtokenInputCached;
    private Long beanPerMtokenOutput;
    private boolean supportsReferenceImages;
    private int maxReferenceImages;
}
