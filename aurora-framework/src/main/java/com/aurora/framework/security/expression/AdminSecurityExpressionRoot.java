package com.aurora.framework.security.expression;

import com.aurora.common.core.constant.Constants;
import com.aurora.common.core.domain.model.LoginUser;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;

/**
 * 自定义安全表达式根对象
 * 添加自定义方法支持超级管理员自动通过所有权限检查
 */
public class AdminSecurityExpressionRoot extends SecurityExpressionRoot implements MethodSecurityExpressionOperations {

    private static final String ADMIN_PERMISSION = Constants.ALL_PERMISSION;

    public AdminSecurityExpressionRoot(Authentication authentication) {
        super(authentication);
    }

    /**
     * 自定义权限检查方法：超级管理员或拥有指定权限
     * 在 @PreAuthorize 中使用：@PreAuthorize("hasAdminOrAuthority('system:menu:list')")
     */
    public boolean hasAdminOrAuthority(String authority) {
        Authentication auth = getAuthentication();
        // 检查是否是超级管理员
        if (isAdmin(auth)) {
            return true;
        }
        // 调用父类的 hasAuthority 方法进行普通权限检查
        boolean hasAuth = hasAuthority(authority);
        return hasAuth;
    }

    /**
     * 检查是否是超级管理员
     * 基于角色标识判断，不再硬编码 userId == 1
     */
    private boolean isAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        // 检查 LoginUser 中的 isAdmin 标识
        if (authentication.getPrincipal() instanceof LoginUser) {
            LoginUser loginUser = (LoginUser) authentication.getPrincipal();
            if (Boolean.TRUE.equals(loginUser.getIsAdmin())) {
                return true;
            }
        }

        // 检查是否有 *:*:* 权限（兜底逻辑）
        return authentication.getAuthorities().stream()
                .anyMatch(auth -> ADMIN_PERMISSION.equals(auth.getAuthority()));
    }

    @Override
    public void setFilterObject(Object filterObject) {
        // 不需要实现
    }

    @Override
    public Object getFilterObject() {
        return null;
    }

    @Override
    public void setReturnObject(Object returnObject) {
        // 不需要实现
    }

    @Override
    public Object getReturnObject() {
        return null;
    }

    @Override
    public Object getThis() {
        return this;
    }
}
