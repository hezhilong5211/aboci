package com.aurora.ai.provider;

import java.util.HashMap;
import java.util.Map;

/**
 * 云雾 GPT-Image 的 size 固定档位表。
 *
 * <p>GPT-Image 端点对 size 有硬约束：最大边 ≤ 3840、总像素 ≤ 8294400、且边长为 16 的倍数，
 * 因此不能随意传分辨率。本表按「分辨率档位（1K/2K/4K）× 常见宽高比」预置合法尺寸；
 * 命中表则直接返回，未命中则按比例推算长边、做 16px 对齐并 clamp 总像素后兜底。</p>
 */
public class GptImageSize {

    /** 分辨率档位 → 宽高比 → 像素尺寸（如 "1024x1024"）的固定档位表 */
    private static final Map<String, Map<String, String>> TABLE = new HashMap<>();

    static {
        TABLE.put("1K", Map.of(
                "1:1", "1024x1024", "16:9", "1536x1024", "9:16", "1024x1536",
                "3:2", "1536x1024", "2:3", "1024x1536", "4:3", "1536x1024", "3:4", "1024x1536"));
        TABLE.put("2K", Map.of(
                "1:1", "2048x2048", "16:9", "2048x1152", "9:16", "1152x2048",
                "3:2", "2048x1376", "2:3", "1376x2048", "4:3", "2048x1536", "3:4", "1536x2048"));
        TABLE.put("4K", Map.of(
                "1:1", "2880x2880", "16:9", "3840x2160", "9:16", "2160x3840",
                "3:2", "3504x2336", "2:3", "2336x3504", "4:3", "3264x2448", "3:4", "2448x3264"));
    }

    private GptImageSize() {
    }

    /**
     * 解析分辨率档位 + 宽高比为合法的 size 字符串。
     *
     * @param resolution 分辨率档位（1K / 2K / 4K）
     * @param aspectRatio 宽高比（如 "16:9"）
     * @return 像素尺寸字符串，如 "1024x1024"
     */
    public static String of(String resolution, String aspectRatio) {
        Map<String, String> m = TABLE.get(resolution);
        if (m != null && m.containsKey(aspectRatio)) {
            return m.get(aspectRatio);
        }
        // 不在表里：按比例算长边后做 16px 对齐，再 clamp 总像素到约束内
        String[] parts = aspectRatio == null ? new String[0] : aspectRatio.split(":");
        if (parts.length != 2) {
            return "1024x1024";
        }
        double rw, rh;
        try {
            rw = Double.parseDouble(parts[0]);
            rh = Double.parseDouble(parts[1]);
        } catch (NumberFormatException e) {
            return "1024x1024";
        }
        if (rh <= 0) {
            return "1024x1024";
        }
        // 按档位确定最大边，长边取最大边、短边按比例缩放
        double maxEdge = "4K".equals(resolution) ? 3840 : "2K".equals(resolution) ? 2048 : 1024;
        double ratio = rw / rh, w, h;
        if (ratio >= 1.0) {
            w = maxEdge;
            h = maxEdge / ratio;
        } else {
            h = maxEdge;
            w = maxEdge * ratio;
        }
        // 16px 对齐
        w = Math.floor(w / 16) * 16;
        h = Math.floor(h / 16) * 16;
        // clamp 总像素 ≤ 8294400
        while (w * h > 8_294_400) {
            w = Math.floor(w * 0.95 / 16) * 16;
            h = Math.floor(h * 0.95 / 16) * 16;
        }
        return (int) w + "x" + (int) h;
    }
}
