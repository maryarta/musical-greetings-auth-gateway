package ru.musicalgreetings.gateway.filter;

import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import ru.musicalgreetings.gateway.logging.GatewayAccessLogger;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdWebFilter implements WebFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_ATTRIBUTE = RequestIdWebFilter.class.getName() + ".requestId";

    private static final int MAX_REQUEST_ID_LENGTH = 128;

    private final GatewayAccessLogger accessLogger;

    public RequestIdWebFilter(GatewayAccessLogger accessLogger) {
        this.accessLogger = accessLogger;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incomingRequestId = validRequestId(
                exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER)
        );
        String requestId = incomingRequestId == null
                ? UUID.randomUUID().toString()
                : incomingRequestId;

        ServerWebExchange requestWithId = exchange.mutate()
                .request(builder -> builder.headers(headers -> headers.set(REQUEST_ID_HEADER, requestId)))
                .build();
        requestWithId.getAttributes().put(REQUEST_ID_ATTRIBUTE, requestId);
        requestWithId.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
        accessLogger.start(requestWithId);

        return chain.filter(requestWithId)
                .doFinally(signal -> {
                    if (signal == SignalType.ON_COMPLETE || signal == SignalType.CANCEL) {
                        accessLogger.logCompleted(requestWithId, signal);
                    }
                });
    }

    private String validRequestId(String requestId) {
        if (requestId == null || requestId.isBlank() || requestId.length() > MAX_REQUEST_ID_LENGTH) {
            return null;
        }
        return requestId.chars().anyMatch(Character::isISOControl) ? null : requestId;
    }
}
