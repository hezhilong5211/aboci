package com.aurora.admin.controller.system;

import com.aurora.common.core.annotation.Log;
import com.aurora.common.core.domain.Result;
import com.aurora.common.core.domain.entity.SysUser;
import com.aurora.common.core.domain.model.LoginUser;
import com.aurora.common.core.enums.BusinessType;
import com.aurora.common.core.service.ISysUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.aurora.common.core.service.ISysUserRoleService;
import com.aurora.framework.storage.ProfileStorage;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

/**
 * 用户信息控制器
 */
@Slf4j
@Tag(name = "用户管理", description = "用户信息的增删改查、密码重置、状态修改等接口")
@RestController
@RequestMapping("/system/user")
public class SysUserController {

    private final ISysUserService userService;
    private final ISysUserRoleService userRoleService;
    private final PasswordEncoder passwordEncoder;
    private final ProfileStorage profileStorage;

    public SysUserController(ISysUserService userService, ISysUserRoleService userRoleService,
            PasswordEncoder passwordEncoder, ProfileStorage profileStorage) {
        this.userService = userService;
        this.userRoleService = userRoleService;
        this.passwordEncoder = passwordEncoder;
        this.profileStorage = profileStorage;
    }

    /**
     * 分页查询用户列表
     */
    @Operation(summary = "分页查询用户列表", description = "根据条件分页查询用户列表")
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('system:user:list')")
    public Result<IPage<SysUser>> list(@RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            SysUser user) {
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();

        // 根据条件查询
        if (user.getUserName() != null && !user.getUserName().isEmpty()) {
            queryWrapper.like(SysUser::getUserName, user.getUserName());
        }
        if (user.getNickName() != null && !user.getNickName().isEmpty()) {
            queryWrapper.like(SysUser::getNickName, user.getNickName());
        }
        if (user.getStatus() != null && !user.getStatus().isEmpty()) {
            queryWrapper.eq(SysUser::getStatus, user.getStatus());
        }
        if (user.getPhonenumber() != null && !user.getPhonenumber().isEmpty()) {
            queryWrapper.like(SysUser::getPhonenumber, user.getPhonenumber());
        }

        // 排除已删除的用户
        queryWrapper.eq(SysUser::getDelFlag, "0");

        // 分页查询
        Page<SysUser> page = new Page<>(pageNum, pageSize);
        IPage<SysUser> pageResult = userService.page(page, queryWrapper);

        return Result.success(pageResult);
    }

    /**
     * 根据用户ID获取详细信息
     */
    @Operation(summary = "获取用户详情", description = "根据用户ID获取用户详细信息")
    @GetMapping("/{userId}")
    @PreAuthorize("@ss.hasPermi('system:user:query')")
    public Result<SysUser> getInfo(@PathVariable Long userId) {
        SysUser user = userService.getById(userId);
        if (user == null || "2".equals(user.getDelFlag())) {
            return Result.error("用户不存在");
        }
        user.setPassword(null); // 不返回密码
        return Result.success(user);
    }

    /**
     * 获取用户的角色ID列表
     */
    @Operation(summary = "获取用户的角色ID列表", description = "根据用户ID获取该用户已分配的角色ID列表")
    @GetMapping("/{userId}/roleIds")
    @PreAuthorize("@ss.hasPermi('system:user:query')")
    public Result<List<Long>> getRoleIds(@PathVariable Long userId) {
        // 使用 Service 层获取角色ID列表
        List<Long> roleIds = userRoleService.selectRoleIdsByUserId(userId);
        return Result.success(roleIds);
    }

    /**
     * 新增用户
     */
    @Log(title = "用户管理", businessType = BusinessType.INSERT)
    @Operation(summary = "新增用户", description = "创建新用户，需要校验用户名唯一性")
    @PostMapping
    @PreAuthorize("@ss.hasPermi('system:user:add')")
    public Result<Void> add(@RequestBody com.aurora.admin.controller.system.dto.UserDTO userDTO) {
        SysUser user = userDTO.getUser();
        List<Long> roleIds = userDTO.getRoleIds();

        // 检查用户名是否已存在
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getUserName, user.getUserName())
                .eq(SysUser::getDelFlag, "0");
        if (userService.count(queryWrapper) > 0) {
            return Result.error("用户名已存在");
        }

        // 设置默认值
        if (user.getStatus() == null) {
            user.setStatus("0"); // 默认正常状态
        }
        if (user.getDelFlag() == null) {
            user.setDelFlag("0"); // 默认未删除
        }

        // 密码处理：前端强校验后仍可能出现空值，此处兜底，避免库中存入 null 导致用户无法登录
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            return Result.error("密码不能为空");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // 安全:服务端控制的字段一律忽略前端传入(防 Mass-Assignment)
        user.setDelFlag("0");
        user.setCreateBy(null);
        user.setCreateTime(null);
        user.setUpdateBy(null);
        user.setUpdateTime(null);
        user.setLoginIp(null);
        user.setLoginDate(null);

        boolean success = userService.save(user);
        if (success && roleIds != null && !roleIds.isEmpty()) {
            insertUserRole(user.getUserId(), roleIds);
        }
        return success ? Result.success() : Result.error("新增用户失败");
    }

    /**
     * 修改用户
     */
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @Operation(summary = "修改用户", description = "更新用户信息")
    @PutMapping
    @PreAuthorize("@ss.hasPermi('system:user:edit')")
    public Result<Void> edit(@RequestBody com.aurora.admin.controller.system.dto.UserDTO userDTO) {
        SysUser user = userDTO.getUser();
        List<Long> roleIds = userDTO.getRoleIds();

        if (user.getUserId() == null) {
            return Result.error("用户ID不能为空");
        }

        // 禁止通过用户管理编辑超级管理员（超管只能通过个人中心修改自己的信息）
        if (user.getUserId() == 1L) {
            return Result.error("超级管理员账号只能通过个人中心修改");
        }

        // 检查用户是否存在
        SysUser existingUser = userService.getById(user.getUserId());
        if (existingUser == null || "2".equals(existingUser.getDelFlag())) {
            return Result.error("用户不存在");
        }

        // 检查用户名是否被其他用户使用
        if (user.getUserName() != null && !user.getUserName().equals(existingUser.getUserName())) {
            LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(SysUser::getUserName, user.getUserName())
                    .ne(SysUser::getUserId, user.getUserId())
                    .eq(SysUser::getDelFlag, "0");
            if (userService.count(queryWrapper) > 0) {
                return Result.error("用户名已被其他用户使用");
            }
        }

        // 安全:这些字段由服务端控制,忽略前端传入(防越权软删 / 伪造审计 / 改用户类型 /
        // 借编辑接口改密码导致明文未编码入库);改密走 resetPwd / updatePassword 专用接口
        user.setDelFlag(null);
        user.setUserType(null);
        user.setCreateBy(null);
        user.setCreateTime(null);
        user.setLoginIp(null);
        user.setLoginDate(null);
        user.setPassword(null);

        boolean success = userService.updateById(user);
        if (success) {
            // 更新角色关联：只有当 roleIds 明确传递时才更新（避免因前端未传导致角色被清空）
            if (roleIds != null) {
                deleteUserRole(user.getUserId());
                if (!roleIds.isEmpty()) {
                    insertUserRole(user.getUserId(), roleIds);
                }
            }

            // 同步更新 Redis 会话缓存(仅当编辑的是当前登录者本人,防跨用户刷错;逻辑下沉至 Service)
            userService.refreshSelfLoginCacheAfterEdit(user.getUserId());
        }
        return success ? Result.success() : Result.error("修改用户失败");
    }

    /**
     * 删除用户
     */
    @Log(title = "用户管理", businessType = BusinessType.DELETE)
    @Operation(summary = "删除用户", description = "批量逻辑删除用户（支持多个用户ID）")
    @DeleteMapping("/{userIds}")
    @PreAuthorize("@ss.hasPermi('system:user:remove')")
    public Result<Void> remove(@PathVariable List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Result.error("请选择要删除的用户");
        }

        // 检查是否包含超级管理员
        if (userIds.contains(1L)) {
            return Result.error("不允许删除超级管理员");
        }

        try {
            // 批量逻辑删除用户 (MyBatis Plus会自动处理逻辑删除，将del_flag更新为2)
            userService.removeBatchByIds(userIds);

            // 删除用户角色关联
            userRoleService.deleteByUserIds(userIds);

            return Result.success();
        } catch (Exception e) {
            // 异常堆栈只写日志，返回前端的文案固定，避免泄漏内部实现细节
            log.error("删除用户失败, userIds={}: {}", userIds, e.getMessage(), e);
            return Result.error("删除用户失败，请联系管理员");
        }
    }

    /**
     * 重置密码
     */
    @Operation(summary = "重置密码", description = "重置指定用户的密码")
    @PutMapping("/resetPwd")
    @PreAuthorize("@ss.hasPermi('system:user:resetPwd')")
    public Result<Void> resetPwd(@RequestBody SysUser user) {
        if (user.getUserId() == null || user.getPassword() == null) {
            return Result.error("参数错误");
        }

        SysUser existingUser = userService.getById(user.getUserId());
        if (existingUser == null || "2".equals(existingUser.getDelFlag())) {
            return Result.error("用户不存在");
        }

        // 更新密码
        existingUser.setPassword(passwordEncoder.encode(user.getPassword()));
        boolean success = userService.updateById(existingUser);
        return success ? Result.success() : Result.error("重置密码失败");
    }

    /**
     * 修改用户状态
     */
    @Operation(summary = "修改用户状态", description = "启用或停用用户账号")
    @PutMapping("/changeStatus")
    @PreAuthorize("@ss.hasPermi('system:user:edit')")
    public Result<Void> changeStatus(@RequestBody SysUser user) {
        if (user.getUserId() == null || user.getStatus() == null) {
            return Result.error("参数错误");
        }

        SysUser existingUser = userService.getById(user.getUserId());
        if (existingUser == null || "2".equals(existingUser.getDelFlag())) {
            return Result.error("用户不存在");
        }

        // 更新状态
        existingUser.setStatus(user.getStatus());
        boolean success = userService.updateById(existingUser);
        return success ? Result.success() : Result.error("修改状态失败");
    }

    /**
     * 修改当前用户密码
     */
    @Operation(summary = "修改当前用户密码", description = "当前登录用户修改自己的密码")
    @PutMapping("/updatePassword")
    public Result<Void> updatePassword(@RequestBody PasswordUpdateDTO dto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication.getPrincipal() instanceof LoginUser)) {
            return Result.error(401, "未登录");
        }

        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        Long userId = loginUser.getUserId();

        if (dto.getOldPassword() == null || dto.getOldPassword().isEmpty()) {
            return Result.error("原密码不能为空");
        }
        if (dto.getNewPassword() == null || dto.getNewPassword().isEmpty()) {
            return Result.error("新密码不能为空");
        }

        SysUser user = userService.getById(userId);
        if (user == null || "2".equals(user.getDelFlag())) {
            return Result.error("用户不存在");
        }

        // 密码列已配置 select=false,getById 不含哈希,需显式查询当前密码用于校验原密码
        SysUser pwdHolder = userService.lambdaQuery()
                .select(SysUser::getPassword)
                .eq(SysUser::getUserId, userId)
                .one();
        String currentPwd = pwdHolder != null ? pwdHolder.getPassword() : null;

        // 验证原密码
        if (currentPwd == null || !passwordEncoder.matches(dto.getOldPassword(), currentPwd)) {
            return Result.error("原密码错误");
        }

        // 更新密码
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        boolean success = userService.updateById(user);
        return success ? Result.success() : Result.error("修改密码失败");
    }

    /**
     * 密码更新DTO
     */
    public static class PasswordUpdateDTO {
        private String oldPassword;
        private String newPassword;

        public String getOldPassword() {
            return oldPassword;
        }

        public void setOldPassword(String oldPassword) {
            this.oldPassword = oldPassword;
        }

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }
    }

    /**
     * 上传头像
     */
    @Operation(summary = "上传头像", description = "上传用户头像并返回文件URL")
    @PostMapping("/uploadAvatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error("请选择要上传的文件");
        }

        // 检查文件类型（Content-Type）
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.error("只支持上传图片文件");
        }

        // 检查文件大小（限制2MB）
        if (file.getSize() > 2 * 1024 * 1024) {
            return Result.error("文件大小不能超过2MB");
        }

        // 文件魔数校验：验证真实文件类型，防止伪造扩展名
        try {
            byte[] header = file.getInputStream().readNBytes(12);
            if (detectImageExtension(header) == null) {
                return Result.error("文件内容与图片格式不匹配，请上传真实的图片文件");
            }
        } catch (Exception e) {
            log.error("文件魔数校验失败: {}", e.getMessage());
            return Result.error("文件校验失败");
        }

        try {
            // 文件名与扩展名都由服务端产生，不信任客户端原始文件名。
            String extension = detectImageExtension(file.getInputStream().readNBytes(12));
            String filename = UUID.randomUUID() + extension;

            // 新文件统一写 spring.profile.upload-path；读取层继续兼容历史目录。
            java.nio.file.Path destFile = profileStorage.prepareWritePath("avatar/" + filename);
            file.transferTo(destFile);

            // 返回相对路径，前端 axios 会自动拼接 baseURL
            String fileUrl = "/profile/avatar/" + filename;

            return Result.success(fileUrl);
        } catch (Exception e) {
            log.error("上传头像失败: {}", e.getMessage(), e);
            return Result.error("上传头像失败");
        }
    }

    /**
     * 验证图片文件魔数（Magic Number）
     * 支持 JPEG、PNG、GIF、BMP、WebP 格式
     */
    private String detectImageExtension(byte[] header) {
        if (header == null || header.length < 4) {
            return null;
        }
        // JPEG: FF D8 FF
        if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
            return ".jpg";
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (header[0] == (byte) 0x89 && header[1] == (byte) 0x50 && header[2] == (byte) 0x4E
                && header[3] == (byte) 0x47) {
            return ".png";
        }
        // GIF: 47 49 46 38
        if (header[0] == (byte) 0x47 && header[1] == (byte) 0x49 && header[2] == (byte) 0x46
                && header[3] == (byte) 0x38) {
            return ".gif";
        }
        // BMP: 42 4D
        if (header[0] == (byte) 0x42 && header[1] == (byte) 0x4D) {
            return ".bmp";
        }
        // WebP: RIFF....WEBP，仅 RIFF 头不足以判定为图片。
        if (header.length >= 12
                && header[0] == (byte) 0x52 && header[1] == (byte) 0x49
                && header[2] == (byte) 0x46 && header[3] == (byte) 0x46
                && header[8] == (byte) 0x57 && header[9] == (byte) 0x45
                && header[10] == (byte) 0x42 && header[11] == (byte) 0x50) {
            return ".webp";
        }
        return null;
    }

    /**
     * 插入用户和角色的关联关系
     */
    private void insertUserRole(Long userId, List<Long> roleIds) {
        userRoleService.insertUserRoles(userId, roleIds);
    }

    /**
     * 删除用户和角色的关联关系
     */
    private void deleteUserRole(Long userId) {
        userRoleService.deleteByUserId(userId);
    }
}
