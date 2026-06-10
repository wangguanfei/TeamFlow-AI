package com.teamflow.ai.modules.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamflow.ai.common.exception.BusinessException;
import com.teamflow.ai.common.security.DemoAccountConstants;
import com.teamflow.ai.modules.ai.dto.AiChatRequest;
import com.teamflow.ai.modules.ai.dto.AiChatResponse;
import com.teamflow.ai.modules.ai.entity.AiMessage;
import com.teamflow.ai.modules.ai.entity.AiSession;
import com.teamflow.ai.modules.ai.mapper.AiEmbeddingMapper;
import com.teamflow.ai.modules.ai.mapper.AiMessageMapper;
import com.teamflow.ai.modules.ai.mapper.AiSessionMapper;
import com.teamflow.ai.modules.ai.provider.AiProperties;
import com.teamflow.ai.modules.ai.provider.AiProvider;
import com.teamflow.ai.modules.ai.rag.RagProperties;
import com.teamflow.ai.modules.knowledge.mapper.KnowledgeSpaceMapper;
import com.teamflow.ai.modules.user.entity.SysUser;
import com.teamflow.ai.modules.user.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiServiceTest {

    @Test
    void demoChatUsesConfiguredProviderConsumesQuotaAndPersistsHistory() {
        ServiceFixture fixture = new ServiceFixture();
        fixture.givenDemoUser();
        when(fixture.aiProvider.chat(anyList(), eq("CHAT"), anyList(), eq("deepseek-chat")))
                .thenReturn(new AiProvider.AiAnswer("真实模型回复", 24, "deepseek-chat", false));

        AiChatResponse response = fixture.service.chat(new AiChatRequest(
                null,
                null,
                "CHAT",
                false,
                "deepseek-chat",
                "请介绍 TeamFlow AI"
        ), 3L);

        assertThat(response.mock()).isFalse();
        assertThat(response.session().id()).isEqualTo(10L);
        assertThat(response.session().modelName()).isEqualTo("deepseek-chat");
        assertThat(response.userMessage().content()).isEqualTo("请介绍 TeamFlow AI");
        assertThat(response.assistantMessage().content()).isEqualTo("真实模型回复");
        assertThat(fixture.messages).extracting(AiMessage::getRole).containsExactly("USER", "ASSISTANT");
        assertThat(fixture.demoAiQuotaService.consumeCount).isEqualTo(1);
        verify(fixture.aiProvider).chat(anyList(), eq("CHAT"), anyList(), eq("deepseek-chat"));
        verify(fixture.sessionMapper).insert(any(AiSession.class));
        verify(fixture.sessionMapper).updateById(any(AiSession.class));
    }

    @Test
    void demoQuotaFailureStopsBeforeProviderAndPersistence() {
        ServiceFixture fixture = new ServiceFixture();
        fixture.givenDemoUser();
        fixture.demoAiQuotaService.failure = new BusinessException(429, "演示账号今日 AI 调用次数已达 100 次，请明天再试");

        assertThatThrownBy(() -> fixture.service.chat(new AiChatRequest(
                null,
                null,
                "CHAT",
                false,
                "deepseek-chat",
                "这次应该被限额拦截"
        ), 3L)).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getCode()).isEqualTo(429);
            assertThat(exception.getMessage()).contains("100 次");
                });

        verify(fixture.aiProvider, never()).chat(anyList(), any(), anyList(), any());
        verify(fixture.sessionMapper, never()).insert(any(AiSession.class));
        verify(fixture.messageMapper, never()).insert(any(AiMessage.class));
        assertThat(fixture.demoAiQuotaService.consumeCount).isEqualTo(1);
    }

    @Test
    void rejectsChatWhenSessionBelongsToAnotherUserBeforeQuotaOrProvider() {
        ServiceFixture fixture = new ServiceFixture();
        AiSession session = new AiSession();
        session.setId(7L);
        session.setUserId(99L);
        session.setDeleted(0);
        when(fixture.sessionMapper.selectById(7L)).thenReturn(session);

        assertThatThrownBy(() -> fixture.service.chat(new AiChatRequest(
                7L,
                null,
                "CHAT",
                false,
                "deepseek-chat",
                "越权追加消息"
        ), 3L)).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getCode()).isEqualTo(403);
            assertThat(exception.getMessage()).contains("无访问权限");
        });

        assertThat(fixture.demoAiQuotaService.consumeCount).isZero();
        verify(fixture.aiProvider, never()).chat(anyList(), any(), anyList(), any());
        verify(fixture.messageMapper, never()).insert(any(AiMessage.class));
    }

    private static class ServiceFixture {
        private final AiSessionMapper sessionMapper = mock(AiSessionMapper.class);
        private final AiMessageMapper messageMapper = mock(AiMessageMapper.class);
        private final AiEmbeddingMapper embeddingMapper = mock(AiEmbeddingMapper.class);
        private final KnowledgeSpaceMapper spaceMapper = mock(KnowledgeSpaceMapper.class);
        private final SysUserMapper userMapper = mock(SysUserMapper.class);
        private final AiProvider aiProvider = mock(AiProvider.class);
        private final AiKnowledgeIndexService knowledgeIndexService = new AiKnowledgeIndexService(
                null, null, null, null, null, new RagProperties(), null, null, null);
        private final RecordingDemoAiQuotaService demoAiQuotaService = new RecordingDemoAiQuotaService();
        private final AiProperties properties = new AiProperties();
        private final List<AiSession> sessions = new ArrayList<>();
        private final List<AiMessage> messages = new ArrayList<>();
        private final AtomicLong sessionIds = new AtomicLong(10);
        private final AtomicLong messageIds = new AtomicLong(100);
        private final AiService service;

        ServiceFixture() {
            properties.setApiKey("test-key");
            properties.setBaseUrl("https://example.test/v1");
            properties.setModel("deepseek-chat");
            stubSessionMapper();
            stubMessageMapper();
            service = new AiService(
                    sessionMapper,
                    messageMapper,
                    embeddingMapper,
                    spaceMapper,
                    userMapper,
                    aiProvider,
                    properties,
                    new ObjectMapper(),
                    knowledgeIndexService,
                    demoAiQuotaService,
                    new com.teamflow.ai.common.cache.DashboardCacheService(
                            new com.teamflow.ai.common.cache.JsonCacheService(null))
            );
        }

        private void givenDemoUser() {
            SysUser demoUser = new SysUser();
            demoUser.setId(3L);
            demoUser.setUsername(DemoAccountConstants.USERNAME);
            demoUser.setNickname("只读演示账号");
            when(userMapper.selectById(3L)).thenReturn(demoUser);
            when(userMapper.selectBatchIds(anyCollection())).thenReturn(List.of(demoUser));
        }

        private void stubSessionMapper() {
            when(sessionMapper.insert(any(AiSession.class))).thenAnswer(invocation -> {
                AiSession session = invocation.getArgument(0);
                session.setId(sessionIds.getAndIncrement());
                sessions.add(session);
                return 1;
            });
            when(sessionMapper.updateById(any(AiSession.class))).thenReturn(1);
            when(sessionMapper.selectBatchIds(anyCollection())).thenAnswer(invocation -> {
                Collection<?> ids = invocation.getArgument(0);
                return sessions.stream()
                        .filter(session -> ids.contains(session.getId()))
                        .toList();
            });
        }

        private void stubMessageMapper() {
            when(messageMapper.insert(any(AiMessage.class))).thenAnswer(invocation -> {
                AiMessage message = invocation.getArgument(0);
                message.setId(messageIds.getAndIncrement());
                messages.add(message);
                return 1;
            });
            when(messageMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(messages));
            when(messageMapper.selectCount(any())).thenAnswer(invocation -> (long) messages.size());
        }
    }

    private static class RecordingDemoAiQuotaService extends DemoAiQuotaService {
        private int consumeCount;
        private BusinessException failure;

        RecordingDemoAiQuotaService() {
            super((DemoAiQuotaService.RedisQuotaCounter) null, new AiProperties());
        }

        @Override
        public void consumeForDemo() {
            consumeCount++;
            if (failure != null) {
                throw failure;
            }
        }
    }
}
