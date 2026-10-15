package com.aurora.ai.agent.dto;

import com.aurora.ai.domain.SysAiModel;

import java.util.Set;

/** Public labels must never fall back to an internal model or supplier name. */
public final class PublicModelTier {
    private PublicModelTier() {}

    public static String nameOf(SysAiModel model) {
        String tier = model.getDisplayTier();
        return tier != null && Set.of("轻", "中", "高").contains(tier) ? tier : "中";
    }
}
