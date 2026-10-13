package com.aurora.test;

import com.aurora.common.core.domain.Result;
import com.aurora.framework.web.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 TD-SEC-02:全局异常处理器不得向前端泄露 RuntimeException 的原始 message
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private HttpServletRequest req() {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        Mockito.when(req.getRequestURI()).thenReturn("/system/user/list");
        return req;
    }

    @Test
    void 运行时异常不应向前端泄露内部细节() {
        String sensitive = "NullPointerException at com.aurora.Secret.line42";
        Result<Void> r = handler.handleRuntimeException(new RuntimeException(sensitive), req());
        assertEquals(500, r.getCode());
        assertEquals("系统运行时异常，请联系管理员", r.getMsg());
        assertFalse(r.getMsg().contains("Secret"), "不应包含内部类名");
        assertFalse(r.getMsg().contains("line42"), "不应包含堆栈细节");
    }

    @Test
    void 权限关键字异常仍返回403() {
        Result<Void> r = handler.handleRuntimeException(new RuntimeException("Access is denied"), req());
        assertEquals(403, r.getCode());
    }
}
