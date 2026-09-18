package ru.musicalgreetings.gateway.config;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.HEAD;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import ru.musicalgreetings.gateway.support.TestJwtFactory;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "security.jwt.secret=" + SecurityConfigurationTests.ENCODED_SECRET,
        "security.jwt.issuer=https://auth.test",
        "security.jwt.audience=musical-greetings-api",
        "gateway.routes.auth-service-url=http://127.0.0.1:18082",
        "gateway.routes.congrats-service-url=http://127.0.0.1:18083"
})
@Import(SecurityConfigurationTests.TestRoutes.class)
class SecurityConfigurationTests {

    static final String ENCODED_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final UUID USER_ID = UUID.fromString("7f3a2b1c-8e4d-47a0-9f60-1a5e8c7d2b03");
    private static final UUID SESSION_ID = UUID.fromString("3b68fdf2-709c-43ea-bb8a-f6da031fe634");

    private final TestJwtFactory tokens = new TestJwtFactory();

    @LocalServerPort
    private int serverPort;

    private WebTestClient webTestClient;

    @BeforeEach
    void connectToGateway() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + serverPort)
                .build();
    }

    @Test
    void allowsPublicCatalogWithoutJwt() {
        webTestClient.get().uri("/api/v1/holidays")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void allowsPublicCatalogWithExpiredJwt() {
        webTestClient.get().uri("/api/v1/holidays")
                .headers(headers -> headers.setBearerAuth(tokens.expiredToken(USER_ID, SESSION_ID)))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void rejectsProtectedRequestWithoutJwtUsingOpenApiError() {
        webTestClient.get().uri("/api/v1/draft")
                .header("X-Request-Id", "request-123")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("X-Request-Id", "request-123")
                .expectBody()
                .jsonPath("$.code").isEqualTo("unauthorized")
                .jsonPath("$.message").isEqualTo("Сессия недействительна")
                .jsonPath("$.request_id").isEqualTo("request-123");
    }

    @Test
    void protectsCongratsHistoryDespitePublicParameterizedRoute() {
        webTestClient.get().uri("/api/v1/congrats/history")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.get().uri("/api/v1/congrats/history")
                .headers(headers -> headers.setBearerAuth(tokens.validToken(USER_ID, SESSION_ID)))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void returnsNotFoundForUnknownApiPathWithoutJwt() {
        webTestClient.get().uri("/api/v1/unknown")
                .header("X-Request-Id", "request-404")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().valueEquals("X-Request-Id", "request-404")
                .expectBody()
                .jsonPath("$.code").isEqualTo("not_found")
                .jsonPath("$.request_id").isEqualTo("request-404");
    }

    @Test
    void returnsNotFoundForUnknownApiPathWithExpiredJwt() {
        webTestClient.get().uri("/api/v1/unknown")
                .headers(headers -> headers.setBearerAuth(tokens.expiredToken(USER_ID, SESSION_ID)))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void returnsNotFoundForUnsupportedPublicMethodWithoutJwt() {
        webTestClient.get().uri("/api/v1/auth/anonymous")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void returnsNotFoundForUnsupportedProtectedMethodWithoutJwt() {
        webTestClient.get().uri("/api/v1/input/prompt")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void allowsHeadOnPublicGetEndpointWithoutJwt() {
        webTestClient.head().uri("/api/v1/holidays")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void doesNotAuthenticateOptionsRequests() {
        webTestClient.options().uri("/api/v1/holidays")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void acceptsValidJwt() {
        getDraft(tokens.validToken(USER_ID, SESSION_ID)).expectStatus().isOk();
    }

    @Test
    void rejectsExpiredJwt() {
        getDraft(tokens.expiredToken(USER_ID, SESSION_ID)).expectStatus().isUnauthorized();
    }

    @Test
    void rejectsJwtSignedWithAnotherSecret() {
        String token = new TestJwtFactory("abcdef0123456789abcdef0123456789")
                .validToken(USER_ID, SESSION_ID);
        getDraft(token).expectStatus().isUnauthorized();
    }

    @Test
    void rejectsWrongIssuer() {
        getDraft(tokens.tokenWithIssuer(USER_ID, SESSION_ID, "https://other-auth.test"))
                .expectStatus().isUnauthorized();
    }

    @Test
    void rejectsWrongAudience() {
        getDraft(tokens.tokenWithAudience(USER_ID, SESSION_ID, "another-api"))
                .expectStatus().isUnauthorized();
    }

    @Test
    void rejectsNonUuidSubject() {
        getDraft(tokens.tokenWithSubjectAndSession("not-a-uuid", SESSION_ID.toString()))
                .expectStatus().isUnauthorized();
    }

    @Test
    void rejectsNonUuidSessionId() {
        getDraft(tokens.tokenWithSubjectAndSession(USER_ID.toString(), "not-a-uuid"))
                .expectStatus().isUnauthorized();
    }

    private WebTestClient.ResponseSpec getDraft(String token) {
        return webTestClient.get().uri("/api/v1/draft")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestRoutes {

        @Bean
        RouterFunction<ServerResponse> securityTestRoutes() {
            return route(GET("/api/v1/holidays"), request -> ok().build())
                    .andRoute(HEAD("/api/v1/holidays"), request -> ok().build())
                    .andRoute(GET("/api/v1/congrats/history"), request -> ok().build())
                    .andRoute(GET("/api/v1/draft"), request -> ok().build());
        }
    }
}
