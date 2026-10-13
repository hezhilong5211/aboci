package com.aurora.framework.config;

import com.aurora.framework.mobile.MobileJwtFilter;
import com.aurora.framework.mobile.MobileAuthEntryPoint;
import com.aurora.framework.security.filter.JwtAuthenticationTokenFilter;
import com.aurora.framework.security.handle.AuthenticationEntryPointImpl;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import com.aurora.framework.security.expression.AdminSecurityExpressionRoot;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    /**
     * 配置方法安全表达式处理器，支持超级管理员权限
     */
    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler() {
            @Override
            protected MethodSecurityExpressionOperations createSecurityExpressionRoot(Authentication authentication,
                    org.aopalliance.intercept.MethodInvocation invocation) {
                AdminSecurityExpressionRoot root = new AdminSecurityExpressionRoot(authentication);
                root.setTrustResolver(getTrustResolver());
                root.setPermissionEvaluator(getPermissionEvaluator());
                root.setRoleHierarchy(getRoleHierarchy());
                return root;
            }
        };
        return handler;
    }

    private final JwtAuthenticationTokenFilter jwtAuthenticationTokenFilter;
    private final AuthenticationEntryPointImpl authenticationEntryPoint;
    private final UserDetailsService userDetailsService;
    private final MobileJwtFilter mobileJwtFilter;
    private final MobileAuthEntryPoint mobileAuthEntryPoint;

    public SecurityConfig(JwtAuthenticationTokenFilter jwtAuthenticationTokenFilter,
            AuthenticationEntryPointImpl authenticationEntryPoint,
            UserDetailsService userDetailsService,
            MobileJwtFilter mobileJwtFilter,
            MobileAuthEntryPoint mobileAuthEntryPoint) {
        this.jwtAuthenticationTokenFilter = jwtAuthenticationTokenFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.userDetailsService = userDetailsService;
        this.mobileJwtFilter = mobileJwtFilter;
        this.mobileAuthEntryPoint = mobileAuthEntryPoint;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 移动端安全过滤链
     * 仅匹配移动端相关路径,与管理后台链隔离;无状态、关闭 csrf;
     * 登录/刷新/支付回调免鉴权,其余需 MobileJwtFilter 注入的认证。
     */
    @Bean
    @Order(0)
    public SecurityFilterChain mobileSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/mobile/**", "/api/pay/notify", "/api/common/upload")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configure(http))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 未认证返回 HTTP 200 + 业务码 401,对齐 hagent 请求层"code 401 触发刷新"契约
                .exceptionHandling(ex -> ex.authenticationEntryPoint(mobileAuthEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 免鉴权:小程序登录、手机号登录、刷新令牌、支付回调、底部导航/分享配置取数(App 启动早于登录)、案例/模板库(公开内容,未登录可浏览)
                        .requestMatchers("/api/mobile/wx/phoneLogin", "/api/mobile/wx/login",
                                "/api/mobile/refreshToken", "/api/pay/notify", "/api/mobile/wx/msg-push",
                                "/api/mobile/tabbar",
                                "/api/mobile/share", "/api/mobile/generation/config", "/api/mobile/template/**",
                                "/api/mobile/distribution/visit")
                        .permitAll()
                        .anyRequest().authenticated());

        http.addFilterBefore(mobileJwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // 禁用 Spring Security 默认 /logout 过滤器,改由 SysLoginController.logout 删除 Redis 会话使令牌失效
                .logout(logout -> logout.disable())
                .cors(cors -> cors.configure(http))
                // 认证失败处理
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint))
                // Session 管理：不使用 Session
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 【关键】允许 ASYNC 和 ERROR 类型的 dispatch 通过（解决 SSE 流完成后的权限问题）
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/login", "/captchaImage").permitAll() // 放行登录和验证码
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll() // 放行健康检查(metrics 等仍需认证)
                        // 测试接口已移除 permitAll，通过 @Profile("dev") 在 Controller 层控制
                        // 放行错误页面（解决 async dispatch 后的错误处理）
                        .requestMatchers("/error").permitAll()
                        // 头像上传接口需要认证，已由 anyRequest().authenticated() 覆盖
                        .requestMatchers("/profile/**").permitAll() // 放行静态资源（头像等）
                        // Knife4j 接口文档相关路径
                        .requestMatchers("/doc.html", "/swagger-ui.html", "/swagger-ui/**", "/webjars/**",
                                "/v3/api-docs/**", "/swagger-resources/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .authenticationProvider(authenticationProvider());

        // 添加 JWT Filter
        http.addFilterBefore(jwtAuthenticationTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 允许的跨域源，从配置文件读取，多个用逗号分隔
     * 默认值适用于开发环境
     */
    @Value("${aurora.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}")
    private String allowedOrigins;

    /**
     * 跨域配置 - 已收紧安全策略
     */
    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        // 从配置文件读取允许的域名，不再使用通配符
        String[] origins = allowedOrigins.split(",");
        for (String origin : origins) {
            config.addAllowedOrigin(origin.trim());
        }
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
