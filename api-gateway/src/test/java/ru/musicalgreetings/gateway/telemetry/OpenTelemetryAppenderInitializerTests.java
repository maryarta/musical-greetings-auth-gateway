package ru.musicalgreetings.gateway.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class OpenTelemetryAppenderInitializerTests {

    @Test
    void installsOpenTelemetryAndExportsStructuredLogFields() {
        InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();
        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
                .build();
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setLoggerProvider(loggerProvider)
                .build();
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger("monium-appender-test");
        OpenTelemetryAppender appender = new OpenTelemetryAppender();
        appender.setName("MONIUM_TEST");
        appender.setContext(context);
        appender.setCaptureKeyValuePairAttributes(true);
        appender.start();
        logger.setAdditive(false);
        logger.addAppender(appender);

        try {
            new OpenTelemetryAppenderInitializer(openTelemetry).afterPropertiesSet();

            logger.atInfo()
                    .addKeyValue("event", "gateway_ready")
                    .log("gateway_ready");

            assertThat(exporter.getFinishedLogRecordItems()).singleElement().satisfies(record -> {
                assertThat(record.getBodyValue().asString()).isEqualTo("gateway_ready");
                assertThat(record.getAttributes().get(AttributeKey.stringKey("event")))
                        .isEqualTo("gateway_ready");
            });
        } finally {
            logger.detachAppender(appender);
            logger.setAdditive(true);
            appender.stop();
            loggerProvider.close();
        }
    }
}
