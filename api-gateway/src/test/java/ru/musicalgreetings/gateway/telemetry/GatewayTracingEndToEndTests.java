package ru.musicalgreetings.gateway.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import ru.musicalgreetings.gateway.support.TestDownstreamServer;
import ru.musicalgreetings.gateway.support.TestJwtFactory;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.jwt.issuer=https://auth.test",
        "security.jwt.audience=musical-greetings-api",
        "spring.data.redis.connect-timeout=1s",
        "spring.data.redis.timeout=1s",
        "management.opentelemetry.enabled=true",
        "management.tracing.propagation.type=w3c",
        "management.tracing.export.otlp.enabled=false",
        "management.tracing.sampling.probability=0.0"
})
class GatewayTracingEndToEndTests {

    private static final TestDownstreamServer AUTH = new TestDownstreamServer("auth");
    private static final TestDownstreamServer CONGRATS = new TestDownstreamServer("congrats");

    private final TestJwtFactory tokens = new TestJwtFactory();

    @LocalServerPort
    private int serverPort;

    @Autowired
    private SdkTracerProvider tracerProvider;

    @Autowired
    private InMemorySpanExporter exporter;

    private WebTestClient client;

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("gateway.routes.auth-service-url", AUTH::baseUrl);
        registry.add("gateway.routes.congrats-service-url", CONGRATS::baseUrl);
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> 1);
    }

    @TestConfiguration
    static class SpanCaptureConfiguration {
        @Bean
        InMemorySpanExporter inMemorySpanExporter() {
            return InMemorySpanExporter.create();
        }
    }

    @BeforeEach
    void setUp() {
        AUTH.clear();
        CONGRATS.clear();
        exporter.reset();
        client = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + serverPort)
                .responseTimeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterAll
    static void stopServers() {
        AUTH.close();
        CONGRATS.close();
    }

    @Test
    void postToInputVoiceIsAlwaysSampledEvenWhenTheRequestItselfFails() {
        String token = tokens.validToken(UUID.randomUUID(), UUID.randomUUID());

        client.post().uri("/api/v1/input/voice")
                .headers(h -> {
                    h.setBearerAuth(token);
                    h.set("Idempotency-Key", UUID.randomUUID().toString());
                })
                .contentType(org.springframework.http.MediaType.valueOf("audio/mpeg"))
                .bodyValue(new byte[] {1, 2, 3})
                .exchange()
                .expectStatus().isEqualTo(500); // Redis unreachable -> rate limit fails closed

        assertThat(capturedSpans()).isNotEmpty();
    }

    @Test
    void postToGenerateMusicIsAlwaysSampledEvenWhenTheRequestItselfFails() {
        String token = tokens.validToken(UUID.randomUUID(), UUID.randomUUID());

        client.post().uri("/api/v1/generate/music")
                .headers(h -> {
                    h.setBearerAuth(token);
                    h.set("Idempotency-Key", UUID.randomUUID().toString());
                })
                .bodyValue("{}")
                .exchange()
                .expectStatus().isEqualTo(500);

        assertThat(capturedSpans()).isNotEmpty();
    }

    @Test
    void postToAuthAnonymousIsNotForcedAndStaysUnsampledAtZeroProbability() {
        client.post().uri("/api/v1/auth/anonymous")
                .exchange()
                .expectStatus().isEqualTo(500);

        assertThat(capturedSpans()).isEmpty();
    }

    @Test
    void getToHolidaysIsNotForcedAndStaysUnsampledAtZeroProbability() {
        client.get().uri("/api/v1/holidays")
                .exchange()
                .expectStatus().isEqualTo(500);

        assertThat(capturedSpans()).isEmpty();
    }

    private List<SpanData> capturedSpans() {
        tracerProvider.forceFlush().join(5, TimeUnit.SECONDS);
        return exporter.getFinishedSpanItems();
    }
}
