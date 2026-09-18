package ru.musicalgreetings.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.session")
public record SessionProperties(Duration ttl) {
}
