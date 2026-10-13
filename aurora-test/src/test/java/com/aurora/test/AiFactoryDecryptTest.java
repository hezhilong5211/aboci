package com.aurora.test;

import com.aurora.ai.domain.SysAiModel;
import com.aurora.ai.service.AiFactory;
import com.aurora.framework.utils.EncryptUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;

/**
 * 验证 TD-AI-01:createImageModel 必须解密 API Key
 * (修复前该路径直接使用密文 config.getApiKey(),从不调用 EncryptUtils.decrypt)
 */
class AiFactoryDecryptTest {

    private SysAiModel model(String encKey) {
        SysAiModel m = new SysAiModel();
        m.setApiKey(encKey);
        m.setBaseUrl("https://api.example.com/v1");
        m.setModelDeployment("gpt-image-1");
        m.setModelType("image");
        return m;
    }

    @Test
    void image模型应解密ApiKey() {
        AiFactory factory = new AiFactory();
        EncryptUtils enc = Mockito.mock(EncryptUtils.class);
        when(enc.decrypt("ENC:cipher")).thenReturn("sk-real-key");
        ReflectionTestUtils.setField(factory, "encryptUtils", enc);

        factory.createImageModel(model("ENC:cipher"));

        verify(enc, atLeast(1)).decrypt("ENC:cipher");
    }
}
