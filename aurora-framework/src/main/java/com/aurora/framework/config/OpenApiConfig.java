package com.aurora.framework.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenApi 接口文档配置类
 * <p>
 * 用于配置 Knife4j/Swagger 生成的接口文档元数据（标题、描述、版本等）。
 * 适配 Spring Boot 3.x + Java 21 环境。
 *
 * @author Aurora Team
 */
@Configuration
public class OpenApiConfig {

    /**
     * 构建自定义 OpenAPI 对象
     *
     * @return OpenAPI
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        // 文档主标题
                        .title("极光(Aurora)管理系统 - 接口文档")
                        // 文档版本号
                        .version("v1.0.0")
                        // 项目描述
                        .description("极光框架(Aurora Framework)后端 API 服务，提供系统核心业务接口能力。")
                        // (可选) 联系人信息，可改为您的名字或团队名
                        .contact(new Contact()
                                .name("Aurora Dev Team")
                                .email("admin@aurora.com"))
                );
    }
}

