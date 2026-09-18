package ru.musicalgreetings.gateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class GlobalKeyResolver implements KeyResolver {

    static final String GLOBAL_KEY = "all-clients";

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        return Mono.just(GLOBAL_KEY);
    }
}
