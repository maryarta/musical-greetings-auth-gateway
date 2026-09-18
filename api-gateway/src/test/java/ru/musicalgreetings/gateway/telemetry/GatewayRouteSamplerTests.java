package ru.musicalgreetings.gateway.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingDecision;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.SdkTracerProviderBuilderCustomizer;

@SpringBootTest(properties = {
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "gateway.routes.auth-service-url=http://auth-service.test:8082",
        "gateway.routes.congrats-service-url=http://congrats-service.test:8083",
        "management.opentelemetry.enabled=true",
        "management.tracing.export.otlp.enabled=false",
        "management.tracing.sampling.probability=0.0"
})
class GatewayRouteSamplerTests {

    private static final Context UNSAMPLED_PARENT = Context.root().with(Span.wrap(SpanContext.create(
            "00000000000000000000000000000001",
            "0000000000000001",
            TraceFlags.getDefault(),
            TraceState.getDefault())));

    private final Sampler sampler = new GatewayRouteSampler(Sampler.alwaysOff());

    @Autowired
    private SdkTracerProviderBuilderCustomizer customizer;

    @Test
    void samplesInputPost() {
        assertThat(decision("POST", "/api/v1/input/prompt")).isEqualTo(SamplingDecision.RECORD_AND_SAMPLE);
    }

    @Test
    void samplesGeneratePost() {
        assertThat(decision("POST", "/api/v1/generate/music")).isEqualTo(SamplingDecision.RECORD_AND_SAMPLE);
    }

    @Test
    void samplesMatchingPostWithUnsampledIncomingParent() {
        assertThat(decision(UNSAMPLED_PARENT, "POST", "/api/v1/input/voice"))
                .isEqualTo(SamplingDecision.RECORD_AND_SAMPLE);
    }

    @Test
    void delegatesNonMatchingRequestsToNormalSampler() {
        assertThat(decision("GET", "/api/v1/input/prompt")).isEqualTo(SamplingDecision.DROP);
        assertThat(decision("POST", "/api/v1/auth/anonymous")).isEqualTo(SamplingDecision.DROP);
        assertThat(decision("POST", "/api/v1/congrats")).isEqualTo(SamplingDecision.DROP);
        assertThat(decision("POST", "/api/v1/sessions/push-token")).isEqualTo(SamplingDecision.DROP);
        assertThat(decision("GET", "/api/v1/holidays")).isEqualTo(SamplingDecision.DROP);
    }

    @Test
    void appliesCustomSamplerThroughSpringTracingConfiguration() {
        SdkTracerProviderBuilder builder = mock(SdkTracerProviderBuilder.class);

        customizer.customize(builder);

        verify(builder).setSampler(argThat(sampler -> sampler instanceof GatewayRouteSampler));
    }

    private SamplingDecision decision(String method, String path) {
        return decision(Context.root(), method, path);
    }

    private SamplingDecision decision(Context parent, String method, String path) {
        return sampler.shouldSample(
                parent,
                "00000000000000000000000000000002",
                "gateway.request",
                SpanKind.SERVER,
                Attributes.builder()
                        .put("http.request.method", method)
                        .put("url.path", path)
                        .build(),
                List.<LinkData>of())
                .getDecision();
    }
}
