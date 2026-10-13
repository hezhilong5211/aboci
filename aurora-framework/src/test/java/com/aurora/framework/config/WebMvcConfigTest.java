package com.aurora.framework.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WebMvcConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void configuredUploadDirectoryTakesPriorityAndLegacyDirectoryRemainsAvailable() throws Exception {
        Path configured = Files.createDirectories(tempDir.resolve("configured-uploads"));
        Path userHome = Files.createDirectories(tempDir.resolve("home"));
        Path legacy = Files.createDirectories(userHome.resolve("aurora/uploadPath"));

        assertThat(WebMvcConfig.profileResourceLocations(configured.toString(), userHome.toString()))
                .containsExactly(asDirectoryUri(configured), asDirectoryUri(legacy));
    }

    @Test
    void identicalConfiguredAndLegacyDirectoriesAreMappedOnlyOnce() throws Exception {
        Path userHome = Files.createDirectories(tempDir.resolve("same-home"));
        Path legacy = Files.createDirectories(userHome.resolve("aurora/uploadPath"));

        assertThat(WebMvcConfig.profileResourceLocations(legacy.toString(), userHome.toString()))
                .containsExactly(asDirectoryUri(legacy));
    }

    private String asDirectoryUri(Path path) {
        String uri = path.toAbsolutePath().normalize().toUri().toString();
        return uri.endsWith("/") ? uri : uri + "/";
    }
}
