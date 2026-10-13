package com.aurora.admin.mobile.controller;

import com.aurora.admin.mobile.dto.UpdateUserDTO;
import com.aurora.admin.mobile.service.IMobileLoginService;
import com.aurora.admin.mobile.service.IUserMembershipService;
import com.aurora.admin.mobile.vo.MembershipInfoVO;
import com.aurora.admin.mobile.vo.UserInfoVO;
import com.aurora.common.core.domain.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 移动端用户信息控制器(均需鉴权)
 */
@Tag(name = "移动端用户", description = "获取/更新当前登录用户信息")
@RestController
@RequestMapping("/api/mobile/user")
@RequiredArgsConstructor
public class MobileUserController {

    private final IMobileLoginService mobileLoginService;
    private final IUserMembershipService userMembershipService;

    /**
     * 获取当前用户信息
     */
    @Operation(summary = "获取当前用户信息")
    @GetMapping("/info")
    public Result<UserInfoVO> info(@RequestAttribute("mobileUserId") Long userId) {
        return Result.success(mobileLoginService.getUserInfo(userId));
    }

    /**
     * 更新用户信息(白名单:昵称/头像/性别)
     */
    @Operation(summary = "更新用户信息")
    @PutMapping("/info")
    public Result<UserInfoVO> update(@RequestBody UpdateUserDTO dto,
                                     @RequestAttribute("mobileUserId") Long userId) {
        return Result.success(mobileLoginService.updateUserInfo(userId, dto));
    }

    /**
     * 获取当前用户会员信息(非会员各字段为空/0)
     */
    @Operation(summary = "获取当前用户会员信息",
            description = "查询前会立即结算已到期会员，最后一个有效段到期后返回非会员状态")
    @GetMapping("/membership")
    public Result<MembershipInfoVO> membership(@RequestAttribute("mobileUserId") Long userId) {
        return Result.success(userMembershipService.getCurrentMembership(userId));
    }
}
