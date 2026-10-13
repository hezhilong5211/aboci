package com.aurora.admin.mobile.controller;

import com.aurora.admin.mobile.vo.UploadVO;
import com.aurora.common.core.domain.Result;
import com.aurora.framework.storage.ProfileStorage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * 移动端通用文件上传控制器(需鉴权)
 * 复用后台头像上传方式:Content-Type + 魔数校验、限制 10MB、按用途隔离子目录、返回相对 URL。
 */
@Slf4j
@Tag(name = "移动端通用上传", description = "移动端图片上传(头像等)")
@RestController
@RequestMapping("/api/common")
public class CommonUploadController {

    private final ProfileStorage profileStorage;

    public CommonUploadController(ProfileStorage profileStorage) {
        this.profileStorage = profileStorage;
    }

    /**
     * 上传图片(头像等)。category 只允许映射到服务端固定目录，禁止客户端控制实际路径。
     */
    @Operation(summary = "上传图片")
    @PostMapping("/upload")
    public Result<UploadVO> upload(@RequestParam("file") MultipartFile file,
                                   @RequestParam(value = "category", defaultValue = "common") String category,
                                   @RequestAttribute("mobileUserId") Long userId) {
        if (file == null || file.isEmpty()) {
            return Result.error("请选择要上传的文件");
        }

        // 检查文件类型(Content-Type)
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.error("只支持上传图片文件");
        }

        // 检查文件大小(限制10MB,与 spring.servlet.multipart.max-file-size 对齐;手机照片普遍 >2MB)
        if (file.getSize() > 10 * 1024 * 1024) {
            return Result.error("图片大小不能超过10MB");
        }

        // 文件魔数校验:验证真实文件类型,防止伪造扩展名
        try {
            byte[] header = file.getInputStream().readNBytes(12);
            if (detectImageExtension(header) == null) {
                return Result.error("文件内容与图片格式不匹配，请上传真实的图片文件");
            }
        } catch (Exception e) {
            log.error("文件魔数校验失败: {}", e.getMessage());
            return Result.error("文件校验失败");
        }

        try {
            // 文件名与扩展名都由服务端产生，不信任客户端原始文件名。
            String extension = detectImageExtension(file.getInputStream().readNBytes(12));
            String filename = UUID.randomUUID() + extension;

            String directory = switch (category) {
                case "avatars" -> "mobile_avatar";
                case "agent" -> "agent_reference";
                default -> "mobile_common";
            };
            String relativePath = directory + "/" + userId + "/" + filename;

            // 新文件统一写 spring.profile.upload-path；读取层继续兼容历史目录。
            java.nio.file.Path destFile = profileStorage.prepareWritePath(relativePath);
            file.transferTo(destFile);

            // 返回相对路径,前端会自动拼接 baseURL
            String fileUrl = "/profile/" + relativePath;

            UploadVO vo = new UploadVO();
            vo.setUrl(fileUrl);
            vo.setPath(fileUrl);
            return Result.success(vo);
        } catch (Exception e) {
            log.error("上传图片失败: {}", e.getMessage(), e);
            return Result.error("上传图片失败");
        }
    }

    /**
     * 验证图片文件魔数(Magic Number)
     * 支持 JPEG、PNG、GIF、BMP、WebP 格式
     */
    private String detectImageExtension(byte[] header) {
        if (header == null || header.length < 4) {
            return null;
        }
        // JPEG: FF D8 FF
        if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
            return ".jpg";
        }
        // PNG: 89 50 4E 47
        if (header[0] == (byte) 0x89 && header[1] == (byte) 0x50 && header[2] == (byte) 0x4E
                && header[3] == (byte) 0x47) {
            return ".png";
        }
        // GIF: 47 49 46 38
        if (header[0] == (byte) 0x47 && header[1] == (byte) 0x49 && header[2] == (byte) 0x46
                && header[3] == (byte) 0x38) {
            return ".gif";
        }
        // BMP: 42 4D
        if (header[0] == (byte) 0x42 && header[1] == (byte) 0x4D) {
            return ".bmp";
        }
        // WebP: RIFF....WEBP，仅 RIFF 头不足以判定为图片。
        if (header.length >= 12
                && header[0] == (byte) 0x52 && header[1] == (byte) 0x49
                && header[2] == (byte) 0x46 && header[3] == (byte) 0x46
                && header[8] == (byte) 0x57 && header[9] == (byte) 0x45
                && header[10] == (byte) 0x42 && header[11] == (byte) 0x50) {
            return ".webp";
        }
        return null;
    }
}
