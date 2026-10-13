package com.aurora.common.core.service;

import com.aurora.common.core.domain.entity.SysDictType;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 字典类型业务层
 */
public interface ISysDictTypeService extends IService<SysDictType> {
    /**
     * 根据字典类型查询字典数据
     * 
     * @param dictType 字典类型
     * @return 字典类型集合信息
     */
    public List<SysDictType> selectDictTypeAll();
}
