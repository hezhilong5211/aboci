package com.aurora.admin.controller.system;

import com.aurora.common.core.domain.Result;
import com.aurora.common.core.domain.entity.SysDictType;
import com.aurora.common.core.service.ISysDictTypeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据字典信息
 */
@RestController
@RequestMapping("/system/dict/type")
@RequiredArgsConstructor
public class SysDictTypeController {
    
    private final ISysDictTypeService dictTypeService;

    /**
     * 查询字典类型列表
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('system:dict:list')")
    public Result<Page<SysDictType>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                         @RequestParam(defaultValue = "10") Integer pageSize,
                                         SysDictType dictType) {
        Page<SysDictType> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<>();
        if (dictType.getDictName() != null && !dictType.getDictName().isEmpty()) {
            wrapper.like(SysDictType::getDictName, dictType.getDictName());
        }
        if (dictType.getDictType() != null && !dictType.getDictType().isEmpty()) {
            wrapper.like(SysDictType::getDictType, dictType.getDictType());
        }
        if (dictType.getStatus() != null && !dictType.getStatus().isEmpty()) {
            wrapper.eq(SysDictType::getStatus, dictType.getStatus());
        }
        wrapper.orderByAsc(SysDictType::getDictId);
        return Result.success(dictTypeService.page(page, wrapper));
    }

    /**
     * 查询字典类型详细
     */
    @GetMapping(value = "/{dictId}")
    @PreAuthorize("@ss.hasPermi('system:dict:query')")
    public Result<SysDictType> getInfo(@PathVariable Long dictId) {
        return Result.success(dictTypeService.getById(dictId));
    }

    /**
     * 新增字典类型
     */
    @PostMapping
    @PreAuthorize("@ss.hasPermi('system:dict:add')")
    public Result<Void> add(@RequestBody SysDictType dictType) {
        dictType.setCreateTime(LocalDateTime.now());
        dictTypeService.save(dictType);
        return Result.success();
    }

    /**
     * 修改字典类型
     */
    @PutMapping
    @PreAuthorize("@ss.hasPermi('system:dict:edit')")
    public Result<Void> edit(@RequestBody SysDictType dictType) {
        dictType.setUpdateTime(LocalDateTime.now());
        dictTypeService.updateById(dictType);
        return Result.success();
    }

    /**
     * 删除字典类型
     */
    @DeleteMapping("/{dictIds}")
    @PreAuthorize("@ss.hasPermi('system:dict:remove')")
    public Result<Void> remove(@PathVariable Long[] dictIds) {
        dictTypeService.removeByIds(java.util.Arrays.asList(dictIds));
        return Result.success();
    }
}
