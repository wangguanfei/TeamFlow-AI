package com.teamflow.ai.common.security;

import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

public class UserPrincipal {

    private final Long userId;
    private final String username;
    private final List<GrantedAuthority> authorities;

    public UserPrincipal(Long userId, String username, Collection<GrantedAuthority> authorities) {
        this.userId = userId;
        this.username = username;
        this.authorities = List.copyOf(authorities);
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public List<GrantedAuthority> getAuthorities() {
        return authorities;
    }
}
