package ru.musicalgreetings.gateway.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import ru.musicalgreetings.gateway.filter.RequestIdWebFilter;

@Component
public class GatewayNotFoundHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayNotFoundHandler.class);

    private final GatewayErrorWriter errorWriter;

    public GatewayNotFoundHandler(GatewayErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    public Mono<Void> handle(ServerWebExchange exchange) {
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();
        String requestId = exchange.getAttribute(RequestIdWebFilter.REQUEST_ID_ATTRIBUTE);
        log.atInfo()
                .addKeyValue("event", "route_not_found")
                .addKeyValue("method", method)
                .addKeyValue("path", path)
                .addKeyValue("requestId", requestId)
                .log(
                "route_not_found method={} path={} requestId={}",
                method,
                path,
                requestId
                );
        return errorWriter.write(
                exchange,
                HttpStatus.NOT_FOUND,
                "not_found",
                "Маршрут не найден"
        );
    }
}
