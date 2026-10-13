package com.aurora.ai.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/** 移动端生成页可选参数。 */
@Data
@AllArgsConstructor
public class GenerationOptionsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<String> supportedResolutions;

    private String defaultResolution;

    private Map<String, Long> beanCosts;
}
