package ru.musicalgreetings.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static ru.musicalgreetings.gateway.config.GatewayRouteSpec.AccessType.PROTECTED;
import static ru.musicalgreetings.gateway.config.GatewayRouteSpec.AccessType.PUBLIC;
import static ru.musicalgreetings.gateway.config.GatewayRouteSpec.ClientKeyType.IP;
import static ru.musicalgreetings.gateway.config.GatewayRouteSpec.ClientKeyType.USER;
import static ru.musicalgreetings.gateway.config.GatewayRouteSpec.TargetService.AUTH;
import static ru.musicalgreetings.gateway.config.GatewayRouteSpec.TargetService.CONGRATS;

class GatewayRouteCatalogTests {

    private final GatewayRouteCatalog catalog = new GatewayRouteCatalog();

    @Test
    void containsCompleteMetadataForEveryApiRoute() {
        assertThat(catalog.routes()).containsExactly(
                spec("auth-anonymous", "/api/v1/auth/anonymous", List.of(HttpMethod.POST), AUTH, PUBLIC, IP),
                spec("auth-refresh", "/api/v1/auth/refresh", List.of(HttpMethod.POST), AUTH, PUBLIC, IP),
                spec("auth-logout", "/api/v1/auth/logout", List.of(HttpMethod.POST), AUTH, PUBLIC, IP),
                spec("holidays", "/api/v1/holidays", getAndHead(), CONGRATS, PUBLIC, IP),
                spec("holiday", "/api/v1/holidays/{holidayId}", getAndHead(), CONGRATS, PUBLIC, IP),
                spec("music-types", "/api/v1/music-types", getAndHead(), CONGRATS, PUBLIC, IP),
                spec("congrats-history", "/api/v1/congrats/history", List.of(HttpMethod.GET),
                        CONGRATS, PROTECTED, USER),
                spec("public-congrats-video", "/api/v1/congrats/{surl}/video",
                        getAndHead(), CONGRATS, PUBLIC, IP),
                spec("public-congrats", "/api/v1/congrats/{surl}",
                        getAndHead(), CONGRATS, PUBLIC, IP),
                spec("input-prompt", "/api/v1/input/prompt", List.of(HttpMethod.POST),
                        CONGRATS, PROTECTED, USER),
                spec("input-voice", "/api/v1/input/voice", List.of(HttpMethod.POST),
                        CONGRATS, PROTECTED, USER),
                spec("input-template", "/api/v1/input/template", List.of(HttpMethod.POST),
                        CONGRATS, PROTECTED, USER),
                spec("generate-lyrics", "/api/v1/generate/lyrics", List.of(HttpMethod.POST),
                        CONGRATS, PROTECTED, USER),
                spec("generate-image", "/api/v1/generate/image", List.of(HttpMethod.POST),
                        CONGRATS, PROTECTED, USER),
                spec("generate-music", "/api/v1/generate/music", List.of(HttpMethod.POST),
                        CONGRATS, PROTECTED, USER),
                spec("publish", "/api/v1/congrats", List.of(HttpMethod.POST),
                        CONGRATS, PROTECTED, USER),
                spec("push-token-set", "/api/v1/sessions/push-token", List.of(HttpMethod.POST),
                        CONGRATS, PROTECTED, USER),
                spec("push-token-delete", "/api/v1/sessions/push-token", List.of(HttpMethod.DELETE),
                        CONGRATS, PROTECTED, USER),
                spec("draft", "/api/v1/draft", List.of(HttpMethod.GET),
                        CONGRATS, PROTECTED, USER)
        );
    }

    @Test
    void rejectsDuplicateRouteIdsAtStartup() {
        GatewayRouteSpec first = new GatewayRouteSpec(
                "duplicate", List.of("/first"), List.of(HttpMethod.GET),
                CONGRATS, PUBLIC, IP);
        GatewayRouteSpec second = new GatewayRouteSpec(
                "duplicate", List.of("/second"), List.of(HttpMethod.POST),
                AUTH, PUBLIC, IP);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GatewayRouteCatalog(List.of(first, second)))
                .withMessageContaining("Duplicate gateway route id: duplicate");
    }

    private static List<HttpMethod> getAndHead() {
        return List.of(HttpMethod.GET, HttpMethod.HEAD);
    }

    private static GatewayRouteSpec spec(
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
}
