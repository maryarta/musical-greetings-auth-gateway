package ru.musicalgreetings.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class TrustedIdentityGlobalFilter implements GlobalFilter, Ordered {

    static final String USER_ID_HEADER = "X-User-Id";
    static final String SESSION_ID_HEADER = "X-Session-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(authentication -> withTrustedIdentity(exchange, authentication))
                .defaultIfEmpty(withoutClientIdentity(exchange))
                .flatMap(chain::filter);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    private ServerWebExchange withTrustedIdentity(
            ServerWebExchange exchange,
            JwtAuthenticationToken authentication
    ) {
        return mutateHeaders(
                exchange,
                authentication.getToken().getSubject(),
                authentication.getToken().getClaimAsString("sid")
        );
    }

    private ServerWebExchange withoutClientIdentity(ServerWebExchange exchange) {
        return mutateHeaders(exchange, null, null);
    }

    private ServerWebExchange mutateHeaders(
            ServerWebExchange exchange,
            String userId,
            String sessionId
    ) {
        return exchange.mutate()
                .request(builder -> builder.headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(SESSION_ID_HEADER);
                    headers.remove("Authorization");
                    if (userId != null) {
                        headers.set(USER_ID_HEADER, userId);
                        headers.set(SESSION_ID_HEADER, sessionId);
                    }
                }))
                .build();
    }
}
