package com.aurora.ai.provider;

import com.aurora.common.core.exception.ServiceException;

/** Exact ratios with OpenLux GPT Image 2.5's documented pixel constraints. */
public final class LuxaiImageSize {
    private LuxaiImageSize() { }

    public static String of(String resolution, String aspectRatio) {
        int target = switch (resolution == null ? "1K" : resolution.toUpperCase(java.util.Locale.ROOT)) {
            case "1K" -> 1024;
            case "2K" -> 2048;
            case "4K" -> 3840;
            default -> throw new ServiceException("Luxai 不支持该分辨率");
        };
        String ratio = aspectRatio == null || aspectRatio.isBlank() ? "1:1" : aspectRatio;
        if (!ratio.matches("[1-9][0-9]?:[1-9][0-9]?")) {
            throw new ServiceException("Luxai 画幅比例无效");
        }
        String[] parts = ratio.split(":");
        int w = Integer.parseInt(parts[0]);
        int h = Integer.parseInt(parts[1]);
        int gcd = gcd(w, h);
        w /= gcd;
        h /= gcd;
        if (Math.max(w, h) > 3 * Math.min(w, h)) {
            throw new ServiceException("Luxai 画幅长短边比不能超过3");
        }
        // Reduced ratio times 16 guarantees both dimensions stay aligned without changing the ratio.
        int unitW = w * 16;
        int unitH = h * 16;
        long unitPixels = (long) unitW * unitH;
        int minimum = (int) Math.ceil(Math.sqrt(655_360D / unitPixels));
        int maximum = Math.min(3840 / Math.max(unitW, unitH),
                (int) Math.floor(Math.sqrt(8_294_400D / unitPixels)));
        int multiplier = Math.min(maximum, Math.max(minimum, target / Math.max(unitW, unitH)));
        if (multiplier < minimum) {
            throw new ServiceException("Luxai 画幅比例无法满足像素约束");
        }
        return unitW * multiplier + "x" + unitH * multiplier;
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int next = a % b;
            a = b;
            b = next;
        }
        return a;
    }
}
