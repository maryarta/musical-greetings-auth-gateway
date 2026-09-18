package ru.musicalgreetings.gateway.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "security.jwt")
public record GatewayJwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull Duration clockSkew
) {
    public GatewayJwtProperties {
        if (clockSkew != null && clockSkew.isNegative()) {
            throw new IllegalArgumentException("JWT clock skew must not be negative");
        }
    }
}
