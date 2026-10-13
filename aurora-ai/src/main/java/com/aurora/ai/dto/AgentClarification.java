package com.aurora.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** Agent 面向用户的一次结构化澄清。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentClarification implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 后端生成的精确澄清范围，客户端只负责原样回传。 */
    private String scopeId;

    /** 稳定问题类型，用于防止跨轮重复追问。 */
    private String key;

    /** 面向普通用户的一句话问题。 */
    private String question;

    /** 0~3 个快捷选项；为空时允许用户直接输入。 */
    private List<Option> options;

    /** 推荐选项 id；用户回答“随便/按推荐”时采用。 */
    private String recommendedOptionId;

    /** 是否允许自由文本回答。 */
    private Boolean allowFreeText;

    /** KEEP_APPEND / REPLACE，控制回答时新图片如何进入原映射。 */
    private String imageUpdatePolicy;

    /** imageUpdatePolicy=REPLACE 时被替换的稳定附件 id。 */
    private String replaceAttachmentId;

    /** 当前澄清轮数，从 1 开始。 */
    private Integer round;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Option implements Serializable {

        private static final long serialVersionUID = 1L;

        private String id;
        private String label;
        private Boolean recommended;
    }
}
