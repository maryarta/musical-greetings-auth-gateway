package ru.musicalgreetings.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import ru.musicalgreetings.auth.config.JwtConfiguration;
import ru.musicalgreetings.auth.config.JwtProperties;

class JwtServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-15T10:00:00Z");

    @Test
    void issuesHs256TokenWithUserAndSessionIdentity() {
        SecretKey secretKey = new SecretKeySpec(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );

        JwtProperties properties = new JwtProperties(
                "https://auth.musical-greetings.test",
                "musical-greetings-api",
                Duration.ofMinutes(15),
                "auth-test-key"
        );
        JwtEncoder encoder = new JwtConfiguration().jwtEncoder(secretKey, properties);
        JwtService jwtService = new JwtService(
                encoder,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        UUID userId = UUID.fromString("7f3a2b1c-8e4d-47a0-9f60-1a5e8c7d2b03");
        UUID sessionId = UUID.fromString("3b68fdf2-709c-43ea-bb8a-f6da031fe634");

        String token = jwtService.issueAccessToken(userId, sessionId);

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
        Jwt jwt = decoder.decode(token);
        assertThat(token.split("\\.")).hasSize(3);
        assertThat(jwt.getHeaders())
                .containsEntry("alg", "HS256")
                .containsEntry("kid", "auth-test-key");
        assertThat(jwt.getIssuer()).hasToString("https://auth.musical-greetings.test");
        assertThat(jwt.getAudience()).containsExactly("musical-greetings-api");
        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsString("sid")).isEqualTo(sessionId.toString());
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(jwt.getId()).isNotBlank();
    }
}
