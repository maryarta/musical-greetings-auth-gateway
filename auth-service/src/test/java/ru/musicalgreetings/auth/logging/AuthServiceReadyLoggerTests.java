package ru.musicalgreetings.auth.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import ru.musicalgreetings.auth.config.JwtProperties;
import ru.musicalgreetings.auth.config.SessionProperties;

@ExtendWith(OutputCaptureExtension.class)
class AuthServiceReadyLoggerTests {

    @Test
    void logsNonSecretConfigurationWhenApplicationIsReady(CapturedOutput output) {
        AuthServiceReadyLogger readyLogger = new AuthServiceReadyLogger(
                new JwtProperties(
                        "http://auth-service:8082",
                        "musical-greetings-api",
                        Duration.ofMinutes(15),
                        "auth-key"
                ),
                new SessionProperties(Duration.ofDays(30))
        );

        readyLogger.onApplicationReady();

        assertThat(output)
                .contains("auth_service_ready")
                .contains("issuer=http://auth-service:8082")
                .contains("audience=musical-greetings-api")
                .contains("accessTokenTtl=PT15M")
                .contains("sessionTtl=PT720H")
                .doesNotContain("auth-key");
    }
}
