package com.aurora.common.core.service.impl;

import com.aurora.common.core.constant.CacheConstants;
import com.aurora.common.core.mapper.SysUserMapper;
import com.aurora.common.core.service.ISysUserService;
import com.aurora.common.core.domain.entity.SysUser;
import com.aurora.common.core.domain.model.LoginUser;
import com.aurora.common.core.redis.RedisCache;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements ISysUserService {

    private final RedisCache redisCache;

    public SysUserServiceImpl(RedisCache redisCache) {
        this.redisCache = redisCache;
    }

    @Override
    public SysUser selectUserByUserName(String userName) {
        // 使用XML中的关联查询，一次性获取用户及其角色信息
        return baseMapper.selectUserByUserNameWithRoles(userName);
    }

    @Override
    public SysUser selectUserByUserId(Long userId) {
        return baseMapper.selectUserByUserIdWithRoles(userId);
    }

    @Override
    public void refreshSelfLoginCacheAfterEdit(Long userId) {
        // 守卫:仅当编辑的是"当前登录者自己"时才刷新会话缓存;编辑他人不刷新(防跨用户覆盖)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser)) {
            return;
        }
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        if (!userId.equals(loginUser.getUserId())) {
            return;
        }
        SysUser updatedUser = this.getById(userId);
        if (updatedUser == null) {
            return;
        }
        // 用最新 DB 信息更新会话内 LoginUser
        loginUser.getUser().setUserName(updatedUser.getUserName());
        loginUser.getUser().setNickName(updatedUser.getNickName());
        loginUser.getUser().setEmail(updatedUser.getEmail());
        loginUser.getUser().setPhonenumber(updatedUser.getPhonenumber());
        loginUser.getUser().setAvatar(updatedUser.getAvatar());
        // 刷新 Redis 会话缓存(过期时间对齐当前 token 剩余有效期)
        String token = loginUser.getToken();
        if (token != null && !token.isEmpty()) {
            String userKey = CacheConstants.LOGIN_TOKEN_KEY + token;
            long expireSeconds = (loginUser.getExpireTime() - System.currentTimeMillis()) / 1000;
            redisCache.setCacheObject(userKey, loginUser, (int) expireSeconds, TimeUnit.SECONDS);
        }
    }
}
