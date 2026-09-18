package ru.musicalgreetings.gateway.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(properties = {
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "gateway.routes.auth-service-url=http://auth-service.test:8082",
        "gateway.routes.congrats-service-url=http://congrats-service.test:8083",
        "management.opentelemetry.enabled=true",
        "management.tracing.propagation.type=w3c",
        "management.tracing.export.otlp.enabled=false",
        "management.tracing.sampling.probability=0.1",
        "MONIUM_ENABLED=true",
        "MONIUM_API_KEY=test",
        "MONIUM_PROJECT=test"
})
class GatewayReceiverHandlerWiringTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void exactlyOneReceiverHandlerBeanExistsAndItIsOurs() {
        Map<String, PropagatingReceiverTracingObservationHandler> beans =
                context.getBeansOfType(PropagatingReceiverTracingObservationHandler.class);

        assertThat(beans).hasSize(1);
        assertThat(beans.values().iterator().next()).isInstanceOf(GatewayReceiverTracingHandler.class);
    }
}
