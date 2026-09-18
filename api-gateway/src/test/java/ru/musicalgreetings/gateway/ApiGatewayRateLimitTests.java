package ru.musicalgreetings.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ru.musicalgreetings.gateway.support.TestDownstreamServer;
import ru.musicalgreetings.gateway.support.TestJwtFactory;
import ru.musicalgreetings.gateway.config.GatewayRouteCatalog;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.jwt.issuer=https://auth.test",
        "security.jwt.audience=musical-greetings-api"
})
class ApiGatewayRateLimitTests {

    private static final TestDownstreamServer AUTH = new TestDownstreamServer("auth");
    private static final TestDownstreamServer CONGRATS = new TestDownstreamServer("congrats");
    private static final UUID SESSION_ID = UUID.fromString("3b68fdf2-709c-43ea-bb8a-f6da031fe634");
    private static final GatewayRouteCatalog ROUTE_CATALOG = new GatewayRouteCatalog();

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private final TestJwtFactory tokens = new TestJwtFactory();

    @LocalServerPort
    private int serverPort;

    @Autowired
    private ReactiveStringRedisTemplate redis;

    private WebTestClient client;

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("gateway.routes.auth-service-url", AUTH::baseUrl);
        registry.add("gateway.routes.congrats-service-url", CONGRATS::baseUrl);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        ROUTE_CATALOG.routeIds().forEach(routeId -> registry.add(
                "gateway.rate-limits.endpoints." + routeId,
                () -> 2
        ));
        ROUTE_CATALOG.routeIds().forEach(routeId -> registry.add(
                "gateway.rate-limits.global-endpoints." + routeId,
                () -> 3
        ));
    }

    @BeforeEach
    void setUp() {
        redis.getConnectionFactory().getReactiveConnection().serverCommands().flushAll().block();
        AUTH.clear();
        CONGRATS.clear();
        client = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + serverPort)
                .build();
    }

    @AfterAll
    static void stopServers() {
        AUTH.close();
        CONGRATS.close();
    }

    @ParameterizedTest(name = "{0} {1} has personal and global limits")
    @MethodSource("limitedRoutes")
    void enforcesPersonalAndGlobalLimitsForEveryEndpoint(
            HttpMethod method,
            String path,
            int limit,
            boolean jwtRequired,
            boolean authTarget
    ) {
        String firstToken = jwtRequired ? tokens.validToken(UUID.randomUUID(), SESSION_ID) : null;
        String secondToken = jwtRequired ? tokens.validToken(UUID.randomUUID(), SESSION_ID) : null;
        String thirdToken = jwtRequired ? tokens.validToken(UUID.randomUUID(), SESSION_ID) : null;
        String firstIp = jwtRequired ? null : "192.0.2.10";
        String secondIp = jwtRequired ? null : "192.0.2.11";
        String thirdIp = jwtRequired ? null : "192.0.2.12";

        for (int request = 0; request < limit; request++) {
            exchange(method, path, firstToken, firstIp, "limit-request")
                    .expectStatus().is2xxSuccessful()
                    .expectHeader().doesNotExist("X-RateLimit-Limit")
                    .expectHeader().doesNotExist("X-RateLimit-Remaining")
                    .expectHeader().doesNotExist("X-Global-RateLimit-Limit")
                    .expectHeader().doesNotExist("X-Global-RateLimit-Remaining")
                    .expectHeader().doesNotExist("Retry-After");
        }

        exchange(method, path, firstToken, firstIp, "limit-request")
                .expectStatus().isEqualTo(429)
                .expectHeader().valueMatches("Retry-After", "[1-9][0-9]*")
                .expectHeader().doesNotExist("X-RateLimit-Limit")
                .expectHeader().doesNotExist("X-RateLimit-Remaining")
                .expectHeader().doesNotExist("X-Global-RateLimit-Limit")
                .expectHeader().doesNotExist("X-Global-RateLimit-Remaining")
                .expectHeader().valueEquals("X-Request-Id", "limit-request")
                .expectBody()
                .jsonPath("$.code").isEqualTo("rate_limited")
                .jsonPath("$.message").isEqualTo("Слишком много запросов, попробуйте позже")
                .jsonPath("$.request_id").isEqualTo("limit-request");

        exchange(method, path, secondToken, secondIp, "limit-request")
                .expectStatus().is2xxSuccessful()
                .expectHeader().doesNotExist("X-RateLimit-Limit")
                .expectHeader().doesNotExist("X-Global-RateLimit-Limit")
                .expectHeader().doesNotExist("Retry-After");

        exchange(method, path, thirdToken, thirdIp, "limit-request")
                .expectStatus().isEqualTo(429)
                .expectHeader().valueMatches("Retry-After", "[1-9][0-9]*")
                .expectHeader().doesNotExist("X-RateLimit-Limit")
                .expectHeader().doesNotExist("X-RateLimit-Remaining")
                .expectHeader().doesNotExist("X-Global-RateLimit-Limit")
                .expectHeader().doesNotExist("X-Global-RateLimit-Remaining");

        assertThat(authTarget ? AUTH.requestCount() : CONGRATS.requestCount()).isEqualTo(3);
    }

    @Test
    void keepsProtectedLimitsIndependentByUserAndEndpoint() {
        String firstToken = tokens.validToken(UUID.randomUUID(), SESSION_ID);
        String secondToken = tokens.validToken(UUID.randomUUID(), SESSION_ID);

        for (int request = 0; request < 2; request++) {
            exchange(HttpMethod.POST, "/api/v1/input/prompt", firstToken, "independent-request")
                    .expectStatus().isOk();
        }

        exchange(HttpMethod.POST, "/api/v1/input/prompt", firstToken, "independent-request")
                .expectStatus().isEqualTo(429);
        exchange(HttpMethod.POST, "/api/v1/input/prompt", secondToken, "independent-request")
                .expectStatus().isOk();
        exchange(HttpMethod.POST, "/api/v1/input/voice", firstToken, "independent-request")
                .expectStatus().isOk();
    }

    @Test
    void getAndHeadShareTheSameEndpointLimit() {
        exchange(HttpMethod.GET, "/api/v1/holidays", null, "method-limit-request")
                .expectStatus().isOk();
        exchange(HttpMethod.HEAD, "/api/v1/holidays", null, "method-limit-request")
                .expectStatus().isOk();
        exchange(HttpMethod.GET, "/api/v1/holidays", null, "method-limit-request")
                .expectStatus().isEqualTo(429);
    }

    @Test
    void keepsPublicLimitsIndependentByForwardedClientIp() {
        for (int request = 0; request < 2; request++) {
            exchangeFromIp("192.0.2.10").expectStatus().isOk();
        }

        exchangeFromIp("192.0.2.10").expectStatus().isEqualTo(429);
        exchangeFromIp("192.0.2.11").expectStatus().isOk();
    }

    private WebTestClient.ResponseSpec exchange(
            HttpMethod method,
            String path,
            String token,
            String requestId
    ) {
        return exchange(method, path, token, null, requestId);
    }

    private WebTestClient.ResponseSpec exchange(
            HttpMethod method,
            String path,
            String token,
            String clientIp,
            String requestId
    ) {
        WebTestClient.RequestBodySpec request = client.method(method).uri(path)
                .headers(headers -> {
                    if (token != null) {
                        headers.setBearerAuth(token);
                    }
                    if (clientIp != null) {
                        headers.set("X-Forwarded-For", clientIp);
                    }
                    headers.set("X-Request-Id", requestId);
                });
        return request.bodyValue("{}").exchange();
    }

    private WebTestClient.ResponseSpec exchangeFromIp(String clientIp) {
        return client.post().uri("/api/v1/auth/anonymous")
                .header("X-Forwarded-For", clientIp)
                .header("X-Request-Id", "forwarded-ip-request")
                .bodyValue("{}")
                .exchange();
    }

    private static Stream<Arguments> limitedRoutes() {
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/api/v1/auth/anonymous", 2, false, true),
                Arguments.of(HttpMethod.POST, "/api/v1/auth/refresh", 2, false, true),
                Arguments.of(HttpMethod.POST, "/api/v1/auth/logout", 2, false, true),
                Arguments.of(HttpMethod.GET, "/api/v1/holidays", 2, false, false),
                Arguments.of(HttpMethod.GET, "/api/v1/holidays/1", 2, false, false),
                Arguments.of(HttpMethod.GET, "/api/v1/music-types", 2, false, false),
                Arguments.of(HttpMethod.GET, "/api/v1/congrats/kJ3nQx7Vb2LmR9tY", 2, false, false),
                Arguments.of(HttpMethod.GET, "/api/v1/congrats/kJ3nQx7Vb2LmR9tY/video", 2, false, false),
                Arguments.of(HttpMethod.POST, "/api/v1/input/prompt", 2, true, false),
                Arguments.of(HttpMethod.POST, "/api/v1/input/voice", 2, true, false),
                Arguments.of(HttpMethod.POST, "/api/v1/input/template", 2, true, false),
                Arguments.of(HttpMethod.POST, "/api/v1/generate/lyrics", 2, true, false),
                Arguments.of(HttpMethod.POST, "/api/v1/generate/image", 2, true, false),
                Arguments.of(HttpMethod.POST, "/api/v1/generate/music", 2, true, false),
                Arguments.of(HttpMethod.POST, "/api/v1/congrats", 2, true, false),
                Arguments.of(HttpMethod.GET, "/api/v1/congrats/history", 2, true, false),
                Arguments.of(HttpMethod.POST, "/api/v1/sessions/push-token", 2, true, false),
                Arguments.of(HttpMethod.DELETE, "/api/v1/sessions/push-token", 2, true, false),
                Arguments.of(HttpMethod.GET, "/api/v1/draft?congrats_id=7c2f4e91-3b8a-4d15-9f60-1a5e8c7d2b03",
                        2, true, false)
        );
    }
}
