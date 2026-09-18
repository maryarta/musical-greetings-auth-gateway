package ru.musicalgreetings.auth.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import ru.musicalgreetings.auth.config.JwtProperties;
import ru.musicalgreetings.auth.config.SessionProperties;

@Component
public class AuthServiceReadyLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthServiceReadyLogger.class);

    private final JwtProperties jwtProperties;
    private final SessionProperties sessionProperties;

    public AuthServiceReadyLogger(
            JwtProperties jwtProperties,
            SessionProperties sessionProperties
    ) {
        this.jwtProperties = jwtProperties;
        this.sessionProperties = sessionProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        LOGGER.info(
                "auth_service_ready issuer={} audience={} accessTokenTtl={} sessionTtl={}",
                jwtProperties.issuer(),
                jwtProperties.audience(),
                jwtProperties.accessTokenTtl(),
                sessionProperties.ttl()
        );
    }
}
