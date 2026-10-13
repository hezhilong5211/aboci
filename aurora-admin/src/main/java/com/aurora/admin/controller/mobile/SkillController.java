package com.aurora.admin.controller.mobile;

import com.aurora.admin.mobile.domain.Skill;
import com.aurora.admin.mobile.dto.SkillStatusDTO;
import com.aurora.admin.mobile.service.ISkillService;
import com.aurora.common.core.annotation.Log;
import com.aurora.common.core.domain.Result;
import com.aurora.common.core.enums.BusinessType;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Skill 配置 管理后台控制器(CRUD + 启停)
 * 本期仅管理后台,不含移动端 @ 列表读接口;删除为逻辑删(@TableLogic)。
 */
@Tag(name = "Skill 配置", description = "Skill 配置管理 CRUD")
@RestController
@RequestMapping("/system/skill")
@RequiredArgsConstructor
public class SkillController {

    private final ISkillService skillService;

    /**
     * 分页查询 Skill 列表(name 模糊 + status 筛选,按 sort)
     */
    @Operation(summary = "分页查询列表", description = "按名称模糊、状态精确筛选;sort 升序、create_time 降序兜底")
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('skill:list')")
    public Result<IPage<Skill>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status) {
        return Result.success(skillService.pageList(pageNum, pageSize, name, status));
    }

    /**
     * Skill 详情
     */
    @Operation(summary = "Skill 详情")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('skill:query')")
    public Result<Skill> getInfo(@PathVariable Long id) {
        return Result.success(skillService.getById(id));
    }

    /**
     * 新增 Skill
     */
    @Operation(summary = "新增 Skill")
    @PostMapping
    @PreAuthorize("@ss.hasPermi('skill:add')")
    @Log(title = "Skill 配置", businessType = BusinessType.INSERT)
    public Result<Void> add(@RequestBody Skill skill) {
        String error = validate(skill);
        if (error != null) {
            return Result.error(error);
        }
        clearServerControlledFields(skill);
        skill.setSkillId(null);
        skillService.save(skill);
        return Result.success();
    }

    /**
     * 修改 Skill
     */
    @Operation(summary = "修改 Skill")
    @PutMapping
    @PreAuthorize("@ss.hasPermi('skill:edit')")
    @Log(title = "Skill 配置", businessType = BusinessType.UPDATE)
    public Result<Void> edit(@RequestBody Skill skill) {
        if (skill.getSkillId() == null) {
            return Result.error("Skill ID 不能为空");
        }
        String error = validate(skill);
        if (error != null) {
            return Result.error(error);
        }
        clearServerControlledFields(skill);
        skillService.updateById(skill);
        return Result.success();
    }

    /**
     * 启停快捷切换
     */
    @Operation(summary = "启停切换", description = "传 {skillId, status} 快捷切换启用/停用")
    @PutMapping("/status")
    @PreAuthorize("@ss.hasPermi('skill:edit')")
    public Result<Void> changeStatus(@RequestBody SkillStatusDTO dto) {
        if (dto.getSkillId() == null) {
            return Result.error("Skill ID 不能为空");
        }
        if (!"0".equals(dto.getStatus()) && !"1".equals(dto.getStatus())) {
            return Result.error("状态非法,仅支持 0启用/1停用");
        }
        Skill update = new Skill();
        update.setSkillId(dto.getSkillId());
        update.setStatus(dto.getStatus());
        skillService.updateById(update);
        return Result.success();
    }

    /**
     * 删除 Skill(逻辑删)
     */
    @Operation(summary = "删除 Skill", description = "逻辑删,@TableLogic 标记 del_flag")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('skill:remove')")
    @Log(title = "Skill 配置", businessType = BusinessType.DELETE)
    public Result<Void> remove(@PathVariable Long id) {
        skillService.removeById(id);
        return Result.success();
    }

    /**
     * 入参校验:name 非空。
     *
     * @return 错误文案;校验通过返回 null
     */
    private String validate(Skill skill) {
        if (!StringUtils.hasText(skill.getName())) {
            return "名称不能为空";
        }
        return null;
    }

    /**
     * 清空服务端控制字段,防止前端篡改审计/逻辑删字段(R-DTO 防 Mass-Assignment)。
     * 审计字段交由 MybatisMetaObjectHandler 自动回填,删除标记由 @TableLogic 管理。
     */
    private void clearServerControlledFields(Skill skill) {
        skill.setCreateBy(null);
        skill.setCreateTime(null);
        skill.setUpdateBy(null);
        skill.setUpdateTime(null);
        skill.setDelFlag(null);
    }
}
