package com.aurora.framework.web.service;

import com.aurora.common.core.service.ISysUserService;
import com.aurora.common.core.service.ISysMenuService;
import com.aurora.common.core.domain.entity.SysUser;
import com.aurora.common.core.domain.model.LoginUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 用户验证处理
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UserDetailsServiceImpl.class);

    private final ISysUserService userService;
    private final ISysMenuService menuService;

    public UserDetailsServiceImpl(ISysUserService userService, ISysMenuService menuService) {
        this.userService = userService;
        this.menuService = menuService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 去除用户名前后的空格
        username = username.trim();
        log.info("正在尝试加载用户: {}", username);
        SysUser user = userService.selectUserByUserName(username);
        if (user == null) {
            log.info("登录用户：{} 不存在", username);
            throw new UsernameNotFoundException("登录用户：" + username + " 不存在");
        } else if ("2".equals(user.getDelFlag())) {
            log.info("登录用户：{} 已被删除", username);
            throw new UsernameNotFoundException("对不起，您的账号：" + username + " 已被删除");
        } else if ("1".equals(user.getStatus())) {
            log.info("登录用户：{} 已被停用", username);
            throw new UsernameNotFoundException("对不起，您的账号：" + username + " 已被停用");
        }

        log.info("用户加载成功: {}", user.getUserName());

        // 查询用户权限
        Set<String> permissions = menuService.selectPermsByUserId(user.getUserId());
        log.info("用户 {} 的权限: {}", user.getUserName(), permissions);

        return createLoginUser(user, permissions);
    }

    /**
     * 创建登录用户对象
     * 根据用户关联的角色判断是否为超级管理员
     */
    public UserDetails createLoginUser(SysUser user, Set<String> permissions) {
        LoginUser loginUser = new LoginUser(user, permissions);
        // 设置超级管理员标识：通过用户关联的角色判断
        if (user.isAdmin()) {
            loginUser.setIsAdmin(true);
            log.info("用户 {} 为超级管理员", user.getUserName());
        }
        return loginUser;
    }
}
