package com.aurora.admin.controller;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.CircleCaptcha;
import cn.hutool.captcha.generator.RandomGenerator;
import cn.hutool.core.util.IdUtil;
import com.aurora.common.core.domain.Result;
import com.aurora.common.core.redis.RedisCache;
import com.aurora.framework.web.service.SysLoginService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
public class CaptchaController {

    @Resource
    private RedisCache redisCache;

    @GetMapping("/captchaImage")
    public Result<Map<String, String>> getCode() {
        // 生成验证码:纯大写字母+数字(去除易混淆字符 O/I/L/0/1),配合后端大小写敏感校验
        CircleCaptcha captcha = CaptchaUtil.createCircleCaptcha(200, 100, 4, 20);
        captcha.setGenerator(new RandomGenerator("ABCDEFGHJKMNPQRSTUVWXYZ23456789", 4));
        captcha.createCode();
        String uuid = IdUtil.simpleUUID();
        String code = captcha.getCode();
        
        // 保存到缓存 (生产环境用 Redis，过期时间 2分钟)
        String verifyKey = SysLoginService.CAPTCHA_CODE_KEY + uuid;
        redisCache.setCacheObject(verifyKey, code, 2, TimeUnit.MINUTES);

        Map<String, String> ajax = new HashMap<>();
        ajax.put("uuid", uuid);
        ajax.put("img", captcha.getImageBase64());
        return Result.success(ajax);
    }
}

