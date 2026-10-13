package com.aurora.admin.distribution.storage;

import com.aurora.common.core.exception.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 分销敏感文件私有存储。
 *
 * <p>该根目录不注册静态资源映射，数据库仅保存受控文件键。</p>
 */
@Component
public class DistributionPrivateStorage {

    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;
    private static final int THUMBNAIL_SIZE = 120;
    private static final Pattern SAFE_KEY = Pattern.compile("(?:receipt|proof|qrcode)/\\d+/[a-zA-Z0-9._-]+");

    private final Path root;

    public DistributionPrivateStorage(
            @Value("${distribution.private-storage-path:${user.home}/aurora/distribution-private}") String rootPath) {
        this.root = Path.of(rootPath).toAbsolutePath().normalize();
    }

    public String saveImage(MultipartFile file, String category, Long ownerId) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("请选择图片文件");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new ServiceException("图片大小不能超过10MB");
        }
        try {
            byte[] bytes = file.getBytes();
            String extension = detectImageExtension(bytes);
            if (extension == null) {
                throw new ServiceException("只支持真实的 JPG、PNG、GIF、BMP 或 WebP 图片");
            }
            return write(category, ownerId, UUID.randomUUID() + extension, bytes);
        } catch (ServiceException e) {
            throw e;
        } catch (IOException e) {
            throw new ServiceException("私有图片保存失败", e);
        }
    }

    public String savePng(String category, Long ownerId, String filename, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_IMAGE_SIZE) {
            throw new ServiceException("二维码图片内容不正确");
        }
        return write(category, ownerId, filename, bytes);
    }

    public boolean exists(String fileKey) {
        return fileKey != null && Files.isRegularFile(resolve(fileKey));
    }

    public String readDataUri(String fileKey) {
        if (fileKey == null || fileKey.isBlank()) {
            return null;
        }
        Path path = resolve(fileKey);
        if (!Files.isRegularFile(path)) {
            throw new ServiceException("私有图片不存在");
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            return "data:" + contentType(path.getFileName().toString()) + ";base64,"
                    + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            throw new ServiceException("私有图片读取失败", e);
        }
    }

    public synchronized String readThumbnailDataUri(String fileKey) {
        if (fileKey == null || fileKey.isBlank()) {
            return null;
        }
        Path source = resolve(fileKey);
        if (!Files.isRegularFile(source)) {
            throw new ServiceException("私有图片不存在");
        }
        String thumbnailKey = fileKey + ".thumb.jpg";
        Path thumbnail = resolve(thumbnailKey);
        if (!Files.isRegularFile(thumbnail)) {
            byte[] bytes = createThumbnail(source);
            if (bytes == null) {
                return null;
            }
            try {
                Files.write(thumbnail, bytes);
                applyOwnerOnlyFilePermissions(thumbnail);
            } catch (IOException e) {
                throw new ServiceException("私有缩略图保存失败", e);
            }
        }
        return readDataUri(thumbnailKey);
    }

    public void deleteQuietly(String fileKey) {
        if (fileKey == null || fileKey.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(fileKey));
            Files.deleteIfExists(resolve(fileKey + ".thumb.jpg"));
        } catch (IOException ignored) {
            // 数据库事务失败后的兜底清理，不覆盖原始业务异常。
        }
    }

    private byte[] createThumbnail(Path source) {
        try {
            BufferedImage original = ImageIO.read(source.toFile());
            if (original == null || original.getWidth() <= 0 || original.getHeight() <= 0) {
                return null;
            }
            double scale = Math.min(1D, (double) THUMBNAIL_SIZE
                    / Math.max(original.getWidth(), original.getHeight()));
            int width = Math.max(1, (int) Math.round(original.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(original.getHeight() * scale));
            BufferedImage thumbnail = new BufferedImage(THUMBNAIL_SIZE, THUMBNAIL_SIZE,
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = thumbnail.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                graphics.drawImage(original, (THUMBNAIL_SIZE - width) / 2,
                        (THUMBNAIL_SIZE - height) / 2, width, height, null);
            } finally {
                graphics.dispose();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            return ImageIO.write(thumbnail, "jpg", output) ? output.toByteArray() : null;
        } catch (IOException e) {
            throw new ServiceException("私有缩略图生成失败", e);
        }
    }

    private String write(String category, Long ownerId, String filename, byte[] bytes) {
        if (!("receipt".equals(category) || "proof".equals(category) || "qrcode".equals(category))
                || ownerId == null || ownerId <= 0) {
            throw new ServiceException("私有文件归属不正确");
        }
        String fileKey = category + "/" + ownerId + "/" + filename;
        Path destination = resolve(fileKey);
        try {
            Files.createDirectories(destination.getParent());
            applyOwnerOnlyDirectoryPermissions(destination.getParent());
            Files.write(destination, bytes);
            applyOwnerOnlyFilePermissions(destination);
            return fileKey;
        } catch (IOException e) {
            throw new ServiceException("私有图片保存失败", e);
        }
    }

    private Path resolve(String fileKey) {
        if (!SAFE_KEY.matcher(fileKey).matches()) {
            throw new ServiceException("私有文件标识不正确");
        }
        Path resolved = root.resolve(fileKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new ServiceException("私有文件标识不正确");
        }
        return resolved;
    }

    private String detectImageExtension(byte[] bytes) {
        if (bytes.length < 4) {
            return null;
        }
        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
            return ".jpg";
        }
        if (bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return ".png";
        }
        if (bytes[0] == 0x47 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x38) {
            return ".gif";
        }
        if (bytes[0] == 0x42 && bytes[1] == 0x4D) {
            return ".bmp";
        }
        if (bytes.length >= 12 && bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46
                && bytes[3] == 0x46 && bytes[8] == 0x57 && bytes[9] == 0x45
                && bytes[10] == 0x42 && bytes[11] == 0x50) {
            return ".webp";
        }
        return null;
    }

    private String contentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".bmp")) {
            return "image/bmp";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/png";
    }

    private void applyOwnerOnlyDirectoryPermissions(Path directory) {
        try {
            Files.setPosixFilePermissions(directory, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // 非 POSIX 文件系统由部署环境访问控制负责。
        }
    }

    private void applyOwnerOnlyFilePermissions(Path file) {
        try {
            Files.setPosixFilePermissions(file, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // 非 POSIX 文件系统由部署环境访问控制负责。
        }
    }
}
