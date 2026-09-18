package ru.musicalgreetings.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class ClientIpKeyResolverTests {

    private final ClientIpKeyResolver resolver = new ClientIpKeyResolver();

    @Test
    void resolvesRemoteClientAddress() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/holidays")
                        .remoteAddress(new InetSocketAddress("192.0.2.10", 54321))
                        .build()
        );

        assertThat(resolver.resolve(exchange).block()).isEqualTo("192.0.2.10");
    }

    @Test
    void deniesRequestWhenRemoteAddressIsUnavailable() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/holidays").build()
        );

        assertThat(resolver.resolve(exchange).blockOptional()).isEmpty();
    }
}
