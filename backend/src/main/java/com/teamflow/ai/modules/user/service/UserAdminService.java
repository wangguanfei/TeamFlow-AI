package com.teamflow.ai.modules.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.exception.BusinessException;
import com.teamflow.ai.modules.system.entity.SysRole;
import com.teamflow.ai.modules.system.entity.SysUserRole;
import com.teamflow.ai.modules.system.mapper.SysRoleMapper;
import com.teamflow.ai.modules.system.mapper.SysUserRoleMapper;
import com.teamflow.ai.modules.user.dto.UserCreateRequest;
import com.teamflow.ai.modules.user.dto.UserPasswordResetRequest;
import com.teamflow.ai.modules.user.dto.UserPageItem;
import com.teamflow.ai.modules.user.dto.UserUpdateRequest;
import com.teamflow.ai.modules.user.entity.SysUser;
import com.teamflow.ai.modules.user.mapper.SysUserMapper;
import com.teamflow.ai.common.cache.PermissionCacheService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserAdminService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final PermissionCacheService permissionCacheService;

    public UserAdminService(
            SysUserMapper userMapper,
            SysUserRoleMapper userRoleMapper,
            SysRoleMapper roleMapper,
            PasswordEncoder passwordEncoder,
            PermissionCacheService permissionCacheService
    ) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.permissionCacheService = permissionCacheService;
    }

    public PageResult<UserPageItem> pageUsers(long page, long size, String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0)
                .and(keyword != null && !keyword.isBlank(), query -> query
                        .like(SysUser::getUsername, keyword)
                        .or()
                        .like(SysUser::getNickname, keyword)
                        .or()
                        .like(SysUser::getEmail, keyword))
                .orderByDesc(SysUser::getId);
        Page<SysUser> result = userMapper.selectPage(Page.of(page, size), wrapper);
        List<Long> userIds = result.getRecords().stream().map(SysUser::getId).toList();
        Map<Long, List<String>> roleCodes = loadRoleCodes(userIds);
        List<UserPageItem> records = result.getRecords().stream()
                .map(user -> new UserPageItem(
                        user.getId(),
                        user.getUsername(),
                        user.getNickname(),
                        user.getEmail(),
                        user.getMobile(),
                        user.getStatus(),
                        user.getLastLoginTime(),
                        roleCodes.getOrDefault(user.getId(), List.of())
                ))
                .toList();
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    public List<UserPageItem> listAssignableUsers() {
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0)
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getId));
        List<Long> userIds = users.stream().map(SysUser::getId).toList();
        Map<Long, List<String>> roleCodes = loadRoleCodes(userIds);
        return users.stream()
                .map(user -> new UserPageItem(
                        user.getId(),
                        user.getUsername(),
                        user.getNickname(),
                        user.getEmail(),
                        user.getMobile(),
                        user.getStatus(),
                        user.getLastLoginTime(),
                        roleCodes.getOrDefault(user.getId(), List.of())
                ))
                .toList();
    }

    @Transactional
    public UserPageItem createUser(UserCreateRequest request) {
        SysUser existing = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.username())
                .last("LIMIT 1"));
        if (existing != null) {
            throw new BusinessException("账号已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname() == null || request.nickname().isBlank() ? request.username() : request.nickname());
        user.setEmail(request.email());
        user.setMobile(request.mobile());
        user.setStatus(request.status() == null ? 1 : request.status());
        user.setDeleted(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        assignUserRoles(user.getId(), request.roleIds());
        Map<Long, List<String>> roleCodes = loadRoleCodes(List.of(user.getId()));
        return new UserPageItem(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getMobile(),
                user.getStatus(),
                user.getLastLoginTime(),
                roleCodes.getOrDefault(user.getId(), List.of())
        );
    }

    @Transactional
    public UserPageItem updateUser(Long userId, UserUpdateRequest request) {
        SysUser user = getUserEntity(userId);
        user.setNickname(request.nickname() == null || request.nickname().isBlank() ? user.getUsername() : request.nickname());
        user.setEmail(request.email());
        user.setMobile(request.mobile());
        user.setStatus(request.status() == null ? 1 : request.status());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return toPageItem(user);
    }

    @Transactional
    public void resetPassword(Long userId, UserPasswordResetRequest request) {
        SysUser user = getUserEntity(userId);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    public List<Long> listUserRoleIds(Long userId) {
        ensureUserExists(userId);
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId))
                .stream()
                .map(SysUserRole::getRoleId)
                .toList();
    }

    @Transactional
    public void assignUserRoles(Long userId, List<Long> roleIds) {
        ensureUserExists(userId);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (roleIds != null && !roleIds.isEmpty()) {
            for (Long roleId : roleIds.stream().distinct().toList()) {
                SysUserRole relation = new SysUserRole();
                relation.setUserId(userId);
                relation.setRoleId(roleId);
                relation.setScopeType("SYSTEM");
                relation.setScopeId(0L);
                relation.setCreatedAt(LocalDateTime.now());
                userRoleMapper.insert(relation);
            }
        }
        permissionCacheService.evictUser(userId);
    }

    private void ensureUserExists(Long userId) {
        getUserEntity(userId);
    }

    private SysUser getUserEntity(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private UserPageItem toPageItem(SysUser user) {
        Map<Long, List<String>> roleCodes = loadRoleCodes(List.of(user.getId()));
        return new UserPageItem(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getMobile(),
                user.getStatus(),
                user.getLastLoginTime(),
                roleCodes.getOrDefault(user.getId(), List.of())
        );
    }

    private Map<Long, List<String>> loadRoleCodes(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<SysUserRole> relations = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .in(SysUserRole::getUserId, userIds));
        List<Long> roleIds = relations.stream().map(SysUserRole::getRoleId).distinct().toList();
        if (roleIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> roleCodeById = roleMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(SysRole::getId, SysRole::getRoleCode));
        return relations.stream()
                .collect(Collectors.groupingBy(
                        SysUserRole::getUserId,
                        Collectors.mapping(relation -> roleCodeById.get(relation.getRoleId()), Collectors.toList())
                ));
    }
}
