package com.aurora.common.core.service.impl;

import com.aurora.common.core.domain.entity.SysDictType;
import com.aurora.common.core.mapper.SysDictTypeMapper;
import com.aurora.common.core.service.ISysDictTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 字典类型业务层处理
 */
@Service
public class SysDictTypeServiceImpl extends ServiceImpl<SysDictTypeMapper, SysDictType> implements ISysDictTypeService {

    @Override
    public List<SysDictType> selectDictTypeAll() {
        return list();
    }
}
