package com.aurora.admin.controller.system;

import com.aurora.admin.service.SysDeptService;
import com.aurora.common.core.domain.Result;
import com.aurora.common.core.domain.entity.SysDept;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门管理 Controller
 */
@RestController
@RequestMapping("/system/dept")
public class SysDeptController {

    @Autowired
    private SysDeptService deptService;

    /**
     * 查询部门树形列表
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('system:dept:list')")
    public Result<List<SysDept>> list() {
        return Result.success(deptService.selectDeptTree());
    }

    /**
     * 查询部门列表（排除某个节点）
     */
    @GetMapping("/list/exclude/{deptId}")
    @PreAuthorize("@ss.hasPermi('system:dept:list')")
    public Result<List<SysDept>> excludeChild(@PathVariable Long deptId) {
        List<SysDept> deptList = deptService.selectDeptTree();
        // 过滤掉当前部门及其子部门
        deptList.removeIf(d -> d.getDeptId().equals(deptId) ||
                (d.getAncestors() != null && d.getAncestors().contains("," + deptId + ",")));
        return Result.success(deptList);
    }

    /**
     * 根据部门ID获取部门信息
     */
    @GetMapping("/{deptId}")
    @PreAuthorize("@ss.hasPermi('system:dept:query')")
    public Result<SysDept> getInfo(@PathVariable Long deptId) {
        return Result.success(deptService.selectDeptById(deptId));
    }

    /**
     * 新增部门
     */
    @PostMapping
    @PreAuthorize("@ss.hasPermi('system:dept:add')")
    public Result<Void> add(@RequestBody SysDept dept) {
        if (!deptService.checkDeptNameUnique(dept)) {
            return Result.error("新增部门'" + dept.getDeptName() + "'失败，部门名称已存在");
        }

        int rows = deptService.insertDept(dept);
        return rows > 0 ? Result.success() : Result.error("新增失败");
    }

    /**
     * 修改部门
     */
    @PutMapping
    @PreAuthorize("@ss.hasPermi('system:dept:edit')")
    public Result<Void> edit(@RequestBody SysDept dept) {
        if (!deptService.checkDeptNameUnique(dept)) {
            return Result.error("修改部门'" + dept.getDeptName() + "'失败，部门名称已存在");
        }

        if (dept.getParentId() != null && dept.getParentId().equals(dept.getDeptId())) {
            return Result.error("修改部门'" + dept.getDeptName() + "'失败，上级部门不能是自己");
        }

        int rows = deptService.updateDept(dept);
        return rows > 0 ? Result.success() : Result.error("修改失败");
    }

    /**
     * 删除部门
     */
    @DeleteMapping("/{deptId}")
    @PreAuthorize("@ss.hasPermi('system:dept:remove')")
    public Result<Void> remove(@PathVariable Long deptId) {
        if (deptService.hasChildDept(deptId)) {
            return Result.error("存在下级部门，不允许删除");
        }

        if (deptService.checkDeptExistUser(deptId)) {
            return Result.error("部门存在用户，不允许删除");
        }

        int rows = deptService.deleteDeptById(deptId);
        return rows > 0 ? Result.success() : Result.error("删除失败");
    }
}
