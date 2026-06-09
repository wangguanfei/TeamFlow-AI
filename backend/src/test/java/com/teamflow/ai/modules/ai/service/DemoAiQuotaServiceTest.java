package com.teamflow.ai.modules.ai.service;

import com.teamflow.ai.common.exception.BusinessException;
import com.teamflow.ai.modules.ai.provider.AiProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoAiQuotaServiceTest {

    @Test
    void allowsFirstOneHundredCallsAndRejectsOneHundredFirst() {
        FakeCounter counter = new FakeCounter();
        AiProperties properties = new AiProperties();
        properties.setDemoDailyLimit(100);
        DemoAiQuotaService service = new DemoAiQuotaService(counter, properties);

        for (int i = 0; i < 100; i++) {
            assertThatCode(service::consumeForDemo).doesNotThrowAnyException();
        }

        assertThatThrownBy(service::consumeForDemo)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(429);
                    assertThat(exception.getMessage()).contains("100 次");
                });
        assertThat(counter.expireCalls).isEqualTo(1);
    }

    @Test
    void failsClosedWhenRedisCounterFails() {
        FakeCounter counter = new FakeCounter();
        counter.failIncrement = true;

        DemoAiQuotaService service = new DemoAiQuotaService(counter, new AiProperties());

        assertThatThrownBy(service::consumeForDemo)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(429);
                    assertThat(exception.getMessage()).contains("限额服务暂不可用");
                });
    }

    private static class FakeCounter implements DemoAiQuotaService.RedisQuotaCounter {
        private final AtomicLong counter = new AtomicLong();
        private boolean failIncrement;
        private int expireCalls;

        @Override
        public long increment(String key) {
            if (failIncrement) {
                throw new RuntimeException("redis down");
            }
            return counter.incrementAndGet();
        }

        @Override
        public boolean expire(String key, Duration ttl) {
            expireCalls++;
            return true;
        }
    }
}
