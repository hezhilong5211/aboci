package com.aurora.common.core.service.impl;

import com.aurora.common.core.domain.entity.SysDictData;
import com.aurora.common.core.mapper.SysDictDataMapper;
import com.aurora.common.core.service.ISysDictDataService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 字典数据业务层处理
 * 使用 Redis 缓存提升查询性能
 */
@Service
public class SysDictDataServiceImpl extends ServiceImpl<SysDictDataMapper, SysDictData> implements ISysDictDataService {

    /**
     * 根据字典类型查询字典数据
     * 缓存 24 小时
     */
    @Override
    @Cacheable(value = "dict", key = "#dictType", unless = "#result == null || #result.isEmpty()")
    public List<SysDictData> selectDictDataByType(String dictType) {
        return list(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, dictType)
                .orderByAsc(SysDictData::getDictSort));
    }

    /**
     * 清除指定字典类型的缓存
     */
    @CacheEvict(value = "dict", key = "#dictType")
    public void clearDictCache(String dictType) {
        // 仅清除缓存，无需其他操作
    }

    /**
     * 清除所有字典缓存
     */
    @CacheEvict(value = "dict", allEntries = true)
    public void clearAllDictCache() {
        // 清除所有字典缓存
    }
}
