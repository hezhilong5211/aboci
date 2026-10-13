package com.aurora.common.core.mapper;

import com.aurora.common.core.domain.entity.SysMenu;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {
    
    /**
     * 根据用户ID查询权限字符串
     * @param userId 用户ID
     * @return 权限字符串列表
     */
    List<String> selectPermsByUserId(@Param("userId") Long userId);
    
    /**
     * 查询所有菜单（用于超级管理员，只过滤删除和状态，不过滤visible）
     * @return 菜单列表
     */
    List<SysMenu> selectMenuTreeAll();
    
    /**
     * 查询所有菜单（用于普通查询，包含visible过滤）
     * @return 菜单列表
     */
    List<SysMenu> selectAllMenus();
    
    /**
     * 根据用户ID查询菜单
     * @param userId 用户ID
     * @return 菜单列表
     */
    List<SysMenu> selectMenusByUserId(@Param("userId") Long userId);
}
