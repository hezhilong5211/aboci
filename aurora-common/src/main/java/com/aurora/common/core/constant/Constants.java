package com.aurora.common.core.constant;

/**
 * 通用业务常量:收口逻辑删除、状态、用户类型、权限标识等散落的魔法值。
 *
 * <p>地基件,接入由后续阶段逐步替换各 Controller / Service 中的 "0" / "2" / "00" / "*:*:*" 等字面量。
 *
 * @author aurora
 */
public final class Constants {

    private Constants() {
    }

    /** 逻辑删除——未删除(存在) */
    public static final String DEL_FLAG_NORMAL = "0";

    /** 逻辑删除——已删除 */
    public static final String DEL_FLAG_DELETED = "2";

    /** 账号 / 角色 / 菜单状态——正常 */
    public static final String STATUS_NORMAL = "0";

    /** 账号 / 角色 / 菜单状态——停用 */
    public static final String STATUS_DISABLE = "1";

    /** 用户类型——系统用户 */
    public static final String USER_TYPE_SYS = "00";

    /** 全部权限标识(超级管理员) */
    public static final String ALL_PERMISSION = "*:*:*";

    /** 种子超级管理员用户 ID(仅用于"不可删 / 改超管账号"的业务护栏,非权限判定) */
    public static final long SUPER_ADMIN_ID = 1L;
}
