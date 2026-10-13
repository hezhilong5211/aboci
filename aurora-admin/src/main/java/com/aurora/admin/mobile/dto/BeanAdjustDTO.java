package com.aurora.admin.mobile.dto;

import lombok.Data;

/**
 * 后台手动调整豆子入参
 */
@Data
public class BeanAdjustDTO {

    /**
     * 目标用户ID
     */
    private Long userId;

    /**
     * 调整数量(正加负减,≠0)
     */
    private Long amount;

    /**
     * 备注
     */
    private String remark;
}
