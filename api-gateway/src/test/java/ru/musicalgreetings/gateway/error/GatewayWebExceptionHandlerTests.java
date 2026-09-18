package ru.musicalgreetings.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.client.HttpClientErrorException;

import reactor.test.StepVerifier;
import ru.musicalgreetings.gateway.filter.RequestIdWebFilter;
import ru.musicalgreetings.gateway.logging.GatewayAccessLogger;
import ru.musicalgreetings.gateway.ratelimit.RateLimiterUnavailableException;
import ru.musicalgreetings.gateway.support.StructuredLogCapture;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(OutputCaptureExtension.class)
class GatewayWebExceptionHandlerTests {

    @Test
    void logsRateLimitContextAndPreservesTooManyRequestsContract(CapturedOutput output) {
        GatewayWebExceptionHandler handler = handler();
        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest.post("/api/v1/input/prompt?query-secret-sentinel=value"),
                "request-429",
                "input"
        );
        exchange.getResponse().getHeaders().set("Retry-After", "20");

        try (StructuredLogCapture logs = StructuredLogCapture.forLogger(
                GatewayWebExceptionHandler.class)) {
            StepVerifier.create(handler.handle(
                    exchange,
                    HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", null, null, null)
            )).verifyComplete();

            assertThat(logs.attributes("rate_limit_exceeded"))
                    .containsEntry("event", "rate_limit_exceeded")
                    .containsEntry("route", "input")
                    .containsEntry("retryAfter", "20");
        }

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .isEqualTo("{\"code\":\"rate_limited\",\"message\":\"Слишком много запросов, попробуйте позже\",\"request_id\":\"request-429\"}");
        assertThat(output)
                .contains("WARN")
                .contains("rate_limit_exceeded")
                .contains("route=input")
                .contains("method=POST")
                .contains("path=/api/v1/input/prompt")
                .contains("requestId=request-429")
                .contains("retryAfter=20")
                .contains("http_request_completed")
                .contains("status=429")
                .contains("outcome=CLIENT_ERROR")
                .doesNotContain("query-secret-sentinel");
    }

    @Test
    void logsSafeRedisFailureContextAndPreservesInternalErrorContract(CapturedOutput output) {
        GatewayWebExceptionHandler handler = handler();
        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest.get("/api/v1/generate/music?redis-key-sentinel=value"),
                "request-redis",
                "generate"
        );

        try (StructuredLogCapture logs = StructuredLogCapture.forLogger(
                GatewayWebExceptionHandler.class)) {
            StepVerifier.create(handler.handle(
                    exchange,
                    new RateLimiterUnavailableException(
                            "rate-limiter-message-sentinel",
                            new IllegalStateException("redis-cause-message-sentinel")
                    )
            )).verifyComplete();

            assertThat(logs.attributes("rate_limiter_unavailable"))
                    .containsEntry("event", "rate_limiter_unavailable")
                    .containsEntry("route", "generate")
                    .containsEntry("causeType", "java.lang.IllegalStateException");
        }

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .isEqualTo("{\"code\":\"internal_error\",\"message\":\"Что-то пошло не так, попробуйте ещё раз\",\"request_id\":\"request-redis\"}");
        assertThat(output)
                .contains("ERROR")
                .contains("rate_limiter_unavailable")
                .contains("route=generate")
                .contains("method=GET")
                .contains("path=/api/v1/generate/music")
                .contains("requestId=request-redis")
                .contains("exceptionType=ru.musicalgreetings.gateway.ratelimit.RateLimiterUnavailableException")
                .contains("causeType=java.lang.IllegalStateException")
                .contains("http_request_completed")
                .contains("status=500")
                .contains("outcome=SERVER_ERROR")
                .doesNotContain("redis-key-sentinel")
                .doesNotContain("rate-limiter-message-sentinel")
                .doesNotContain("redis-cause-message-sentinel")
                .doesNotContain("GatewayWebExceptionHandlerTests.java");
    }

    @Test
    void logsSafeDownstreamFailureContextAndPreservesInternalErrorContract(CapturedOutput output) {
        GatewayWebExceptionHandler handler = handler();
        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest.get("/api/v1/draft?query-secret-sentinel=value"),
                "request-500",
                "draft"
        );

        try (StructuredLogCapture logs = StructuredLogCapture.forLogger(
                GatewayWebExceptionHandler.class)) {
            StepVerifier.create(handler.handle(
                    exchange,
                    new IllegalStateException(
                            "gateway-message-sentinel",
                            new ConnectException("downstream-cause-message-sentinel")
                    )
            )).verifyComplete();

            assertThat(logs.attributes("downstream_request_failed"))
                    .containsEntry("event", "downstream_request_failed")
                    .containsEntry("failureType", "CONNECTION_REFUSED")
                    .containsEntry("route", "draft");
        }

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .isEqualTo("{\"code\":\"internal_error\",\"message\":\"Что-то пошло не так, попробуйте ещё раз\",\"request_id\":\"request-500\"}");
        assertThat(output)
                .contains("ERROR")
                .contains("downstream_request_failed")
                .contains("route=draft")
                .contains("method=GET")
                .contains("path=/api/v1/draft")
                .contains("requestId=request-500")
                .contains("exceptionType=java.lang.IllegalStateException")
                .contains("causeType=java.net.ConnectException")
                .contains("http_request_completed")
                .contains("status=500")
                .contains("outcome=SERVER_ERROR")
                .doesNotContain("query-secret-sentinel")
                .doesNotContain("gateway-message-sentinel")
                .doesNotContain("downstream-cause-message-sentinel")
                .doesNotContain("GatewayWebExceptionHandlerTests.java");
    }

    @Test
    void logsSafeUnexpectedGatewayFailureContext(CapturedOutput output) {
        GatewayWebExceptionHandler handler = handler();
        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest.get("/api/v1/draft"),
                "request-unexpected",
                "draft"
        );

        try (StructuredLogCapture logs = StructuredLogCapture.forLogger(
                GatewayWebExceptionHandler.class)) {
            StepVerifier.create(handler.handle(
                    exchange,
                    new IllegalStateException("gateway-message-sentinel")
            )).verifyComplete();

            assertThat(logs.attributes("gateway_request_failed"))
                    .containsEntry("event", "gateway_request_failed")
                    .containsEntry("route", "draft")
                    .containsEntry("exceptionType", "java.lang.IllegalStateException");
        }

        assertThat(output)
                .contains("gateway_request_failed")
                .contains("route=draft")
                .contains("requestId=request-unexpected")
                .contains("exceptionType=java.lang.IllegalStateException")
                .contains("causeType=none")
                .contains("http_request_completed")
                .contains("status=500")
                .contains("outcome=SERVER_ERROR")
                .doesNotContain("gateway-message-sentinel");
    }

    @Test
    void logsDownstreamTimeoutWithoutSensitiveMessage(CapturedOutput output) {
        GatewayWebExceptionHandler handler = handler();
        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest.get("/api/v1/holidays/1"),
                "request-timeout",
                "holiday"
        );

        StepVerifier.create(handler.handle(
                exchange,
                new TimeoutException("timeout-message-sentinel")
        )).verifyComplete();

        assertThat(output)
                .contains("downstream_request_failed")
                .contains("route=holiday")
                .contains("requestId=request-timeout")
                .contains("failureType=TIMEOUT")
                .contains("http_request_completed")
                .contains("status=500")
                .contains("outcome=SERVER_ERROR")
                .doesNotContain("timeout-message-sentinel");
    }

    private GatewayWebExceptionHandler handler() {
        return new GatewayWebExceptionHandler(
                new GatewayErrorWriter(new ObjectMapper()),
                new GatewayAccessLogger()
        );
    }

    private MockServerWebExchange exchange(
            MockServerHttpRequest.BaseBuilder<?> request,
            String requestId,
            String routeId
    ) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(RequestIdWebFilter.REQUEST_ID_ATTRIBUTE, requestId);
        exchange.getAttributes().put(
                ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
                Route.async()
                        .id(routeId)
                        .uri("http://congrats-service:8081")
                        .predicate(ignored -> true)
                        .build()
        );
        return exchange;
    }
}
