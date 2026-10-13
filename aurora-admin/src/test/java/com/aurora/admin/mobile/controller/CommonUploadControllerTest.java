package com.aurora.admin.mobile.controller;

import com.aurora.admin.mobile.vo.UploadVO;
import com.aurora.common.core.domain.Result;
import com.aurora.framework.storage.ProfileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CommonUploadControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void separatesAvatarAndAgentReferenceDirectoriesByUser() {
        ProfileStorage storage = new ProfileStorage(
                tempDir.resolve("uploads").toString(), tempDir.resolve("home").toString());
        CommonUploadController controller = new CommonUploadController(storage);

        Result<UploadVO> avatar = controller.upload(png("avatar.png"), "avatars", 12L);
        Result<UploadVO> reference = controller.upload(png("reference.png"), "agent", 12L);

        assertThat(avatar.getCode()).isEqualTo(200);
        assertThat(avatar.getData().getUrl()).startsWith("/profile/mobile_avatar/12/");
        assertThat(reference.getCode()).isEqualTo(200);
        assertThat(reference.getData().getUrl()).startsWith("/profile/agent_reference/12/");
        assertThat(tempDir.resolve("uploads/mobile_avatar/12")).isDirectory();
        assertThat(tempDir.resolve("uploads/agent_reference/12")).isDirectory();
    }

    @Test
    void ignoresDangerousOriginalExtensionAndUsesDetectedImageType() {
        ProfileStorage storage = new ProfileStorage(
                tempDir.resolve("uploads").toString(), tempDir.resolve("home").toString());
        CommonUploadController controller = new CommonUploadController(storage);

        Result<UploadVO> result = controller.upload(png("avatar.php"), "avatars", 12L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getUrl()).endsWith(".png").doesNotContain(".php");
    }

    @Test
    void rejectsRiffFileWithoutWebpSignature() {
        ProfileStorage storage = new ProfileStorage(
                tempDir.resolve("uploads").toString(), tempDir.resolve("home").toString());
        CommonUploadController controller = new CommonUploadController(storage);
        MockMultipartFile fake = new MockMultipartFile("file", "fake.webp", "image/webp",
                new byte[]{0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0, 0, 0, 0});

        Result<UploadVO> result = controller.upload(fake, "agent", 12L);

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMsg()).contains("文件内容与图片格式不匹配");
    }

    private MockMultipartFile png(String filename) {
        return new MockMultipartFile("file", filename, "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
    }
}
