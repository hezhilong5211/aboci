package com.aurora.admin.mobile.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 豆子充值套餐配置实体(到账豆数/价格/排序 + 上下架)
 * 价格 price 单位为元(DECIMAL),下单时元×100 转分;逻辑删仅从配置列表消失,历史订单快照不受影响。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("bean_recharge_package")
public class BeanRechargePackage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 套餐ID
     */
    @TableId(value = "package_id", type = IdType.AUTO)
    private Long packageId;

    /**
     * 套餐名称
     */
    private String name;

    /**
     * 微信虚拟支付普通道具ID(英文字母/数字/下划线,最长20位)
     */
    private String virtualProductId;

    /**
     * 到账豆数
     */
    private Long beans;

    /**
     * 价格(单位:元)
     */
    private BigDecimal price;

    /**
     * 排序(小在前)
     */
    private Integer sort;

    /**
     * 状态(0上架/1下架)
     */
    private String status;

    /**
     * 创建者
     */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新者
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 备注
     */
    private String remark;

    /**
     * 逻辑删除标记(0存在/2删除)
     */
    @TableLogic
    private String delFlag;
}
