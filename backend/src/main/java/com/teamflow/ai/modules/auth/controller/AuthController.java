package com.teamflow.ai.modules.auth.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.exception.BusinessException;
import com.teamflow.ai.common.security.CurrentUser;
import com.teamflow.ai.modules.auth.dto.AuthTokenResponse;
import com.teamflow.ai.modules.auth.dto.CurrentUserResponse;
import com.teamflow.ai.modules.auth.dto.LoginRequest;
import com.teamflow.ai.modules.auth.dto.LogoutRequest;
import com.teamflow.ai.modules.auth.dto.RefreshTokenRequest;
import com.teamflow.ai.modules.auth.service.AuthService;
import com.teamflow.ai.modules.auth.service.LoginRateLimitService;
import com.teamflow.ai.common.web.ClientIpResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimitService loginRateLimitService;

    public AuthController(AuthService authService, LoginRateLimitService loginRateLimitService) {
        this.authService = authService;
        this.loginRateLimitService = loginRateLimitService;
    }

    @Operation(summary = "账号密码登录")
    @PostMapping("/login")
    public ApiResult<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return ApiResult.success(authService.login(request, servletRequest));
    }

    @Operation(summary = "自主注册已关闭")
    @PostMapping("/register")
    public ApiResult<AuthTokenResponse> register(HttpServletRequest servletRequest) {
        loginRateLimitService.checkAndRecordRegisterAttempt(ClientIpResolver.resolve(servletRequest));
        throw new BusinessException(403, "企业后台不支持自主注册，请联系系统管理员创建账号");
    }

    @Operation(summary = "刷新Token")
    @PostMapping("/refresh-token")
    public ApiResult<AuthTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResult.success(authService.refresh(request));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public ApiResult<Void> logout(
            HttpServletRequest servletRequest,
            @RequestBody(required = false) LogoutRequest body) {
        String refreshToken = body != null ? body.refreshToken() : null;
        authService.logout(resolveBearerToken(servletRequest), refreshToken);
        return ApiResult.success();
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length());
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public ApiResult<CurrentUserResponse> me() {
        return ApiResult.success(authService.currentUser(CurrentUser.require()));
    }
}
