package ru.musicalgreetings.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class GatewayConfigurationTests {

    private final ApplicationContextRunner baseContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class
            ))
            .withUserConfiguration(TestConfiguration.class);

    private final ApplicationContextRunner contextRunner = baseContextRunner
            .withPropertyValues(
                    "gateway.rate-limits.endpoints.test-route=1",
                    "gateway.rate-limits.global-endpoints.test-route=2"
            );

    @Test
    void bindsValidGatewayConfiguration() {
        contextRunner
                .withPropertyValues(
                        "security.jwt.issuer=https://auth.test",
                        "security.jwt.audience=musical-greetings-api",
                        "security.jwt.clock-skew=30s",
                        "gateway.routes.auth-service-url=http://localhost:8082",
                        "gateway.routes.congrats-service-url=http://localhost:8083"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(GatewayJwtProperties.class))
                            .extracting(
                                    GatewayJwtProperties::issuer,
                                    GatewayJwtProperties::audience,
                                    GatewayJwtProperties::clockSkew
                            )
                            .containsExactly(
                                    "https://auth.test",
                                    "musical-greetings-api",
                                    Duration.ofSeconds(30)
                            );
                    assertThat(context.getBean(GatewayRoutesProperties.class))
                            .extracting(
                                    GatewayRoutesProperties::authServiceUrl,
                                    GatewayRoutesProperties::congratsServiceUrl
                            )
                            .containsExactly(
                                    URI.create("http://localhost:8082"),
                                    URI.create("http://localhost:8083")
                            );
                    assertThat(context.getBean(GatewayRateLimitProperties.class)
                            .limitFor("test-route")).isEqualTo(1);
                    assertThat(context.getBean(GatewayRateLimitProperties.class)
                            .globalLimitFor("test-route")).isEqualTo(2);
                });
    }

    @Test
    void rejectsNonPositiveRateLimit() {
        contextRunner
                .withPropertyValues("gateway.rate-limits.endpoints.test-route=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsNonPositiveGlobalRateLimit() {
        contextRunner
                .withPropertyValues("gateway.rate-limits.global-endpoints.test-route=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsRateLimitConfigurationMissingCatalogRoute() {
        GatewayRateLimitProperties properties = new GatewayRateLimitProperties(
                Map.of("route-a", 1),
                Map.of("route-a", 2, "route-b", 2)
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties.validateRoutes(Set.of("route-a", "route-b")))
                .withMessageContaining("personal")
                .withMessageContaining("missing=[route-b]");
    }

    @Test
    void rejectsRateLimitConfigurationWithUnknownRoute() {
        GatewayRateLimitProperties properties = new GatewayRateLimitProperties(
                Map.of("route-a", 1, "obsolete-route", 1),
                Map.of("route-a", 2)
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties.validateRoutes(Set.of("route-a")))
                .withMessageContaining("personal")
                .withMessageContaining("unexpected=[obsolete-route]");
    }

    @Test
    void rejectsGlobalRateLimitConfigurationMissingCatalogRoute() {
        GatewayRateLimitProperties properties = new GatewayRateLimitProperties(
                Map.of("route-a", 1, "route-b", 1),
                Map.of("route-a", 2)
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties.validateRoutes(Set.of("route-a", "route-b")))
                .withMessageContaining("global")
                .withMessageContaining("missing=[route-b]");
    }

    @Test
    void rejectsGlobalRateLimitConfigurationWithUnknownRoute() {
        GatewayRateLimitProperties properties = new GatewayRateLimitProperties(
                Map.of("route-a", 1),
                Map.of("route-a", 2, "obsolete-route", 2)
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties.validateRoutes(Set.of("route-a")))
                .withMessageContaining("global")
                .withMessageContaining("unexpected=[obsolete-route]");
    }

    @Test
    void acceptsExactPersonalAndGlobalRateLimitConfiguration() {
        GatewayRateLimitProperties properties = new GatewayRateLimitProperties(
                Map.of("route-a", 1, "route-b", 1),
                Map.of("route-a", 2, "route-b", 2)
        );

        properties.validateRoutes(Set.of("route-a", "route-b"));
    }

    @Test
    void rejectsBlankJwtAudience() {
        contextRunner
                .withPropertyValues(
                        "security.jwt.issuer=https://auth.test",
                        "security.jwt.audience= ",
                        "security.jwt.clock-skew=30s",
                        "gateway.routes.auth-service-url=http://localhost:8082",
                        "gateway.routes.congrats-service-url=http://localhost:8083"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsNegativeClockSkew() {
        contextRunner
                .withPropertyValues(
                        "security.jwt.issuer=https://auth.test",
                        "security.jwt.audience=musical-greetings-api",
                        "security.jwt.clock-skew=-1s",
                        "gateway.routes.auth-service-url=http://localhost:8082",
                        "gateway.routes.congrats-service-url=http://localhost:8083"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsMissingCongratsServiceUrl() {
        contextRunner
                .withPropertyValues(
                        "security.jwt.issuer=https://auth.test",
                        "security.jwt.audience=musical-greetings-api",
                        "security.jwt.clock-skew=30s",
                        "gateway.routes.auth-service-url=http://localhost:8082"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void applicationConfigurationRequiresBothServiceEnvironmentVariables() {
        contextRunner
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void applicationConfigurationDefinesLimitForEveryEndpoint() {
        baseContextRunner
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(
                        "gateway.routes.auth-service-url=http://localhost:8082",
                        "gateway.routes.congrats-service-url=http://localhost:8083"
                )
                .run(context -> {
                    GatewayRateLimitProperties properties = context.getBean(
                            GatewayRateLimitProperties.class);
                    properties.validateRoutes(new GatewayRouteCatalog().routeIds());
                    assertThat(properties.endpoints()).containsAllEntriesOf(Map.ofEntries(
                                Map.entry("auth-anonymous", 10),
                                Map.entry("auth-refresh", 10),
                                Map.entry("auth-logout", 10),
                                Map.entry("holidays", 60),
                                Map.entry("holiday", 60),
                                Map.entry("music-types", 60),
                                Map.entry("congrats-history", 60),
                                Map.entry("public-congrats", 60),
                                Map.entry("public-congrats-video", 60),
                                Map.entry("input-prompt", 3),
                                Map.entry("input-voice", 3),
                                Map.entry("input-template", 3),
                                Map.entry("generate-lyrics", 5),
                                Map.entry("generate-image", 5),
                                Map.entry("generate-music", 5),
                                Map.entry("publish", 2),
                                Map.entry("push-token-set", 10),
                                Map.entry("push-token-delete", 10),
                                Map.entry("draft", 60)
                    ));
                    assertThat(properties.globalEndpoints()).containsAllEntriesOf(Map.ofEntries(
                            Map.entry("auth-anonymous", 1000),
                            Map.entry("auth-refresh", 1000),
                            Map.entry("auth-logout", 1000),
                            Map.entry("holidays", 6000),
                            Map.entry("holiday", 6000),
                            Map.entry("music-types", 6000),
                            Map.entry("congrats-history", 6000),
                            Map.entry("public-congrats", 6000),
                            Map.entry("public-congrats-video", 6000),
                            Map.entry("input-prompt", 300),
                            Map.entry("input-voice", 300),
                            Map.entry("input-template", 300),
                            Map.entry("generate-lyrics", 500),
                            Map.entry("generate-image", 500),
                            Map.entry("generate-music", 500),
                            Map.entry("publish", 200),
                            Map.entry("push-token-set", 1000),
                            Map.entry("push-token-delete", 1000),
                            Map.entry("draft", 6000)
                    ));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            GatewayJwtProperties.class,
            GatewayRateLimitProperties.class,
            GatewayRoutesProperties.class
    })
    static class TestConfiguration {
    }
}
