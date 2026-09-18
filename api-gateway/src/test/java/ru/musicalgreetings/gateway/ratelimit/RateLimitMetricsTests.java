package ru.musicalgreetings.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import ru.musicalgreetings.gateway.ratelimit.RateLimitMetrics.Result;
import ru.musicalgreetings.gateway.ratelimit.RateLimitMetrics.Scope;

class RateLimitMetricsTests {

    @Test
    void recordsAllowedRejectedAndErrorWithBoundedTagsOnly() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RateLimitMetrics metrics = new RateLimitMetrics(registry);

        metrics.record("input-prompt", Scope.USER, Result.ALLOWED);
        metrics.record("input-prompt", Scope.IP, Result.REJECTED);
        metrics.record("input-prompt", Scope.GLOBAL, Result.ERROR);

        assertThat(registry.get("gateway.rate.limit.decisions")
                .tags("route", "input-prompt", "scope", "user", "result", "allowed")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("gateway.rate.limit.decisions")
                .tags("route", "input-prompt", "scope", "ip", "result", "rejected")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("gateway.rate.limit.decisions")
                .tags("route", "input-prompt", "scope", "global", "result", "error")
                .counter().count()).isEqualTo(1);

        Set<String> tagKeys = registry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .map(tag -> tag.getKey())
                .collect(Collectors.toSet());
        assertThat(tagKeys).containsExactlyInAnyOrder("route", "scope", "result");
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags())
                        .noneMatch(tag -> tag.getValue().contains("request-id-sentinel")
                                || tag.getValue().contains("user-id-sentinel")
                                || tag.getValue().contains("192.0.2.1")));
    }
}
