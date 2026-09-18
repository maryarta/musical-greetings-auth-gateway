package ru.musicalgreetings.gateway.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import ru.musicalgreetings.gateway.config.GatewayJwtProperties;
import ru.musicalgreetings.gateway.config.GatewayRoutesProperties;

@Component
public class GatewayReadyLogger {

    private static final Logger log = LoggerFactory.getLogger(GatewayReadyLogger.class);

    private final GatewayRoutesProperties routes;
    private final GatewayJwtProperties jwt;

    public GatewayReadyLogger(GatewayRoutesProperties routes, GatewayJwtProperties jwt) {
        this.routes = routes;
        this.jwt = jwt;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logGatewayReady() {
        log.atInfo()
                .addKeyValue("event", "gateway_ready")
                .addKeyValue("authServiceUrl", routes.authServiceUrl())
                .addKeyValue("congratsServiceUrl", routes.congratsServiceUrl())
                .addKeyValue("issuer", jwt.issuer())
                .addKeyValue("audience", jwt.audience())
                .addKeyValue("rateLimitPolicy", "personal-and-global-per-endpoint")
                .log(
                "gateway_ready authServiceUrl={} congratsServiceUrl={} issuer={} audience={} rateLimitPolicy=personal-and-global-per-endpoint",
                routes.authServiceUrl(),
                routes.congratsServiceUrl(),
                jwt.issuer(),
                jwt.audience()
                );
    }
}
