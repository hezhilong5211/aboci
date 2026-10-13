package com.aurora.admin.mobile.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 微信应用配置实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_wx_config")
public class SysWxConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 配置ID
     */
    @TableId(value = "wx_id", type = IdType.AUTO)
    private Long wxId;

    /**
     * 应用类型(mini_app小程序/pay支付)
     */
    private String appType;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 微信 AppId
     */
    private String appId;

    /**
     * 小程序密钥(加密存储)
     */
    private String appSecret;

    /**
     * 微信支付商户号
     */
    private String mchId;

    /**
     * 支付 APIv3 密钥(加密存储)
     */
    private String apiV3Key;

    /**
     * 证书私钥内容(加密存储)
     */
    private String privateKey;

    /**
     * 证书内容(加密存储)
     */
    private String privateCert;

    /**
     * 支付回调地址
     */
    private String notifyUrl;

    /**
     * 微信支付公钥pub_key.pem内容(加密存储)
     */
    private String publicKey;

    /**
     * 微信支付公钥ID(PUB_KEY_ID_前缀)
     */
    private String publicKeyId;

    /**
     * 虚拟支付商户号 OfferId(appType=virtual_pay)
     */
    private String offerId;

    /**
     * 虚拟支付沙箱 AppKey(env=1,加密存储)
     */
    private String sandboxAppKey;

    /**
     * 虚拟支付现网 AppKey(env=0,加密存储)
     */
    private String prodAppKey;

    /**
     * 虚拟支付环境:0现网/1沙箱
     */
    private Integer env;

    /**
     * 消息推送 Token(验签,加密存储)
     */
    private String pushToken;

    /**
     * 消息推送 EncodingAESKey(解密,加密存储)
     */
    private String pushAesKey;

    /**
     * 是否启用(1启用/0停用)
     */
    private Integer isActive;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
