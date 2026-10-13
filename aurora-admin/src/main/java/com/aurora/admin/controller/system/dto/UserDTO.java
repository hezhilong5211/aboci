package com.aurora.admin.controller.system.dto;

import com.aurora.common.core.domain.entity.SysUser;
import lombok.Data;

import java.util.List;

/**
 * 用户数据传输对象
 */
@Data
public class UserDTO {
    /**
     * 用户信息
     */
    private SysUser user;

    /**
     * 角色ID列表
     */
    private List<Long> roleIds;
}
