package com.aurora.common.core.enums;

import lombok.Getter;

/**
 * 统一返回码:收口 Result 的 code 与默认文案,避免散落的魔法数字。
 *
 * <p>本枚举为地基件,供 Result 重载与各处统一引用;接入由后续阶段逐步替换硬编码。
 *
 * @author aurora
 */
@Getter
public enum ResultCode {

    /** 成功 */
    SUCCESS(200, "操作成功"),

    /** 失败(通用业务 / 服务端错误) */
    ERROR(500, "操作失败"),

    /** 请求参数错误 */
    BAD_REQUEST(400, "请求参数错误"),

    /** 未认证(未登录或登录态失效) */
    UNAUTHORIZED(401, "认证失败,无法访问系统资源"),

    /** 无权限 */
    FORBIDDEN(403, "没有权限,请联系管理员授权");

    private final int code;
    private final String msg;

    ResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
