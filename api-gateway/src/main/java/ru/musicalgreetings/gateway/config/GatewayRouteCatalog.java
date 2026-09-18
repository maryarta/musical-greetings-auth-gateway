package ru.musicalgreetings.gateway.config;

import static ru.musicalgreetings.gateway.config.GatewayRouteSpec.AccessType.PROTECTED;
import static ru.musicalgreetings.gateway.config.GatewayRouteSpec.AccessType.PUBLIC;
import static ru.musicalgreetings.gateway.config.GatewayRouteSpec.ClientKeyType.IP;
import static ru.musicalgreetings.gateway.config.GatewayRouteSpec.ClientKeyType.USER;
import static ru.musicalgreetings.gateway.config.GatewayRouteSpec.TargetService.AUTH;
import static ru.musicalgreetings.gateway.config.GatewayRouteSpec.TargetService.CONGRATS;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
public final class GatewayRouteCatalog {

    private static final List<HttpMethod> GET_AND_HEAD = List.of(HttpMethod.GET, HttpMethod.HEAD);

    private final List<GatewayRouteSpec> routes;

    public GatewayRouteCatalog() {
        this(List.of(
                route("auth-anonymous", "/api/v1/auth/anonymous", HttpMethod.POST, AUTH, PUBLIC, IP),
                route("auth-refresh", "/api/v1/auth/refresh", HttpMethod.POST, AUTH, PUBLIC, IP),
                route("auth-logout", "/api/v1/auth/logout", HttpMethod.POST, AUTH, PUBLIC, IP),
                route("holidays", "/api/v1/holidays", GET_AND_HEAD, CONGRATS, PUBLIC, IP),
                route("holiday", "/api/v1/holidays/{holidayId}", GET_AND_HEAD, CONGRATS, PUBLIC, IP),
                route("music-types", "/api/v1/music-types", GET_AND_HEAD, CONGRATS, PUBLIC, IP),
                route("congrats-history", "/api/v1/congrats/history",
                        HttpMethod.GET, CONGRATS, PROTECTED, USER),
                route("public-congrats-video", "/api/v1/congrats/{surl}/video",
                        GET_AND_HEAD, CONGRATS, PUBLIC, IP),
                route("public-congrats", "/api/v1/congrats/{surl}",
                        GET_AND_HEAD, CONGRATS, PUBLIC, IP),
                route("input-prompt", "/api/v1/input/prompt", HttpMethod.POST, CONGRATS, PROTECTED, USER),
                route("input-voice", "/api/v1/input/voice", HttpMethod.POST, CONGRATS, PROTECTED, USER),
                route("input-template", "/api/v1/input/template", HttpMethod.POST,
                        CONGRATS, PROTECTED, USER),
                route("generate-lyrics", "/api/v1/generate/lyrics", HttpMethod.POST,
                        CONGRATS, PROTECTED, USER),
                route("generate-image", "/api/v1/generate/image", HttpMethod.POST,
                        CONGRATS, PROTECTED, USER),
                route("generate-music", "/api/v1/generate/music", HttpMethod.POST,
                        CONGRATS, PROTECTED, USER),
                route("publish", "/api/v1/congrats", HttpMethod.POST, CONGRATS, PROTECTED, USER),
                route("push-token-set", "/api/v1/sessions/push-token", HttpMethod.POST,
                        CONGRATS, PROTECTED, USER),
                route("push-token-delete", "/api/v1/sessions/push-token", HttpMethod.DELETE,
                        CONGRATS, PROTECTED, USER),
                route("draft", "/api/v1/draft", HttpMethod.GET, CONGRATS, PROTECTED, USER)
        ));
    }

    GatewayRouteCatalog(List<GatewayRouteSpec> routes) {
        this.routes = List.copyOf(routes);
        validateUniqueIds(this.routes);
    }

    public List<GatewayRouteSpec> routes() {
        return routes;
    }

    public Set<String> routeIds() {
        return routes.stream()
                .map(GatewayRouteSpec::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static GatewayRouteSpec route(
            String id,
            String path,
            HttpMethod method,
            GatewayRouteSpec.TargetService targetService,
            GatewayRouteSpec.AccessType accessType,
            GatewayRouteSpec.ClientKeyType clientKeyType
    ) {
        return route(id, path, List.of(method), targetService, accessType, clientKeyType);
    }

    private static GatewayRouteSpec route(
            String id,
            String path,
            List<HttpMethod> methods,
            GatewayRouteSpec.TargetService targetService,
            GatewayRouteSpec.AccessType accessType,
            GatewayRouteSpec.ClientKeyType clientKeyType
    ) {
        return new GatewayRouteSpec(
                id, List.of(path), methods, targetService, accessType, clientKeyType);
    }

    private static void validateUniqueIds(List<GatewayRouteSpec> routes) {
        Set<String> routeIds = new HashSet<>();
        for (GatewayRouteSpec route : routes) {
            if (!routeIds.add(route.id())) {
                throw new IllegalArgumentException("Duplicate gateway route id: " + route.id());
            }
        }
    }
}
