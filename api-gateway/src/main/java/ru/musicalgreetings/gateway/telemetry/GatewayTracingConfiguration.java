package ru.musicalgreetings.gateway.telemetry;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.propagation.Propagator;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.SdkTracerProviderBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration(proxyBeanMethods = false)
public class GatewayTracingConfiguration {

    @Bean
    SdkTracerProviderBuilderCustomizer gatewayRouteSamplerCustomizer(ObjectProvider<Sampler> samplerProvider) {
        return builder -> builder.setSampler(new GatewayRouteSampler(samplerProvider.getObject()));
    }

    @Bean
    @Order(MicrometerTracingAutoConfiguration.RECEIVER_TRACING_OBSERVATION_HANDLER_ORDER)
    PropagatingReceiverTracingObservationHandler<?> propagatingReceiverTracingObservationHandler(
            Tracer tracer, Propagator propagator) {
        return new GatewayReceiverTracingHandler(tracer, propagator);
    }
}
