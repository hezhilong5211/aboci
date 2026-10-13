package com.aurora.admin.mobile.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserNoGenerator 单测:验证格式、大量生成唯一性、强制饱和触发数字段扩容后仍唯一。
 */
class UserNoGeneratorTest {

    /** 标准格式:Ts + 8位日期 + 4位[a-zA-Z0-9] + 4位及以上数字 */
    private static final Pattern STANDARD =
            Pattern.compile("^Ts\\d{8}[0-9a-zA-Z]{4}\\d{4,}$");

    private final LocalDateTime time = LocalDateTime.of(2026, 6, 21, 8, 30);

    @Test
    void 单个编号符合格式约定() {
        String no = UserNoGenerator.generate(time, candidate -> false);
        assertTrue(STANDARD.matcher(no).matches(), "编号不符合格式: " + no);
        // 2(前缀) + 8(日期) + 4(字母数字) + 4(数字) = 18
        assertEquals(18, no.length());
        assertTrue(no.startsWith("Ts26062108"), "日期段应为 yyMMddHH=26062108, 实际: " + no);
    }

    @Test
    void 大量生成全部唯一() {
        Set<String> pool = new HashSet<>();
        int count = 50_000;
        for (int i = 0; i < count; i++) {
            // exists 谓词:已在池中即视为撞库,迫使重随
            String no = UserNoGenerator.generate(time, pool::contains);
            assertTrue(pool.add(no), "出现重复编号: " + no);
        }
        assertEquals(count, pool.size());
    }

    @Test
    void 强制饱和触发数字段扩容后仍唯一() {
        Set<String> pool = new HashSet<>();
        // 谓词:凡数字段恰为 4 位(即标准初始宽度)的候选一律判为已存在,
        // 模拟 4 位数字命名空间彻底饱和,强制生成器把数字段位宽 +1 扩容。
        java.util.function.Predicate<String> exists = candidate -> {
            if (digitWidth(candidate) == 4) {
                return true;
            }
            return !pool.add(candidate);
        };
        for (int i = 0; i < 5_000; i++) {
            String no = UserNoGenerator.generate(time, exists);
            // 扩容后数字段应 > 4 位,且整体仍唯一(已由 pool.add 在谓词内保证)
            assertTrue(digitWidth(no) > 4, "未触发扩容, 数字段仍为 4 位: " + no);
            assertTrue(STANDARD.matcher(no).matches(), "扩容后编号格式异常: " + no);
        }
        assertEquals(5_000, pool.size());
    }

    @Test
    void 字母数字段不含非法字符且数字段非全零() {
        for (int i = 0; i < 2_000; i++) {
            String no = UserNoGenerator.generate(time, candidate -> false);
            String alnum = no.substring(10, 14);
            assertTrue(alnum.matches("[0-9a-zA-Z]{4}"), "字母数字段非法: " + alnum);
            String digits = no.substring(14);
            assertTrue(digits.matches("\\d{4,}"), "数字段非法: " + digits);
            assertNotEquals(0L, Long.parseLong(digits), "数字段不应全为 0: " + digits);
        }
    }

    /**
     * 取候选编号数字段的位宽(用于断言是否触发扩容)。
     * 按已知结构切分:前缀 Ts(2) + 日期(8) + 字母数字段(4) = 14,其后即数字段。
     */
    private static int digitWidth(String no) {
        return no.length() - 14;
    }
}
