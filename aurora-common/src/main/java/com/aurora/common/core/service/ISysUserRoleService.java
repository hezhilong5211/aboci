package com.aurora.common.core.service;

import com.aurora.common.core.domain.entity.SysUserRole;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 用户角色关联服务接口
 */
public interface ISysUserRoleService extends IService<SysUserRole> {

    /**
     * 根据用户ID获取角色ID列表
     * 
     * @param userId 用户ID
     * @return 角色ID列表
     */
    List<Long> selectRoleIdsByUserId(Long userId);

    /**
     * 批量插入用户角色关联
     * 
     * @param userId  用户ID
     * @param roleIds 角色ID列表
     */
    void insertUserRoles(Long userId, List<Long> roleIds);

    /**
     * 根据用户ID删除用户角色关联
     * 
     * @param userId 用户ID
     */
    void deleteByUserId(Long userId);

    /**
     * 根据用户ID列表批量删除用户角色关联
     *
     * @param userIds 用户ID列表
     */
    void deleteByUserIds(List<Long> userIds);

    /**
     * 统计绑定了指定角色的用户数量
     *
     * @param roleId 角色ID
     * @return 绑定该角色的用户数
     */
    long countByRoleId(Long roleId);
}
