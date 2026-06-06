package com.teamflow.ai.modules.auth.service;

import com.teamflow.ai.common.security.DemoAccountConstants;
import com.teamflow.ai.common.security.JwtProperties;
import com.teamflow.ai.common.security.JwtService;
import com.teamflow.ai.common.security.TokenBlacklistService;
import com.teamflow.ai.modules.auth.dto.AuthTokenResponse;
import com.teamflow.ai.modules.auth.dto.LoginRequest;
import com.teamflow.ai.modules.system.entity.LoginLog;
import com.teamflow.ai.modules.system.entity.SysMenu;
import com.teamflow.ai.modules.system.mapper.LoginLogMapper;
import com.teamflow.ai.modules.system.service.LoginLogService;
import com.teamflow.ai.modules.system.service.PermissionQueryService;
import com.teamflow.ai.modules.user.entity.SysUser;
import com.teamflow.ai.modules.user.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AuthServiceTest {

    @Test
    void loginRecordsLastLoginForReadOnlyDemoAccount() {
        SysUser demoUser = new SysUser();
        demoUser.setId(3L);
        demoUser.setUsername(DemoAccountConstants.USERNAME);
        demoUser.setPassword("encoded-password");
        demoUser.setNickname("演示账号");
        demoUser.setStatus(1);
        demoUser.setDeleted(0);

        AtomicReference<SysUser> updatedUser = new AtomicReference<>();
        AtomicReference<LoginLog> insertedLoginLog = new AtomicReference<>();
        RecordingLoginRateLimitService rateLimitService = new RecordingLoginRateLimitService();
        AuthService authService = new AuthService(
                userMapperReturning(demoUser, updatedUser),
                null,
                new StubLoginLogService(insertedLoginLog),
                new StubPermissionQueryService(),
                new DemoPasswordEncoder(),
                jwtService(),
                rateLimitService,
                new TokenBlacklistService(null),
                new com.teamflow.ai.common.web.IpLocationResolver()
        );

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr("10.0.0.8");
        servletRequest.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.8");
        servletRequest.addHeader("User-Agent", "JUnit");

        AuthTokenResponse response = authService.login(
                new LoginRequest(DemoAccountConstants.USERNAME, DemoAccountConstants.PASSWORD, false),
                servletRequest
        );

        assertThat(response.accessToken()).isNotBlank();
        assertThat(updatedUser.get()).isSameAs(demoUser);
        assertThat(updatedUser.get().getLastLoginTime()).isNotNull();
        assertThat(updatedUser.get().getLastLoginIp()).isEqualTo("203.0.113.10");

        assertThat(insertedLoginLog.get()).isNotNull();
        assertThat(insertedLoginLog.get().getUserId()).isEqualTo(demoUser.getId());
        assertThat(insertedLoginLog.get().getUsername()).isEqualTo(DemoAccountConstants.USERNAME);
        assertThat(insertedLoginLog.get().getLoginIp()).isEqualTo("203.0.113.10");
        assertThat(insertedLoginLog.get().getUserAgent()).isEqualTo("JUnit");
        assertThat(insertedLoginLog.get().getStatus()).isEqualTo(1);
        assertThat(insertedLoginLog.get().getMessage()).isEqualTo("登录成功");
        assertThat(insertedLoginLog.get().getCreatedAt()).isNotNull();

        assertThat(rateLimitService.checkCount.get()).isZero();
        assertThat(rateLimitService.failureCount.get()).isZero();
        assertThat(rateLimitService.clearCount.get()).isZero();
    }

    private static SysUserMapper userMapperReturning(SysUser user, AtomicReference<SysUser> updatedUser) {
        return (SysUserMapper) Proxy.newProxyInstance(
                SysUserMapper.class.getClassLoader(),
                new Class<?>[]{SysUserMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "selectOne" -> user;
                    case "updateById" -> {
                        updatedUser.set((SysUser) args[0]);
                        yield 1;
                    }
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == Void.TYPE) {
            return null;
        }
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Byte.TYPE) {
            return (byte) 0;
        }
        if (returnType == Short.TYPE) {
            return (short) 0;
        }
        if (returnType == Integer.TYPE) {
            return 0;
        }
        if (returnType == Long.TYPE) {
            return 0L;
        }
        if (returnType == Float.TYPE) {
            return 0F;
        }
        if (returnType == Double.TYPE) {
            return 0D;
        }
        if (returnType == Character.TYPE) {
            return '\0';
        }
        return null;
    }

    private static JwtService jwtService() {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer("teamflow-test");
        properties.setSecret("0123456789abcdef0123456789abcdef");
        properties.setAccessTokenMinutes(120);
        properties.setRefreshTokenDays(7);
        return new JwtService(properties);
    }

    private static class StubPermissionQueryService extends PermissionQueryService {
        StubPermissionQueryService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public List<String> listRoleCodes(Long userId) {
            return List.of(DemoAccountConstants.ROLE_CODE);
        }

        @Override
        public List<String> listPermissionCodes(Long userId) {
            return List.of();
        }

        @Override
        public List<SysMenu> listMenus(Long userId) {
            return List.of();
        }
    }

    private static class DemoPasswordEncoder implements PasswordEncoder {
        @Override
        public String encode(CharSequence rawPassword) {
            return rawPassword.toString();
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return DemoAccountConstants.PASSWORD.contentEquals(rawPassword);
        }
    }

    private static class StubLoginLogService extends LoginLogService {
        private final AtomicReference<LoginLog> captured;

        StubLoginLogService(AtomicReference<LoginLog> captured) {
            super(null);
            this.captured = captured;
        }

        @Override
        public void insert(LoginLog loginLog) {
            captured.set(loginLog);
        }
    }

    private static class RecordingLoginRateLimitService extends LoginRateLimitService {
        private final AtomicInteger checkCount = new AtomicInteger();
        private final AtomicInteger failureCount = new AtomicInteger();
        private final AtomicInteger clearCount = new AtomicInteger();

        RecordingLoginRateLimitService() {
            super(null);
        }

        @Override
        public void checkNotLocked(String username, String ip) {
            checkCount.incrementAndGet();
        }

        @Override
        public int recordFailure(String username, String ip) {
            failureCount.incrementAndGet();
            return 0;
        }

        @Override
        public void clearFailures(String username) {
            clearCount.incrementAndGet();
        }
    }
}
