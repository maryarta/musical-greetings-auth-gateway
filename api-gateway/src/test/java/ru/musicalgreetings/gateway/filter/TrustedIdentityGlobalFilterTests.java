package ru.musicalgreetings.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TrustedIdentityGlobalFilterTests {

    private static final UUID USER_ID = UUID.fromString("7f3a2b1c-8e4d-47a0-9f60-1a5e8c7d2b03");
    private static final UUID SESSION_ID = UUID.fromString("3b68fdf2-709c-43ea-bb8a-f6da031fe634");

    private final TrustedIdentityGlobalFilter filter = new TrustedIdentityGlobalFilter();

    @Test
    void replacesSpoofedIdentityAndRemovesAuthorization() {
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt());
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/input/prompt")
                        .header("Authorization", "Bearer token")
                        .header("X-User-Id", "spoofed-user")
                        .header("X-Session-Id", "spoofed-session")
        ).mutate().principal(Mono.just(authentication)).build();
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, current -> {
            downstream.set(current);
            return Mono.empty();
        })).verifyComplete();

        assertThat(downstream.get().getRequest().getHeaders().getFirst("X-User-Id"))
                .isEqualTo(USER_ID.toString());
        assertThat(downstream.get().getRequest().getHeaders().getFirst("X-Session-Id"))
                .isEqualTo(SESSION_ID.toString());
        assertThat(downstream.get().getRequest().getHeaders().containsHeader("Authorization"))
                .isFalse();
    }

    @Test
    void removesSpoofedIdentityFromPublicRequest() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/holidays")
                        .header("X-User-Id", "spoofed-user")
                        .header("X-Session-Id", "spoofed-session")
        );
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, current -> {
            downstream.set(current);
            return Mono.empty();
        })).verifyComplete();

        assertThat(downstream.get().getRequest().getHeaders().containsHeader("X-User-Id"))
                .isFalse();
        assertThat(downstream.get().getRequest().getHeaders().containsHeader("X-Session-Id"))
                .isFalse();
    }

    private Jwt jwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(USER_ID.toString())
                .claim("sid", SESSION_ID.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }
}
