package com.teamflow.ai.common.security;

import com.teamflow.ai.modules.system.service.PermissionQueryService;
import com.teamflow.ai.modules.user.entity.SysUser;
import com.teamflow.ai.modules.user.mapper.SysUserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final SysUserMapper userMapper;
    private final PermissionQueryService permissionQueryService;
    private final TokenBlacklistService tokenBlacklistService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            SysUserMapper userMapper,
            PermissionQueryService permissionQueryService,
            TokenBlacklistService tokenBlacklistService
    ) {
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.permissionQueryService = permissionQueryService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null
                && !tokenBlacklistService.isBlacklisted(token)) {
            authenticate(token);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            JwtClaims claims = jwtService.parse(token);
            if (claims.tokenType() != JwtTokenType.ACCESS) {
                return;
            }
            SysUser user = userMapper.selectById(claims.userId());
            if (user == null || user.getDeleted() == 1 || user.getStatus() != 1) {
                return;
            }
            List<GrantedAuthority> authorities = new ArrayList<>();
            permissionQueryService.listRoleCodes(user.getId())
                    .forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
            permissionQueryService.listPermissionCodes(user.getId())
                    .forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
            UserPrincipal principal = new UserPrincipal(user.getId(), user.getUsername(), authorities);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (RuntimeException ignored) {
            SecurityContextHolder.clearContext();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorization.substring(BEARER_PREFIX.length());
    }
}
