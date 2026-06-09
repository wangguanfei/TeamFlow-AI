package com.teamflow.ai.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ReadOnlyDemoAccountFilterTest {

    private final ReadOnlyDemoAccountFilter filter = new ReadOnlyDemoAccountFilter(new ObjectMapper());

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/ai/chat/stream",
            "/api/ai/knowledge/ask",
            "/api/ai/doc/summary",
            "/api/ai/code/generate"
    })
    void allowsDemoAiChatWriteEndpoints(String uri) throws Exception {
        authenticateDemo();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        MockHttpServletResponse response = doFilter("POST", uri, chainCalled);

        assertThat(chainCalled).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/tasks",
            "/api/ai-sessions",
            "/api/ai-messages",
            "/api/notifications/read"
    })
    void blocksDemoNonAllowedWriteEndpoints(String uri) throws Exception {
        authenticateDemo();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        MockHttpServletResponse response = doFilter("POST", uri, chainCalled);

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("演示账号为只读模式");
    }

    private MockHttpServletResponse doFilter(String method, String uri, AtomicBoolean chainCalled) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (servletRequest, servletResponse) -> chainCalled.set(true));
        return response;
    }

    private void authenticateDemo() {
        UserPrincipal principal = new UserPrincipal(
                3L,
                DemoAccountConstants.USERNAME,
                List.of(new SimpleGrantedAuthority("ai:chat"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
