package com.aurora.ai.agent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 独立出图端点 POST /api/mobile/agent/generate 入参(不含 userId,防越权)。
 *
 * <p>{@code refImages} 为空 → 文生图;非空(1~9 张)→ 图生图(走云雾 /v1/images/edits)。</p>
 */
@Data
public class GenerateImageRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 出图提示词(必填) */
    private String prompt;

    /** 画幅比例(可空,如 1:1 / 16:9 / 9:16 / 3:4;空走默认) */
    private String aspectRatio;

    /** 分辨率档位，必须属于后台默认图像模型勾选项 */
    private String resolution;

    /** 张数(1~4) */
    private Integer n;

    /** 图生图参考图 /profile URL 列表(可空,1~9 张;空=文生图) */
    private List<String> refImages;
}
