package ru.musicalgreetings.gateway.ratelimit;

import java.util.Locale;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class RateLimitMetrics {

    static final String METRIC_NAME = "gateway.rate.limit.decisions";

    private final MeterRegistry meterRegistry;

    public RateLimitMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(String route, Scope scope, Result result) {
        meterRegistry.counter(
                METRIC_NAME,
                "route", route,
                "scope", tag(scope),
                "result", tag(result)
        ).increment();
    }

    private String tag(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    public enum Scope {
        GLOBAL,
        USER,
        IP
    }

    public enum Result {
        ALLOWED,
        REJECTED,
        ERROR
    }
}
