package ru.musicalgreetings.gateway.error;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;

import reactor.core.publisher.Mono;
import ru.musicalgreetings.gateway.filter.RequestIdWebFilter;
import ru.musicalgreetings.gateway.logging.GatewayAccessLogger;
import ru.musicalgreetings.gateway.ratelimit.RateLimiterUnavailableException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayWebExceptionHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayWebExceptionHandler.class);

    private final GatewayErrorWriter errorWriter;
    private final GatewayAccessLogger accessLogger;

    public GatewayWebExceptionHandler(
            GatewayErrorWriter errorWriter,
            GatewayAccessLogger accessLogger
    ) {
        this.errorWriter = errorWriter;
        this.accessLogger = accessLogger;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        String route = routeId(exchange);
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();
        String requestId = requestId(exchange);
        String exceptionType = error.getClass().getName();
        String causeType = causeType(error);
        if (error instanceof RateLimiterUnavailableException) {
            Mono<Void> response = errorWriter.write(
                    exchange,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "internal_error",
                    "Что-то пошло не так, попробуйте ещё раз"
            );
            log.atError()
                    .addKeyValue("event", "rate_limiter_unavailable")
                    .addKeyValue("route", route)
                    .addKeyValue("method", method)
                    .addKeyValue("path", path)
                    .addKeyValue("requestId", requestId)
                    .addKeyValue("exceptionType", exceptionType)
                    .addKeyValue("causeType", causeType)
                    .log(
                    "rate_limiter_unavailable route={} method={} path={} requestId={} exceptionType={} causeType={}",
                    route, method, path, requestId, exceptionType, causeType
                    );
            return accessLogger.logWhenTerminated(exchange, response);
        }
        if (error instanceof HttpClientErrorException clientError
                && clientError.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            Mono<Void> response = errorWriter.write(
                    exchange,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "rate_limited",
                    "Слишком много запросов, попробуйте позже"
            );
            String retryAfter = retryAfter(exchange);
            log.atWarn()
                    .addKeyValue("event", "rate_limit_exceeded")
                    .addKeyValue("route", route)
                    .addKeyValue("method", method)
                    .addKeyValue("path", path)
                    .addKeyValue("requestId", requestId)
                    .addKeyValue("retryAfter", retryAfter)
                    .log(
                    "rate_limit_exceeded route={} method={} path={} requestId={} retryAfter={}",
                    route, method, path, requestId, retryAfter
                    );
            return accessLogger.logWhenTerminated(exchange, response);
        }
        Mono<Void> response = errorWriter.write(
                exchange,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal_error",
                "Что-то пошло не так, попробуйте ещё раз"
        );
        String downstreamFailureType = downstreamFailureType(exchange, error);
        if (downstreamFailureType == null) {
            log.atError()
                    .addKeyValue("event", "gateway_request_failed")
                    .addKeyValue("route", route)
                    .addKeyValue("method", method)
                    .addKeyValue("path", path)
                    .addKeyValue("requestId", requestId)
                    .addKeyValue("exceptionType", exceptionType)
                    .addKeyValue("causeType", causeType)
                    .log(
                    "gateway_request_failed route={} method={} path={} requestId={} exceptionType={} causeType={}",
                    route, method, path, requestId, exceptionType, causeType
                    );
        } else {
            log.atError()
                    .addKeyValue("event", "downstream_request_failed")
                    .addKeyValue("route", route)
                    .addKeyValue("method", method)
                    .addKeyValue("path", path)
                    .addKeyValue("requestId", requestId)
                    .addKeyValue("failureType", downstreamFailureType)
                    .addKeyValue("exceptionType", exceptionType)
                    .addKeyValue("causeType", causeType)
                    .log(
                    "downstream_request_failed route={} method={} path={} requestId={} failureType={} exceptionType={} causeType={}",
                    route, method, path, requestId, downstreamFailureType, exceptionType, causeType
                    );
        }
        return accessLogger.logWhenTerminated(exchange, response);
    }

    private String routeId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route == null ? "unmatched" : route.getId();
    }

    private String requestId(ServerWebExchange exchange) {
        return exchange.getAttribute(RequestIdWebFilter.REQUEST_ID_ATTRIBUTE);
    }

    private String retryAfter(ServerWebExchange exchange) {
        String retryAfter = exchange.getResponse().getHeaders().getFirst("Retry-After");
        return retryAfter == null ? "unknown" : retryAfter;
    }

    private String causeType(Throwable error) {
        Throwable cause = error.getCause();
        return cause == null ? "none" : cause.getClass().getName();
    }

    private String downstreamFailureType(ServerWebExchange exchange, Throwable error) {
        if (exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR) == null) {
            return null;
        }
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof UnknownHostException) {
                return "DNS_FAILURE";
            }
            if (current instanceof NoRouteToHostException) {
                return "NO_ROUTE_TO_HOST";
            }
            if (current instanceof ConnectException) {
                return "CONNECTION_REFUSED";
            }
            if (current instanceof SocketTimeoutException || current instanceof TimeoutException) {
                return "TIMEOUT";
            }
            String simpleName = current.getClass().getSimpleName();
            if ("ConnectTimeoutException".equals(simpleName)) {
                return "CONNECT_TIMEOUT";
            }
            if ("ReadTimeoutException".equals(simpleName)) {
                return "RESPONSE_TIMEOUT";
            }
            if ("PrematureCloseException".equals(simpleName)) {
                return "CONNECTION_CLOSED";
            }
            if (current instanceof SocketException) {
                return "CONNECTION_FAILURE";
            }
        }
        return null;
    }
}
