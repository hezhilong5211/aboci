package com.aurora.common.core.service.impl;

import com.aurora.common.core.domain.entity.SysMenu;
import com.aurora.common.core.mapper.SysMenuMapper;
import com.aurora.common.core.service.ISysMenuService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements ISysMenuService {

    @Override
    public Set<String> selectPermsByUserId(Long userId) {
        // 如果是超级管理员（user_id=1），直接返回所有权限
        if (userId != null && 1L == userId) {
            Set<String> perms = new HashSet<>();
            perms.add("*:*:*");
            return perms;
        }
        
        // 普通用户：查询用户拥有的权限
        // 这里需要多表关联查询：sys_user -> sys_user_role -> sys_role_menu -> sys_menu
        // 由于MyBatis-Plus的Wrapper难以实现复杂的多表关联，我们将在Mapper XML中实现
        List<String> permsList = baseMapper.selectPermsByUserId(userId);
        
        // 过滤空权限并去重
        return permsList.stream()
                .filter(perm -> perm != null && !perm.trim().isEmpty())
                .collect(Collectors.toSet());
    }

    @Override
    public List<SysMenu> selectMenuTreeByUserId(Long userId) {
        List<SysMenu> menus;
        
        // 如果是超级管理员（user_id=1），直接查询所有菜单，不查关联表
        if (userId != null && 1L == userId) {
            // 使用 selectMenuTreeAll，只过滤删除和状态，不过滤visible
            // 这样超级管理员可以看到所有菜单，包括隐藏的菜单
            menus = baseMapper.selectMenuTreeAll();
        } else {
            // 普通用户：查询用户拥有的菜单（通过关联表查询）
            menus = baseMapper.selectMenusByUserId(userId);
        }
        
        // 构建菜单树
        return buildMenuTree(menus);
    }
    
    /**
     * 构建菜单树
     */
    private List<SysMenu> buildMenuTree(List<SysMenu> menus) {
        List<SysMenu> tree = new ArrayList<>();
        
        // 按父菜单ID分组
        Map<Long, List<SysMenu>> menuMap = menus.stream()
                .collect(Collectors.groupingBy(SysMenu::getParentId));
        
        // 构建树形结构
        for (SysMenu menu : menus) {
            if (menu.getParentId() == 0) {
                // 根菜单
                tree.add(menu);
                // 设置子菜单
                setChildren(menu, menuMap);
            }
        }
        
        // 按显示顺序排序
        tree.sort(Comparator.comparing(SysMenu::getOrderNum));
        return tree;
    }
    
    /**
     * 递归设置子菜单
     */
    private void setChildren(SysMenu parent, Map<Long, List<SysMenu>> menuMap) {
        List<SysMenu> children = menuMap.get(parent.getMenuId());
        if (children != null && !children.isEmpty()) {
            // 按显示顺序排序
            children.sort(Comparator.comparing(SysMenu::getOrderNum));
            parent.setChildren(children);
            
            // 递归设置子菜单的子菜单
            for (SysMenu child : children) {
                setChildren(child, menuMap);
            }
        }
    }
}
