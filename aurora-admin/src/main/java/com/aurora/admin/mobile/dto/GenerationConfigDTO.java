package com.aurora.admin.mobile.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 小程序 AI 生成固定配置。
 */
@Data
public class GenerationConfigDTO {

    /** 独立生成页面及提交接口是否启用。 */
    @NotNull(message = "AI 生成功能状态不能为空")
    private Boolean generationEnabled;

    /** 保存到相册的图片是否包含 AI 水印。 */
    @NotNull(message = "下载水印状态不能为空")
    private Boolean downloadWatermarkEnabled;
}
