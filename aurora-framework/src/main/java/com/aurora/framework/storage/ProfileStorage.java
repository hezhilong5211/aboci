package com.aurora.framework.storage;

import com.aurora.common.core.exception.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * /profile 静态资源的统一文件存储入口。
 *
 * <p>新文件统一写入 {@code spring.profile.upload-path}；读取时兼容历史目录
 * {@code ${user.home}/aurora/uploadPath}，避免部署切换目录后旧头像、参考图无法被 AI 读取。</p>
 */
@Component
public class ProfileStorage {

    private static final String PROFILE_PREFIX = "/profile/";

    private final Path configuredRoot;
    private final Path legacyRoot;

    public ProfileStorage(@Value("${spring.profile.upload-path:./uploadPath}") String configuredUploadPath,
                          @Value("${user.home}") String userHome) {
        this.configuredRoot = Path.of(configuredUploadPath).toAbsolutePath().normalize();
        this.legacyRoot = Path.of(userHome, "aurora", "uploadPath").toAbsolutePath().normalize();
    }

    /**
     * 为新文件生成安全写入路径，并创建父目录。
     *
     * @param relativePath 相对 profile 根目录的路径，如 {@code mobile_avatar/a.png}
     */
    public Path prepareWritePath(String relativePath) throws IOException {
        Path target = resolveUnder(configuredRoot, relativePath);
        Files.createDirectories(target.getParent());
        return target;
    }

    /** 写入新 profile 文件并返回相对 URL。 */
    public String write(String relativePath, byte[] data) throws IOException {
        Path target = prepareWritePath(relativePath);
        Files.write(target, data);
        return PROFILE_PREFIX + normalizeRelativePath(relativePath);
    }

    /**
     * 读取 profile 文件：先查当前配置目录，再查历史目录。
     */
    public byte[] read(String profileUrl) throws IOException {
        String relativePath = requireRelativePath(profileUrl);
        for (Path root : roots()) {
            Path candidate = resolveUnder(root, relativePath);
            if (Files.isRegularFile(candidate)) {
                return Files.readAllBytes(candidate);
            }
        }
        throw new ServiceException("图片文件不存在: " + profileUrl);
    }

    /**
     * 从当前目录和历史目录删除同一个 profile 文件。不存在视为成功。
     *
     * @return 是否实际删除过至少一个文件
     */
    public boolean deleteEverywhere(String profileUrl) throws IOException {
        String relativePath = requireRelativePath(profileUrl);
        boolean deleted = false;
        for (Path root : roots()) {
            deleted |= Files.deleteIfExists(resolveUnder(root, relativePath));
        }
        return deleted;
    }

    /**
     * 枚举当前配置目录下指定子目录中，最后修改时间不晚于 cutoff 的文件 URL。
     * 历史目录不参与枚举，避免把旧 mobile_avatar 中无法区分用途的文件误当参考图清理。
     */
    public List<String> listConfiguredProfileUrls(String relativeDirectory, Instant cutoff, int limit)
            throws IOException {
        if (cutoff == null || limit <= 0) {
            return List.of();
        }
        Path directory = resolveUnder(configuredRoot, relativeDirectory);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }

        List<StoredFile> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.toList()) {
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                Instant modifiedAt = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant();
                if (!modifiedAt.isAfter(cutoff)) {
                    files.add(new StoredFile(path, modifiedAt));
                }
            }
        }
        files.sort(Comparator.comparing(StoredFile::modifiedAt));

        List<String> urls = new ArrayList<>(Math.min(files.size(), limit));
        for (int i = 0; i < files.size() && i < limit; i++) {
            String relativePath = configuredRoot.relativize(files.get(i).path()).toString().replace('\\', '/');
            urls.add(PROFILE_PREFIX + relativePath);
        }
        return urls;
    }

    /**
     * 将相对或绝对 URL 归一化为 /profile/...；非 profile URL 返回 null。
     */
    public static String normalizeProfileUrl(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        int start = trimmed.indexOf(PROFILE_PREFIX);
        if (start < 0) {
            return null;
        }
        String normalized = trimmed.substring(start);
        int query = normalized.indexOf('?');
        int fragment = normalized.indexOf('#');
        int end = normalized.length();
        if (query >= 0) {
            end = Math.min(end, query);
        }
        if (fragment >= 0) {
            end = Math.min(end, fragment);
        }
        normalized = normalized.substring(0, end).replace('\\', '/');
        if (normalized.contains("..") || normalized.length() <= PROFILE_PREFIX.length()) {
            return null;
        }
        try {
            normalizeRelativePath(normalized.substring(PROFILE_PREFIX.length()));
            return normalized;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String requireRelativePath(String profileUrl) {
        String normalized = normalizeProfileUrl(profileUrl);
        if (normalized == null) {
            throw new ServiceException("非法图片地址");
        }
        return normalized.substring(PROFILE_PREFIX.length());
    }

    private Set<Path> roots() {
        Set<Path> roots = new LinkedHashSet<>();
        roots.add(configuredRoot);
        roots.add(legacyRoot);
        return roots;
    }

    private static Path resolveUnder(Path root, String relativePath) {
        String normalizedRelative = normalizeRelativePath(relativePath);
        Path target = root.resolve(normalizedRelative).normalize();
        if (!target.startsWith(root)) {
            throw new ServiceException("非法文件路径");
        }
        return target;
    }

    private static String normalizeRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("文件路径不能为空");
        }
        String slashPath = relativePath.replace('\\', '/');
        if (slashPath.startsWith("/") || slashPath.contains("..")) {
            throw new IllegalArgumentException("非法文件路径");
        }
        Path normalized = Path.of(slashPath).normalize();
        if (normalized.isAbsolute() || normalized.startsWith("..")) {
            throw new IllegalArgumentException("非法文件路径");
        }
        return normalized.toString().replace('\\', '/');
    }

    private record StoredFile(Path path, Instant modifiedAt) {
    }
}
