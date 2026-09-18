package ru.musicalgreetings.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import ru.musicalgreetings.gateway.support.TestDownstreamServer;
import ru.musicalgreetings.gateway.support.TestJwtFactory;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.jwt.issuer=https://auth.test",
        "security.jwt.audience=musical-greetings-api",
        "spring.data.redis.connect-timeout=1s",
        "spring.data.redis.timeout=1s"
})
class ApiGatewayRedisFailureTests {

    private static final TestDownstreamServer AUTH = new TestDownstreamServer("auth");
    private static final TestDownstreamServer CONGRATS = new TestDownstreamServer("congrats");

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
                .responseTimeout(java.time.Duration.ofSeconds(5))
                .build();
    }

    @AfterAll
    static void stopServers() {
        AUTH.close();
        CONGRATS.close();
    }

    @Test
    void redisFailureReturnsContractInternalErrorWithoutCallingCongratsService() {
        String token = tokens.validToken(UUID.randomUUID(), UUID.randomUUID());

        client.post().uri("/api/v1/generate/lyrics")
                .headers(headers -> {
                    headers.setBearerAuth(token);
                    headers.set("X-Request-Id", "redis-failure-request");
                })
                .bodyValue("{}")
                .exchange()
                .expectStatus().isEqualTo(500)
                .expectHeader().valueEquals("X-Request-Id", "redis-failure-request")
                .expectBody()
                .jsonPath("$.code").isEqualTo("internal_error")
                .jsonPath("$.message").isEqualTo("Что-то пошло не так, попробуйте ещё раз")
                .jsonPath("$.request_id").isEqualTo("redis-failure-request");

        assertThat(CONGRATS.requestCount()).isZero();
    }
}
