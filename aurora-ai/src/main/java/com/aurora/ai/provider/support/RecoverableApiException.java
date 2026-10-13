package com.aurora.ai.provider.support;

/**
 * 可恢复的 API 调用异常(provider 限流 / 一过性服务端故障)。
 *
 * <p>由 {@link AiHttpUtil} 在收到 429(Too Many Requests)/500/502/503 等一过性状态码时抛出,
 * 供上层(如出图消费者)据此做退避重试;其余状态码(如 400 参数错)仍抛普通 RuntimeException,不重试。</p>
 */
public class RecoverableApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 触发的 HTTP 状态码 */
    private final int statusCode;

    public RecoverableApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
