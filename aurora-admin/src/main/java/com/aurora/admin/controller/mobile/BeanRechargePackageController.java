package com.aurora.admin.controller.mobile;

import com.aurora.admin.mobile.domain.BeanRechargePackage;
import com.aurora.admin.mobile.service.IBeanRechargePackageService;
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
 * 豆子充值套餐 管理后台控制器(CRUD)
 * 配置类增删改查,逻辑删 del_flag;业务封装在 IBeanRechargePackageService,Controller 不写 Wrapper 之外的业务。
 */
@Tag(name = "豆子套餐管理", description = "豆子充值套餐增删改查")
@RestController
@RequestMapping("/system/mobile/bean-package")
@RequiredArgsConstructor
public class BeanRechargePackageController {

    private final IBeanRechargePackageService beanRechargePackageService;

    /**
     * 套餐分页列表(可按状态筛选,按 sort 升序)
     */
    @Operation(summary = "套餐列表", description = "分页查询豆子充值套餐,可按状态筛选,按排序升序")
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('mobile:beanpackage:list')")
    public Result<IPage<BeanRechargePackage>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<BeanRechargePackage> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<BeanRechargePackage> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(BeanRechargePackage::getStatus, status);
        }
        wrapper.orderByAsc(BeanRechargePackage::getSort);
        return Result.success(beanRechargePackageService.page(page, wrapper));
    }

    /**
     * 套餐详情
     */
    @Operation(summary = "套餐详情", description = "按主键ID查询套餐详情")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('mobile:beanpackage:query')")
    public Result<BeanRechargePackage> getInfo(@PathVariable Long id) {
        return Result.success(beanRechargePackageService.getById(id));
    }

    /**
     * 新增套餐
     */
    @Operation(summary = "新增套餐", description = "新增豆子充值套餐")
    @PostMapping
    @PreAuthorize("@ss.hasPermi('mobile:beanpackage:add')")
    @Log(title = "豆子套餐", businessType = BusinessType.INSERT)
    public Result<Void> add(@RequestBody BeanRechargePackage beanRechargePackage) {
        // 安全:审计字段与逻辑删标记由服务端控制,忽略前端传入;清空后由 MetaObjectHandler 自动回填
        beanRechargePackage.setPackageId(null);
        beanRechargePackage.setCreateBy(null);
        beanRechargePackage.setCreateTime(null);
        beanRechargePackage.setUpdateBy(null);
        beanRechargePackage.setUpdateTime(null);
        beanRechargePackage.setDelFlag(null);
        beanRechargePackageService.save(beanRechargePackage);
        return Result.success();
    }

    /**
     * 修改套餐
     */
    @Operation(summary = "修改套餐", description = "按主键修改豆子充值套餐")
    @PutMapping
    @PreAuthorize("@ss.hasPermi('mobile:beanpackage:edit')")
    @Log(title = "豆子套餐", businessType = BusinessType.UPDATE)
    public Result<Void> edit(@RequestBody BeanRechargePackage beanRechargePackage) {
        // 安全:审计字段与逻辑删标记由服务端控制,忽略前端传入;更新信息由 MetaObjectHandler 自动回填
        beanRechargePackage.setCreateBy(null);
        beanRechargePackage.setCreateTime(null);
        beanRechargePackage.setUpdateBy(null);
        beanRechargePackage.setUpdateTime(null);
        beanRechargePackage.setDelFlag(null);
        beanRechargePackageService.updateById(beanRechargePackage);
        return Result.success();
    }

    /**
     * 删除套餐(逻辑删)
     */
    @Operation(summary = "删除套餐", description = "按主键逻辑删除豆子充值套餐")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('mobile:beanpackage:remove')")
    @Log(title = "豆子套餐", businessType = BusinessType.DELETE)
    public Result<Void> remove(@PathVariable Long id) {
        beanRechargePackageService.removeById(id);
        return Result.success();
    }
}
