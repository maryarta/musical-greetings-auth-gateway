package ru.musicalgreetings.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import ru.musicalgreetings.gateway.support.TestDownstreamServer;
import ru.musicalgreetings.gateway.support.TestJwtFactory;
import ru.musicalgreetings.gateway.support.StructuredLogCapture;
import ru.musicalgreetings.gateway.error.GatewayNotFoundHandler;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.jwt.issuer=https://auth.test",
        "security.jwt.audience=musical-greetings-api",
        "spring.data.redis.connect-timeout=1s",
        "spring.data.redis.timeout=1s"
})
class ApiGatewayNotFoundTests {

    private static final TestDownstreamServer AUTH = new TestDownstreamServer("auth");
    private static final TestDownstreamServer CONGRATS = new TestDownstreamServer("congrats");
    private static final UUID USER_ID = UUID.fromString("7f3a2b1c-8e4d-47a0-9f60-1a5e8c7d2b03");
    private static final UUID SESSION_ID = UUID.fromString("3b68fdf2-709c-43ea-bb8a-f6da031fe634");

    private final TestJwtFactory tokens = new TestJwtFactory();

    @LocalServerPort
    private int serverPort;

    private WebTestClient client;

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("gateway.routes.auth-service-url", AUTH::baseUrl);
        registry.add("gateway.routes.congrats-service-url", CONGRATS::baseUrl);
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> 1);
    }

    @BeforeEach
    void setUp() {
        AUTH.clear();
        CONGRATS.clear();
        client = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + serverPort)
                .responseTimeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterAll
    static void stopServers() {
        AUTH.close();
        CONGRATS.close();
    }

    @ParameterizedTest
    @MethodSource("notFoundRequests")
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
    void ignoresExpiredJwtForUnknownPath() {
        client.get().uri("/api/v1/unknown")
                .headers(headers -> headers.setBearerAuth(tokens.expiredToken(USER_ID, SESSION_ID)))
                .exchange()
                .expectStatus().isNotFound();

        assertThat(AUTH.requestCount()).isZero();
        assertThat(CONGRATS.requestCount()).isZero();
    }

    @Test
    void ignoresExpiredJwtForUnsupportedMethod() {
        client.get().uri("/api/v1/auth/anonymous")
                .headers(headers -> headers.setBearerAuth(tokens.expiredToken(USER_ID, SESSION_ID)))
                .exchange()
                .expectStatus().isNotFound();

        assertThat(AUTH.requestCount()).isZero();
        assertThat(CONGRATS.requestCount()).isZero();
    }

    @Test
    void keepsSupportedProtectedEndpointUnauthorizedWithoutJwt() {
        client.get().uri("/api/v1/draft")
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(CONGRATS.requestCount()).isZero();
    }

    @Test
    void logsNotFoundAndCompletedRequest(CapturedOutput output) {
        try (StructuredLogCapture logs = StructuredLogCapture.forLogger(
                GatewayNotFoundHandler.class)) {
            client.get().uri("/api/v1/unknown?query-secret-sentinel=value")
                    .header("X-Request-Id", "request-not-found-log")
                    .exchange()
                    .expectStatus().isNotFound();

            assertThat(logs.attributes("route_not_found"))
                    .containsEntry("event", "route_not_found")
                    .containsEntry("method", "GET")
                    .containsEntry("requestId", "request-not-found-log");
        }

        assertThat(output)
                .contains("route_not_found")
                .contains("method=GET")
                .contains("path=/api/v1/unknown")
                .contains("requestId=request-not-found-log")
                .contains("http_request_completed")
                .contains("status=404")
                .contains("outcome=CLIENT_ERROR")
                .doesNotContain("query-secret-sentinel");
    }

    @Test
    void redisFailureOnSupportedProtectedEndpointDoesNotForward() {
        client.get().uri("/api/v1/draft")
                .headers(headers -> headers.setBearerAuth(tokens.validToken(USER_ID, SESSION_ID)))
                .exchange()
                .expectStatus().isEqualTo(500);

        assertThat(CONGRATS.requestCount()).isZero();
    }

    private static Stream<Arguments> notFoundRequests() {
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
