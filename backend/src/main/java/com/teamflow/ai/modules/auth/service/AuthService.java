package com.teamflow.ai.modules.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teamflow.ai.common.exception.BusinessException;
import com.teamflow.ai.common.security.DemoAccountConstants;
import com.teamflow.ai.common.security.JwtClaims;
import com.teamflow.ai.common.security.JwtService;
import com.teamflow.ai.common.security.JwtTokenType;
import com.teamflow.ai.common.security.TokenBlacklistService;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.modules.auth.dto.AuthTokenResponse;
import com.teamflow.ai.modules.auth.dto.CurrentUserResponse;
import com.teamflow.ai.modules.auth.dto.LoginRequest;
import com.teamflow.ai.modules.auth.dto.MenuRoute;
import com.teamflow.ai.modules.auth.dto.RefreshTokenRequest;
import com.teamflow.ai.modules.auth.dto.RegisterRequest;
import com.teamflow.ai.modules.auth.dto.UserSummary;
import com.teamflow.ai.modules.system.entity.LoginLog;
import com.teamflow.ai.modules.system.entity.SysMenu;
import com.teamflow.ai.modules.system.mapper.LoginLogMapper;
import com.teamflow.ai.modules.system.service.PermissionQueryService;
import com.teamflow.ai.modules.user.entity.SysUser;
import com.teamflow.ai.modules.user.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final SysUserMapper userMapper;
    private final LoginLogMapper loginLogMapper;
    private final PermissionQueryService permissionQueryService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimitService loginRateLimitService;
    private final TokenBlacklistService tokenBlacklistService;

    public AuthService(
            SysUserMapper userMapper,
            LoginLogMapper loginLogMapper,
            PermissionQueryService permissionQueryService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginRateLimitService loginRateLimitService,
            TokenBlacklistService tokenBlacklistService
    ) {
        this.userMapper = userMapper;
        this.loginLogMapper = loginLogMapper;
        this.permissionQueryService = permissionQueryService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginRateLimitService = loginRateLimitService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest request, HttpServletRequest servletRequest) {
        String username = request.username();
        String clientIp = resolveClientIp(servletRequest);
        boolean demoAccount = isReadOnlyDemoUsername(username);
        if (!demoAccount) {
            loginRateLimitService.checkNotLocked(username, clientIp);
        }

        SysUser user = findByUsername(username);
        if (user == null || user.getDeleted() == 1) {
            if (!demoAccount) {
                loginRateLimitService.recordFailure(username, clientIp);
                writeLoginLog(null, username, servletRequest, 0, "账号或密码错误");
            }
            throw new BusinessException("账号或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            if (!isReadOnlyDemoUser(user)) {
                loginRateLimitService.recordFailure(username, clientIp);
                writeLoginLog(user.getId(), user.getUsername(), servletRequest, 0, "账号已被禁用");
            }
            throw new BusinessException("账号已被禁用");
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            if (!isReadOnlyDemoUser(user)) {
                loginRateLimitService.recordFailure(username, clientIp);
                writeLoginLog(user.getId(), user.getUsername(), servletRequest, 0, "账号或密码错误");
            }
            throw new BusinessException("账号或密码错误");
        }
        if (!isReadOnlyDemoUser(user)) {
            loginRateLimitService.clearFailures(username);
        }
        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(clientIp);
        userMapper.updateById(user);
        writeLoginLog(user.getId(), user.getUsername(), servletRequest, 1, "登录成功");
        return buildTokenResponse(user);
    }

    @Transactional
    public AuthTokenResponse register(RegisterRequest request) {
        SysUser existing = findByUsername(request.username());
        if (existing != null) {
            throw new BusinessException("账号已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname() == null || request.nickname().isBlank() ? request.username() : request.nickname());
        user.setEmail(request.email());
        user.setMobile(request.mobile());
        user.setStatus(1);
        user.setDeleted(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return buildTokenResponse(user);
    }

    public AuthTokenResponse refresh(RefreshTokenRequest request) {
        JwtClaims claims = jwtService.parse(request.refreshToken());
        if (claims.tokenType() != JwtTokenType.REFRESH) {
            throw new BusinessException(401, "refreshToken无效");
        }
        SysUser user = userMapper.selectById(claims.userId());
        if (user == null || user.getDeleted() == 1 || user.getStatus() != 1) {
            throw new BusinessException(401, "账号不可用");
        }
        return buildTokenResponse(user);
    }

    /**
     * 退出登录：将当前 access token 加入黑名单，使其在自然过期前立即失效。
     * 尽力而为——token 缺失或解析失败时静默忽略（登出本就是幂等操作）。
     */
    public void logout(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        try {
            JwtClaims claims = jwtService.parse(accessToken);
            tokenBlacklistService.blacklist(accessToken, claims.expiresAt());
        } catch (RuntimeException ignored) {
            // token 无效或已过期，无需加入黑名单
        }
    }

    public CurrentUserResponse currentUser(UserPrincipal principal) {
        SysUser user = userMapper.selectById(principal.getUserId());
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException(401, "账号不存在");
        }
        return new CurrentUserResponse(
                toUserSummary(user),
                permissionQueryService.listRoleCodes(user.getId()),
                permissionQueryService.listPermissionCodes(user.getId()),
                buildMenuTree(permissionQueryService.listMenus(user.getId()))
        );
    }

    private AuthTokenResponse buildTokenResponse(SysUser user) {
        return new AuthTokenResponse(
                jwtService.createAccessToken(user.getId(), user.getUsername()),
                jwtService.createRefreshToken(user.getId(), user.getUsername()),
                "Bearer",
                120 * 60,
                toUserSummary(user),
                permissionQueryService.listRoleCodes(user.getId()),
                permissionQueryService.listPermissionCodes(user.getId()),
                buildMenuTree(permissionQueryService.listMenus(user.getId()))
        );
    }

    private UserSummary toUserSummary(SysUser user) {
        return new UserSummary(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getEmail(),
                user.getMobile()
        );
    }

    private List<MenuRoute> buildMenuTree(List<SysMenu> menus) {
        Map<Long, List<SysMenu>> childrenByParent = menus.stream()
                .collect(Collectors.groupingBy(menu -> menu.getParentId() == null ? 0L : menu.getParentId()));
        return buildMenuChildren(0L, childrenByParent);
    }

    private List<MenuRoute> buildMenuChildren(Long parentId, Map<Long, List<SysMenu>> childrenByParent) {
        List<SysMenu> children = childrenByParent.getOrDefault(parentId, List.of()).stream()
                .sorted(Comparator.comparing(menu -> menu.getSortNo() == null ? 0 : menu.getSortNo()))
                .toList();
        List<MenuRoute> routes = new ArrayList<>();
        for (SysMenu menu : children) {
            routes.add(new MenuRoute(
                    menu.getId(),
                    menu.getParentId(),
                    menu.getMenuName(),
                    menu.getPath(),
                    menu.getComponent(),
                    menu.getIcon(),
                    menu.getPermissionCode(),
                    menu.getMenuType(),
                    menu.getSortNo(),
                    buildMenuChildren(menu.getId(), childrenByParent)
            ));
        }
        return routes;
    }

    private SysUser findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .last("LIMIT 1"));
    }

    private boolean isReadOnlyDemoUser(SysUser user) {
        return user != null && isReadOnlyDemoUsername(user.getUsername());
    }

    private boolean isReadOnlyDemoUsername(String username) {
        return DemoAccountConstants.USERNAME.equals(username);
    }

    private void writeLoginLog(Long userId, String username, HttpServletRequest request, int status, String message) {
        LoginLog log = new LoginLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setLoginIp(resolveClientIp(request));
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setStatus(status);
        log.setMessage(message);
        log.setCreatedAt(LocalDateTime.now());
        loginLogMapper.insert(log);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
