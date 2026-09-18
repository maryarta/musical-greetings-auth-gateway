package ru.musicalgreetings.gateway.ratelimit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.cloud.gateway.filter.ratelimit.AbstractRateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter.Response;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import reactor.core.publisher.Mono;
import ru.musicalgreetings.gateway.ratelimit.RateLimitMetrics.Result;
import ru.musicalgreetings.gateway.ratelimit.RateLimitMetrics.Scope;

@Component
@Primary
public class FailClosedRedisRateLimiter extends AbstractRateLimiter<FailClosedRedisRateLimiter.Config> {

    static final String CONFIGURATION_PROPERTY_NAME = "fail-closed-redis-rate-limiter";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisScript<List<Long>> script;
    private final RateLimitMetrics metrics;
    private final Map<String, Scope> scopes = new ConcurrentHashMap<>();

    public FailClosedRedisRateLimiter(
            ReactiveStringRedisTemplate redisTemplate,
            ConfigurationService configurationService,
            RateLimitMetrics metrics
    ) {
        super(Config.class, CONFIGURATION_PROPERTY_NAME, configurationService);
        this.redisTemplate = redisTemplate;
        this.metrics = metrics;
        this.script = tokenBucketScript();
    }

    public void setScope(String routeId, Scope scope) {
        scopes.put(routeId, scope);
    }

    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        return Mono.defer(() -> {
            Config config = getConfig().get(routeId);
            if (config == null) {
                return Mono.error(new IllegalArgumentException(
                        "No rate limit configuration found for route " + routeId));
            }

            List<String> arguments = List.of(
                    Integer.toString(config.replenishRate),
                    Long.toString(config.burstCapacity),
                    Integer.toString(config.requestedTokens),
                    Long.toString(Math.max(1,
                            (long) Math.ceil((double) config.burstCapacity / config.replenishRate * 2)))
            );

            return redisTemplate.execute(script, getKeys(routeId, id), arguments)
                    .reduce(new ArrayList<Long>(), (all, result) -> {
                        all.addAll(result);
                        return all;
                    })
                    .filter(result -> result.size() >= 3)
                    .switchIfEmpty(Mono.error(new IllegalStateException(
                            "Rate limiter script returned an invalid response")))
                    .map(result -> response(config, result))
                    .doOnNext(response -> metrics.record(
                            routeId,
                            scope(routeId),
                            response.isAllowed() ? Result.ALLOWED : Result.REJECTED
                    ));
        }).onErrorMap(
                error -> !(error instanceof RateLimiterUnavailableException),
                error -> new RateLimiterUnavailableException("Redis rate limiter is unavailable", error)
        ).doOnError(error -> recordError(routeId));
    }

    private Scope scope(String routeId) {
        Scope scope = scopes.get(routeId);
        if (scope == null) {
            throw new IllegalArgumentException("No rate limit scope found for route " + routeId);
        }
        return scope;
    }

    private void recordError(String routeId) {
        Scope scope = scopes.get(routeId);
        if (scope != null) {
            metrics.record(routeId, scope, Result.ERROR);
        }
    }

    static List<String> getKeys(String routeId, String id) {
        String prefix = "request_rate_limiter.{" + routeId + "." + id + "}.";
        return Arrays.asList(prefix + "tokens", prefix + "timestamp");
    }

    private static Response response(Config config, List<Long> result) {
        boolean allowed = result.get(0) == 1L;
        long retryAfter = result.get(2);

        Map<String, String> headers = new LinkedHashMap<>();
        if (!allowed) {
            headers.put("Retry-After", Long.toString(Math.max(1, retryAfter)));
        }
        return new Response(allowed, headers);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static RedisScript<List<Long>> tokenBucketScript() {
        DefaultRedisScript script = new DefaultRedisScript();
        script.setLocation(new ClassPathResource("scripts/generation_rate_limiter.lua"));
        script.setResultType(List.class);
        script.afterPropertiesSet();
        return script;
    }

    public static class Config {

        private int replenishRate;
        private long burstCapacity;
        private int requestedTokens = 1;

        public int getReplenishRate() {
            return replenishRate;
        }

        public Config setReplenishRate(int replenishRate) {
            Assert.isTrue(replenishRate > 0, "Replenish rate must be positive");
            this.replenishRate = replenishRate;
            return this;
        }

        public long getBurstCapacity() {
            return burstCapacity;
        }

        public Config setBurstCapacity(long burstCapacity) {
            Assert.isTrue(burstCapacity > 0, "Burst capacity must be positive");
            Assert.isTrue(burstCapacity >= requestedTokens,
                    "Burst capacity must be greater than or equal to requested tokens");
            this.burstCapacity = burstCapacity;
            return this;
        }

        public int getRequestedTokens() {
            return requestedTokens;
        }

        public Config setRequestedTokens(int requestedTokens) {
            Assert.isTrue(requestedTokens > 0, "Requested tokens must be positive");
            if (burstCapacity > 0) {
                Assert.isTrue(burstCapacity >= requestedTokens,
                        "Burst capacity must be greater than or equal to requested tokens");
            }
            this.requestedTokens = requestedTokens;
            return this;
        }

    }
}
