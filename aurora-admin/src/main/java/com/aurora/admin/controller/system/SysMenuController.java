package com.aurora.admin.controller.system;

import com.aurora.common.core.domain.Result;
import com.aurora.common.core.domain.entity.SysMenu;
import com.aurora.common.core.service.ISysMenuService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * 菜单信息控制器
 */
@Slf4j
@Tag(name = "菜单管理", description = "菜单信息的增删改查、菜单树查询等接口")
@RestController
@RequestMapping("/system/menu")
public class SysMenuController {

    private final ISysMenuService menuService;

    public SysMenuController(ISysMenuService menuService) {
        this.menuService = menuService;
    }

    /**
     * 获取菜单列表
     */
    @Operation(summary = "获取菜单列表", description = "根据条件查询菜单列表")
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('system:menu:list')")
    public Result<List<SysMenu>> list(SysMenu menu) {
        LambdaQueryWrapper<SysMenu> queryWrapper = new LambdaQueryWrapper<>();

        // 根据条件查询
        if (menu.getMenuName() != null && !menu.getMenuName().isEmpty()) {
            queryWrapper.like(SysMenu::getMenuName, menu.getMenuName());
        }
        if (menu.getStatus() != null && !menu.getStatus().isEmpty()) {
            queryWrapper.eq(SysMenu::getStatus, menu.getStatus());
        }
        if (menu.getVisible() != null && !menu.getVisible().isEmpty()) {
            queryWrapper.eq(SysMenu::getVisible, menu.getVisible());
        }

        // 排除已删除的菜单
        queryWrapper.eq(SysMenu::getDelFlag, "0");

        // 按父菜单ID和显示顺序排序
        queryWrapper.orderByAsc(SysMenu::getParentId, SysMenu::getOrderNum);

        List<SysMenu> menus = menuService.list(queryWrapper);
        return Result.success(menus);
    }

    /**
     * 根据菜单ID获取详细信息
     */
    @Operation(summary = "获取菜单详情", description = "根据菜单ID获取菜单详细信息")
    @GetMapping("/{menuId}")
    @PreAuthorize("@ss.hasPermi('system:menu:query')")
    public Result<SysMenu> getInfo(@PathVariable Long menuId) {
        SysMenu menu = menuService.getById(menuId);
        if (menu == null || "2".equals(menu.getDelFlag())) {
            return Result.error("菜单不存在");
        }
        return Result.success(menu);
    }

    /**
     * 获取菜单下拉树列表
     */
    @Operation(summary = "获取菜单树", description = "获取树形结构的菜单列表，用于下拉选择")
    @GetMapping("/treeselect")
    @PreAuthorize("@ss.hasPermi('system:menu:list')")
    public Result<List<SysMenu>> treeselect() {
        LambdaQueryWrapper<SysMenu> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysMenu::getDelFlag, "0")
                .eq(SysMenu::getStatus, "0")
                .orderByAsc(SysMenu::getParentId, SysMenu::getOrderNum);

        List<SysMenu> menus = menuService.list(queryWrapper);
        List<SysMenu> menuTree = buildMenuTree(menus);
        return Result.success(menuTree);
    }

    /**
     * 新增菜单
     */
    @Operation(summary = "新增菜单", description = "创建新菜单，需要校验同一层级下菜单名称唯一性")
    @PostMapping
    @PreAuthorize("@ss.hasPermi('system:menu:add')")
    public Result<Void> add(@RequestBody SysMenu menu) {
        // 调试日志：打印接收到的菜单数据
        log.debug("接收到新增菜单请求，icon字段: {}", menu.getIcon());
        // 验证菜单名称是否唯一
        LambdaQueryWrapper<SysMenu> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysMenu::getMenuName, menu.getMenuName())
                .eq(SysMenu::getParentId, menu.getParentId())
                .eq(SysMenu::getDelFlag, "0");
        if (menuService.count(queryWrapper) > 0) {
            return Result.error("同一层级下菜单名称不能重复");
        }

        // 设置默认值
        if (menu.getStatus() == null || menu.getStatus().isEmpty()) {
            menu.setStatus("0"); // 默认正常状态
        }
        if (menu.getVisible() == null || menu.getVisible().isEmpty()) {
            menu.setVisible("0"); // 默认显示
        }
        if (menu.getDelFlag() == null || menu.getDelFlag().isEmpty()) {
            menu.setDelFlag("0"); // 默认未删除
        }
        if (menu.getIsFrame() == null) {
            menu.setIsFrame(1); // 默认不是外链
        }
        if (menu.getIsCache() == null) {
            menu.setIsCache(0); // 默认不缓存
        }
        if (menu.getOrderNum() == null) {
            menu.setOrderNum(0); // 默认显示顺序
        }

        // 如果是菜单类型（C），确保有 path 和 component
        if ("C".equals(menu.getMenuType())) {
            if (menu.getPath() == null || menu.getPath().trim().isEmpty()) {
                // 如果没有 path，使用菜单名称的拼音或ID
                menu.setPath(menu.getMenuName() != null ? menu.getMenuName().toLowerCase().replaceAll("\\s+", "")
                        : "menu" + System.currentTimeMillis());
            }
            if (menu.getComponent() == null || menu.getComponent().trim().isEmpty()) {
                // 如果没有 component，根据 path 生成默认 component
                String path = menu.getPath();
                if (path != null && !path.isEmpty()) {
                    // 将 path 转换为 component 路径，例如 "user" -> "system/user/index"
                    menu.setComponent(path.startsWith("/") ? path.substring(1) + "/index" : path + "/index");
                }
            }
        }

        // 如果是目录类型（M），确保有 path
        if ("M".equals(menu.getMenuType())) {
            if (menu.getPath() == null || menu.getPath().trim().isEmpty()) {
                menu.setPath(menu.getMenuName() != null ? menu.getMenuName().toLowerCase().replaceAll("\\s+", "")
                        : "menu" + System.currentTimeMillis());
            }
        }

        // 安全:审计字段由服务端控制,忽略前端传入(防伪造 createBy/createTime/updateBy/updateTime)
        menu.setCreateBy(null);
        menu.setCreateTime(null);
        menu.setUpdateBy(null);
        menu.setUpdateTime(null);

        // 再次确认 icon 字段
        log.debug("保存前菜单icon字段: {}", menu.getIcon());
        boolean success = menuService.save(menu);
        log.debug("保存结果: {}", success);
        return success ? Result.success() : Result.error("新增菜单失败");
    }

    /**
     * 修改菜单
     */
    @Operation(summary = "修改菜单", description = "更新菜单信息")
    @PutMapping
    @PreAuthorize("@ss.hasPermi('system:menu:edit')")
    public Result<Void> edit(@RequestBody SysMenu menu) {
        // 调试日志：打印接收到的菜单数据
        log.debug("接收到修改菜单请求，menuId: {}, icon字段: {}", menu.getMenuId(), menu.getIcon());
        if (menu.getMenuId() == null) {
            return Result.error("菜单ID不能为空");
        }

        // 检查菜单是否存在
        SysMenu existingMenu = menuService.getById(menu.getMenuId());
        if (existingMenu == null || "2".equals(existingMenu.getDelFlag())) {
            return Result.error("菜单不存在");
        }

        // 验证菜单名称是否唯一（排除自身）
        LambdaQueryWrapper<SysMenu> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysMenu::getMenuName, menu.getMenuName())
                .eq(SysMenu::getParentId, menu.getParentId())
                .ne(SysMenu::getMenuId, menu.getMenuId())
                .eq(SysMenu::getDelFlag, "0");
        if (menuService.count(queryWrapper) > 0) {
            return Result.error("同一层级下菜单名称不能重复");
        }

        // 不能将父菜单设置为自己或自己的子菜单
        if (menu.getParentId() != null && menu.getParentId().equals(menu.getMenuId())) {
            return Result.error("父菜单不能是自己");
        }

        // 确保必填字段有值（与新增菜单逻辑一致）
        if (menu.getStatus() == null || menu.getStatus().isEmpty()) {
            menu.setStatus("0");
        }
        if (menu.getVisible() == null || menu.getVisible().isEmpty()) {
            menu.setVisible("0");
        }
        if (menu.getDelFlag() == null || menu.getDelFlag().isEmpty()) {
            menu.setDelFlag("0");
        }

        // 如果是菜单类型（C），确保有 path 和 component
        if ("C".equals(menu.getMenuType())) {
            if (menu.getPath() == null || menu.getPath().trim().isEmpty()) {
                menu.setPath(menu.getMenuName() != null ? menu.getMenuName().toLowerCase().replaceAll("\\s+", "")
                        : "menu" + menu.getMenuId());
            }
            if (menu.getComponent() == null || menu.getComponent().trim().isEmpty()) {
                String path = menu.getPath();
                if (path != null && !path.isEmpty()) {
                    menu.setComponent(path.startsWith("/") ? path.substring(1) + "/index" : path + "/index");
                }
            }
        }

        // 如果是目录类型（M），确保有 path
        if ("M".equals(menu.getMenuType())) {
            if (menu.getPath() == null || menu.getPath().trim().isEmpty()) {
                menu.setPath(menu.getMenuName() != null ? menu.getMenuName().toLowerCase().replaceAll("\\s+", "")
                        : "menu" + menu.getMenuId());
            }
        }

        // 安全:审计字段由服务端控制,忽略前端传入(防伪造覆盖 createBy/createTime/updateBy/updateTime)
        menu.setCreateBy(null);
        menu.setCreateTime(null);
        menu.setUpdateBy(null);
        menu.setUpdateTime(null);

        // 再次确认 icon 字段
        log.debug("更新前菜单icon字段: {}", menu.getIcon());
        boolean success = menuService.updateById(menu);
        log.debug("更新结果: {}", success);
        return success ? Result.success() : Result.error("修改菜单失败");
    }

    /**
     * 删除菜单
     */
    @Operation(summary = "删除菜单", description = "逻辑删除菜单，如果存在子菜单则不允许删除")
    @DeleteMapping("/{menuId}")
    @PreAuthorize("@ss.hasPermi('system:menu:remove')")
    public Result<Void> remove(@PathVariable Long menuId) {
        if (menuId == null) {
            return Result.error("菜单ID不能为空");
        }

        // 检查菜单是否存在
        SysMenu menu = menuService.getById(menuId);
        if (menu == null) {
            return Result.error("菜单不存在");
        }

        if ("2".equals(menu.getDelFlag())) {
            return Result.error("菜单已被删除");
        }

        // 检查是否有子菜单（包括已删除的，但只检查未删除的）
        LambdaQueryWrapper<SysMenu> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysMenu::getParentId, menuId)
                .eq(SysMenu::getDelFlag, "0");
        long childCount = menuService.count(queryWrapper);
        if (childCount > 0) {
            return Result.error("存在子菜单，不允许删除。请先删除子菜单");
        }

        // 逻辑删除菜单:走 @TableLogic,由 MyBatis-Plus 按全局配置自动置 del_flag=2
        boolean success = menuService.removeById(menuId);
        if (!success) {
            return Result.error("删除菜单失败，请检查数据库连接");
        }

        return Result.success();
    }

    /**
     * 构建菜单树
     */
    private List<SysMenu> buildMenuTree(List<SysMenu> menus) {
        if (menus == null || menus.isEmpty()) {
            return new ArrayList<>();
        }

        List<SysMenu> tree = new ArrayList<>();

        // 按父菜单ID分组
        Map<Long, List<SysMenu>> menuMap = menus.stream()
                .collect(Collectors.groupingBy(SysMenu::getParentId));

        // 构建树形结构
        for (SysMenu menu : menus) {
            if (menu.getParentId() == null || menu.getParentId() == 0) {
                // 根菜单
                tree.add(menu);
                // 设置子菜单
                setChildren(menu, menuMap);
            }
        }

        // 按显示顺序排序
        tree.sort(Comparator.comparing(SysMenu::getOrderNum, Comparator.nullsLast(Comparator.naturalOrder())));
        return tree;
    }

    /**
     * 递归设置子菜单
     */
    private void setChildren(SysMenu parent, Map<Long, List<SysMenu>> menuMap) {
        List<SysMenu> children = menuMap.get(parent.getMenuId());
        if (children != null && !children.isEmpty()) {
            // 按显示顺序排序
            children.sort(Comparator.comparing(SysMenu::getOrderNum, Comparator.nullsLast(Comparator.naturalOrder())));
            parent.setChildren(children);

            // 递归设置子菜单的子菜单
            for (SysMenu child : children) {
                setChildren(child, menuMap);
            }
        }
    }
}
