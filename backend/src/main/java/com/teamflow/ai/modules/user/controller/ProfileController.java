package com.teamflow.ai.modules.user.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.security.CurrentUser;
import com.teamflow.ai.modules.file.dto.FileContent;
import com.teamflow.ai.modules.user.dto.ProfileOverviewResponse;
import com.teamflow.ai.modules.user.dto.ProfilePasswordRequest;
import com.teamflow.ai.modules.user.dto.ProfileResponse;
import com.teamflow.ai.modules.user.dto.ProfileUpdateRequest;
import com.teamflow.ai.modules.user.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@Tag(name = "个人中心")
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Operation(summary = "获取个人资料")
    @GetMapping
    public ApiResult<ProfileResponse> profile() {
        return ApiResult.success(profileService.getProfile(CurrentUser.require().getUserId()));
    }

    @Operation(summary = "更新个人资料")
    @PutMapping
    public ApiResult<ProfileResponse> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        return ApiResult.success(profileService.updateProfile(CurrentUser.require().getUserId(), request));
    }

    @Operation(summary = "上传个人头像")
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<ProfileResponse> uploadAvatar(@RequestPart("file") MultipartFile file) {
        return ApiResult.success(profileService.uploadAvatar(CurrentUser.require().getUserId(), file));
    }

    @Operation(summary = "公开读取头像文件")
    @GetMapping("/avatar-file/{fileId}")
    public ResponseEntity<Resource> avatarFile(@PathVariable Long fileId) {
        FileContent content = profileService.loadAvatarFile(fileId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(content.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.contentLength())
                .body(content.resource());
    }

    @Operation(summary = "修改个人密码")
    @PutMapping("/password")
    public ApiResult<Void> updatePassword(@Valid @RequestBody ProfilePasswordRequest request) {
        profileService.updatePassword(CurrentUser.require().getUserId(), request);
        return ApiResult.success();
    }

    @Operation(summary = "获取个人数据概览")
    @GetMapping("/overview")
    public ApiResult<ProfileOverviewResponse> overview() {
        return ApiResult.success(profileService.getOverview(CurrentUser.require().getUserId()));
    }
}
