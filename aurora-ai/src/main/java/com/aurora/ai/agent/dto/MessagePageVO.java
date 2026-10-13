package com.aurora.ai.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 会话历史消息游标分页。
 * records 始终按消息编号正序返回；nextCursor 供继续向前加载。
 */
@Data
@AllArgsConstructor
public class MessagePageVO {

    /**
     * 当前页消息。
     */
    private List<MessageVO> records;

    /**
     * 下一页 beforeId；没有更早消息时为空。
     */
    private Long nextCursor;

    /**
     * 是否还有更早消息。
     */
    private boolean hasMore;
}
