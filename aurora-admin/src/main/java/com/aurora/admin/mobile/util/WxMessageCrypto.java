package com.aurora.admin.mobile.util;

import com.aurora.common.core.exception.ServiceException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/**
 * 微信小程序消息推送验签与安全模式 AES 解密。
 *
 * <p>WxJava 4.8.4 的消息解密实现未校验尾部 AppId，本工具按微信官方协议补齐该校验。</p>
 */
public final class WxMessageCrypto {

    private static final int RANDOM_PREFIX_LENGTH = 16;
    private static final int LENGTH_FIELD_LENGTH = 4;
    private static final int PKCS7_BLOCK_SIZE = 32;

    private WxMessageCrypto() {
    }

    /** 校验明文模式签名：sha1(sort(token,timestamp,nonce))。 */
    public static boolean verifyPlainSignature(String token, String timestamp, String nonce, String signature) {
        if (isBlank(token) || isBlank(timestamp) || isBlank(nonce) || isBlank(signature)) {
            return false;
        }
        String expected = sha1(token, timestamp, nonce);
        return constantTimeEquals(expected, signature);
    }

    /**
     * 校验安全模式 msg_signature，并解密、校验消息尾部 AppId。
     */
    public static String decryptJson(String token, String encodingAesKey, String appId,
                                     String timestamp, String nonce, String msgSignature,
                                     String encryptedContent) {
        if (isBlank(token) || isBlank(encodingAesKey) || isBlank(appId)
                || isBlank(timestamp) || isBlank(nonce) || isBlank(msgSignature)
                || isBlank(encryptedContent)) {
            throw new ServiceException("微信消息推送配置或请求参数不完整");
        }

        String expectedSignature = sha1(token, timestamp, nonce, encryptedContent);
        if (!constantTimeEquals(expectedSignature, msgSignature)) {
            throw new ServiceException("微信消息推送验签失败");
        }

        try {
            String normalizedKey = encodingAesKey.replace(" ", "");
            int padding = (4 - normalizedKey.length() % 4) % 4;
            normalizedKey = normalizedKey + "=".repeat(padding);
            byte[] aesKey = Base64.getDecoder().decode(normalizedKey);
            if (aesKey.length != 32) {
                throw new ServiceException("消息推送 EncodingAESKey 格式错误");
            }

            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(aesKey, "AES"),
                    new IvParameterSpec(aesKey, 0, 16));
            byte[] padded = cipher.doFinal(Base64.getDecoder().decode(encryptedContent));
            byte[] plain = removePkcs7Padding(padded);

            int minLength = RANDOM_PREFIX_LENGTH + LENGTH_FIELD_LENGTH;
            if (plain.length < minLength) {
                throw new ServiceException("微信消息推送密文格式错误");
            }
            int messageLength = ByteBuffer.wrap(plain, RANDOM_PREFIX_LENGTH, LENGTH_FIELD_LENGTH).getInt();
            int messageStart = minLength;
            int messageEnd = messageStart + messageLength;
            if (messageLength < 0 || messageEnd > plain.length) {
                throw new ServiceException("微信消息推送密文长度错误");
            }

            String fromAppId = new String(
                    Arrays.copyOfRange(plain, messageEnd, plain.length), StandardCharsets.UTF_8);
            if (!constantTimeEquals(appId, fromAppId)) {
                throw new ServiceException("微信消息推送 AppId 不匹配");
            }
            return new String(Arrays.copyOfRange(plain, messageStart, messageEnd), StandardCharsets.UTF_8);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("微信消息推送解密失败");
        }
    }

    private static byte[] removePkcs7Padding(byte[] padded) {
        if (padded == null || padded.length == 0) {
            throw new ServiceException("微信消息推送密文为空");
        }
        int pad = padded[padded.length - 1] & 0xff;
        if (pad < 1 || pad > PKCS7_BLOCK_SIZE || pad > padded.length) {
            throw new ServiceException("微信消息推送填充格式错误");
        }
        for (int i = padded.length - pad; i < padded.length; i++) {
            if ((padded[i] & 0xff) != pad) {
                throw new ServiceException("微信消息推送填充格式错误");
            }
        }
        return Arrays.copyOf(padded, padded.length - pad);
    }

    private static String sha1(String... values) {
        try {
            String[] sorted = Arrays.copyOf(values, values.length);
            Arrays.sort(sorted);
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(String.join("", sorted).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new ServiceException("微信消息推送验签失败");
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
