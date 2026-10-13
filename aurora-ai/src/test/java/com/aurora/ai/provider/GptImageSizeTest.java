package com.aurora.ai.provider;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GptImageSizeTest {

    @Test
    void shouldKeepFourKPortraitWithinYunwuPixelLimit() {
        assertEquals("2448x3264", GptImageSize.of("4K", "3:4"));
        assertEquals("3264x2448", GptImageSize.of("4K", "4:3"));
    }

    @Test
    void shouldProduceLegalSizesForAllProductOptions() {
        List<String> resolutions = List.of("1K", "2K", "4K");
        List<String> ratios = List.of("1:1", "16:9", "9:16", "3:2", "2:3", "4:3", "3:4");
        for (String resolution : resolutions) {
            for (String ratio : ratios) {
                String[] parts = GptImageSize.of(resolution, ratio).split("x");
                int width = Integer.parseInt(parts[0]);
                int height = Integer.parseInt(parts[1]);
                long pixels = (long) width * height;
                assertEquals(0, width % 16, resolution + " " + ratio + " width");
                assertEquals(0, height % 16, resolution + " " + ratio + " height");
                assertTrue(Math.max(width, height) <= 3840, resolution + " " + ratio + " max edge");
                assertTrue((double) Math.max(width, height) / Math.min(width, height) <= 3.0,
                        resolution + " " + ratio + " aspect ratio");
                assertTrue(pixels >= 655_360L && pixels <= 8_294_400L,
                        resolution + " " + ratio + " pixels=" + pixels);
            }
        }
    }
}
