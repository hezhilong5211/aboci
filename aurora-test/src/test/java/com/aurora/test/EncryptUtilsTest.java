package com.aurora.test;

import com.aurora.framework.utils.EncryptUtils;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 EncryptUtils 的 AES-GCM 升级 + 向后兼容旧 CBC 密文(TD-AUDIT 加密项)
 */
class EncryptUtilsTest {

    private EncryptUtils newUtils() {
        EncryptUtils u = new EncryptUtils();
        ReflectionTestUtils.setField(u, "secretKey", "aurora-secret-16");
        ReflectionTestUtils.setField(u, "initVector", "aurora-iv-vector");
        return u;
    }

    @Test
    void GCM加解密往返且新密文为ENC2前缀() {
        EncryptUtils u = newUtils();
        String plain = "sk-abc123-secret-key";
        String enc = u.encrypt(plain);
        assertTrue(enc.startsWith("ENC2:"), "新密文应为 GCM 格式 ENC2:");
        assertEquals(plain, u.decrypt(enc));
    }

    @Test
    void 随机IV使同明文密文不同() {
        EncryptUtils u = newUtils();
        String plain = "same-plaintext";
        assertNotEquals(u.encrypt(plain), u.encrypt(plain), "随机 IV 应使同明文产生不同密文(GCM 抗确定性)");
    }

    @Test
    void 向后兼容解密旧CBC密文() throws Exception {
        EncryptUtils u = newUtils();
        String plain = "legacy-api-key";
        // 用旧的 AES/CBC/固定 IV 手工构造一条 ENC: 旧密文
        IvParameterSpec iv = new IvParameterSpec("aurora-iv-vector".getBytes(StandardCharsets.UTF_8));
        SecretKeySpec key = new SecretKeySpec("aurora-secret-16".getBytes(StandardCharsets.UTF_8), "AES");
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, key, iv);
        String legacy = "ENC:" + Base64.getEncoder().encodeToString(c.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        assertEquals(plain, u.decrypt(legacy), "旧 CBC 密文应仍能解密(平滑兼容)");
    }

    @Test
    void 明文原样返回且加密幂等() {
        EncryptUtils u = newUtils();
        assertEquals("plain-no-prefix", u.decrypt("plain-no-prefix"));
        String enc = u.encrypt("x");
        assertEquals(enc, u.encrypt(enc), "已加密内容再次 encrypt 应幂等");
    }
}
