package ru.musicalgreetings.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.musicalgreetings.gateway.logging.GatewayAccessLogger;
import ru.musicalgreetings.gateway.support.StructuredLogCapture;

@ExtendWith(OutputCaptureExtension.class)
class RequestIdWebFilterTests {

    private final RequestIdWebFilter filter = new RequestIdWebFilter(new GatewayAccessLogger());

    @Test
    void createsOneRequestIdAndLogsCompletedRequest(CapturedOutput output) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/holidays?query-secret-sentinel=value")
        );
        route(exchange, "holidays");
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        try (StructuredLogCapture logs = StructuredLogCapture.forLogger(GatewayAccessLogger.class)) {
            StepVerifier.create(filter.filter(exchange, current -> {
                downstream.set(current);
                current.getResponse().setStatusCode(HttpStatus.OK);
                return current.getResponse().setComplete();
            })).verifyComplete();

            assertThat(logs.attributes("http_request_completed"))
                    .containsEntry("event", "http_request_completed")
                    .containsEntry("route", "holidays")
                    .containsEntry("status", "200")
                    .containsEntry("outcome", "SUCCESS");
        }

        String requestId = downstream.get().getRequest().getHeaders()
                .getFirst(RequestIdWebFilter.REQUEST_ID_HEADER);
        String requestIdAttribute = downstream.get()
                .getAttribute(RequestIdWebFilter.REQUEST_ID_ATTRIBUTE);
        assertThat(requestId).isNotBlank();
        assertThat(requestIdAttribute).isEqualTo(requestId);
        assertThat(exchange.getResponse().getHeaders()
                .getFirst(RequestIdWebFilter.REQUEST_ID_HEADER))
                .isEqualTo(requestId);
        assertThat(output)
                .contains("INFO")
                .contains("http_request_completed")
                .contains("route=holidays")
                .contains("method=GET")
                .contains("path=/api/v1/holidays")
                .contains("status=200")
                .contains("durationMs=")
                .contains("requestId=" + requestId)
                .contains("outcome=SUCCESS")
                .doesNotContain("query-secret-sentinel");
    }

    @Test
    void logsGatewayGeneratedClientError(CapturedOutput output) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/generate/image")
                        .header(RequestIdWebFilter.REQUEST_ID_HEADER, "request-401")
        );
        route(exchange, "generate-image");

        StepVerifier.create(filter.filter(exchange, current -> {
            current.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return current.getResponse().setComplete();
        })).verifyComplete();

        assertThat(output)
                .contains("http_request_completed")
                .contains("route=generate-image")
                .contains("status=401")
                .contains("requestId=request-401")
                .contains("outcome=CLIENT_ERROR");
    }

    @Test
    void logsRateLimitedRequest(CapturedOutput output) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/generate/image")
                        .header(RequestIdWebFilter.REQUEST_ID_HEADER, "request-429")
        );
        route(exchange, "generate-image");

        StepVerifier.create(filter.filter(exchange, current -> {
            current.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return current.getResponse().setComplete();
        })).verifyComplete();

        assertThat(output)
                .contains("http_request_completed")
                .contains("route=generate-image")
                .contains("status=429")
                .contains("requestId=request-429")
                .contains("outcome=CLIENT_ERROR");
    }

    @Test
    void logsCancelledRequest(CapturedOutput output) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/holidays")
                        .header(RequestIdWebFilter.REQUEST_ID_HEADER, "request-cancelled")
        );
        route(exchange, "holidays");

        StepVerifier.create(filter.filter(exchange, current -> Mono.never()))
                .thenCancel()
                .verify();

        assertThat(output)
                .contains("http_request_completed")
                .contains("route=holidays")
                .contains("status=uncommitted")
                .contains("requestId=request-cancelled")
                .contains("outcome=CANCELLED");
    }

    @Test
    void preservesValidIncomingRequestId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/holidays")
                        .header(RequestIdWebFilter.REQUEST_ID_HEADER, "request-123")
        );
        AtomicReference<String> downstreamRequestId = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, current -> {
            downstreamRequestId.set(current.getRequest().getHeaders()
                    .getFirst(RequestIdWebFilter.REQUEST_ID_HEADER));
            return Mono.empty();
        })).verifyComplete();

        assertThat(downstreamRequestId.get()).isEqualTo("request-123");
        assertThat(exchange.getResponse().getHeaders()
                .getFirst(RequestIdWebFilter.REQUEST_ID_HEADER))
                .isEqualTo("request-123");
    }

    @Test
    void replacesRequestIdLongerThan128Characters() {
        String invalidRequestId = "x".repeat(129);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/holidays")
                        .header(RequestIdWebFilter.REQUEST_ID_HEADER, invalidRequestId)
        );
        AtomicReference<String> downstreamRequestId = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, current -> {
            downstreamRequestId.set(current.getRequest().getHeaders()
                    .getFirst(RequestIdWebFilter.REQUEST_ID_HEADER));
            return Mono.empty();
        })).verifyComplete();

        assertThat(downstreamRequestId.get())
                .isNotBlank()
                .isNotEqualTo(invalidRequestId)
                .hasSize(36);
    }

    private void route(MockServerWebExchange exchange, String routeId) {
        exchange.getAttributes().put(
                ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
                Route.async()
                        .id(routeId)
                        .uri("http://congrats-service:8081")
                        .predicate(ignored -> true)
                        .build()
        );
    }
}
