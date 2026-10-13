package com.aurora.common.core.service;

import com.aurora.common.core.domain.entity.SysMenu;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Set;

public interface ISysMenuService extends IService<SysMenu> {
    
    /**
     * 根据用户ID查询权限字符串
     * @param userId 用户ID
     * @return 权限字符串集合
     */
    Set<String> selectPermsByUserId(Long userId);
    
    /**
     * 根据用户ID查询菜单树
     * @param userId 用户ID
     * @return 菜单树列表
     */
    List<SysMenu> selectMenuTreeByUserId(Long userId);
}
