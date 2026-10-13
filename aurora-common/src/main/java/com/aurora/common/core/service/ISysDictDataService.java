package com.aurora.common.core.service;

import com.aurora.common.core.domain.entity.SysDictData;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 字典数据业务层
 */
public interface ISysDictDataService extends IService<SysDictData> {
    /**
     * 根据字典类型查询字典数据
     * 
     * @param dictType 字典类型
     * @return 字典数据集合信息
     */
    public List<SysDictData> selectDictDataByType(String dictType);
}
