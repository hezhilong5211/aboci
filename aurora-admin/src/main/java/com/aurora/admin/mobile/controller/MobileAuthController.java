package com.aurora.admin.mobile.controller;

import com.aurora.admin.mobile.dto.RefreshTokenDTO;
import com.aurora.admin.mobile.dto.WxLoginDTO;
import com.aurora.admin.mobile.dto.WxPhoneLoginDTO;
import com.aurora.admin.mobile.service.IMobileLoginService;
import com.aurora.admin.mobile.vo.LoginVO;
import com.aurora.admin.mobile.vo.TokenVO;
import com.aurora.common.core.domain.Result;
import com.aurora.framework.annotation.RateLimiter;
import com.aurora.framework.mobile.MobileTokenService;
import com.aurora.framework.mobile.TokenPair;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 移动端认证控制器:微信登录/补绑手机号/刷新令牌
 */
@Tag(name = "移动端认证", description = "微信一键登录、静默登录、补绑手机号、刷新令牌")
@RestController
@RequestMapping("/api/mobile")
@RequiredArgsConstructor
public class MobileAuthController {

    private final IMobileLoginService mobileLoginService;
    private final MobileTokenService mobileTokenService;

    /**
     * 微信手机号一键登录(免鉴权)
     */
    @Operation(summary = "微信手机号一键登录")
    @RateLimiter(time = 60, count = 10, limitType = RateLimiter.LimitType.IP, message = "登录请求过于频繁，请稍后再试")
    @PostMapping("/wx/phoneLogin")
    public Result<LoginVO> wxPhoneLogin(@RequestBody WxPhoneLoginDTO dto) {
        if (dto.getLoginCode() == null || dto.getLoginCode().isEmpty()
                || dto.getPhoneCode() == null || dto.getPhoneCode().isEmpty()) {
            return Result.error("loginCode 和 phoneCode 不能为空");
        }
        return Result.success(mobileLoginService.wxPhoneLogin(dto.getLoginCode(), dto.getPhoneCode()), "登录成功");
    }

    /**
     * 仅 openid 静默登录(免鉴权)
     */
    @Operation(summary = "微信静默登录")
    @RateLimiter(time = 60, count = 10, limitType = RateLimiter.LimitType.IP, message = "登录请求过于频繁，请稍后再试")
    @PostMapping("/wx/login")
    public Result<LoginVO> wxLogin(@RequestBody WxLoginDTO dto) {
        if (dto.getCode() == null || dto.getCode().isEmpty()) {
            return Result.error("code 不能为空");
        }
        return Result.success(mobileLoginService.wxLogin(dto.getCode()), "登录成功");
    }

    /**
     * 已登录用户刷新微信 session_key（虚拟支付签名前使用）。
     */
    @Operation(summary = "刷新微信登录状态", description = "核对 code2Session 的 openid 后更新服务端 session_key")
    @PostMapping("/wx/session/refresh")
    public Result<Void> refreshWxSession(
            @RequestBody WxLoginDTO dto,
            @RequestAttribute("mobileUserId") Long userId) {
        if (dto.getCode() == null || dto.getCode().isEmpty()) {
            return Result.error("code 不能为空");
        }
        mobileLoginService.refreshWxSession(userId, dto.getCode());
        return Result.success();
    }

    /**
     * 已登录用户补绑手机号(需鉴权)
     */
    @Operation(summary = "补绑手机号")
    @PostMapping("/wx/bindPhone")
    public Result<LoginVO> wxBindPhone(@RequestBody WxLoginDTO dto,
                                       @RequestAttribute("mobileUserId") Long userId) {
        if (dto.getCode() == null || dto.getCode().isEmpty()) {
            return Result.error("code 不能为空");
        }
        return Result.success(mobileLoginService.wxBindPhone(userId, dto.getCode()), "绑定成功");
    }

    /**
     * 刷新令牌(免鉴权,凭 refreshToken 换新双令牌)
     */
    @Operation(summary = "刷新令牌")
    @RateLimiter(key = "mobile:refresh-token", time = 60, count = 20,
            limitType = RateLimiter.LimitType.IP, message = "刷新请求过于频繁，请稍后再试")
    @PostMapping("/refreshToken")
    public Result<TokenVO> refreshToken(@RequestBody RefreshTokenDTO dto) {
        if (dto.getRefreshToken() == null || dto.getRefreshToken().isEmpty()) {
            return Result.error("refreshToken 不能为空");
        }
        TokenPair tokens = mobileTokenService.refresh(dto.getRefreshToken());
        return Result.success(TokenVO.of(tokens));
    }
}
