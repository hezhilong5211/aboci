package com.aurora.admin.controller.mobile;

import com.aurora.admin.mobile.dto.GrantMembershipDTO;
import com.aurora.admin.mobile.service.IUserMembershipService;
import com.aurora.admin.mobile.vo.MembershipUserVO;
import com.aurora.common.core.annotation.Log;
import com.aurora.common.core.domain.Result;
import com.aurora.common.core.enums.BusinessType;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会员开通 + 会员列表 管理后台控制器
 * 开通走叠加制(每次新建一段会员 + 发订阅豆);列表只列当前有效会员,带当前最高有效等级。
 */
@Tag(name = "会员列表", description = "会员手动开通与会员列表")
@RestController
@RequestMapping("/system/membership")
@RequiredArgsConstructor
public class MembershipUserController {

    private final IUserMembershipService userMembershipService;

    /**
     * 手动开通会员(叠加制:新建一段会员 + 发订阅豆)
     */
    @Operation(summary = "手动开通会员", description = "叠加制:按所选等级配置新建会员段并发放订阅豆")
    @PostMapping("/grant")
    @PreAuthorize("@ss.hasPermi('membership:user:grant')")
    @Log(title = "会员开通", businessType = BusinessType.INSERT)
    public Result<Void> grant(@RequestBody GrantMembershipDTO dto) {
        if (dto.getUserId() == null) {
            return Result.error("userId 不能为空");
        }
        if (dto.getLevelId() == null) {
            return Result.error("levelId 不能为空");
        }
        userMembershipService.grant(dto.getUserId(), dto.getLevelId(), dto.getDurationDays());
        return Result.success();
    }

    /**
     * 会员列表(只列当前有效会员,带当前最高有效等级)
     */
    @Operation(summary = "会员列表", description = "只列当前有效会员;keyword 匹配昵称/手机号,levelId 按当前等级筛选")
    @GetMapping("/user/list")
    @PreAuthorize("@ss.hasPermi('membership:user:list')")
    public Result<IPage<MembershipUserVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long levelId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(userMembershipService.pageMembers(keyword, levelId, pageNum, pageSize));
    }
}
