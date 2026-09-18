package ru.musicalgreetings.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import ru.musicalgreetings.gateway.ratelimit.RateLimitMetrics.Scope;

@Testcontainers(disabledWithoutDocker = true)
class FailClosedRedisRateLimiterTests {

    private static final String ROUTE_ID = "input";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private ReactiveStringRedisTemplate redis;
    private FailClosedRedisRateLimiter limiter;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redis = new ReactiveStringRedisTemplate(connectionFactory);
        redis.getConnectionFactory().getReactiveConnection().serverCommands().flushAll().block();

        limiter = new FailClosedRedisRateLimiter(
                redis,
                null,
                new RateLimitMetrics(new SimpleMeterRegistry())
        );
        limiter.getConfig().put(ROUTE_ID, minuteLimit(3));
        limiter.setScope(ROUTE_ID, Scope.USER);
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void allowsConfiguredMinuteLimitAndThenReturnsRetryAfter() {
        List<Boolean> allowed = Flux.range(0, 4)
                .concatMap(index -> limiter.isAllowed(ROUTE_ID, "user-1"))
                .map(response -> response.isAllowed())
                .collectList()
                .block();

        assertThat(allowed).containsExactly(true, true, true, false);

        var denied = limiter.isAllowed(ROUTE_ID, "user-1").block();
        assertThat(denied).isNotNull();
        assertThat(denied.isAllowed()).isFalse();
        assertThat(denied.getHeaders().get("Retry-After")).matches("[1-9][0-9]*");
        assertThat(denied.getHeaders()).hasSize(1);
    }

    @Test
    void keepsBucketsIndependentByRouteAndSubject() {
        limiter.getConfig().put("generate", minuteLimit(5));
        limiter.setScope("generate", Scope.USER);

        consume(ROUTE_ID, "user-1", 3);

        assertThat(limiter.isAllowed(ROUTE_ID, "user-1").block().isAllowed()).isFalse();
        assertThat(limiter.isAllowed(ROUTE_ID, "user-2").block().isAllowed()).isTrue();
        assertThat(limiter.isAllowed("generate", "user-1").block().isAllowed()).isTrue();
    }

    @Test
    void doesNotAddHeadersToAllowedResponse() {
        var allowed = limiter.isAllowed(ROUTE_ID, "user-with-quota").block();

        assertThat(allowed).isNotNull();
        assertThat(allowed.isAllowed()).isTrue();
        assertThat(allowed.getHeaders()).isEmpty();
    }

    @Test
    void addsOnlyRetryAfterToDeniedResponse() {
        limiter.getConfig().put("global", minuteLimit(1));
        limiter.setScope("global", Scope.GLOBAL);

        limiter.isAllowed("global", "all-clients").block();
        var denied = limiter.isAllowed("global", "all-clients").block();

        assertThat(denied).isNotNull();
        assertThat(denied.isAllowed()).isFalse();
        assertThat(denied.getHeaders())
                .containsOnlyKeys("Retry-After");
    }

    @Test
    void refillsTokensUsingTimeStoredByRedis() {
        consume(ROUTE_ID, "user-1", 3);
        List<String> keys = FailClosedRedisRateLimiter.getKeys(ROUTE_ID, "user-1");

        redis.opsForValue().set(keys.get(0), "0")
                .then(redis.opsForValue().set(keys.get(1), "0"))
                .block(Duration.ofSeconds(5));

        assertThat(limiter.isAllowed(ROUTE_ID, "user-1").block().isAllowed()).isTrue();
    }

    @Test
    void luaScriptAtomicallyEnforcesLimitForConcurrentRequests() {
        long allowed = Flux.range(0, 20)
                .flatMap(index -> limiter.isAllowed(ROUTE_ID, "user-1"), 20)
                .filter(response -> response.isAllowed())
                .count()
                .block();

        assertThat(allowed).isEqualTo(3);
    }

    @Test
    void redisFailureIsPropagatedAsRateLimiterUnavailable() {
        connectionFactory.destroy();
        connectionFactory = null;

        StepVerifier.create(limiter.isAllowed(ROUTE_ID, "user-1"))
                .expectError(RateLimiterUnavailableException.class)
                .verify();
    }

    private void consume(String routeId, String subject, int count) {
        Flux.range(0, count)
                .concatMap(index -> limiter.isAllowed(routeId, subject))
                .blockLast();
    }

    private static FailClosedRedisRateLimiter.Config minuteLimit(int limit) {
        return new FailClosedRedisRateLimiter.Config()
                .setReplenishRate(limit)
                .setBurstCapacity(limit * 60L)
                .setRequestedTokens(60);
    }
}
