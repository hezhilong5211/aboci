package com.aurora.admin.mobile.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * 移动端用户编号生成器(纯逻辑,不依赖 DB,便于单测)。
 *
 * <p>编号格式 = "Ts" + yyMMddHH(注册时刻,GMT+8) + 4位随机[a-zA-Z0-9] + N位随机数字。
 * 数字段初始 4 位(0001-9999);同一位宽内重试 {@link #RETRY_PER_WIDTH} 次仍撞库时,
 * 把数字段位宽 +1(9999→99999…)扩容,以保证在命名空间逼近饱和时仍能产出唯一编号。</p>
 */
public final class UserNoGenerator {

    /** 前缀 */
    private static final String PREFIX = "Ts";

    /** 日期段格式:两位年+月+日+时(注册时刻) */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyMMddHH");

    /** 随机字母数字字符集([a-zA-Z0-9]) */
    private static final char[] ALNUM =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    /** 字母数字随机段位宽 */
    private static final int ALNUM_WIDTH = 4;

    /** 数字段初始位宽 */
    private static final int INITIAL_DIGIT_WIDTH = 4;

    /** 同一数字位宽下的重试次数,超过则扩容位宽 */
    private static final int RETRY_PER_WIDTH = 16;

    /** 数字段位宽上限(防御饱和死循环;到上限仍冲突视为异常) */
    private static final int MAX_DIGIT_WIDTH = 12;

    private UserNoGenerator() {
    }

    /**
     * 生成全局唯一的用户编号。
     *
     * @param time   注册时刻(GMT+8 的本地时间,直接取其 yyMMddHH 作为日期段)
     * @param exists 撞库谓词:返回 true 表示该编号已存在,需重新生成
     * @return 唯一编号
     * @throws IllegalStateException 数字段位宽扩容到上限仍无法生成唯一编号时抛出
     */
    public static String generate(LocalDateTime time, Predicate<String> exists) {
        String datePart = time.format(DATE_FMT);
        for (int digitWidth = INITIAL_DIGIT_WIDTH; digitWidth <= MAX_DIGIT_WIDTH; digitWidth++) {
            for (int attempt = 0; attempt < RETRY_PER_WIDTH; attempt++) {
                String candidate = PREFIX + datePart + randomAlnum() + randomDigits(digitWidth);
                if (!exists.test(candidate)) {
                    return candidate;
                }
            }
            // 当前位宽连续 RETRY_PER_WIDTH 次撞库,扩容数字段位宽后继续
        }
        throw new IllegalStateException("用户编号生成失败:数字段位宽已达上限仍无法保证唯一");
    }

    /**
     * 生成 {@link #ALNUM_WIDTH} 位随机字母数字串。
     */
    private static String randomAlnum() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(ALNUM_WIDTH);
        for (int i = 0; i < ALNUM_WIDTH; i++) {
            sb.append(ALNUM[rnd.nextInt(ALNUM.length)]);
        }
        return sb.toString();
    }

    /**
     * 生成指定位宽的随机数字串,范围 [1, 10^width-1] 左补零定宽(避免全 0)。
     *
     * @param width 数字段位宽
     */
    private static String randomDigits(int width) {
        long upper = pow10(width);
        long value = ThreadLocalRandom.current().nextLong(1, upper);
        StringBuilder sb = new StringBuilder(Long.toString(value));
        while (sb.length() < width) {
            sb.insert(0, '0');
        }
        return sb.toString();
    }

    /**
     * 计算 10 的 width 次方(width 受 {@link #MAX_DIGIT_WIDTH} 约束,不溢出 long)。
     */
    private static long pow10(int width) {
        long r = 1L;
        for (int i = 0; i < width; i++) {
            r *= 10L;
        }
        return r;
    }
}
