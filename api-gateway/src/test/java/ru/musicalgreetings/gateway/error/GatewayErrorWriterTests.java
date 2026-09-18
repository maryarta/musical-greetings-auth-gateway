package ru.musicalgreetings.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import reactor.test.StepVerifier;
import ru.musicalgreetings.gateway.filter.RequestIdWebFilter;
import tools.jackson.databind.ObjectMapper;

class GatewayErrorWriterTests {

    @Test
    void writesOpenApiErrorWithMatchingRequestId() {
        GatewayErrorWriter writer = new GatewayErrorWriter(new ObjectMapper());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/draft")
        );
        exchange.getAttributes().put(RequestIdWebFilter.REQUEST_ID_ATTRIBUTE, "request-123");

        StepVerifier.create(writer.write(
                exchange,
                HttpStatus.UNAUTHORIZED,
                "unauthorized",
                "Сессия недействительна"
        )).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(exchange.getResponse().getHeaders()
                .getFirst(RequestIdWebFilter.REQUEST_ID_HEADER))
                .isEqualTo("request-123");
        assertThat(exchange.getResponse().getBodyAsString().block())
                .isEqualTo("{\"code\":\"unauthorized\",\"message\":\"Сессия недействительна\",\"request_id\":\"request-123\"}");
    }
}
