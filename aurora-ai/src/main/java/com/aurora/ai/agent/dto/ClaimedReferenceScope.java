package com.aurora.ai.agent.dto;

import com.aurora.ai.agent.domain.ReferenceScope;

import java.util.List;

/** 已被当前用户消息原子消费的一次性引用范围。 */
public record ClaimedReferenceScope(ReferenceScope scope, List<String> attachmentIds) {
}
