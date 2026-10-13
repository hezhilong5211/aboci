package com.aurora.admin.mobile.service;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;
import com.aurora.admin.mobile.domain.SysWxConfig;
import com.aurora.common.core.exception.ServiceException;
import com.aurora.framework.utils.EncryptUtils;
import com.github.binarywang.wxpay.config.WxPayConfig;
import com.github.binarywang.wxpay.service.WxPayService;
import com.github.binarywang.wxpay.service.impl.WxPayServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信 SDK 动态工厂
 * 读取 sys_wx_config 启用配置、解密敏感项后动态构建 WxMaService / WxPayService,
 * 并按"配置标识 + 更新时间"缓存复用(SDK 内部缓存 access_token/平台证书,必须单例复用)。
 * 配置更新后缓存键自然失效,无需手动清理。
 */
@Service
@RequiredArgsConstructor
public class WxServiceFactory {

    private final ISysWxConfigService sysWxConfigService;
    private final EncryptUtils encryptUtils;

    /**
     * 小程序客户端缓存,key = appId + "_" + updateTime
     */
    private final Map<String, WxMaService> maServiceCache = new ConcurrentHashMap<>();

    /**
     * 支付客户端缓存,key = mchId + "_" + updateTime
     */
    private final Map<String, WxPayService> payServiceCache = new ConcurrentHashMap<>();

    /**
     * 获取小程序 WxMaService(取启用中的 mini_app 配置,解密 appSecret 后构建)
     */
    public WxMaService getWxMaService() {
        SysWxConfig config = sysWxConfigService.getActiveByType("mini_app");
        if (config == null) {
            throw new ServiceException("未配置启用中的小程序应用");
        }

        String cacheKey = config.getAppId() + "_" + cacheStamp(config);
        return maServiceCache.computeIfAbsent(cacheKey, k -> {
            WxMaDefaultConfigImpl maConfig = new WxMaDefaultConfigImpl();
            maConfig.setAppid(config.getAppId());
            maConfig.setSecret(encryptUtils.decrypt(config.getAppSecret()));

            WxMaService service = new WxMaServiceImpl();
            service.setWxMaConfig(maConfig);
            return service;
        });
    }

    /**
     * 获取微信支付 WxPayService(取启用中的 pay 配置,解密 apiV3Key/私钥/证书后构建)
     */
    public WxPayService getWxPayService() {
        SysWxConfig config = sysWxConfigService.getActiveByType("pay");
        if (config == null) {
            throw new ServiceException("未配置启用中的微信支付应用");
        }

        String cacheKey = config.getMchId() + "_" + cacheStamp(config);
        return payServiceCache.computeIfAbsent(cacheKey, k -> {
            WxPayConfig payConfig = new WxPayConfig();
            payConfig.setAppId(config.getAppId());
            payConfig.setMchId(config.getMchId());
            payConfig.setApiV3Key(encryptUtils.decrypt(config.getApiV3Key()));
            // 私钥/证书用字符串内容注入,避免打 jar 后读不到相对路径文件
            payConfig.setPrivateKeyString(encryptUtils.decrypt(config.getPrivateKey()));
            payConfig.setPrivateCertString(encryptUtils.decrypt(config.getPrivateCert()));
            payConfig.setNotifyUrl(config.getNotifyUrl());
            // 微信支付公钥模式(新商户默认):配公钥ID+公钥内容,用于回调/应答验签;
            // 公钥为公开内容、明文存取;两者留空则回退平台证书自动模式(旧商户)
            if (config.getPublicKeyId() != null && !config.getPublicKeyId().isEmpty()) {
                payConfig.setPublicKeyId(config.getPublicKeyId());
            }
            if (config.getPublicKey() != null && !config.getPublicKey().isEmpty()) {
                payConfig.setPublicKeyString(config.getPublicKey());
            }

            WxPayService service = new WxPayServiceImpl();
            service.setConfig(payConfig);
            return service;
        });
    }

    /**
     * 清空两类客户端缓存(配置变更后强制重建时调用)
     */
    public void clearCache() {
        maServiceCache.clear();
        payServiceCache.clear();
    }

    /**
     * 缓存戳:更新时间存在取其毫秒值,否则用 0 兜底,保证缓存键稳定
     */
    private long cacheStamp(SysWxConfig config) {
        return config.getUpdateTime() == null
                ? 0L
                : config.getUpdateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
