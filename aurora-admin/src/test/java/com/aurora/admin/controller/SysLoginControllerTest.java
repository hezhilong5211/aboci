package com.aurora.admin.controller;

import com.aurora.common.core.constant.Constants;
import com.aurora.common.core.domain.Result;
import com.aurora.common.core.domain.entity.SysRole;
import com.aurora.common.core.domain.entity.SysUser;
import com.aurora.common.core.domain.model.LoginUser;
import com.aurora.common.core.service.ISysMenuService;
import com.aurora.common.core.service.ISysUserService;
import com.aurora.framework.web.service.SysLoginService;
import com.aurora.framework.web.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysLoginControllerTest {

    @Mock
    private SysLoginService loginService;
    @Mock
    private TokenService tokenService;
    @Mock
    private ISysMenuService menuService;
    @Mock
    private ISysUserService userService;
    @Mock
    private HttpServletRequest request;

    private SysLoginController controller;

    @BeforeEach
    void setUp() {
        controller = new SysLoginController(loginService, tokenService, menuService, userService);
    }

    @Test
    void getInfoRefreshesChangedRolePermissionsIntoSession() {
        LoginUser loginUser = loginUser(115L, Set.of("skill:add"), false);
        when(tokenService.getLoginUser(request)).thenReturn(loginUser);
        when(userService.selectUserByUserId(115L)).thenReturn(user(115L, false));
        when(menuService.selectPermsByUserId(115L)).thenReturn(Set.of("skill:list", "skill:edit"));

        Result<Map<String, Object>> result = controller.getInfo(request);

        assertEquals(Result.SUCCESS, result.getCode());
        assertEquals(Set.of("skill:list", "skill:edit"), result.getData().get("permissions"));
        assertEquals(Set.of("skill:list", "skill:edit"), loginUser.getPermissions());
        verify(tokenService).refreshToken(loginUser);
    }

    @Test
    void getInfoDoesNotRewriteUnchangedSession() {
        Set<String> permissions = Set.of("skill:list");
        LoginUser loginUser = loginUser(115L, permissions, false);
        when(tokenService.getLoginUser(request)).thenReturn(loginUser);
        when(userService.selectUserByUserId(115L)).thenReturn(user(115L, false));
        when(menuService.selectPermsByUserId(115L)).thenReturn(permissions);

        controller.getInfo(request);

        verify(tokenService, never()).refreshToken(loginUser);
    }

    @Test
    void getInfoExposesWildcardForAdminRole() {
        LoginUser loginUser = loginUser(9L, Set.of("skill:list"), true);
        when(tokenService.getLoginUser(request)).thenReturn(loginUser);
        when(userService.selectUserByUserId(9L)).thenReturn(user(9L, true));
        when(menuService.selectPermsByUserId(9L)).thenReturn(Set.of("skill:list"));

        Result<Map<String, Object>> result = controller.getInfo(request);

        @SuppressWarnings("unchecked")
        Set<String> permissions = (Set<String>) result.getData().get("permissions");
        assertTrue(permissions.contains(Constants.ALL_PERMISSION));
        verify(tokenService).refreshToken(loginUser);
    }

    @Test
    void getInfoRevokesWildcardWhenAdminRoleWasRemoved() {
        LoginUser loginUser = loginUser(9L, Set.of(Constants.ALL_PERMISSION, "skill:list"), true);
        when(tokenService.getLoginUser(request)).thenReturn(loginUser);
        when(userService.selectUserByUserId(9L)).thenReturn(user(9L, false));
        when(menuService.selectPermsByUserId(9L)).thenReturn(Set.of("skill:list"));

        Result<Map<String, Object>> result = controller.getInfo(request);

        assertEquals(Set.of("skill:list"), result.getData().get("permissions"));
        assertEquals(false, loginUser.getIsAdmin());
        verify(tokenService).refreshToken(loginUser);
    }

    @Test
    void getInfoInvalidatesSessionWhenUserWasDisabledOrDeleted() {
        LoginUser loginUser = loginUser(9L, Set.of(Constants.ALL_PERMISSION), true);
        when(tokenService.getLoginUser(request)).thenReturn(loginUser);
        when(userService.selectUserByUserId(9L)).thenReturn(null);

        Result<Map<String, Object>> result = controller.getInfo(request);

        assertEquals(401, result.getCode());
        verify(tokenService).deleteLoginUser("test-token");
        verifyNoInteractions(menuService);
    }

    private LoginUser loginUser(Long userId, Set<String> permissions, boolean admin) {
        SysUser user = user(userId, admin);
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(userId);
        loginUser.setUser(user);
        loginUser.setPermissions(new HashSet<>(permissions));
        loginUser.setIsAdmin(admin);
        loginUser.setToken("test-token");
        return loginUser;
    }

    private SysUser user(Long userId, boolean admin) {
        SysUser user = new SysUser();
        user.setUserId(userId);
        SysRole role = new SysRole();
        role.setRoleId(admin ? 1L : 2L);
        role.setIsAdmin(admin ? 1 : 0);
        user.setRoles(List.of(role));
        return user;
    }
}
