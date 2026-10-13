package com.aurora.common.core.constant;

/**
 * 小程序 AI 生成相关固定配置键。
 */
public final class MobileGenerationConfigKeys {

    public static final String GENERATION_ENABLED = "mobile.generation.enabled";

    public static final String DOWNLOAD_WATERMARK_ENABLED =
            "mobile.generation.download_watermark.enabled";

    /** 缺失或非法配置保持当前功能开启，避免迁移前后意外中断线上能力。 */
    public static boolean enabledOrDefault(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = value.trim();
        if ("true".equalsIgnoreCase(normalized) || "1".equals(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized) || "0".equals(normalized)) {
            return false;
        }
        return true;
    }

    private MobileGenerationConfigKeys() {
    }
}
