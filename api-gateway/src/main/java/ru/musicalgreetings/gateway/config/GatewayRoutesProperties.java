package ru.musicalgreetings.gateway.config;

import java.net.URI;
import java.util.Set;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "gateway.routes")
public record GatewayRoutesProperties(
        @NotNull URI authServiceUrl,
        @NotNull URI congratsServiceUrl
) {
    private static final Set<String> SUPPORTED_SCHEMES = Set.of("http", "https");

    public GatewayRoutesProperties {
        validateServiceUrl(authServiceUrl, "auth-service-url");
        validateServiceUrl(congratsServiceUrl, "congrats-service-url");
    }

    private static void validateServiceUrl(URI value, String propertyName) {
        if (value != null && (!value.isAbsolute() || !SUPPORTED_SCHEMES.contains(value.getScheme()))) {
            throw new IllegalArgumentException(propertyName + " must be an absolute HTTP(S) URL");
        }
    }
}
