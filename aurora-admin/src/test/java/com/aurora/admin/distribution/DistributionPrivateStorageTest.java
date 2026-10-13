package com.aurora.admin.distribution;

import com.aurora.admin.distribution.storage.DistributionPrivateStorage;
import com.aurora.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributionPrivateStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void storesValidatedImageByOpaqueKeyAndReturnsDataUri() {
        DistributionPrivateStorage storage = new DistributionPrivateStorage(tempDir.toString());
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile file = new MockMultipartFile("file", "receipt.png", "image/png", png);

        String key = storage.saveImage(file, "receipt", 7L);

        assertTrue(key.matches("receipt/7/[a-f0-9-]+\\.png"));
        assertTrue(storage.exists(key));
        assertTrue(storage.readDataUri(key).startsWith("data:image/png;base64,"));
    }

    @Test
    void rejectsFakeImageAndTraversalKey() {
        DistributionPrivateStorage storage = new DistributionPrivateStorage(tempDir.toString());
        MockMultipartFile fake = new MockMultipartFile("file", "fake.png", "image/png", "not-image".getBytes());

        assertThrows(ServiceException.class, () -> storage.saveImage(fake, "receipt", 7L));
        assertThrows(ServiceException.class, () -> storage.readDataUri("receipt/7/../../secret.png"));
    }

    @Test
    void createsAndCachesPrivateJpegThumbnail() throws IOException {
        DistributionPrivateStorage storage = new DistributionPrivateStorage(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "receipt.png", "image/png", realPng(400, 240));

        String key = storage.saveImage(file, "receipt", 7L);
        String first = storage.readThumbnailDataUri(key);
        String second = storage.readThumbnailDataUri(key);

        assertTrue(first.startsWith("data:image/jpeg;base64,"));
        assertTrue(first.equals(second));
        Path thumbnail = tempDir.resolve(key + ".thumb.jpg");
        assertTrue(Files.isRegularFile(thumbnail));
        BufferedImage image = ImageIO.read(thumbnail.toFile());
        assertTrue(image.getWidth() == 120 && image.getHeight() == 120);
    }

    private byte[] realPng(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(64, 110, 238));
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
