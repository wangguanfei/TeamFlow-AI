package com.teamflow.ai.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamflow.ai.common.api.ApiResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class ReadOnlyDemoAccountFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of(
            HttpMethod.GET.name(),
            HttpMethod.HEAD.name(),
            HttpMethod.OPTIONS.name()
    );
    private static final Set<String> ALLOWED_AUTH_ENDPOINTS = Set.of(
            "/api/auth/login",
            "/api/auth/refresh-token",
            "/api/auth/logout",
            "/api/ai/chat/stream"
    );

    private final ObjectMapper objectMapper;

    public ReadOnlyDemoAccountFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isBlockedDemoWrite(request)) {
            writeForbidden(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isBlockedDemoWrite(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod()) || ALLOWED_AUTH_ENDPOINTS.contains(request.getRequestURI())) {
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (authentication.getPrincipal() instanceof UserPrincipal principal) {
            return DemoAccountConstants.USERNAME.equals(principal.getUsername());
        }
        return false;
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResult.error(403, "演示账号为只读模式，禁止数据写入")
        ));
    }
}
