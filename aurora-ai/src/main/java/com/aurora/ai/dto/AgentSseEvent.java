package com.aurora.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.io.Serializable;

/**
 * 分型 SSE 事件(PRD §9.2,冻结契约 D4)。
 * type 取值:meta / queue / thinking / text / tool / clarification / result / done / error。
 * 字段全可选(@JsonInclude NON_NULL),按事件类型仅输出对应字段;
 * 前端「有什么渲染什么」,不得写死段数。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentSseEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 单行 JSON 序列化复用实例(DTO 非 Spring Bean,内部自持;默认不缩进即单行)。
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 事件类型:meta / queue / thinking / text / tool / clarification / result / done / error
     */
    private String type;

    /**
     * 会话 id(meta)
     */
    private Long conversationId;

    /**
     * 消息 id(meta)
     */
    private Long messageId;

    /** 持久化执行标识，用于切换会话后恢复。 */
    private String runId;

    private String requestId;

    /**
     * 增量内容:正文增量(text)或思考增量(thinking),按 type 区分
     */
    private String content;

    /**
     * 工具名(tool):generate_images / submit_result
     */
    private String name;

    /**
     * 工具状态(tool):running / done
     */
    private String status;

    /**
     * 工具状态摘要(tool):如「识别中…」「已提交 N 张」
     */
    private String summary;

    /** 已提交的图片任务号(tool=generate_images,done)，供前端立即开始轮询。 */
    private String taskId;

    /** 已提交图片数量(tool=generate_images,done)。 */
    private Integer n;

    /**
     * 结构化产出(result)
     */
    private AgentResult data;

    /** 结构化澄清卡片(clarification)。 */
    private AgentClarification clarification;

    /**
     * 错误提示(error)
     */
    private String message;

    /** 稳定业务错误标识(error)，前端不得依赖中文文案判断 */
    private String errorCode;

    /** 建议动作(error)，如 RECHARGE */
    private String action;

    /**
     * 流开始,绑定会话/消息。
     */
    public static AgentSseEvent meta(Long conversationId, Long messageId) {
        AgentSseEvent event = new AgentSseEvent();
        event.type = "meta";
        event.conversationId = conversationId;
        event.messageId = messageId;
        return event;
    }

    /** 对话等待并发许可超过提醒阈值时的临时状态，不写入回答正文和历史。 */
    public static AgentSseEvent queue(String message) {
        AgentSseEvent event = new AgentSseEvent();
        event.type = "queue";
        event.message = message;
        return event;
    }

    /**
     * 正文增量。
     */
    public static AgentSseEvent text(String content) {
        AgentSseEvent event = new AgentSseEvent();
        event.type = "text";
        event.content = content;
        return event;
    }

    /**
     * 思考增量(reasoner 思考流):复用 content 字段承载增量,前端拼接渲染折叠思考块。
     */
    public static AgentSseEvent thinking(String content) {
        AgentSseEvent event = new AgentSseEvent();
        event.type = "thinking";
        event.content = content;
        return event;
    }

    /**
     * 工具状态条。
     */
    public static AgentSseEvent tool(String name, String status, String summary) {
        AgentSseEvent event = new AgentSseEvent();
        event.type = "tool";
        event.name = name;
        event.status = status;
        event.summary = summary;
        return event;
    }

    /** 带图片任务引用的工具完成事件。 */
    public static AgentSseEvent tool(String name, String status, String summary, String taskId, Integer n) {
        AgentSseEvent event = tool(name, status, summary);
        event.taskId = taskId;
        event.n = n;
        return event;
    }

    /**
     * 末尾结构化块。
     */
    public static AgentSseEvent result(AgentResult data) {
        AgentSseEvent event = new AgentSseEvent();
        event.type = "result";
        event.data = data;
        return event;
    }

    /** 需要用户补充一个关键决策时下发的结构化卡片。 */
    public static AgentSseEvent clarification(AgentClarification clarification) {
        AgentSseEvent event = new AgentSseEvent();
        event.type = "clarification";
        event.clarification = clarification;
        return event;
    }

    /**
     * 正常结束(兼容旧 [DONE] 语义,改用 JSON)。
     */
    public static AgentSseEvent done() {
        AgentSseEvent event = new AgentSseEvent();
        event.type = "done";
        return event;
    }

    /**
     * 异常事件。
     */
    public static AgentSseEvent error(String message) {
        AgentSseEvent event = new AgentSseEvent();
        event.type = "error";
        event.message = message;
        return event;
    }

    /** 带稳定业务标识和建议动作的异常事件。 */
    public static AgentSseEvent error(String message, String errorCode, String action) {
        AgentSseEvent event = error(message);
        event.errorCode = errorCode;
        event.action = action;
        return event;
    }

    /**
     * 序列化为单行 JSON(供 SSE data 帧载荷)。
     * 序列化失败时降级为最简 error 事件,避免中断整条流。
     */
    public String toJsonLine() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{\"type\":\"error\",\"message\":\"事件序列化失败\"}";
        }
    }
}
