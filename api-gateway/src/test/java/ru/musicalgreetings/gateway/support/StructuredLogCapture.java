package ru.musicalgreetings.gateway.support;

import java.util.LinkedHashMap;
import java.util.Map;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;

public final class StructuredLogCapture implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender;

    private StructuredLogCapture(Class<?> loggerType) {
        logger = (Logger) LoggerFactory.getLogger(loggerType);
        appender = new ListAppender<>();
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
    }

    public static StructuredLogCapture forLogger(Class<?> loggerType) {
        return new StructuredLogCapture(loggerType);
    }

    public Map<String, Object> attributes(String messagePrefix) {
        ILoggingEvent event = appender.list.stream()
                .filter(candidate -> candidate.getFormattedMessage().startsWith(messagePrefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Log event not found: " + messagePrefix));
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (event.getKeyValuePairs() != null) {
            for (KeyValuePair pair : event.getKeyValuePairs()) {
                attributes.put(pair.key, pair.value);
            }
        }
        return attributes;
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }
}
