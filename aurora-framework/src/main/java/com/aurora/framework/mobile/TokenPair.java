package com.aurora.framework.mobile;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 移动端双令牌载体
 * access:短期访问令牌;refresh:长期刷新令牌
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenPair {

    /** 访问令牌 */
    private String access;

    /** 刷新令牌 */
    private String refresh;
}
