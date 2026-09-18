package ru.musicalgreetings.gateway.telemetry;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MoniumConfigurationValidator implements InitializingBean {

    private final MoniumProperties properties;

    public MoniumConfigurationValidator(MoniumProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        if (!properties.enabled()) {
            return;
        }

        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(properties.apiKey())) {
            missing.add("MONIUM_API_KEY");
        }
        if (!StringUtils.hasText(properties.project())) {
            missing.add("MONIUM_PROJECT");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Monium telemetry is enabled but required variables are missing: "
                            + String.join(", ", missing));
        }
    }
}
