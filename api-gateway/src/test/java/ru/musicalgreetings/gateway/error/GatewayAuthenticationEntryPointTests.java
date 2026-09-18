package ru.musicalgreetings.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.BadCredentialsException;

import reactor.test.StepVerifier;
import ru.musicalgreetings.gateway.filter.RequestIdWebFilter;
import ru.musicalgreetings.gateway.support.StructuredLogCapture;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(OutputCaptureExtension.class)
class GatewayAuthenticationEntryPointTests {

    @Test
    void logsSafeRequestContextAndPreservesUnauthorizedContract(CapturedOutput output) {
        GatewayAuthenticationEntryPoint entryPoint = new GatewayAuthenticationEntryPoint(
                new GatewayErrorWriter(new ObjectMapper())
        );
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/generate/lyrics?query-secret-sentinel=value")
                        .header("Authorization", "Bearer jwt-token-sentinel")
        );
        exchange.getAttributes().put(RequestIdWebFilter.REQUEST_ID_ATTRIBUTE, "request-auth");

        try (StructuredLogCapture logs = StructuredLogCapture.forLogger(
                GatewayAuthenticationEntryPoint.class)) {
            StepVerifier.create(entryPoint.commence(
                    exchange,
                    new BadCredentialsException("exception-message-sentinel")
            )).verifyComplete();

            assertThat(logs.attributes("authentication_rejected"))
                    .containsEntry("event", "authentication_rejected")
                    .containsEntry("method", "GET")
                    .containsEntry("requestId", "request-auth");
        }

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst(RequestIdWebFilter.REQUEST_ID_HEADER))
                .isEqualTo("request-auth");
        assertThat(exchange.getResponse().getBodyAsString().block())
                .isEqualTo("{\"code\":\"unauthorized\",\"message\":\"Сессия недействительна\",\"request_id\":\"request-auth\"}");
        assertThat(output)
                .contains("WARN")
                .contains("authentication_rejected")
                .contains("method=GET")
                .contains("path=/api/v1/generate/lyrics")
                .contains("requestId=request-auth")
                .contains("exceptionType=org.springframework.security.authentication.BadCredentialsException")
                .doesNotContain("query-secret-sentinel")
                .doesNotContain("exception-message-sentinel")
                .doesNotContain("jwt-token-sentinel");
    }
}
