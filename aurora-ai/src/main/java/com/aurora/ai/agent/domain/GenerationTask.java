package com.aurora.ai.agent.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 出图异步任务实体(业务主表,PRD §5.3)。
 * status 用 String 枚举值:CREATED/QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED(状态机见 PRD §六);
 * 状态/结果以本表为唯一真相源,Redis Streams 仅作工作队列;
 * result_urls 列存成功 URL 的 JSON 数组串(由 Service 用 ObjectMapper 序列化);
 * 任务流水,不做逻辑删,仅 create_time/update_time。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("generation_task")
public class GenerationTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * Redis 队列 taskId(UUID 去横线),唯一
     */
    private String taskId;

    /**
     * 所属用户(mobile_user.id,取自登录态)
     */
    private Long userId;

    /**
     * 对话内生图来源会话(可空)
     */
    private Long conversationId;

    /**
     * 触发任务的用户消息(可空；独立生成页任务为空)
     */
    private Long messageId;

    /**
     * 任务类型(本期固定 image,预留 video)
     */
    private String taskType;

    /**
     * 出图模型 id(yunwu/gptsapi)
     */
    private Long modelId;

    private String requestId;

    /** Immutable public model parameters and price captured before the debit. */
    private String modelNameSnapshot;
    private String providerSnapshot;
    private String baseUrlSnapshot;
    private String modelDeploymentSnapshot;
    private String imageQualitySnapshot;
    private Long beanPerImageSnapshot;
    private Long prepaidBeans;

    /**
     * 该任务提示词
     */
    private String prompt;

    /**
     * 张数(1~9；生成页入口仍限制 1~4)
     */
    private Integer n;

    /**
     * 分辨率(空走模型 default_resolution)
     */
    private String resolution;

    /**
     * 画幅比例(空走默认)
     */
    private String aspectRatio;

    /**
     * 图生图参考图 /profile URL 的 JSON 数组串(1~9 张);空/null 表示文生图
     */
    private String refImages;

    /**
     * 批量生图任务项 JSON；空表示沿用 prompt + n 的同提示词生成方式。
     */
    private String requestItems;

    /**
     * 状态(CREATED/QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED)
     */
    private String status;

    /**
     * 成功的 /profile/*.png URL,JSON 数组串
     */
    private String resultUrls;

    /**
     * 实际成功张数
     */
    private Integer successCount;

    /**
     * 失败原因(脱敏)
     */
    private String errorMsg;

    /**
     * 预留计费,本期 null
     */
    private Integer beanCost;

    /**
     * 创建时间(自动填充)
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间(自动填充)
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
