package ru.musicalgreetings.gateway.logging;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import ru.musicalgreetings.gateway.filter.RequestIdWebFilter;

@Component
public class GatewayAccessLogger {

    private static final Logger log = LoggerFactory.getLogger(GatewayAccessLogger.class);
    private static final String STARTED_AT_ATTRIBUTE = GatewayAccessLogger.class.getName() + ".startedAt";
    private static final String LOGGED_ATTRIBUTE = GatewayAccessLogger.class.getName() + ".logged";

    public void start(ServerWebExchange exchange) {
        exchange.getAttributes().putIfAbsent(STARTED_AT_ATTRIBUTE, System.nanoTime());
        exchange.getAttributes().putIfAbsent(LOGGED_ATTRIBUTE, new AtomicBoolean());
    }

    public Mono<Void> logWhenTerminated(ServerWebExchange exchange, Mono<Void> response) {
        return response.doFinally(signal -> logCompleted(exchange, signal));
    }

    public void logCompleted(ServerWebExchange exchange, SignalType signal) {
        AtomicBoolean logged = exchange.getAttribute(LOGGED_ATTRIBUTE);
        if (logged == null) {
            start(exchange);
            logged = exchange.getAttribute(LOGGED_ATTRIBUTE);
        }
        if (logged == null || !logged.compareAndSet(false, true)) {
            return;
        }

        boolean cancelled = signal == SignalType.CANCEL;
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        String statusValue = status == null ? "uncommitted" : Integer.toString(status.value());
        String outcome = cancelled ? "CANCELLED" : outcome(status);
        Long startedAt = exchange.getAttribute(STARTED_AT_ATTRIBUTE);
        long durationMs = startedAt == null
                ? 0
                : TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        String routeId = routeId(exchange);
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();
        String requestId = exchange.getAttribute(RequestIdWebFilter.REQUEST_ID_ATTRIBUTE);

        log.atInfo()
                .addKeyValue("event", "http_request_completed")
                .addKeyValue("route", routeId)
                .addKeyValue("method", method)
                .addKeyValue("path", path)
                .addKeyValue("status", statusValue)
                .addKeyValue("durationMs", durationMs)
                .addKeyValue("requestId", requestId)
                .addKeyValue("outcome", outcome)
                .log(
                "http_request_completed route={} method={} path={} status={} durationMs={} requestId={} outcome={}",
                routeId,
                method,
                path,
                statusValue,
                durationMs,
                requestId,
                outcome
                );
    }

    private String routeId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route == null ? "unmatched" : route.getId();
    }

    private String outcome(HttpStatusCode status) {
        if (status == null) {
            return "UNKNOWN";
        }
        if (status.is4xxClientError()) {
            return "CLIENT_ERROR";
        }
        if (status.is5xxServerError()) {
            return "SERVER_ERROR";
        }
        return "SUCCESS";
    }
}
