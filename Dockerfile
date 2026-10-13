# ============================================================
# 极光框架后端镜像(多阶段:Maven 构建 → JRE21 运行)
# 构建上下文 = 仓库根目录。产物为可执行 fat jar(aurora-admin -exec.jar)。
# ============================================================

# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
# 仅打包可启动模块及其依赖,跳过测试
RUN mvn -B -q clean package -pl aurora-admin -am -DskipTests

# ---- 运行阶段 ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# -exec.jar 为 spring-boot 插件 repackage 的可执行包(见 aurora-admin pom classifier=exec)
COPY --from=build /build/aurora-admin/target/aurora-admin-*-exec.jar app.jar
EXPOSE 8080
# 生产 profile + 可调 JVM 参数;敏感配置全部经环境变量注入(见 application-prod.yml)
ENV JAVA_OPTS="-Xms512m -Xmx1024m" \
    SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
