package com.aurora.admin.controller.system;

import com.aurora.common.core.domain.Result;
import com.aurora.common.core.domain.entity.SysConfig;
import com.aurora.common.core.service.ISysConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

/**
 * 参数配置信息
 */
@RestController
@RequestMapping("/system/config")
@RequiredArgsConstructor
public class SysConfigController {

    private final ISysConfigService configService;

    /**
     * 获取参数配置列表
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('system:config:list')")
    public Result<Page<SysConfig>> list(@RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            SysConfig config) {
        Page<SysConfig> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        if (config.getConfigName() != null && !config.getConfigName().isEmpty()) {
            wrapper.like(SysConfig::getConfigName, config.getConfigName());
        }
        if (config.getConfigKey() != null && !config.getConfigKey().isEmpty()) {
            wrapper.like(SysConfig::getConfigKey, config.getConfigKey());
        }
        if (config.getConfigType() != null && !config.getConfigType().isEmpty()) {
            wrapper.eq(SysConfig::getConfigType, config.getConfigType());
        }
        wrapper.orderByAsc(SysConfig::getConfigId);
        return Result.success(configService.page(page, wrapper));
    }

    /**
     * 查询参数配置详细
     */
    @GetMapping(value = "/{configId}")
    @PreAuthorize("@ss.hasPermi('system:config:query')")
    public Result<SysConfig> getInfo(@PathVariable Long configId) {
        return Result.success(configService.getById(configId));
    }

    /**
     * 根据参数键名查询参数值
     */
    // 按 key 读取展示型公共配置(标题/logo/主题等),登录后布局初始化必调;放开管理权限,仅需认证即可读
    @GetMapping(value = "/configKey/{configKey}")
    public Result<String> getConfigKey(@PathVariable String configKey) {
        return Result.success(configService.selectConfigByKey(configKey));
    }

    /**
     * 新增参数配置
     * 新增时 key 不存在缓存，无需清除
     */
    @PostMapping
    @PreAuthorize("@ss.hasPermi('system:config:add')")
    public Result<Void> add(@RequestBody SysConfig config) {
        if (!configService.list(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, config.getConfigKey()))
                .isEmpty()) {
            return Result.error("新增参数'" + config.getConfigName() + "'失败，参数键名已存在");
        }
        // 安全:审计字段由服务端控制,忽略前端传入(防伪造);清空后由 MetaObjectHandler 自动回填
        config.setCreateBy(null);
        config.setCreateTime(null);
        config.setUpdateBy(null);
        config.setUpdateTime(null);
        configService.save(config);
        return Result.success();
    }

    /**
     * 修改参数配置
     * 同时清除该配置项的缓存
     */
    @PutMapping
    @PreAuthorize("@ss.hasPermi('system:config:edit')")
    @CacheEvict(value = "config", key = "#config.configKey")
    public Result<Void> edit(@RequestBody SysConfig config) {
        SysConfig oldConfig = configService.getById(config.getConfigId());
        // 如果 key 变更，需要清除旧 key 的缓存
        if (!oldConfig.getConfigKey().equals(config.getConfigKey())) {
            if (!configService
                    .list(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, config.getConfigKey()))
                    .isEmpty()) {
                return Result.error("修改参数'" + config.getConfigName() + "'失败，参数键名已存在");
            }
        }
        // 安全:审计字段由服务端控制,忽略前端传入;创建信息不覆盖(NOT_NULL 策略),更新信息由 MetaObjectHandler 自动回填
        config.setCreateBy(null);
        config.setCreateTime(null);
        config.setUpdateBy(null);
        config.setUpdateTime(null);
        configService.updateById(config);
        return Result.success();
    }

    /**
     * 删除参数配置
     * 删除时清除所有配置缓存
     */
    @DeleteMapping("/{configIds}")
    @PreAuthorize("@ss.hasPermi('system:config:remove')")
    @CacheEvict(value = "config", allEntries = true)
    public Result<Void> remove(@PathVariable Long[] configIds) {
        configService.removeByIds(Arrays.asList(configIds));
        return Result.success();
    }
}
