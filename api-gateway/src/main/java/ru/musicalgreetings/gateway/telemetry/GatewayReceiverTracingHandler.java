package ru.musicalgreetings.gateway.telemetry;

import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;

public final class GatewayReceiverTracingHandler
        extends PropagatingReceiverTracingObservationHandler<ReceiverContext> {

    public GatewayReceiverTracingHandler(Tracer tracer, Propagator propagator) {
        super(tracer, propagator);
    }

    @Override
    public Span.Builder customizeExtractedSpan(ReceiverContext context, Span.Builder builder) {
        if (context instanceof ServerRequestObservationContext serverContext) {
            ServerHttpRequest request = serverContext.getCarrier();
            if (request != null) {
                builder.tag("http.request.method", request.getMethod().name());
                builder.tag("url.path", request.getPath().value());
            }
        }
        return builder;
    }
}
