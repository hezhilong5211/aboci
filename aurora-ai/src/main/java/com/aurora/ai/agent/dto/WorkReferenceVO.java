package com.aurora.ai.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 单张作品实际使用的参考图。
 */
@Data
@AllArgsConstructor
public class WorkReferenceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer index;
    private String sourceType;
    private Integer sourceSeq;
    private String url;
}
