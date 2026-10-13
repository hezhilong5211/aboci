package com.aurora.ai.agent.dto;

import java.io.Serializable;
import java.util.List;

/**
 * sourceType 固定 original；sourceId 为本次提交前已确定的稳定附件 ID。
 * attributes 为规划器统一命名的属性标签；usage 为 primary 或 detail。
 */
public record ImageReferenceBinding(String sourceType, String sourceId,
                                    List<String> attributes, String usage) implements Serializable {

    private static final long serialVersionUID = 1L;
}
