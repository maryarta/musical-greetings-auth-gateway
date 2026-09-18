package ru.musicalgreetings.gateway.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import ru.musicalgreetings.gateway.filter.RequestIdWebFilter;

@Component
public class GatewayAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(GatewayAuthenticationEntryPoint.class);

    private final GatewayErrorWriter errorWriter;

    public GatewayAuthenticationEntryPoint(GatewayErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException exception) {
        Mono<Void> response = errorWriter.write(
                exchange,
                HttpStatus.UNAUTHORIZED,
                "unauthorized",
                "Сессия недействительна"
        );
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();
        String requestId = exchange.getAttribute(RequestIdWebFilter.REQUEST_ID_ATTRIBUTE);
        String exceptionType = exception.getClass().getName();
        log.atWarn()
                .addKeyValue("event", "authentication_rejected")
                .addKeyValue("method", method)
                .addKeyValue("path", path)
                .addKeyValue("requestId", requestId)
                .addKeyValue("exceptionType", exceptionType)
                .log(
                "authentication_rejected method={} path={} requestId={} exceptionType={}",
                method,
                path,
                requestId,
                exceptionType
                );
        return response;
    }
}
