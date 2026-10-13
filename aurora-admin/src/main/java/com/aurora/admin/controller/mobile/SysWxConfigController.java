package com.aurora.admin.controller.mobile;

import com.aurora.admin.mobile.domain.SysWxConfig;
import com.aurora.admin.mobile.service.ISysWxConfigService;
import com.aurora.admin.mobile.service.WxServiceFactory;
import com.aurora.common.core.domain.Result;
import com.aurora.framework.utils.EncryptUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 微信应用配置 管理后台控制器
 * 敏感项(appSecret/apiV3Key/privateKey/privateCert)出参一律脱敏,入参非空才加密入库;
 * 编辑时若传入的是脱敏占位值(****)则跳过该项更新,避免把脱敏串写回库覆盖真实密文。
 */
@Tag(name = "微信应用配置", description = "微信小程序/支付应用配置管理")
@RestController
@RequestMapping("/system/wx-config")
@RequiredArgsConstructor
public class SysWxConfigController {

    private final ISysWxConfigService sysWxConfigService;
    private final WxServiceFactory wxServiceFactory;
    private final EncryptUtils encryptUtils;

    /**
     * 分页查询微信配置列表(敏感项脱敏返回)
     */
    @Operation(summary = "分页查询列表", description = "分页查询微信应用配置,敏感项脱敏")
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('system:wxconfig:list')")
    public Result<IPage<SysWxConfig>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            SysWxConfig query) {

        LambdaQueryWrapper<SysWxConfig> wrapper = new LambdaQueryWrapper<>();
        if (query.getAppType() != null && !query.getAppType().isEmpty()) {
            wrapper.eq(SysWxConfig::getAppType, query.getAppType());
        }
        if (query.getAppName() != null && !query.getAppName().isEmpty()) {
            wrapper.like(SysWxConfig::getAppName, query.getAppName());
        }
        wrapper.orderByDesc(SysWxConfig::getCreateTime);

        Page<SysWxConfig> page = new Page<>(pageNum, pageSize);
        IPage<SysWxConfig> pageResult = sysWxConfigService.page(page, wrapper);
        pageResult.getRecords().forEach(this::maskSensitive);
        return Result.success(pageResult);
    }

    /**
     * 获取配置详情(敏感项脱敏返回)
     */
    @Operation(summary = "获取详情", description = "根据ID获取微信配置详情,敏感项脱敏")
    @GetMapping("/{wxId}")
    @PreAuthorize("@ss.hasPermi('system:wxconfig:list')")
    public Result<SysWxConfig> getInfo(@PathVariable Long wxId) {
        SysWxConfig config = sysWxConfigService.getById(wxId);
        if (config == null) {
            return Result.error("配置不存在");
        }
        maskSensitive(config);
        return Result.success(config);
    }

    /**
     * 新增微信配置(非空敏感项加密入库)
     */
    @Operation(summary = "新增配置", description = "新增微信应用配置")
    @PostMapping
    @PreAuthorize("@ss.hasPermi('system:wxconfig:add')")
    public Result<Void> add(@RequestBody SysWxConfig config) {
        if (config.getAppType() == null || config.getAppType().isEmpty()) {
            return Result.error("应用类型不能为空");
        }
        if (config.getAppId() == null || config.getAppId().isEmpty()) {
            return Result.error("AppId 不能为空");
        }

        validateVirtualPayBeforeEnable(config, null);
        encryptSensitive(config);
        LocalDateTime now = LocalDateTime.now();
        config.setCreateTime(now);
        config.setUpdateTime(now);
        sysWxConfigService.save(config);
        // 渠道互斥:新增即启用某支付渠道时停用另一渠道
        applyChannelExclusion(config);
        return Result.success();
    }

    /**
     * 修改微信配置
     * 敏感项:脱敏占位值(****)跳过更新,其余非空值加密;改后清空 WxServiceFactory 缓存强制重建
     */
    @Operation(summary = "修改配置", description = "修改微信应用配置")
    @PutMapping
    @PreAuthorize("@ss.hasPermi('system:wxconfig:edit')")
    public Result<Void> edit(@RequestBody SysWxConfig config) {
        if (config.getWxId() == null) {
            return Result.error("配置ID不能为空");
        }
        SysWxConfig existing = sysWxConfigService.getById(config.getWxId());
        if (existing == null) {
            return Result.error("配置不存在");
        }

        // 敏感项逐项处理:脱敏占位则沿用库内原值,真实新值则加密
        config.setAppSecret(resolveSensitive(config.getAppSecret(), existing.getAppSecret()));
        config.setApiV3Key(resolveSensitive(config.getApiV3Key(), existing.getApiV3Key()));
        config.setPrivateKey(resolveSensitive(config.getPrivateKey(), existing.getPrivateKey()));
        config.setPrivateCert(resolveSensitive(config.getPrivateCert(), existing.getPrivateCert()));
        config.setSandboxAppKey(resolveSensitive(config.getSandboxAppKey(), existing.getSandboxAppKey()));
        config.setProdAppKey(resolveSensitive(config.getProdAppKey(), existing.getProdAppKey()));
        config.setPushToken(resolveSensitive(config.getPushToken(), existing.getPushToken()));
        config.setPushAesKey(resolveSensitive(config.getPushAesKey(), existing.getPushAesKey()));

        validateVirtualPayBeforeEnable(config, existing);

        config.setCreateTime(null);
        config.setUpdateTime(LocalDateTime.now());
        sysWxConfigService.updateById(config);

        // 渠道互斥:启用某一支付渠道时自动停用另一渠道(virtual_pay ↔ pay)
        applyChannelExclusion(config);

        // 配置变更后清缓存,下次取用按新配置重建客户端
        wxServiceFactory.clearCache();
        return Result.success();
    }

    /**
     * 支付渠道互斥:当本次保存的是 virtual_pay/pay 且置为启用(is_active=1)时,
     * 自动把另一支付渠道停用(is_active=0),保证同一时刻只有一条支付渠道生效。
     * mini_app(小程序基础配置)不参与互斥。
     */
    private void applyChannelExclusion(SysWxConfig saved) {
        String type = saved.getAppType();
        if (saved.getIsActive() == null || saved.getIsActive() != 1) {
            return;
        }
        String opposite;
        if ("virtual_pay".equals(type)) {
            opposite = "pay";
        } else if ("pay".equals(type)) {
            opposite = "virtual_pay";
        } else {
            return;
        }
        SysWxConfig other = sysWxConfigService.getOne(
                new LambdaQueryWrapper<SysWxConfig>().eq(SysWxConfig::getAppType, opposite).last("LIMIT 1"));
        if (other != null && other.getIsActive() != null && other.getIsActive() == 1) {
            SysWxConfig upd = new SysWxConfig();
            upd.setWxId(other.getWxId());
            upd.setIsActive(0);
            upd.setUpdateTime(LocalDateTime.now());
            sysWxConfigService.updateById(upd);
        }
    }

    /**
     * 删除微信配置
     */
    @Operation(summary = "删除配置", description = "删除微信应用配置")
    @DeleteMapping("/{wxId}")
    @PreAuthorize("@ss.hasPermi('system:wxconfig:remove')")
    public Result<Void> remove(@PathVariable Long wxId) {
        sysWxConfigService.removeById(wxId);
        wxServiceFactory.clearCache();
        return Result.success();
    }

    /**
     * 敏感项脱敏(出参用):微信支付四项 + 虚拟支付四项(沙箱/现网 AppKey、消息推送 Token/AESKey)
     */
    private void maskSensitive(SysWxConfig config) {
        config.setAppSecret(maskIfPresent(config.getAppSecret()));
        config.setApiV3Key(maskIfPresent(config.getApiV3Key()));
        config.setPrivateKey(maskIfPresent(config.getPrivateKey()));
        config.setPrivateCert(maskIfPresent(config.getPrivateCert()));
        config.setSandboxAppKey(maskIfPresent(config.getSandboxAppKey()));
        config.setProdAppKey(maskIfPresent(config.getProdAppKey()));
        config.setPushToken(maskIfPresent(config.getPushToken()));
        config.setPushAesKey(maskIfPresent(config.getPushAesKey()));
    }

    /**
     * 敏感项加密(新增用),非空才加密
     */
    private void encryptSensitive(SysWxConfig config) {
        if (config.getAppSecret() != null && !config.getAppSecret().isEmpty()) {
            config.setAppSecret(encryptUtils.encrypt(config.getAppSecret()));
        }
        if (config.getApiV3Key() != null && !config.getApiV3Key().isEmpty()) {
            config.setApiV3Key(encryptUtils.encrypt(config.getApiV3Key()));
        }
        if (config.getPrivateKey() != null && !config.getPrivateKey().isEmpty()) {
            config.setPrivateKey(encryptUtils.encrypt(config.getPrivateKey()));
        }
        if (config.getPrivateCert() != null && !config.getPrivateCert().isEmpty()) {
            config.setPrivateCert(encryptUtils.encrypt(config.getPrivateCert()));
        }
        if (config.getSandboxAppKey() != null && !config.getSandboxAppKey().isEmpty()) {
            config.setSandboxAppKey(encryptUtils.encrypt(config.getSandboxAppKey()));
        }
        if (config.getProdAppKey() != null && !config.getProdAppKey().isEmpty()) {
            config.setProdAppKey(encryptUtils.encrypt(config.getProdAppKey()));
        }
        if (config.getPushToken() != null && !config.getPushToken().isEmpty()) {
            config.setPushToken(encryptUtils.encrypt(config.getPushToken()));
        }
        if (config.getPushAesKey() != null && !config.getPushAesKey().isEmpty()) {
            config.setPushAesKey(encryptUtils.encrypt(config.getPushAesKey()));
        }
    }

    /**
     * 编辑场景单个敏感项取值:
     * - 入参为空或为脱敏占位值(含 ****)→ 沿用库内原密文,跳过更新
     * - 入参为真实新值 → 加密返回
     */
    private String resolveSensitive(String input, String original) {
        if (input == null || input.isEmpty() || input.contains("****")) {
            return original;
        }
        return encryptUtils.encrypt(input);
    }

    /**
     * 启用虚拟支付前做完整性校验，避免在无回调能力或 AppId 错配时收款。
     */
    private void validateVirtualPayBeforeEnable(SysWxConfig input, SysWxConfig existing) {
        String appType = input.getAppType() == null && existing != null
                ? existing.getAppType() : input.getAppType();
        Integer active = input.getIsActive() == null && existing != null
                ? existing.getIsActive() : input.getIsActive();
        if (!"virtual_pay".equals(appType) || !Integer.valueOf(1).equals(active)) {
            return;
        }

        String appId = valueOrExisting(input.getAppId(), existing == null ? null : existing.getAppId());
        String offerId = valueOrExisting(input.getOfferId(), existing == null ? null : existing.getOfferId());
        Integer env = input.getEnv() == null && existing != null ? existing.getEnv() : input.getEnv();
        if (isBlank(appId) || isBlank(offerId) || (env == null || (env != 0 && env != 1))) {
            throw new com.aurora.common.core.exception.ServiceException(
                    "启用虚拟支付前必须配置 AppId、OfferId 和正确环境");
        }

        String selectedAppKey = env == 1
                ? valueOrExisting(input.getSandboxAppKey(), existing == null ? null : existing.getSandboxAppKey())
                : valueOrExisting(input.getProdAppKey(), existing == null ? null : existing.getProdAppKey());
        String pushToken = valueOrExisting(input.getPushToken(), existing == null ? null : existing.getPushToken());
        String pushAesKey = valueOrExisting(input.getPushAesKey(), existing == null ? null : existing.getPushAesKey());
        String plainToken = decryptIfNeeded(pushToken);
        String plainAesKey = decryptIfNeeded(pushAesKey);
        if (isBlank(decryptIfNeeded(selectedAppKey)) || isBlank(plainToken) || isBlank(plainAesKey)) {
            throw new com.aurora.common.core.exception.ServiceException(
                    "启用虚拟支付前必须配置当前环境 AppKey、消息推送 Token 和 EncodingAESKey");
        }
        if (plainToken.length() < 3 || plainToken.length() > 32
                || plainAesKey.replace(" ", "").length() != 43) {
            throw new com.aurora.common.core.exception.ServiceException(
                    "消息推送 Token 或 EncodingAESKey 格式错误");
        }

        SysWxConfig miniApp = sysWxConfigService.getActiveByType("mini_app");
        if (miniApp == null || !appId.equals(miniApp.getAppId())) {
            throw new com.aurora.common.core.exception.ServiceException(
                    "虚拟支付 AppId 必须与已启用的小程序 AppId 一致");
        }
    }

    private String valueOrExisting(String value, String existing) {
        return value == null ? existing : value;
    }

    private String decryptIfNeeded(String value) {
        if (isBlank(value)) {
            return value;
        }
        return encryptUtils.decrypt(value);
    }

    private String maskIfPresent(String value) {
        return isBlank(value) ? null : EncryptUtils.mask(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
