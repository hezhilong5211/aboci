package com.aurora.framework.web.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.aurora.common.core.constant.CacheConstants;
import com.aurora.common.core.domain.model.LoginUser;
import com.aurora.common.core.exception.ServiceException;
import com.aurora.common.core.redis.RedisCache;
import jakarta.annotation.Resource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * 登录校验方法
 */
@Service
public class SysLoginService {

    @Resource
    private AuthenticationManager authenticationManager;

    @Resource
    private TokenService tokenService;

    @Resource
    private RedisCache redisCache;

    // 验证码 Redis 前缀(收口到 CacheConstants)
    public static final String CAPTCHA_CODE_KEY = CacheConstants.CAPTCHA_CODE_KEY;

    // 密码错误计数前缀 + 账号锁定阈值/时长
    private static final String PWD_ERR_PREFIX = CacheConstants.PWD_ERR_CNT_KEY;
    private static final int MAX_PWD_ERR = 5;
    private static final int LOCK_MINUTES = 10;

    /**
     * 登录验证
     * 
     * @param username 用户名
     * @param password 密码
     * @param code 验证码
     * @param uuid 唯一标识
     * @return 结果
     */
    public String login(String username, String password, String code, String uuid) {
        // 验证码校验
        validateCaptcha(username, code, uuid);

        // 账号级失败锁定:密码错误累计达上限则在锁定期内直接拒绝(独立于可伪造的 IP)
        String errKey = PWD_ERR_PREFIX + username;
        Integer errCnt = redisCache.getCacheObject(errKey);
        if (errCnt != null && errCnt >= MAX_PWD_ERR) {
            throw new ServiceException("密码错误次数过多，账号已锁定，请 " + LOCK_MINUTES + " 分钟后再试");
        }

        // 用户验证
        Authentication authentication = null;
        try {
            // 该方法会去调用 UserDetailsServiceImpl.loadUserByUsername
            authentication = authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(username, password));
        } catch (Exception e) {
            if (e instanceof BadCredentialsException) {
                // 累加失败计数(锁定窗口内)
                int c = (errCnt == null ? 0 : errCnt) + 1;
                redisCache.setCacheObject(errKey, c, LOCK_MINUTES, TimeUnit.MINUTES);
                throw new ServiceException("用户不存在或密码错误");
            } else {
                // 其它认证异常对外统一文案，原始原因仅由全局异常处理器写入日志，避免泄露内部细节
                throw new ServiceException("登录失败，请稍后重试", e);
            }
        }

        // 登录成功:清除失败计数
        redisCache.deleteObject(errKey);

        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        // 抹掉密码哈希,避免随 LoginUser 缓存进 Redis(纵深防御)
        if (loginUser.getUser() != null) {
            loginUser.getUser().setPassword(null);
        }
        // 生成token
        return tokenService.createToken(loginUser);
    }

    /**
     * 校验验证码
     */
    public void validateCaptcha(String username, String code, String uuid) {
        if (StrUtil.isEmpty(uuid)) {
            throw new ServiceException("验证码uuid不能为空");
        }
        if (StrUtil.isEmpty(code)) {
            throw new ServiceException("验证码不能为空");
        }
        String verifyKey = CAPTCHA_CODE_KEY + uuid;
        String captcha = redisCache.getCacheObject(verifyKey);
        // 一次性使用
        redisCache.deleteObject(verifyKey);

        if (captcha == null) {
            throw new ServiceException("验证码已失效");
        }
        // 大小写敏感比对(验证码已生成为纯大写字母+数字,无大小写歧义,提升爆破成本)
        if (!code.equals(captcha)) {
            throw new ServiceException("验证码错误");
        }
    }
}

