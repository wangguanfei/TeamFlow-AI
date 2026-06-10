package com.teamflow.ai.modules.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teamflow.ai.common.cache.DashboardCacheService;
import com.teamflow.ai.common.exception.BusinessException;
import com.teamflow.ai.common.security.DemoAccountConstants;
import com.teamflow.ai.common.security.JwtClaims;
import com.teamflow.ai.common.security.JwtService;
import com.teamflow.ai.common.security.JwtTokenType;
import com.teamflow.ai.common.security.TokenBlacklistService;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.common.web.ClientIpResolver;
import com.teamflow.ai.common.web.IpLocationResolver;
import com.teamflow.ai.common.web.UserAgentParser;
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
import com.teamflow.ai.modules.system.service.LoginLogService;
import com.teamflow.ai.modules.system.service.PermissionQueryService;
import com.teamflow.ai.modules.user.entity.SysUser;
import com.teamflow.ai.modules.user.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final SysUserMapper userMapper;
    private final LoginLogMapper loginLogMapper;
    private final LoginLogService loginLogService;
    private final PermissionQueryService permissionQueryService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimitService loginRateLimitService;
    private final TokenBlacklistService tokenBlacklistService;
    private final IpLocationResolver ipLocationResolver;
    private final DashboardCacheService dashboardCache;

    public AuthService(
            SysUserMapper userMapper,
            LoginLogMapper loginLogMapper,
            LoginLogService loginLogService,
            PermissionQueryService permissionQueryService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginRateLimitService loginRateLimitService,
            TokenBlacklistService tokenBlacklistService,
            IpLocationResolver ipLocationResolver,
            DashboardCacheService dashboardCache
    ) {
        this.userMapper = userMapper;
        this.loginLogMapper = loginLogMapper;
        this.loginLogService = loginLogService;
        this.permissionQueryService = permissionQueryService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginRateLimitService = loginRateLimitService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.ipLocationResolver = ipLocationResolver;
        this.dashboardCache = dashboardCache;
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest request, HttpServletRequest servletRequest) {
        String username = request.username();
        String clientIp = ClientIpResolver.resolve(servletRequest);
        boolean demoAccount = isReadOnlyDemoUsername(username);
        if (!demoAccount) {
            try {
                loginRateLimitService.checkNotLocked(username, clientIp);
            } catch (BusinessException e) {
                writeLoginLog(null, username, servletRequest, 0, "登录失败次数过多，账号已锁定");
                log.warn("登录失败：账号已锁定 username={} ip={}", username, clientIp);
                throw e;
            }
        }

        SysUser user = findByUsername(username);
        if (user == null || user.getDeleted() == 1) {
            log.warn("登录失败：账号不存在 username={} ip={}", username, clientIp);
            if (!demoAccount) {
                int failCount = loginRateLimitService.recordFailure(username, clientIp);
                writeLoginLog(null, username, servletRequest, 0, "账号或密码错误");
                throw new BusinessException(loginRateLimitService.buildLoginErrorMsg(failCount));
            }
            throw new BusinessException("账号或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            if (!isReadOnlyDemoUser(user)) {
                loginRateLimitService.recordFailure(username, clientIp);
                writeLoginLog(user.getId(), user.getUsername(), servletRequest, 0, "账号已被禁用");
            }
            log.warn("登录失败：账号已禁用 username={} userId={} ip={}", username, user.getId(), clientIp);
            throw new BusinessException("账号已被禁用");
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            log.warn("登录失败：密码错误 username={} userId={} ip={}", username, user.getId(), clientIp);
            if (!isReadOnlyDemoUser(user)) {
                int failCount = loginRateLimitService.recordFailure(username, clientIp);
                writeLoginLog(user.getId(), user.getUsername(), servletRequest, 0, "账号或密码错误");
                throw new BusinessException(loginRateLimitService.buildLoginErrorMsg(failCount));
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
        log.info("登录成功 username={} userId={} ip={}", username, user.getId(), clientIp);
        return buildTokenResponse(user);
    }

    @Transactional
    public AuthTokenResponse register(RegisterRequest request) {
        SysUser existing = findByUsername(request.username());
        if (existing != null) {
            log.warn("注册失败：账号已存在 username={}", request.username());
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
        log.info("注册成功 username={} userId={}", user.getUsername(), user.getId());
        dashboardCache.evictUserStats();
        return buildTokenResponse(user);
    }

    public AuthTokenResponse refresh(RefreshTokenRequest request) {
        JwtClaims claims = jwtService.parse(request.refreshToken());
        if (claims.tokenType() != JwtTokenType.REFRESH) {
            log.warn("刷新 Token 失败：tokenType 非 REFRESH userId={}", claims.userId());
            throw new BusinessException(401, "refreshToken无效");
        }
        SysUser user = userMapper.selectById(claims.userId());
        if (user == null || user.getDeleted() == 1 || user.getStatus() != 1) {
            log.warn("刷新 Token 失败：账号不可用 userId={}", claims.userId());
            throw new BusinessException(401, "账号不可用");
        }
        log.debug("刷新 Token 成功 userId={}", user.getId());
        return buildTokenResponse(user);
    }

    /**
     * 退出登录：将 access token 和 refresh token 同时加入黑名单，使其在自然过期前立即失效。
     * 尽力而为——token 缺失或解析失败时静默忽略（登出本就是幂等操作）。
     */
    public void logout(String accessToken, String refreshToken) {
        blacklistToken(accessToken);
        blacklistToken(refreshToken);
    }

    private void blacklistToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            JwtClaims claims = jwtService.parse(token);
            tokenBlacklistService.blacklist(token, claims.expiresAt());
            log.info("登出成功，token 已加入黑名单 userId={} type={}", claims.userId(), claims.tokenType());
        } catch (RuntimeException ignored) {
            log.debug("登出时 token 无效或已过期，忽略");
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
        try {
            String ip = ClientIpResolver.resolve(request);
            String ua = request.getHeader("User-Agent");
            LoginLog loginLog = new LoginLog();
            loginLog.setUserId(userId);
            loginLog.setUsername(username);
            loginLog.setLoginIp(ip);
            loginLog.setUserAgent(ua);
            loginLog.setStatus(status);
            loginLog.setMessage(message);
            loginLog.setCreatedAt(LocalDateTime.now());
            try {
                loginLog.setLoginLocation(ipLocationResolver.resolve(ip));
                loginLog.setBrowser(UserAgentParser.parseBrowser(ua));
                loginLog.setOs(UserAgentParser.parseOs(ua));
            } catch (Exception e) {
                log.warn("登录日志富化失败: {}", e.getMessage());
            }
            loginLogService.insert(loginLog);
        } catch (Exception e) {
            log.error("写入登录日志失败: {}", e.getMessage());
        }
    }
}
