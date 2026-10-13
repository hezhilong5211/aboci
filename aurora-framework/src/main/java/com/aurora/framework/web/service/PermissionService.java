package com.aurora.framework.web.service;

import com.aurora.common.core.constant.Constants;
import com.aurora.common.core.domain.model.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/**
 * 权限校验服务
 * 用于 SpEL 表达式中的权限校验，如 @PreAuthorize("@ss.hasPermi('system:user:list')")
 */
@Service("ss")
public class PermissionService {

    /** 所有权限标识 */
    private static final String ALL_PERMISSION = Constants.ALL_PERMISSION;

    /**
     * 验证用户是否具有指定权限
     *
     * @param permission 权限字符串
     * @return 用户是否具有该权限
     */
    public boolean hasPermi(String permission) {
        if (permission == null || permission.isEmpty()) {
            return false;
        }
        LoginUser loginUser = getLoginUser();
        if (loginUser == null || CollectionUtils.isEmpty(loginUser.getPermissions())) {
            return false;
        }
        // 超级管理员拥有所有权限
        if (Boolean.TRUE.equals(loginUser.getIsAdmin())) {
            return true;
        }
        // 检查是否拥有所有权限标识
        if (loginUser.getPermissions().contains(ALL_PERMISSION)) {
            return true;
        }
        // 检查是否拥有指定权限
        return loginUser.getPermissions().contains(permission);
    }

    /**
     * 验证用户是否具有指定角色
     *
     * @param role 角色字符串
     * @return 用户是否具有该角色
     */
    public boolean hasRole(String role) {
        if (role == null || role.isEmpty()) {
            return false;
        }
        LoginUser loginUser = getLoginUser();
        if (loginUser == null || loginUser.getUser() == null) {
            return false;
        }
        // 超级管理员拥有所有角色
        if (Boolean.TRUE.equals(loginUser.getIsAdmin())) {
            return true;
        }
        // 检查用户角色（需要 SysUser 中有角色信息）
        return loginUser.getUser().getRoles() != null &&
                loginUser.getUser().getRoles().stream()
                        .anyMatch(r -> role.equals(r.getRoleKey()));
    }

    /**
     * 验证用户是否具有任一权限
     *
     * @param permissions 权限数组
     * @return 用户是否具有任一权限
     */
    public boolean hasAnyPermi(String... permissions) {
        if (permissions == null || permissions.length == 0) {
            return false;
        }
        for (String permission : permissions) {
            if (hasPermi(permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取当前登录用户
     */
    private LoginUser getLoginUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return null;
            }
            Object principal = authentication.getPrincipal();
            if (principal instanceof LoginUser) {
                return (LoginUser) principal;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
