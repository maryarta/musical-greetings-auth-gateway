package ru.musicalgreetings.gateway.config;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "gateway.rate-limits")
public record GatewayRateLimitProperties(
        @NotEmpty Map<String, @Positive Integer> endpoints,
        @NotEmpty Map<String, @Positive Integer> globalEndpoints
) {

    public int limitFor(String routeId) {
        return requiredLimit(endpoints, routeId, "personal");
    }

    public int globalLimitFor(String routeId) {
        return requiredLimit(globalEndpoints, routeId, "global");
    }

    public void validateRoutes(Set<String> routeIds) {
        validateRouteKeys(endpoints.keySet(), routeIds, "personal");
        validateRouteKeys(globalEndpoints.keySet(), routeIds, "global");
    }

    private static int requiredLimit(Map<String, Integer> limits, String routeId, String scope) {
        Integer limit = limits.get(routeId);
        if (limit == null) {
            throw new IllegalArgumentException(
                    "No " + scope + " rate limit configured for route " + routeId);
        }
        return limit;
    }

    private static void validateRouteKeys(
            Set<String> configuredRouteIds,
            Set<String> catalogRouteIds,
            String scope
    ) {
        Set<String> missing = new TreeSet<>(catalogRouteIds);
        missing.removeAll(configuredRouteIds);
        Set<String> unexpected = new TreeSet<>(configuredRouteIds);
        unexpected.removeAll(catalogRouteIds);
        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid " + scope + " rate limit routes: missing=" + missing
                            + ", unexpected=" + unexpected);
        }
    }
}
