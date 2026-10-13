package com.aurora.framework.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 敏感数据加密工具类
 * 使用 AES-128-CBC 加密
 *
 * @author aurora
 */
@Slf4j
@Component
public class EncryptUtils {

    /**
     * 加密密钥（16字节）
     * 建议通过配置文件配置，这里使用默认值
     */
    @Value("${aurora.encrypt.key:aurora-secret-16}")
    private String secretKey;

    /**
     * 加密向量（16字节）
     */
    @Value("${aurora.encrypt.iv:aurora-iv-vector}")
    private String initVector;

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";   // 仅用于兼容旧密文(固定 IV)
    private static final String ALGORITHM_GCM = "AES/GCM/NoPadding";  // 新密文:GCM 认证加密 + 每次随机 IV
    private static final String ENCRYPT_PREFIX = "ENC:";              // 旧格式前缀(CBC,固定 IV)
    private static final String GCM_PREFIX = "ENC2:";                 // 新格式前缀(GCM,随机 IV 前置拼接)
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 加密字符串
     *
     * @param plainText 明文
     * @return 加密后的字符串（带前缀）
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        // 已加密(新旧任一格式)则幂等返回
        if (plainText.startsWith(GCM_PREFIX) || plainText.startsWith(ENCRYPT_PREFIX)) {
            return plainText;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv); // 每次随机 IV,杜绝确定性加密(同明文不再产生同密文)
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");

            Cipher cipher = Cipher.getInstance(ALGORITHM_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));

            byte[] ct = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            // 输出格式:ENC2: + Base64([iv(12) | ciphertext+tag]),IV 随密文一并存储
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return GCM_PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            log.error("加密失败: {}", e.getMessage());
            throw new RuntimeException("加密失败", e);
        }
    }

    /**
     * 解密字符串
     *
     * @param encryptedText 加密后的字符串（带前缀）
     * @return 明文
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        try {
            if (encryptedText.startsWith(GCM_PREFIX)) {
                // 新格式:GCM + 随机 IV(前置)
                byte[] all = Base64.getDecoder().decode(encryptedText.substring(GCM_PREFIX.length()));
                byte[] iv = new byte[GCM_IV_LENGTH];
                System.arraycopy(all, 0, iv, 0, GCM_IV_LENGTH);
                byte[] ct = new byte[all.length - GCM_IV_LENGTH];
                System.arraycopy(all, GCM_IV_LENGTH, ct, 0, ct.length);
                SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");
                Cipher cipher = Cipher.getInstance(ALGORITHM_GCM);
                cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
                return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
            }
            if (encryptedText.startsWith(ENCRYPT_PREFIX)) {
                // 旧格式:CBC + 固定 IV(向后兼容历史密文,改写保存时会自动升级为 GCM)
                return legacyDecryptCbc(encryptedText.substring(ENCRYPT_PREFIX.length()));
            }
            // 无前缀=明文,直接返回
            return encryptedText;
        } catch (Exception e) {
            log.error("解密失败: {}", e.getMessage());
            // 解密失败可能是因为数据本身未加密，返回原文(显式失败告警属待办,需配合调用方改造)
            return encryptedText;
        }
    }

    /**
     * 旧格式 AES/CBC/固定 IV 解密,仅用于兼容历史密文
     */
    private String legacyDecryptCbc(String base64) throws Exception {
        IvParameterSpec iv = new IvParameterSpec(initVector.getBytes(StandardCharsets.UTF_8));
        SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, iv);
        return new String(cipher.doFinal(Base64.getDecoder().decode(base64)), StandardCharsets.UTF_8);
    }

    /**
     * 脱敏显示（只显示前4位和后4位）
     *
     * @param text 原文
     * @return 脱敏后的字符串
     */
    public static String mask(String text) {
        if (text == null || text.length() <= 8) {
            return "****";
        }
        return text.substring(0, 4) + "****" + text.substring(text.length() - 4);
    }
}
