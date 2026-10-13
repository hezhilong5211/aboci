package com.aurora.common.core.service;

import com.aurora.common.core.domain.entity.SysUser;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ISysUserService extends IService<SysUser> {
    SysUser selectUserByUserName(String userName);

    /** 根据用户 ID 查询用户及当前启用角色。 */
    SysUser selectUserByUserId(Long userId);

    /**
     * 编辑用户后,若编辑的是当前登录者本人,刷新其 Redis 会话缓存中的用户信息
     * (编辑他人不刷新,防跨用户覆盖自己的 LoginUser)
     */
    void refreshSelfLoginCacheAfterEdit(Long userId);
}
