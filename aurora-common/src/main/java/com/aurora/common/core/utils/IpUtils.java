package com.aurora.common.core.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP地址工具类
 */
public class IpUtils {

    private static final String UNKNOWN = "unknown";

    /**
     * 获取客户端IP地址
     */
    public static String getIpAddr(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        // 安全:不再盲信客户端可伪造的 X-Forwarded-For / X-Real-IP 等请求头
        // (否则攻击者轮换该头即可绕过限流/IP 封禁、伪造来源 IP)。
        // 真实客户端 IP 交由 Servlet 容器经 server.forward-headers-strategy 在可信代理范围内解析,
        // 再由 getRemoteAddr() 返回;直连或伪造头的请求只会拿到其真实 socket 地址。
        String ip = request.getRemoteAddr();
        if (ip == null || ip.isEmpty()) {
            return UNKNOWN;
        }
        return "0:0:0:0:0:0:0:1".equals(ip) ? "127.0.0.1" : ip;
    }

    /**
     * 获取IP地址归属地（简化版本，返回内网/外网标识）
     * 如需精确归属地，可集成第三方IP库如 ip2region
     */
    public static String getIpLocation(String ip) {
        if (ip == null || ip.isEmpty()) {
            return "未知";
        }
        if (isInternalIp(ip)) {
            return "内网IP";
        }
        // 简化实现，实际可接入 ip2region 等库
        return "外网IP";
    }

    /**
     * 判断是否为内网IP
     */
    public static boolean isInternalIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        if ("127.0.0.1".equals(ip) || "localhost".equalsIgnoreCase(ip)) {
            return true;
        }
        // 10.x.x.x
        if (ip.startsWith("10.")) {
            return true;
        }
        // 172.16.x.x - 172.31.x.x
        if (ip.startsWith("172.")) {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                int second = Integer.parseInt(parts[1]);
                if (second >= 16 && second <= 31) {
                    return true;
                }
            }
        }
        // 192.168.x.x
        if (ip.startsWith("192.168.")) {
            return true;
        }
        return false;
    }
}
