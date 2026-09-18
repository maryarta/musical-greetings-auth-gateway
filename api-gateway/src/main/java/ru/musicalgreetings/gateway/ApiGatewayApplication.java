package ru.musicalgreetings.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import ru.musicalgreetings.gateway.config.GatewayJwtProperties;
import ru.musicalgreetings.gateway.config.GatewayRateLimitProperties;
import ru.musicalgreetings.gateway.config.GatewayRoutesProperties;
import ru.musicalgreetings.gateway.telemetry.MoniumProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        GatewayJwtProperties.class,
        GatewayRateLimitProperties.class,
        GatewayRoutesProperties.class,
        MoniumProperties.class
})
public class ApiGatewayApplication {

    static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
