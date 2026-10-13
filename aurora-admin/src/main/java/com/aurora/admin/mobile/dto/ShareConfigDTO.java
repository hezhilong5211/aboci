package com.aurora.admin.mobile.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 小程序分享配置。
 *
 * 仅暴露系统设置页允许维护的两个字段，避免通过通用参数接口修改其他系统配置。
 */
@Data
public class ShareConfigDTO {

    /** 分享卡片标题。 */
    @Size(max = 30, message = "分享标题不能超过30个字符")
    private String title;

    /** 分享卡片图片地址。 */
    @Size(max = 500, message = "分享图片地址不能超过500个字符")
    private String image;
}
