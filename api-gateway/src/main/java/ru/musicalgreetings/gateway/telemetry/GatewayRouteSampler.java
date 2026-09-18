package ru.musicalgreetings.gateway.telemetry;

import java.util.List;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;

/** Samples gateway input and generation requests regardless of their parent decision. */
public final class GatewayRouteSampler implements Sampler {

    private static final AttributeKey<String> HTTP_REQUEST_METHOD =
            AttributeKey.stringKey("http.request.method");
    private static final AttributeKey<String> LEGACY_HTTP_METHOD = AttributeKey.stringKey("http.method");
    private static final AttributeKey<String> URL_PATH = AttributeKey.stringKey("url.path");

    private final Sampler delegate;

    public GatewayRouteSampler(Sampler delegate) {
        this.delegate = delegate;
    }

    @Override
    public SamplingResult shouldSample(
            Context parentContext,
            String traceId,
            String name,
            SpanKind spanKind,
            Attributes attributes,
            List<LinkData> parentLinks) {
        String method = attributes.get(HTTP_REQUEST_METHOD);
        if (method == null) {
            method = attributes.get(LEGACY_HTTP_METHOD);
        }
        String path = attributes.get(URL_PATH);
        if ("POST".equals(method) && path != null
                && (path.startsWith("/api/v1/input/") || path.startsWith("/api/v1/generate/"))) {
            return SamplingResult.recordAndSample();
        }
        return delegate.shouldSample(parentContext, traceId, name, spanKind, attributes, parentLinks);
    }

    @Override
    public String getDescription() {
        return "gateway-route(" + delegate.getDescription() + ")";
    }
}
