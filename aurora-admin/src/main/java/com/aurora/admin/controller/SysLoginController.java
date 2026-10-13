package com.aurora.admin.controller;

import com.aurora.common.core.domain.Result;
import com.aurora.common.core.constant.Constants;
import com.aurora.common.core.domain.entity.SysMenu;
import com.aurora.common.core.domain.entity.SysRole;
import com.aurora.common.core.domain.entity.SysUser;
import com.aurora.common.core.domain.model.LoginBody;
import com.aurora.common.core.domain.model.LoginUser;
import com.aurora.common.core.service.ISysMenuService;
import com.aurora.common.core.service.ISysUserService;
import com.aurora.framework.annotation.RateLimiter;
import com.aurora.framework.web.service.SysLoginService;
import com.aurora.framework.web.service.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Tag(name = "登录管理", description = "用户登录、获取用户信息、菜单路由等接口")
@RestController
public class SysLoginController {

    private final SysLoginService loginService;
    private final TokenService tokenService;
    private final ISysMenuService menuService;
    private final ISysUserService userService;

    public SysLoginController(SysLoginService loginService, TokenService tokenService, ISysMenuService menuService,
                              ISysUserService userService) {
        this.loginService = loginService;
        this.tokenService = tokenService;
        this.menuService = menuService;
        this.userService = userService;
    }

    /**
     * 登录方法
     */
    @Operation(summary = "用户登录", description = "用户登录接口，返回JWT Token")
    @PostMapping("/login")
    @RateLimiter(time = 60, count = 5, message = "登录请求过于频繁，请1分钟后再试")
    public Result<Map<String, String>> login(@Valid @RequestBody LoginBody loginBody) {
        String token = loginService.login(loginBody.getUsername(), loginBody.getPassword(), loginBody.getCode(),
                loginBody.getUuid());
        Map<String, String> ajax = new HashMap<>();
        ajax.put("token", token);
        return Result.success(ajax);
    }

    /**
     * 退出登录:删除服务端 Redis 会话,使该令牌立即失效
     */
    @Operation(summary = "退出登录", description = "删除服务端会话使令牌立即失效")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        LoginUser loginUser = tokenService.getLoginUser(request);
        if (loginUser != null && loginUser.getToken() != null) {
            tokenService.deleteLoginUser(loginUser.getToken());
        }
        return Result.success();
    }

    /**
     * 获取用户信息
     */
    @Operation(summary = "获取用户信息", description = "获取当前登录用户的基本信息、角色和权限")
    @GetMapping("/getInfo")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> getInfo(HttpServletRequest request) {
        LoginUser loginUser = tokenService.getLoginUser(request);
        if (loginUser == null) {
            return Result.error("用户未登录或登录已过期");
        }

        // 角色及菜单可能在用户登录后被管理员调整。角色和超管标识也必须从数据库重算，
        // 不能沿用 Redis 中的 isAdmin，否则撤销超管角色后旧会话仍会保留通配权限。
        SysUser currentUser = userService.selectUserByUserId(loginUser.getUserId());
        if (currentUser == null) {
            tokenService.deleteLoginUser(loginUser.getToken());
            return Result.error(401, "用户不存在、已停用或登录已过期");
        }
        boolean currentAdmin = currentUser.isAdmin();
        Set<String> selectedPermissions = menuService.selectPermsByUserId(loginUser.getUserId());
        Set<String> currentPermissions = new HashSet<>(
                selectedPermissions == null ? Set.of() : selectedPermissions);
        if (currentAdmin) {
            currentPermissions.add(Constants.ALL_PERMISSION);
        }

        Set<Long> previousRoleIds = roleIds(loginUser.getUser());
        Set<Long> currentRoleIds = roleIds(currentUser);
        boolean sessionChanged = !currentPermissions.equals(loginUser.getPermissions())
                || !Objects.equals(currentAdmin, loginUser.getIsAdmin())
                || !currentRoleIds.equals(previousRoleIds);

        loginUser.setUser(currentUser);
        loginUser.setUserId(currentUser.getUserId());
        loginUser.setDeptId(currentUser.getDeptId());
        loginUser.setIsAdmin(currentAdmin);
        loginUser.setPermissions(currentPermissions);
        if (sessionChanged) {
            tokenService.refreshToken(loginUser);
        }

        Map<String, Object> ajax = new HashMap<>();
        ajax.put("user", loginUser.getUser());
        ajax.put("roles", loginUser.getUser().getRoles());
        ajax.put("permissions", currentPermissions);
        return Result.success(ajax);
    }

    private Set<Long> roleIds(SysUser user) {
        if (user == null || user.getRoles() == null) {
            return Set.of();
        }
        return user.getRoles().stream()
                .map(SysRole::getRoleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 获取路由信息（菜单树）
     */
    @Operation(summary = "获取菜单路由", description = "获取当前用户可访问的菜单树，用于前端动态路由")
    @GetMapping("/getRouters")
    @PreAuthorize("isAuthenticated()")
    public Result<List<SysMenu>> getRouters(HttpServletRequest request) {
        LoginUser loginUser = tokenService.getLoginUser(request);
        if (loginUser == null) {
            return Result.error("用户未登录或登录已过期");
        }

        List<SysMenu> menus = menuService.selectMenuTreeByUserId(loginUser.getUserId());
        return Result.success(menus);
    }
}
