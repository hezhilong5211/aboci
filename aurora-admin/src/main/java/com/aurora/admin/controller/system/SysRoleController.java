package com.aurora.admin.controller.system;

import com.aurora.admin.controller.system.dto.RoleDTO;
import com.aurora.common.core.domain.Result;
import com.aurora.common.core.domain.entity.SysRole;
import com.aurora.common.core.service.ISysRoleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.aurora.common.core.service.ISysRoleMenuService;
import com.aurora.common.core.service.ISysUserRoleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色信息控制器
 */
@Tag(name = "角色管理", description = "角色信息的增删改查、角色菜单权限分配等接口")
@RestController
@RequestMapping("/system/role")
public class SysRoleController {

    private final ISysRoleService roleService;
    private final ISysRoleMenuService roleMenuService;
    private final ISysUserRoleService userRoleService;

    public SysRoleController(ISysRoleService roleService, ISysRoleMenuService roleMenuService,
            ISysUserRoleService userRoleService) {
        this.roleService = roleService;
        this.roleMenuService = roleMenuService;
        this.userRoleService = userRoleService;
    }

    /**
     * 分页查询角色列表
     */
    @Operation(summary = "分页查询角色列表", description = "根据条件分页查询角色列表")
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('system:role:list')")
    public Result<IPage<SysRole>> list(@RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) String roleKey,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<SysRole> queryWrapper = new LambdaQueryWrapper<>();

        // 根据条件查询（支持模糊查询）
        // 如果同时传递了 roleName 和 roleKey，说明是同一个关键词，需要同时匹配
        if (roleName != null && !roleName.trim().isEmpty() &&
                roleKey != null && !roleKey.trim().isEmpty() &&
                roleName.trim().equals(roleKey.trim())) {
            // 同一个关键词，使用 OR 条件
            String keyword = roleName.trim();
            queryWrapper.and(wrapper -> wrapper
                    .like(SysRole::getRoleName, keyword)
                    .or()
                    .like(SysRole::getRoleKey, keyword));
        } else {
            // 分别查询
            if (roleName != null && !roleName.trim().isEmpty()) {
                queryWrapper.like(SysRole::getRoleName, roleName.trim());
            }
            if (roleKey != null && !roleKey.trim().isEmpty()) {
                queryWrapper.like(SysRole::getRoleKey, roleKey.trim());
            }
        }
        if (status != null && !status.trim().isEmpty()) {
            queryWrapper.eq(SysRole::getStatus, status.trim());
        }

        // 排除已删除的角色
        queryWrapper.eq(SysRole::getDelFlag, "0");

        // 按显示顺序排序
        queryWrapper.orderByAsc(SysRole::getRoleSort);

        // 分页查询
        Page<SysRole> page = new Page<>(pageNum, pageSize);
        IPage<SysRole> pageResult = roleService.page(page, queryWrapper);

        return Result.success(pageResult);
    }

    /**
     * 根据角色ID获取详细信息
     */
    @Operation(summary = "获取角色详情", description = "根据角色ID获取角色详细信息，包括已分配的菜单权限")
    @GetMapping("/{roleId}")
    @PreAuthorize("@ss.hasPermi('system:role:query')")
    public Result<SysRole> getInfo(@PathVariable Long roleId) {
        SysRole role = roleService.getById(roleId);
        if (role == null || "2".equals(role.getDelFlag())) {
            return Result.error("角色不存在");
        }

        // 查询该角色已分配的菜单ID列表（通过单独的接口返回）
        // 这里只返回角色基本信息，菜单ID通过 /{roleId}/menuIds 接口获取
        return Result.success(role);
    }

    /**
     * 获取角色的菜单ID列表
     */
    @Operation(summary = "获取角色的菜单ID列表", description = "根据角色ID获取该角色已分配的菜单ID列表")
    @GetMapping("/{roleId}/menuIds")
    @PreAuthorize("@ss.hasPermi('system:role:query')")
    public Result<List<Long>> getMenuIds(@PathVariable Long roleId) {
        List<Long> menuIds = roleMenuService.selectMenuIdsByRoleId(roleId);
        return Result.success(menuIds);
    }

    /**
     * 新增角色
     */
    @Operation(summary = "新增角色", description = "创建新角色，需要校验角色名称和权限字符唯一性")
    @PostMapping
    @PreAuthorize("@ss.hasPermi('system:role:add')")
    public Result<Void> add(@RequestBody RoleDTO roleDTO) {
        SysRole role = roleDTO.getRole();
        List<Long> menuIds = roleDTO.getMenuIds();

        // 验证必填字段
        if (role.getRoleName() == null || role.getRoleName().trim().isEmpty()) {
            return Result.error("角色名称不能为空");
        }
        if (role.getRoleKey() == null || role.getRoleKey().trim().isEmpty()) {
            return Result.error("权限字符不能为空");
        }

        // 检查角色名称是否唯一
        LambdaQueryWrapper<SysRole> nameQuery = new LambdaQueryWrapper<>();
        nameQuery.eq(SysRole::getRoleName, role.getRoleName())
                .eq(SysRole::getDelFlag, "0");
        if (roleService.count(nameQuery) > 0) {
            return Result.error("角色名称已存在");
        }

        // 检查权限字符是否唯一
        LambdaQueryWrapper<SysRole> keyQuery = new LambdaQueryWrapper<>();
        keyQuery.eq(SysRole::getRoleKey, role.getRoleKey())
                .eq(SysRole::getDelFlag, "0");
        if (roleService.count(keyQuery) > 0) {
            return Result.error("权限字符已存在");
        }

        // 设置默认值
        if (role.getStatus() == null || role.getStatus().isEmpty()) {
            role.setStatus("0"); // 默认正常状态
        }
        if (role.getDelFlag() == null || role.getDelFlag().isEmpty()) {
            role.setDelFlag("0"); // 默认未删除
        }
        if (role.getRoleSort() == null) {
            role.setRoleSort(0); // 默认显示顺序
        }
        // 安全:超管标识严禁来自前端,新增角色一律为非超管(防越权提权)
        role.setIsAdmin(0);

        // 保存角色
        boolean success = roleService.save(role);
        if (!success) {
            return Result.error("新增角色失败");
        }

        // 保存角色和菜单的关联关系
        if (menuIds != null && !menuIds.isEmpty()) {
            roleMenuService.insertRoleMenus(role.getRoleId(), menuIds);
        }

        return Result.success();
    }

    /**
     * 修改角色
     */
    @Operation(summary = "修改角色", description = "更新角色信息，包括菜单权限")
    @PutMapping
    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    public Result<Void> edit(@RequestBody RoleDTO roleDTO) {
        SysRole role = roleDTO.getRole();
        List<Long> menuIds = roleDTO.getMenuIds();

        if (role.getRoleId() == null) {
            return Result.error("角色ID不能为空");
        }

        // 禁止编辑超级管理员角色
        if (role.getRoleId() == 1L) {
            return Result.error("不允许修改超级管理员角色");
        }

        // 检查角色是否存在
        SysRole existingRole = roleService.getById(role.getRoleId());
        if (existingRole == null || "2".equals(existingRole.getDelFlag())) {
            return Result.error("角色不存在");
        }

        // 验证必填字段
        if (role.getRoleName() == null || role.getRoleName().trim().isEmpty()) {
            return Result.error("角色名称不能为空");
        }
        if (role.getRoleKey() == null || role.getRoleKey().trim().isEmpty()) {
            return Result.error("权限字符不能为空");
        }

        // 检查角色名称是否被其他角色使用
        if (!role.getRoleName().equals(existingRole.getRoleName())) {
            LambdaQueryWrapper<SysRole> nameQuery = new LambdaQueryWrapper<>();
            nameQuery.eq(SysRole::getRoleName, role.getRoleName())
                    .ne(SysRole::getRoleId, role.getRoleId())
                    .eq(SysRole::getDelFlag, "0");
            if (roleService.count(nameQuery) > 0) {
                return Result.error("角色名称已被其他角色使用");
            }
        }

        // 检查权限字符是否被其他角色使用
        if (!role.getRoleKey().equals(existingRole.getRoleKey())) {
            LambdaQueryWrapper<SysRole> keyQuery = new LambdaQueryWrapper<>();
            keyQuery.eq(SysRole::getRoleKey, role.getRoleKey())
                    .ne(SysRole::getRoleId, role.getRoleId())
                    .eq(SysRole::getDelFlag, "0");
            if (roleService.count(keyQuery) > 0) {
                return Result.error("权限字符已被其他角色使用");
            }
        }

        // 安全:超管标识与逻辑删除标志由服务端控制,忽略前端传入值
        // (防止借修改接口把角色刷成超管,或塞 delFlag=2 越过删除接口软删角色)
        role.setIsAdmin(existingRole.getIsAdmin());
        role.setDelFlag(existingRole.getDelFlag());

        // 更新角色
        boolean success = roleService.updateById(role);
        if (!success) {
            return Result.error("修改角色失败");
        }

        // 更新角色和菜单的关联关系
        // 只有当 menuIds 明确传递时才更新（避免因前端未传导致权限被清空）
        if (menuIds != null) {
            // 先删除旧的关联关系
            roleMenuService.deleteByRoleId(role.getRoleId());
            // 再插入新的关联关系
            if (!menuIds.isEmpty()) {
                roleMenuService.insertRoleMenus(role.getRoleId(), menuIds);
            }
        }

        return Result.success();
    }

    /**
     * 删除角色
     */
    @Operation(summary = "删除角色", description = "批量逻辑删除角色，如果角色绑定了用户则不允许删除")
    @DeleteMapping("/{roleIds}")
    @PreAuthorize("@ss.hasPermi('system:role:remove')")
    public Result<Void> remove(@PathVariable String roleIds) {
        if (roleIds == null || roleIds.trim().isEmpty()) {
            return Result.error("请选择要删除的角色");
        }

        // 解析角色ID列表
        List<Long> roleIdList = Arrays.stream(roleIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());

        if (roleIdList.isEmpty()) {
            return Result.error("请选择要删除的角色");
        }

        // 禁止删除超级管理员角色(精确匹配 roleId==1;修正原 roleIds.contains("1") 子串误判——
        // 原写法会把 101/11/100 等含字符"1"的角色 ID 误判为超管角色而拒删)
        if (roleIdList.contains(1L)) {
            return Result.error("不允许删除超级管理员角色");
        }

        // 检查角色是否被用户绑定
        for (Long roleId : roleIdList) {
            if (userRoleService.countByRoleId(roleId) > 0) {
                SysRole role = roleService.getById(roleId);
                String roleName = role != null ? role.getRoleName() : "未知角色";
                return Result.error("角色\"" + roleName + "\"已被绑定，请先解除用户");
            }
        }

        // 逻辑删除角色:走 @TableLogic 自动生成 UPDATE ... SET del_flag=2 WHERE role_id=? AND del_flag=0。
        // 原手工 new SysRole+setDelFlag(@TableLogic 字段)+updateById 会被 MyBatis-Plus 忽略该字段,
        // 产生空 SET 的畸形 UPDATE 而抛异常,故改用 removeById(同时收口 TD-DEL-01 手工置位)。
        for (Long roleId : roleIdList) {
            roleService.removeById(roleId);

            // 删除角色和菜单的关联关系
            roleMenuService.deleteByRoleId(roleId);
        }

        return Result.success();
    }

}
