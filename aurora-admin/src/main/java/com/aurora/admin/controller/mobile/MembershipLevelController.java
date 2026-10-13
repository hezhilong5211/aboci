package com.aurora.admin.controller.mobile;

import com.aurora.admin.mobile.domain.MembershipLevel;
import com.aurora.admin.mobile.service.IMembershipLevelService;
import com.aurora.common.core.annotation.Log;
import com.aurora.common.core.domain.Result;
import com.aurora.common.core.enums.BusinessType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会员等级配置 管理后台控制器(CRUD)
 * 删除为逻辑删(@TableLogic),仅从配置列表与可开通选项消失,历史快照不受影响。
 */
@Tag(name = "会员等级", description = "会员等级配置 CRUD")
@RestController
@RequestMapping("/system/membership/level")
@RequiredArgsConstructor
public class MembershipLevelController {

    private final IMembershipLevelService membershipLevelService;

    /**
     * 等级列表(按 sort 升序)
     */
    @Operation(summary = "等级列表", description = "按 sort 升序返回所有未删除等级")
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('membership:level:list')")
    public Result<List<MembershipLevel>> list() {
        return Result.success(membershipLevelService.listBySort());
    }

    /**
     * 新增等级
     */
    @Operation(summary = "新增等级")
    @PostMapping
    @PreAuthorize("@ss.hasPermi('membership:level:add')")
    @Log(title = "会员等级", businessType = BusinessType.INSERT)
    public Result<Void> add(@RequestBody MembershipLevel level) {
        clearServerControlledFields(level);
        level.setLevelId(null);
        membershipLevelService.save(level);
        return Result.success();
    }

    /**
     * 修改等级
     */
    @Operation(summary = "修改等级")
    @PutMapping
    @PreAuthorize("@ss.hasPermi('membership:level:edit')")
    @Log(title = "会员等级", businessType = BusinessType.UPDATE)
    public Result<Void> edit(@RequestBody MembershipLevel level) {
        if (level.getLevelId() == null) {
            return Result.error("等级ID不能为空");
        }
        clearServerControlledFields(level);
        membershipLevelService.updateById(level);
        return Result.success();
    }

    /**
     * 删除等级(逻辑删)
     */
    @Operation(summary = "删除等级", description = "逻辑删,历史会员快照不受影响")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('membership:level:remove')")
    @Log(title = "会员等级", businessType = BusinessType.DELETE)
    public Result<Void> remove(@PathVariable Long id) {
        membershipLevelService.removeById(id);
        return Result.success();
    }

    /**
     * 清空服务端控制字段,防止前端篡改审计/逻辑删字段(R-DTO 防 Mass-Assignment)。
     * 审计字段交由 MybatisMetaObjectHandler 自动回填,删除标记由 @TableLogic 管理。
     */
    private void clearServerControlledFields(MembershipLevel level) {
        level.setCreateBy(null);
        level.setCreateTime(null);
        level.setUpdateBy(null);
        level.setUpdateTime(null);
        level.setDelFlag(null);
    }
}
