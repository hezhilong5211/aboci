package com.aurora.framework.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.http.CacheControl;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Web MVC 配置类 - 配置静态资源映射
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final String uploadPath;

    public WebMvcConfig(@Value("${spring.profile.upload-path}") String uploadPath) {
        this.uploadPath = uploadPath;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置目录用于生图等新文件；旧目录保留为头像等历史文件的兼容回退。
        registry.addResourceHandler("/profile/**")
                .addResourceLocations(profileResourceLocations(uploadPath, System.getProperty("user.home")))
                // profile 新文件均使用唯一文件名，不原地覆盖，可安全长期缓存以降低 5M 出口带宽压力。
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }

    static String[] profileResourceLocations(String configuredUploadPath, String userHome) {
        String configured = toResourceLocation(Path.of(configuredUploadPath));
        String legacy = toResourceLocation(Path.of(userHome, "aurora", "uploadPath"));
        if (configured.equals(legacy)) {
            return new String[] { configured };
        }
        return new String[] { configured, legacy };
    }

    private static String toResourceLocation(Path path) {
        String location = path.toAbsolutePath().normalize().toUri().toString();
        return location.endsWith("/") ? location : location + "/";
    }
}
