package com.aurora.admin.controller.system;

import com.aurora.common.core.domain.Result;
import com.aurora.common.core.domain.entity.SysDictData;
import com.aurora.common.core.service.ISysDictDataService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据字典数据信息
 */
@RestController
@RequestMapping("/system/dict/data")
@RequiredArgsConstructor
public class SysDictDataController {

    private final ISysDictDataService dictDataService;

    /**
     * 分页查询字典数据列表
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('system:dict:list')")
    public Result<Page<SysDictData>> list(@RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            SysDictData dictData) {
        Page<SysDictData> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<>();
        if (dictData.getDictType() != null && !dictData.getDictType().isEmpty()) {
            wrapper.eq(SysDictData::getDictType, dictData.getDictType());
        }
        if (dictData.getDictLabel() != null && !dictData.getDictLabel().isEmpty()) {
            wrapper.like(SysDictData::getDictLabel, dictData.getDictLabel());
        }
        if (dictData.getStatus() != null && !dictData.getStatus().isEmpty()) {
            wrapper.eq(SysDictData::getStatus, dictData.getStatus());
        }
        wrapper.orderByAsc(SysDictData::getDictSort);
        return Result.success(dictDataService.page(page, wrapper));
    }

    /**
     * 根据字典类型查询字典数据信息
     */
    // 按字典类型读取下拉数据,各业务表单(状态/类型等下拉)通用;放开管理权限,仅需认证即可读
    @GetMapping(value = "/type/{dictType}")
    public Result<List<SysDictData>> dictType(@PathVariable String dictType) {
        return Result.success(dictDataService.selectDictDataByType(dictType));
    }

    /**
     * 查询字典数据详细
     */
    @GetMapping(value = "/{dictCode}")
    @PreAuthorize("@ss.hasPermi('system:dict:query')")
    public Result<SysDictData> getInfo(@PathVariable Long dictCode) {
        return Result.success(dictDataService.getById(dictCode));
    }

    /**
     * 新增字典数据
     * 同时清除该字典类型的缓存
     */
    @PostMapping
    @PreAuthorize("@ss.hasPermi('system:dict:add')")
    @CacheEvict(value = "dict", key = "#dictData.dictType")
    public Result<Void> add(@RequestBody SysDictData dictData) {
        dictData.setCreateTime(LocalDateTime.now());
        dictDataService.save(dictData);
        return Result.success();
    }

    /**
     * 修改字典数据
     * 同时清除该字典类型的缓存
     */
    @PutMapping
    @PreAuthorize("@ss.hasPermi('system:dict:edit')")
    @CacheEvict(value = "dict", key = "#dictData.dictType")
    public Result<Void> edit(@RequestBody SysDictData dictData) {
        dictData.setUpdateTime(LocalDateTime.now());
        dictDataService.updateById(dictData);
        return Result.success();
    }

    /**
     * 删除字典数据
     * 删除时无法确定具体 dictType，清除所有字典缓存
     */
    @DeleteMapping("/{dictCodes}")
    @PreAuthorize("@ss.hasPermi('system:dict:remove')")
    @CacheEvict(value = "dict", allEntries = true)
    public Result<Void> remove(@PathVariable Long[] dictCodes) {
        dictDataService.removeByIds(java.util.Arrays.asList(dictCodes));
        return Result.success();
    }
}
