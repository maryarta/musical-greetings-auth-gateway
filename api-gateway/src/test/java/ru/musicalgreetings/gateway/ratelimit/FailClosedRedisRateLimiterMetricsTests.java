package ru.musicalgreetings.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import ru.musicalgreetings.gateway.ratelimit.RateLimitMetrics.Scope;

class FailClosedRedisRateLimiterMetricsTests {

    private static final String ROUTE = "input-prompt";

    @Test
    @SuppressWarnings("unchecked")
    void recordsAllowedDecisionWithoutUsingLimiterKeyAsTag() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.execute(any(), anyList(), anyList()))
                .thenReturn(Flux.just(List.of(1L, 10L, 0L)));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        FailClosedRedisRateLimiter limiter = limiter(redis, registry, Scope.USER);

        StepVerifier.create(limiter.isAllowed(ROUTE, "user-id-sentinel"))
                .assertNext(response -> assertThat(response.isAllowed()).isTrue())
                .verifyComplete();

        assertThat(counter(registry, "user", "allowed")).isEqualTo(1);
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags())
                        .noneMatch(tag -> tag.getValue().contains("user-id-sentinel")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordsRejectedDecision() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.execute(any(), anyList(), anyList()))
                .thenReturn(Flux.just(List.of(0L, 0L, 15L)));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        FailClosedRedisRateLimiter limiter = limiter(redis, registry, Scope.IP);

        StepVerifier.create(limiter.isAllowed(ROUTE, "192.0.2.1"))
                .assertNext(response -> assertThat(response.isAllowed()).isFalse())
                .verifyComplete();

        assertThat(counter(registry, "ip", "rejected")).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordsRedisErrorExactlyOnce() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.execute(any(), anyList(), anyList()))
                .thenReturn(Flux.error(new IllegalStateException("redis unavailable")));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        FailClosedRedisRateLimiter limiter = limiter(redis, registry, Scope.GLOBAL);

        StepVerifier.create(limiter.isAllowed(ROUTE, "all-clients"))
                .expectError(RateLimiterUnavailableException.class)
                .verify();

        assertThat(counter(registry, "global", "error")).isEqualTo(1);
    }

    private FailClosedRedisRateLimiter limiter(
            ReactiveStringRedisTemplate redis,
            SimpleMeterRegistry registry,
            Scope scope
    ) {
        FailClosedRedisRateLimiter limiter = new FailClosedRedisRateLimiter(
                redis,
                null,
                new RateLimitMetrics(registry)
        );
        limiter.getConfig().put(ROUTE, new FailClosedRedisRateLimiter.Config()
                .setReplenishRate(1)
                .setBurstCapacity(60)
                .setRequestedTokens(60));
        limiter.setScope(ROUTE, scope);
        return limiter;
    }

    private double counter(SimpleMeterRegistry registry, String scope, String result) {
        return registry.get("gateway.rate.limit.decisions")
                .tags("route", ROUTE, "scope", scope, "result", result)
                .counter()
                .count();
    }
}
