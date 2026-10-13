package com.aurora.ai.agent.service;

import com.aurora.ai.agent.domain.GenerationTask;
import com.aurora.ai.agent.dto.GenerationDownloadVO;
import com.aurora.common.core.exception.ServiceException;
import com.aurora.framework.storage.ProfileStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeneratedImageDownloadServiceTest {

    @TempDir
    Path tempDir;

    private IGenerationTaskService generationTaskService;
    private GenerationFeaturePolicy featurePolicy;
    private ProfileStorage profileStorage;
    private GeneratedImageDownloadService service;

    @BeforeEach
    void setUp() {
        generationTaskService = mock(IGenerationTaskService.class);
        featurePolicy = mock(GenerationFeaturePolicy.class);
        profileStorage = new ProfileStorage(tempDir.resolve("upload").toString(),
                tempDir.resolve("home").toString());
        service = new GeneratedImageDownloadService(
                generationTaskService, featurePolicy, profileStorage, new ObjectMapper());
    }

    @Test
    void 水印开启时保留原图并生成带标识派生图() throws Exception {
        String originalUrl = profileStorage.write("generated/test.png", samplePng());
        when(generationTaskService.getOwned("task-1", 7L)).thenReturn(task(originalUrl));
        when(featurePolicy.isDownloadWatermarkEnabled()).thenReturn(true);

        GenerationDownloadVO result = service.resolveDownload(7L, "task-1", 1);

        assertThat(result.isWatermarked()).isTrue();
        assertThat(result.getUrl()).isEqualTo("/profile/generated/test.watermarked.png");
        assertThat(profileStorage.read(originalUrl)).isEqualTo(samplePng());
        BufferedImage derived = ImageIO.read(new ByteArrayInputStream(profileStorage.read(result.getUrl())));
        assertThat(derived.getWidth()).isEqualTo(800);
        assertThat(derived.getHeight()).isEqualTo(600);
        assertThat(profileStorage.read(result.getUrl())).isNotEqualTo(profileStorage.read(originalUrl));
    }

    @Test
    void 水印关闭时直接返回原图且不创建派生图() throws Exception {
        String originalUrl = profileStorage.write("generated/test.png", samplePng());
        when(generationTaskService.getOwned("task-1", 7L)).thenReturn(task(originalUrl));
        when(featurePolicy.isDownloadWatermarkEnabled()).thenReturn(false);

        GenerationDownloadVO result = service.resolveDownload(7L, "task-1", 1);

        assertThat(result.isWatermarked()).isFalse();
        assertThat(result.getUrl()).isEqualTo(originalUrl);
        assertThatThrownBy(() -> profileStorage.read("/profile/generated/test.watermarked.png"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void 非本人任务不返回任何下载地址() {
        when(generationTaskService.getOwned("task-1", 7L)).thenReturn(null);

        assertThatThrownBy(() -> service.resolveDownload(7L, "task-1", 1))
                .isInstanceOf(ServiceException.class)
                .hasMessage("任务不存在或无权访问");
    }

    private GenerationTask task(String resultUrl) {
        GenerationTask task = new GenerationTask();
        task.setTaskId("task-1");
        task.setUserId(7L);
        task.setResultUrls("[\"" + resultUrl + "\"]");
        return task;
    }

    private byte[] samplePng() throws Exception {
        BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(230, 240, 255));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        }
    }
}
