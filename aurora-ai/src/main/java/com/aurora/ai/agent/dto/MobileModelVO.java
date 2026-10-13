package com.aurora.ai.agent.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/** Public catalogue: deliberately contains no endpoint or credential fields. */
@Data
public class MobileModelVO {
    private Long modelId;
    private String modelName;
    private String modelDeployment;
    private String provider;
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
