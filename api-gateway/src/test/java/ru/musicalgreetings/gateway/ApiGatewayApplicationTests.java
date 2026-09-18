package ru.musicalgreetings.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.ConsoleAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "gateway.routes.auth-service-url=http://auth-service.test:8082",
        "gateway.routes.congrats-service-url=http://congrats-service.test:8083"
})
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void keepsExporterDiagnosticsOnStderrAndOutOfRootOtlpAppender() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger exporterLogger = context.getLogger("io.opentelemetry.exporter");

        assertThat(exporterLogger.isAdditive()).isFalse();
        assertThat(exporterLogger.iteratorForAppenders()).toIterable().singleElement()
                .isInstanceOfSatisfying(ConsoleAppender.class, appender -> {
                    assertThat(appender.getName()).isEqualTo("STDERR");
                    assertThat(appender.getTarget()).isEqualTo("System.err");
                });
    }
}
