package ru.musicalgreetings.gateway.ratelimit;

import java.net.InetSocketAddress;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class ClientIpKeyResolver implements KeyResolver {

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null) {
            return Mono.empty();
        }
        if (remoteAddress.getAddress() != null) {
            return Mono.just(remoteAddress.getAddress().getHostAddress());
        }
        return Mono.just(remoteAddress.getHostString());
    }
}
