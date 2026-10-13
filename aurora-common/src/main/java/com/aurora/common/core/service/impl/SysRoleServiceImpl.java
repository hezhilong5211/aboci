package com.aurora.common.core.service.impl;

import com.aurora.common.core.domain.entity.SysRole;
import com.aurora.common.core.mapper.SysRoleMapper;
import com.aurora.common.core.service.ISysRoleService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements ISysRoleService {
}

