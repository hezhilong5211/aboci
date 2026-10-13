package com.aurora.framework.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void readsConfiguredFirstAndFallsBackToLegacy() throws Exception {
        Path configured = tempDir.resolve("configured");
        Path home = tempDir.resolve("home");
        Path legacy = home.resolve("aurora/uploadPath/mobile_avatar/old.png");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "legacy");

        ProfileStorage storage = new ProfileStorage(configured.toString(), home.toString());

        assertThat(new String(storage.read("/profile/mobile_avatar/old.png"))).isEqualTo("legacy");
        assertThat(storage.write("mobile_avatar/new.png", "new".getBytes()))
                .isEqualTo("/profile/mobile_avatar/new.png");
        assertThat(new String(storage.read("https://example.com/profile/mobile_avatar/new.png?x=1")))
                .isEqualTo("new");
    }

    @Test
    void deletesBothRootsAndRejectsTraversal() throws Exception {
        Path configured = tempDir.resolve("configured");
        Path home = tempDir.resolve("home");
        Path currentFile = configured.resolve("same.png");
        Path legacyFile = home.resolve("aurora/uploadPath/same.png");
        Files.createDirectories(currentFile.getParent());
        Files.createDirectories(legacyFile.getParent());
        Files.writeString(currentFile, "current");
        Files.writeString(legacyFile, "legacy");

        ProfileStorage storage = new ProfileStorage(configured.toString(), home.toString());

        assertThat(storage.deleteEverywhere("/profile/same.png")).isTrue();
        assertThat(currentFile).doesNotExist();
        assertThat(legacyFile).doesNotExist();
        assertThatThrownBy(() -> storage.prepareWritePath("../escape.png"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listsOnlyExpiredFilesFromConfiguredSubdirectory() throws Exception {
        Path configured = tempDir.resolve("configured");
        Path home = tempDir.resolve("home");
        ProfileStorage storage = new ProfileStorage(configured.toString(), home.toString());

        storage.write("agent_reference/7/old.png", "old".getBytes());
        storage.write("agent_reference/7/recent.png", "recent".getBytes());
        Path oldAvatar = home.resolve("aurora/uploadPath/mobile_avatar/legacy.png");
        Files.createDirectories(oldAvatar.getParent());
        Files.writeString(oldAvatar, "legacy");

        Instant now = Instant.now();
        Files.setLastModifiedTime(configured.resolve("agent_reference/7/old.png"),
                FileTime.from(now.minusSeconds(40 * 24 * 60 * 60L)));

        List<String> urls = storage.listConfiguredProfileUrls(
                "agent_reference", now.minusSeconds(30 * 24 * 60 * 60L), 100);

        assertThat(urls).containsExactly("/profile/agent_reference/7/old.png");
        assertThat(oldAvatar).exists();
    }
}
