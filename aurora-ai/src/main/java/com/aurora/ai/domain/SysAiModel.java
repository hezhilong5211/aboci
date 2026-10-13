package com.aurora.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI Model Configuration Entity
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_ai_model")
public class SysAiModel implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * Display Name
     */
    private String modelName;

    /**
     * Encrypted API Key
     */
    private String apiKey;

    /**
     * 加密的SecretKey(仅可灵等双密钥模型用，出参脱敏)
     */
    private String secretKey;

    /**
     * Base URL
     */
    private String baseUrl;

    /**
     * Model Deployment Name (e.g., deepseek-chat)
     */
    private String modelDeployment;

    /**
     * Temperature
     */
    private Double temperature;

    /**
     * 单次模型输出 token 上限，不代表模型总上下文上限。
     */
    private Integer maxTokens;

    /**
     * Provider (openai, azure, etc.)
     */
    private String provider;

    /**
     * Model Type (chat, vision, image, audio, video)
     */
    private String modelType;

    /**
     * 接口协议(chat_completions / responses)
     */
    private String apiProtocol;

    /**
     * 允许的画质集合，逗号分隔(仅图像模型)，如 1K,2K
     */
    private String supportedResolutions;

    /**
     * 允许的画幅比例集合，逗号分隔(仅图像模型)，如 1:1,4:3,3:4
     */
    private String supportedAspectRatios;

    /**
     * 默认分辨率(仅图像模型，移动端不传分辨率时用此值，须在 supportedResolutions 内)，如 1K
     */
    private String defaultResolution;

    /** Fixed supplier quality for the model; image pricing is configured for this quality. */
    private String imageQuality;

    /**
     * 文本-每百万token输入豆子单价(缓存未命中)
     */
    private Long beanPerMtokenInput;

    /**
     * 文本-每百万token输入豆子单价(缓存命中；为空时回退普通输入单价)
     */
    private Long beanPerMtokenInputCached;

    /**
     * 文本-每百万token输出豆子单价
     */
    private Long beanPerMtokenOutput;

    /**
     * 图片-每张豆子单价
     */
    private Long beanPerImage;

    /**
     * 图片-1K 每张豆子单价
     */
    @TableField("bean_per_image_1k")
    private Long beanPerImage1k;

    /**
     * 图片-2K 每张豆子单价
     */
    @TableField("bean_per_image_2k")
    private Long beanPerImage2k;

    /**
     * 图片-4K 每张豆子单价
     */
    @TableField("bean_per_image_4k")
    private Long beanPerImage4k;

    /**
     * 视频-每秒豆子单价(本期仅配置不实扣)
     */
    private Long beanPerSecond;

    /**
     * Is Active (1: yes, 0: no)
     */
    private Integer isActive;

    /**
     * 是否默认(同 model_type 唯一,1默认/0否);出图选型优先取默认图像模型
     */
    private Integer isDefault;

    /**
     * Sort Order
     */
    private Integer sortOrder;

    /**
     * Create Time
     */
    private LocalDateTime createTime;

    /**
     * Update Time
     */
    private LocalDateTime updateTime;
}
