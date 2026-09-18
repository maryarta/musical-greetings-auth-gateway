package ru.musicalgreetings.gateway.support;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

public final class TestJwtFactory {

    public static final String RAW_SECRET = "0123456789abcdef0123456789abcdef";

    private final JwtEncoder encoder;

    public TestJwtFactory() {
        this(RAW_SECRET);
    }

    public TestJwtFactory(String rawSecret) {
        SecretKey key = new SecretKeySpec(rawSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = NimbusJwtEncoder.withSecretKey(key)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    public String validToken(UUID userId, UUID sessionId) {
        Instant now = Instant.now();
        return token(userId.toString(), sessionId.toString(), "https://auth.test",
                "musical-greetings-api", now.minusSeconds(1), now.plusSeconds(300));
    }

    public String expiredToken(UUID userId, UUID sessionId) {
        Instant now = Instant.now();
        return token(userId.toString(), sessionId.toString(), "https://auth.test",
                "musical-greetings-api", now.minusSeconds(600), now.minusSeconds(120));
    }

    public String tokenWithIssuer(UUID userId, UUID sessionId, String issuer) {
        Instant now = Instant.now();
        return token(userId.toString(), sessionId.toString(), issuer,
                "musical-greetings-api", now.minusSeconds(1), now.plusSeconds(300));
    }

    public String tokenWithAudience(UUID userId, UUID sessionId, String audience) {
        Instant now = Instant.now();
        return token(userId.toString(), sessionId.toString(), "https://auth.test",
                audience, now.minusSeconds(1), now.plusSeconds(300));
    }

    public String tokenWithSubjectAndSession(String subject, String sessionId) {
        Instant now = Instant.now();
        return token(subject, sessionId, "https://auth.test",
                "musical-greetings-api", now.minusSeconds(1), now.plusSeconds(300));
    }

    private String token(
            String subject,
            String sessionId,
            String issuer,
            String audience,
            Instant issuedAt,
            Instant expiresAt
    ) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(subject)
                .claim("sid", sessionId)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
