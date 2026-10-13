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
import java.time.LocalDateTime;

/**
 * 案例/模板实体(案例=模板同一实体:案例页展示 / 生成页带入)。
 * params、result_media 以 String 原样存取 JSON,不强转对象——结构按 category 不同,本期由后台录入。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("template")
public class Template implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模板ID
     */
    @TableId(value = "template_id", type = IdType.AUTO)
    private Long templateId;

    /**
     * 分类(image图片/video视频)
     */
    private String category;

    /** 行业分类ID。 */
    private Long industryCategoryId;

    /** 行业分类名称，仅用于管理端和移动端展示。 */
    @TableField(exist = false)
    private String industryCategoryName;

    /**
     * 案例标题
     */
    private String title;

    /**
     * 封面图 URL
     */
    private String coverUrl;

    /**
     * 提示词文本(必填)
     */
    private String prompt;

    /**
     * 参数预设(JSON 字符串,原样存取)
     */
    private String params;

    /**
     * 效果媒体 URL 列表(JSON 数组字符串,原样存取)
     */
    private String resultMedia;

    /**
     * 图生图模板预置参考图(JSON 数组字符串,/profile URL,1~4 张);仅 image+img2img 用
     */
    private String referenceImages;

    /**
     * 标签(逗号分隔)
     */
    private String tags;

    /**
     * 使用次数(本期仅展示)
     */
    private Long useCount;

    /**
     * 排序(小在前)
     */
    private Integer sort;

    /**
     * 本周精选(1是/0否,后台从案例列表选最多5个,C端顶部轮播)
     */
    private Integer isFeatured;

    /**
     * 上下架(0上架/1下架)
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
