package ru.musicalgreetings.gateway.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import ru.musicalgreetings.gateway.config.GatewayJwtProperties;
import ru.musicalgreetings.gateway.config.GatewayRoutesProperties;
import ru.musicalgreetings.gateway.support.StructuredLogCapture;

@ExtendWith(OutputCaptureExtension.class)
class GatewayReadyLoggerTests {

    @Test
    void logsSafeConfigurationWhenGatewayIsReady(CapturedOutput output) {
        GatewayRoutesProperties routes = new GatewayRoutesProperties(
                URI.create("http://auth-service:8082"),
                URI.create("http://congrats-service:8081")
        );
        GatewayJwtProperties jwt = new GatewayJwtProperties(
                "http://auth-service:8082",
                "musical-greetings-api",
                Duration.ofSeconds(30)
        );

        try (StructuredLogCapture logs = StructuredLogCapture.forLogger(GatewayReadyLogger.class)) {
            new GatewayReadyLogger(routes, jwt).logGatewayReady();

            assertThat(logs.attributes("gateway_ready"))
                    .containsEntry("event", "gateway_ready")
                    .containsEntry("audience", "musical-greetings-api")
                    .containsEntry("rateLimitPolicy", "personal-and-global-per-endpoint");
        }

        assertThat(output)
                .contains("gateway_ready")
                .contains("authServiceUrl=http://auth-service:8082")
                .contains("congratsServiceUrl=http://congrats-service:8081")
                .contains("issuer=http://auth-service:8082")
                .contains("audience=musical-greetings-api")
                .contains("rateLimitPolicy=personal-and-global-per-endpoint")
                .doesNotContain("jwt-secret-sentinel")
                .doesNotContain("redis-password-sentinel");
    }
}
