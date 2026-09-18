package ru.musicalgreetings.gateway.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.route.builder.UriSpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import ru.musicalgreetings.gateway.error.GatewayNotFoundHandler;
import ru.musicalgreetings.gateway.ratelimit.ClientIpKeyResolver;
import ru.musicalgreetings.gateway.ratelimit.FailClosedRedisRateLimiter;
import ru.musicalgreetings.gateway.ratelimit.GlobalKeyResolver;
import ru.musicalgreetings.gateway.ratelimit.RateLimitMetrics.Scope;
import ru.musicalgreetings.gateway.ratelimit.UserKeyResolver;

@Configuration(proxyBeanMethods = false)
public class GatewayRoutesConfiguration {

    @Bean
    RouteLocator gatewayRoutes(
            RouteLocatorBuilder builder,
            GatewayRouteCatalog catalog,
            GatewayRoutesProperties routes,
            GatewayRateLimitProperties rateLimits,
            FailClosedRedisRateLimiter rateLimiter,
            @Qualifier("globalRateLimiter") FailClosedRedisRateLimiter globalRateLimiter,
            UserKeyResolver userKeyResolver,
            ClientIpKeyResolver clientIpKeyResolver,
            GlobalKeyResolver globalKeyResolver,
            GatewayNotFoundHandler notFoundHandler
    ) {
        rateLimits.validateRoutes(catalog.routeIds());

        for (GatewayRouteSpec route : catalog.routes()) {
            rateLimiter.getConfig().put(route.id(), minuteLimit(rateLimits.limitFor(route.id())));
            rateLimiter.setScope(route.id(), switch (route.clientKeyType()) {
                case USER -> Scope.USER;
                case IP -> Scope.IP;
            });
            globalRateLimiter.getConfig().put(
                    route.id(), minuteLimit(rateLimits.globalLimitFor(route.id())));
            globalRateLimiter.setScope(route.id(), Scope.GLOBAL);
        }

        RouteLocatorBuilder.Builder routesBuilder = builder.routes();
        for (GatewayRouteSpec routeSpec : catalog.routes()) {
            KeyResolver clientKeyResolver = clientKeyResolverFor(
                    routeSpec, userKeyResolver, clientIpKeyResolver);
            String targetUrl = targetUrlFor(routeSpec, routes);
            routesBuilder.route(routeSpec.id(), route -> route
                    .path(routeSpec.paths().toArray(String[]::new))
                    .and().method(routeSpec.methods().toArray(HttpMethod[]::new))
                    .filters(filters -> rateLimit(filters, rateLimiter, clientKeyResolver,
                            globalRateLimiter, globalKeyResolver))
                    .uri(targetUrl));
        }

        return routesBuilder
                .route("api-not-found", route -> route
                        .path("/api/v1/**")
                        .filters(filters -> filters.filter(
                                (exchange, chain) -> notFoundHandler.handle(exchange)))
                        .uri("no://op"))
                .build();
    }

    private static KeyResolver clientKeyResolverFor(
            GatewayRouteSpec route,
            UserKeyResolver userKeyResolver,
            ClientIpKeyResolver clientIpKeyResolver
    ) {
        return switch (route.clientKeyType()) {
            case USER -> userKeyResolver;
            case IP -> clientIpKeyResolver;
        };
    }

    private static String targetUrlFor(
            GatewayRouteSpec route,
            GatewayRoutesProperties routes
    ) {
        return switch (route.targetService()) {
            case AUTH -> routes.authServiceUrl().toString();
            case CONGRATS -> routes.congratsServiceUrl().toString();
        };
    }

    private static UriSpec rateLimit(
            GatewayFilterSpec filters,
            FailClosedRedisRateLimiter rateLimiter,
            KeyResolver keyResolver,
            FailClosedRedisRateLimiter globalRateLimiter,
            GlobalKeyResolver globalKeyResolver
    ) {
        return filters.requestRateLimiter(config -> config
                .setRateLimiter(rateLimiter)
                .setKeyResolver(keyResolver)
                .setDenyEmptyKey(true)
                .setStatusCode(HttpStatus.TOO_MANY_REQUESTS)
                .setThrowOnLimit(true))
                .requestRateLimiter(config -> config
                        .setRateLimiter(globalRateLimiter)
                        .setKeyResolver(globalKeyResolver)
                        .setDenyEmptyKey(true)
                        .setStatusCode(HttpStatus.TOO_MANY_REQUESTS)
                        .setThrowOnLimit(true));
    }

    private static FailClosedRedisRateLimiter.Config minuteLimit(int limit) {
        return new FailClosedRedisRateLimiter.Config()
                .setReplenishRate(limit)
                .setBurstCapacity(limit * 60L)
                .setRequestedTokens(60);
    }
}
