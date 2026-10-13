package com.aurora.admin.controller.mobile;

import com.aurora.admin.mobile.domain.MobileUser;
import com.aurora.admin.mobile.domain.UserMembership;
import com.aurora.admin.mobile.dto.MembershipLevelAdjustDTO;
import com.aurora.admin.mobile.service.IBeanAccountService;
import com.aurora.admin.mobile.service.IMembershipLevelService;
import com.aurora.admin.mobile.service.IMobileUserService;
import com.aurora.admin.mobile.service.IUserMembershipService;
import com.aurora.admin.mobile.vo.MembershipLevelOptionVO;
import com.aurora.common.core.annotation.Log;
import com.aurora.common.core.domain.Result;
import com.aurora.common.core.enums.BusinessType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 移动端用户 管理后台控制器
 * 仅供后台运营查看与启禁用,密码字段一律不返回前端。
 */
@Tag(name = "移动端用户", description = "移动端用户管理")
@RestController
@RequestMapping("/system/mobile/user")
@RequiredArgsConstructor
public class MobileUserAdminController {

    private final IMobileUserService mobileUserService;
    private final IBeanAccountService beanAccountService;
    private final IUserMembershipService userMembershipService;
    private final IMembershipLevelService membershipLevelService;

    /**
     * 分页查询移动端用户列表(密码字段不返回)
     */
    @Operation(summary = "分页查询列表", description = "分页查询移动端用户,keyword 对昵称/手机号/用户编号做 OR 模糊匹配")
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('mobile:user:list')")
    public Result<IPage<MobileUser>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword) {

        LambdaQueryWrapper<MobileUser> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(MobileUser::getNickName, keyword)
                    .or().like(MobileUser::getPhone, keyword)
                    .or().like(MobileUser::getUserNo, keyword));
        }
        wrapper.orderByDesc(MobileUser::getCreateTime);

        Page<MobileUser> page = new Page<>(pageNum, pageSize);
        IPage<MobileUser> pageResult = mobileUserService.page(page, wrapper);
        List<MobileUser> records = pageResult.getRecords();
        // 批量关联 bean_account 填充余额列(无账户视为 0),避免逐行查询
        List<Long> userIds = records.stream().map(MobileUser::getUserId).collect(Collectors.toList());
        Map<Long, Long> balanceMap = beanAccountService.balanceMapByUserIds(userIds);
        // 批量计算当前最高有效会员等级名(非会员为 null),避免 N+1
        Map<Long, UserMembership> membershipMap = userMembershipService.currentLevelMapByUserIds(userIds);
        records.forEach(user -> {
            // 密码字段不返回前端
            user.setPassword(null);
            user.setBalance(balanceMap.getOrDefault(user.getUserId(), 0L));
            UserMembership membership = membershipMap.get(user.getUserId());
            user.setMembershipLevel(membership == null ? null : membership.getLevelName());
            user.setMembershipLevelId(membership == null ? null : membership.getLevelId());
        });
        return Result.success(pageResult);
    }

    /**
     * 查询移动端用户列表可选择的已启用会员等级。
     */
    @Operation(summary = "会员等级选项", description = "仅返回会员等级管理中已启用的等级")
    @GetMapping("/membership-level-options")
    @PreAuthorize("@ss.hasAnyPermi('mobile:user:list', 'mobile:user:edit')")
    public Result<List<MembershipLevelOptionVO>> membershipLevelOptions() {
        return Result.success(membershipLevelService.listBySort().stream()
                .filter(level -> "0".equals(level.getStatus()))
                .map(MembershipLevelOptionVO::from)
                .toList());
    }

    /**
     * 调整当前会员等级；null 表示无会员。
     */
    @Operation(summary = "调整会员等级", description = "不改变当前会员期限且不发放豆子")
    @PutMapping("/{id}/membership")
    @PreAuthorize("@ss.hasPermi('mobile:user:edit')")
    @Log(title = "移动端用户会员等级", businessType = BusinessType.UPDATE)
    public Result<Void> editMembership(@PathVariable Long id,
                                       @RequestBody MembershipLevelAdjustDTO body) {
        userMembershipService.adjustCurrentLevel(id, body == null ? null : body.getLevelId());
        return Result.success();
    }

    /**
     * 启用/停用移动端用户(更新 status:0正常/1停用)
     */
    @Operation(summary = "启禁用", description = "更新移动端用户状态")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('mobile:user:edit')")
    public Result<Void> editStatus(@PathVariable Long id, @RequestBody MobileUser body) {
        if (body.getStatus() == null || body.getStatus().isEmpty()) {
            return Result.error("状态不能为空");
        }
        MobileUser existing = mobileUserService.getById(id);
        if (existing == null) {
            return Result.error("用户不存在");
        }

        MobileUser update = new MobileUser();
        update.setUserId(id);
        update.setStatus(body.getStatus());
        mobileUserService.updateById(update);
        return Result.success();
    }
}
