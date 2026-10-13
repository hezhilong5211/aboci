package com.aurora.framework.annotation;

import java.lang.annotation.*;

/**
 * 接口限流注解
 * 
 * @author aurora
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimiter {

    /**
     * 限流 key 前缀
     */
    String key() default "";

    /**
     * 限流时间窗口（秒）
     */
    int time() default 60;

    /**
     * 限流次数
     */
    int count() default 100;

    /**
     * 限流类型：IP 或 用户
     */
    LimitType limitType() default LimitType.IP;

    /**
     * 限流提示信息
     */
    String message() default "请求过于频繁，请稍后再试";

    enum LimitType {
        /** 根据 IP 限流 */
        IP,
        /** 根据用户 ID 限流 */
        USER
    }
}
