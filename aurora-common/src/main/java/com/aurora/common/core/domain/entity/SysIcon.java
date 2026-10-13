package com.aurora.common.core.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 自定义图标表 sys_icon
 * 用于存储用户上传的PNG/SVG图标
 */
@Data
@NoArgsConstructor
@TableName("sys_icon")
public class SysIcon implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 图标ID */
    @TableId(value = "icon_id", type = IdType.AUTO)
    private Long iconId;

    /** 图标名称 */
    private String iconName;

    /** 图标存储路径 */
    private String iconPath;

    /** 图标类型: png/svg */
    private String iconType;

    /** 创建者（insert 时由 MetaObjectHandler 自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /** 创建时间（insert 时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新者（insert/update 时自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /** 更新时间（insert/update 时自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 删除标志（0正常 2已删除） */
    @TableLogic(value = "0", delval = "2")
    private String delFlag;
}
