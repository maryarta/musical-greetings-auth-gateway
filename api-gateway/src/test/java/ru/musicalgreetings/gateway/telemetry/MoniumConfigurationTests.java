package ru.musicalgreetings.gateway.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class MoniumConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void startsWithoutCredentialsWhenMoniumIsDisabled() {
        contextRunner
                .withPropertyValues("monium.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(MoniumProperties.class).enabled()).isFalse();
                });
    }

    @Test
    void startsWithCredentialsWhenMoniumIsEnabled() {
        contextRunner
                .withPropertyValues(
                        "monium.enabled=true",
                        "monium.api-key=test-api-key",
                        "monium.project=folder__test"
                )
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void rejectsEnabledMoniumWithoutApiKey() {
        contextRunner
                .withPropertyValues(
                        "monium.enabled=true",
                        "monium.project=folder__test"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .rootCause()
                            .hasMessageContaining("MONIUM_API_KEY")
                            .hasMessageNotContaining("folder__test");
                });
    }

    @Test
    void rejectsEnabledMoniumWithoutProject() {
        contextRunner
                .withPropertyValues(
                        "monium.enabled=true",
                        "monium.api-key=test-api-key"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .rootCause()
                            .hasMessageContaining("MONIUM_PROJECT")
                            .hasMessageNotContaining("test-api-key");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MoniumProperties.class)
    @Import(MoniumConfigurationValidator.class)
    static class TestConfiguration {
    }
}
