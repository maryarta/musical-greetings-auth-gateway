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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ru.musicalgreetings.gateway.support.TestDownstreamServer;
import ru.musicalgreetings.gateway.support.TestJwtFactory;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.jwt.issuer=https://auth.test",
        "security.jwt.audience=musical-greetings-api"
})
class ApiGatewayRoutingTests {

    private static final TestDownstreamServer AUTH = new TestDownstreamServer("auth");
    private static final TestDownstreamServer CONGRATS = new TestDownstreamServer("congrats");
    private static final UUID USER_ID = UUID.fromString("7f3a2b1c-8e4d-47a0-9f60-1a5e8c7d2b03");
    private static final UUID SESSION_ID = UUID.fromString("3b68fdf2-709c-43ea-bb8a-f6da031fe634");

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
    static void downstreamUrls(DynamicPropertyRegistry registry) {
        registry.add("gateway.routes.auth-service-url", AUTH::baseUrl);
        registry.add("gateway.routes.congrats-service-url", CONGRATS::baseUrl);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
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

    @ParameterizedTest
    @MethodSource("publicRoutes")
    void routesPublicContractPaths(
            HttpMethod method,
            String path,
            TestDownstreamServer target
    ) throws Exception {
        client.method(method).uri(path)
                .exchange()
                .expectStatus().is2xxSuccessful();

        assertThat(target.takeRequest().uri()).isEqualTo(path);
    }

    @ParameterizedTest
    @MethodSource("protectedRoutes")
    void routesProtectedContractPaths(
            HttpMethod method,
            String externalPath,
            String downstreamPath
    ) throws Exception {
        client.method(method).uri(externalPath)
                .headers(headers -> headers.setBearerAuth(tokens.validToken(USER_ID, SESSION_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk();

        assertThat(CONGRATS.takeRequest().uri()).isEqualTo(downstreamPath);
    }

    @Test
    void forwardsTrustedHeadersAndPreservesApplicationHeadersAndBody() throws Exception {
        client.post().uri("/api/v1/input/prompt?mode=fast")
                .headers(headers -> {
                    headers.setBearerAuth(tokens.validToken(USER_ID, SESSION_ID));
                    headers.set("X-User-Id", "spoofed-user");
                    headers.set("X-Session-Id", "spoofed-session");
                    headers.set("User-Agent", "ios-test-client");
                    headers.set("Idempotency-Key", "7c2f4e91-3b8a-4d15-9f60-1a5e8c7d2b03");
                    headers.set("X-Request-Id", "request-123");
                })
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"prompt\":\"hello\"}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Request-Id", "request-123");

        TestDownstreamServer.RecordedRequest request = CONGRATS.takeRequest();
        assertThat(request.uri()).isEqualTo("/api/v1/input/prompt?mode=fast");
        assertThat(request.firstHeader("X-User-Id")).isEqualTo(USER_ID.toString());
        assertThat(request.firstHeader("X-Session-Id")).isEqualTo(SESSION_ID.toString());
        assertThat(request.firstHeader("Authorization")).isNull();
        assertThat(request.firstHeader("User-Agent")).isEqualTo("ios-test-client");
        assertThat(request.firstHeader("Idempotency-Key"))
                .isEqualTo("7c2f4e91-3b8a-4d15-9f60-1a5e8c7d2b03");
        assertThat(request.firstHeader("X-Request-Id")).isEqualTo("request-123");
        assertThat(request.firstHeader("Content-Type")).startsWith("application/json");
        assertThat(request.bodyAsString()).isEqualTo("{\"prompt\":\"hello\"}");
    }

    @Test
    void preservesRefreshTokenForAuthService() throws Exception {
        client.post().uri("/api/v1/auth/refresh")
                .header("X-Refresh-Token", "refresh-token")
                .exchange()
                .expectStatus().isOk();

        TestDownstreamServer.RecordedRequest request = AUTH.takeRequest();
        assertThat(request.uri()).isEqualTo("/api/v1/auth/refresh");
        assertThat(request.firstHeader("X-Refresh-Token")).isEqualTo("refresh-token");
    }

    @Test
    void preservesNoContentForAuthLogout() throws Exception {
        client.post().uri("/api/v1/auth/logout")
                .header("X-Refresh-Token", "refresh-token")
                .exchange()
                .expectStatus().isNoContent();

        assertThat(AUTH.takeRequest().uri()).isEqualTo("/api/v1/auth/logout");
    }

    @ParameterizedTest
    @MethodSource("notFoundRoutes")
    void returnsNotFoundWithoutForwarding(HttpMethod method, String path) {
        client.method(method).uri(path)
                .header("X-Request-Id", "request-404")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().valueEquals("X-Request-Id", "request-404")
                .expectBody()
                .jsonPath("$.code").isEqualTo("not_found")
                .jsonPath("$.request_id").isEqualTo("request-404");

        assertThat(AUTH.requestCount()).isZero();
        assertThat(CONGRATS.requestCount()).isZero();
    }

    @Test
    void returnsNotFoundForUnknownPathWithValidJwtWithoutForwarding() {
        client.get().uri("/api/v1/unknown")
                .headers(headers -> headers.setBearerAuth(tokens.validToken(USER_ID, SESSION_ID)))
                .exchange()
                .expectStatus().isNotFound();

        assertThat(AUTH.requestCount()).isZero();
        assertThat(CONGRATS.requestCount()).isZero();
    }

    @Test
    void returnsNotFoundForUnknownPathWithExpiredJwtWithoutForwarding() {
        client.get().uri("/api/v1/unknown")
                .headers(headers -> headers.setBearerAuth(tokens.expiredToken(USER_ID, SESSION_ID)))
                .exchange()
                .expectStatus().isNotFound();

        assertThat(AUTH.requestCount()).isZero();
        assertThat(CONGRATS.requestCount()).isZero();
    }

    private static Stream<Arguments> publicRoutes() {
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/api/v1/auth/anonymous", AUTH),
                Arguments.of(HttpMethod.POST, "/api/v1/auth/refresh", AUTH),
                Arguments.of(HttpMethod.POST, "/api/v1/auth/logout", AUTH),
                Arguments.of(HttpMethod.GET, "/api/v1/holidays", CONGRATS),
                Arguments.of(HttpMethod.GET, "/api/v1/holidays/1", CONGRATS),
                Arguments.of(HttpMethod.GET, "/api/v1/music-types", CONGRATS),
                Arguments.of(HttpMethod.GET, "/api/v1/congrats/kJ3nQx7Vb2LmR9tY", CONGRATS),
                Arguments.of(HttpMethod.GET, "/api/v1/congrats/kJ3nQx7Vb2LmR9tY/video", CONGRATS)
        );
    }

    private static Stream<Arguments> protectedRoutes() {
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/api/v1/input/prompt", "/api/v1/input/prompt"),
                Arguments.of(HttpMethod.POST, "/api/v1/input/voice", "/api/v1/input/voice"),
                Arguments.of(HttpMethod.POST, "/api/v1/input/template", "/api/v1/input/template"),
                Arguments.of(HttpMethod.POST, "/api/v1/generate/lyrics", "/api/v1/generate/lyrics"),
                Arguments.of(HttpMethod.POST, "/api/v1/generate/image", "/api/v1/generate/image"),
                Arguments.of(HttpMethod.POST, "/api/v1/generate/music", "/api/v1/generate/music"),
                Arguments.of(HttpMethod.POST, "/api/v1/congrats", "/api/v1/congrats"),
                Arguments.of(HttpMethod.GET, "/api/v1/congrats/history", "/api/v1/congrats/history"),
                Arguments.of(HttpMethod.POST, "/api/v1/sessions/push-token", "/api/v1/sessions/push-token"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/sessions/push-token", "/api/v1/sessions/push-token"),
                Arguments.of(HttpMethod.GET, "/api/v1/draft?congrats_id=7c2f4e91-3b8a-4d15-9f60-1a5e8c7d2b03",
                        "/api/v1/draft?congrats_id=7c2f4e91-3b8a-4d15-9f60-1a5e8c7d2b03")
        );
    }

    private static Stream<Arguments> notFoundRoutes() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/v1/auth/anonymous"),
                Arguments.of(HttpMethod.GET, "/api/v1/auth/unknown"),
                Arguments.of(HttpMethod.POST, "/api/v1/holidays"),
                Arguments.of(HttpMethod.GET, "/api/v1/holidays/1/extra"),
                Arguments.of(HttpMethod.GET, "/api/v1/music-types/extra"),
                Arguments.of(HttpMethod.GET, "/api/v1/unknown"),
                Arguments.of(HttpMethod.OPTIONS, "/api/v1/holidays")
        );
    }
}
