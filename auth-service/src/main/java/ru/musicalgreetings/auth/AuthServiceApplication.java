package ru.musicalgreetings.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import ru.musicalgreetings.auth.config.SessionProperties;

@SpringBootApplication
@EnableConfigurationProperties(SessionProperties.class)
public class AuthServiceApplication {

    static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
