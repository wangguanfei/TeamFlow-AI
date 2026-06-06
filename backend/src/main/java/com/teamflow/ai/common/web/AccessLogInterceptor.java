package com.teamflow.ai.common.web;

import com.teamflow.ai.common.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * HTTP 访问日志拦截器：为每个 {@code /api/**} 请求输出一行结构化访问日志。
 *
 * <p>记录内容：HTTP 方法、路径、查询串、响应状态码、耗时、操作人、客户端 IP。
 * 配合 {@link TraceIdFilter} 写入 MDC 的 traceId，可在日志中按 traceId 串起
 * 单次请求的全部业务日志，是线上问题定位的主要抓手。
 *
 * <p>日志分级策略：
 * <ul>
 *   <li>5xx 或耗时超过 {@link #SLOW_REQUEST_MILLIS} 的慢请求 → {@code WARN}</li>
 *   <li>4xx 客户端错误 → {@code WARN}（便于发现异常调用 / 越权探测）</li>
 *   <li>其余正常请求 → {@code INFO}</li>
 * </ul>
 */
@Component
public class AccessLogInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AccessLogInterceptor.class);

    /** 请求开始时间存入 request 的属性名，用于在 afterCompletion 计算耗时。 */
    private static final String START_TIME_ATTR = "ACCESS_LOG_START_TIME";
    /** 慢请求阈值（毫秒），超过即以 WARN 记录以便排查性能问题。 */
    private static final long SLOW_REQUEST_MILLIS = 1000L;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        long cost = costMillis(request);
        int status = response.getStatus();
        String method = request.getMethod();
        String path = request.getRequestURI();
        String query = request.getQueryString();
        String fullPath = query == null ? path : path + "?" + query;
        String operator = currentOperator();
        String clientIp = resolveClientIp(request);

        if (status >= 500 || cost >= SLOW_REQUEST_MILLIS) {
            log.warn("HTTP {} {} -> {} ({}ms) user={} ip={}{}",
                    method, fullPath, status, cost, operator, clientIp,
                    cost >= SLOW_REQUEST_MILLIS ? " [慢请求]" : "");
        } else if (status >= 400) {
            log.warn("HTTP {} {} -> {} ({}ms) user={} ip={}", method, fullPath, status, cost, operator, clientIp);
        } else {
            log.info("HTTP {} {} -> {} ({}ms) user={} ip={}", method, fullPath, status, cost, operator, clientIp);
        }
    }

    private long costMillis(HttpServletRequest request) {
        Object start = request.getAttribute(START_TIME_ATTR);
        return start instanceof Long startMillis ? System.currentTimeMillis() - startMillis : -1L;
    }

    /** 从 Spring Security 上下文取当前登录人标识，未登录返回 anonymous。 */
    private String currentOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUsername() + "#" + principal.getUserId();
        }
        return "anonymous";
    }

    /** 解析真实客户端 IP，优先取反向代理透传的 X-Forwarded-For 首段。 */
    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
