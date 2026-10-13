package com.aurora.common.core.service;

import com.aurora.common.core.domain.entity.SysRoleMenu;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 角色菜单关联服务接口
 */
public interface ISysRoleMenuService extends IService<SysRoleMenu> {

    /**
     * 根据角色ID获取菜单ID列表
     *
     * @param roleId 角色ID
     * @return 菜单ID列表
     */
    List<Long> selectMenuIdsByRoleId(Long roleId);

    /**
     * 批量插入角色菜单关联
     *
     * @param roleId  角色ID
     * @param menuIds 菜单ID列表
     */
    void insertRoleMenus(Long roleId, List<Long> menuIds);

    /**
     * 根据角色ID删除角色菜单关联
     *
     * @param roleId 角色ID
     */
    void deleteByRoleId(Long roleId);
}
