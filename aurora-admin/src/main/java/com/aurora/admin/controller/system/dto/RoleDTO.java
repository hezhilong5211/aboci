package com.aurora.admin.controller.system.dto;

import com.aurora.common.core.domain.entity.SysRole;
import lombok.Data;

import java.util.List;

/**
 * 角色数据传输对象
 */
@Data
public class RoleDTO {
    /**
     * 角色信息
     */
    private SysRole role;
    
    /**
     * 菜单ID列表
     */
    private List<Long> menuIds;
}

