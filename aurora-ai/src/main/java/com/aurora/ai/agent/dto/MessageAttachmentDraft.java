package com.aurora.ai.agent.dto;

/** 用户消息与图片附件同事务落库时的内部参数。 */
public record MessageAttachmentDraft(
        String attachmentId,
        int position,
        String sourceType,
        String profileUrl,
        String sourceTaskId,
        Integer sourceSeq) {
}
