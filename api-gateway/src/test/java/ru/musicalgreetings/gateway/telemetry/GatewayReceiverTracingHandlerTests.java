package ru.musicalgreetings.gateway.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

class GatewayReceiverTracingHandlerTests {

    private InMemorySpanExporter exporter;
    private OpenTelemetrySdk openTelemetry;
    private GatewayReceiverTracingHandler handler;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .setSampler(new GatewayRouteSampler(Sampler.alwaysOff()))
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();

        io.opentelemetry.api.trace.Tracer otelTracer = openTelemetry.getTracer("test");
        OtelCurrentTraceContext currentTraceContext = new OtelCurrentTraceContext();
        io.micrometer.tracing.Tracer tracer = new OtelTracer(otelTracer, currentTraceContext, _ -> { });
        io.micrometer.tracing.propagation.Propagator propagator =
                new OtelPropagator(openTelemetry.getPropagators(), otelTracer);

        handler = new GatewayReceiverTracingHandler(tracer, propagator);
    }

    @AfterEach
    void tearDown() {
        openTelemetry.close();
    }

    @Test
    void samplesRealPostToInputRoute() {
        assertThat(runObservation(MockServerHttpRequest.post("/api/v1/input/voice").build())).hasSize(1);
    }

    @Test
    void doesNotForceSampleGetRequests() {
        assertThat(runObservation(MockServerHttpRequest.get("/api/v1/input/voice").build())).isEmpty();
    }

    @Test
    void doesNotForceSampleUnrelatedPostRoutes() {
        assertThat(runObservation(MockServerHttpRequest.post("/api/v1/auth/anonymous").build())).isEmpty();
    }

    private java.util.List<SpanData> runObservation(org.springframework.http.server.reactive.ServerHttpRequest request) {
        ServerRequestObservationContext context = new ServerRequestObservationContext(
                request, new MockServerHttpResponse(), Map.of());
        context.setName("http.server.requests");

        handler.onStart(context);
        handler.onStop(context);

        return exporter.getFinishedSpanItems();
    }
}
