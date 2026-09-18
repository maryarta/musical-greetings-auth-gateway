package ru.musicalgreetings.gateway.error;

import java.util.UUID;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import ru.musicalgreetings.gateway.filter.RequestIdWebFilter;
import tools.jackson.databind.ObjectMapper;

@Component
public class GatewayErrorWriter {

    private final ObjectMapper objectMapper;

    public GatewayErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(
            ServerWebExchange exchange,
            HttpStatus status,
            String code,
            String message
    ) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }

        String requestId = exchange.getAttribute(RequestIdWebFilter.REQUEST_ID_ATTRIBUTE);
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
            exchange.getAttributes().put(RequestIdWebFilter.REQUEST_ID_ATTRIBUTE, requestId);
        }

        byte[] body = objectMapper.writeValueAsBytes(new ErrorResponse(code, message, requestId));
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(RequestIdWebFilter.REQUEST_ID_HEADER, requestId);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
