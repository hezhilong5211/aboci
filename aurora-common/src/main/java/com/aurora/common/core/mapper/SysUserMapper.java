package com.aurora.common.core.mapper;

import com.aurora.common.core.domain.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    
    /**
     * 根据用户名查询用户及其角色信息
     * @param userName 用户名
     * @return 用户对象（包含角色列表）
     */
    SysUser selectUserByUserNameWithRoles(@Param("userName") String userName);

    /**
     * 根据用户 ID 查询用户及当前启用角色。
     *
     * @param userId 用户 ID
     * @return 用户对象（包含当前启用角色）
     */
    SysUser selectUserByUserIdWithRoles(@Param("userId") Long userId);
}
