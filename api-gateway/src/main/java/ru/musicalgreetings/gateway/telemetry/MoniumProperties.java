package ru.musicalgreetings.gateway.telemetry;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("monium")
public record MoniumProperties(
        boolean enabled,
        String apiKey,
        String project
) {
}
