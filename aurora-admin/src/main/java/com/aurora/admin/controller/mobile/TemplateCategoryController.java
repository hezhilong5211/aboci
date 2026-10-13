package com.aurora.admin.controller.mobile;

import com.aurora.admin.mobile.domain.TemplateCategory;
import com.aurora.admin.mobile.service.ITemplateCategoryService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 管理后台案例行业分类。 */
@Tag(name = "案例行业分类")
@RestController
@RequestMapping("/system/template/category")
@RequiredArgsConstructor
public class TemplateCategoryController {

    private final ITemplateCategoryService categoryService;

    @Operation(summary = "行业分类列表")
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('template:category:list')")
    public Result<List<TemplateCategory>> list(@RequestParam(required = false) String name,
                                                @RequestParam(required = false) String status) {
        return Result.success(categoryService.listCategories(name, status));
    }

    @Operation(summary = "行业分类详情")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('template:category:query')")
    public Result<TemplateCategory> detail(@PathVariable Long id) {
        return Result.success(categoryService.getById(id));
    }

    @Operation(summary = "新增行业分类")
    @PostMapping
    @PreAuthorize("@ss.hasPermi('template:category:add')")
    @Log(title = "案例行业分类", businessType = BusinessType.INSERT)
    public Result<Void> add(@RequestBody TemplateCategory category) {
        categoryService.createCategory(category);
        return Result.success();
    }

    @Operation(summary = "修改行业分类")
    @PutMapping
    @PreAuthorize("@ss.hasPermi('template:category:edit')")
    @Log(title = "案例行业分类", businessType = BusinessType.UPDATE)
    public Result<Void> edit(@RequestBody TemplateCategory category) {
        categoryService.updateCategory(category);
        return Result.success();
    }

    @Operation(summary = "删除行业分类")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('template:category:remove')")
    @Log(title = "案例行业分类", businessType = BusinessType.DELETE)
    public Result<Void> remove(@PathVariable Long id) {
        categoryService.removeCategory(id);
        return Result.success();
    }
}
