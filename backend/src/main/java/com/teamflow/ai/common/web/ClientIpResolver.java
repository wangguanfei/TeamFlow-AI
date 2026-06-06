package com.teamflow.ai.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 解析真实客户端 IP 的工具类。
 *
 * <p>优先取反向代理透传的 X-Forwarded-For 首段，并把 IPv6 回环地址归一化为 127.0.0.1，
 * 避免本机直连时记录成 {@code 0:0:0:0:0:0:0:1} 这类不直观的形式。
 */
public final class ClientIpResolver {

    private static final String LOOPBACK_IPV4 = "127.0.0.1";
    private static final String UNKNOWN = "unknown";

    private ClientIpResolver() {
    }

    /** 解析客户端 IP，依次尝试 X-Forwarded-For、X-Real-IP，最后回退 remoteAddr。 */
    public static String resolve(HttpServletRequest request) {
        String ip = firstValid(request.getHeader("X-Forwarded-For"));
        if (ip == null) {
            ip = sanitize(request.getHeader("X-Real-IP"));
        }
        if (ip == null) {
            ip = request.getRemoteAddr();
        }
        return normalize(ip);
    }

    /** 取 X-Forwarded-For 链路中第一个有效地址（客户端真实 IP）。 */
    private static String firstValid(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return null;
        }
        for (String segment : forwardedFor.split(",")) {
            String candidate = sanitize(segment);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || UNKNOWN.equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }

    /** 把 IPv6 回环、IPv4-mapped 回环统一成 127.0.0.1。 */
    private static String normalize(String ip) {
        if (ip == null) {
            return null;
        }
        if ("::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            return LOOPBACK_IPV4;
        }
        if (ip.startsWith("::ffff:")) {
            return ip.substring("::ffff:".length());
        }
        return ip;
    }
}
