package ru.musicalgreetings.gateway.config;

import java.util.Base64;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

import ru.musicalgreetings.gateway.config.GatewayRouteSpec.AccessType;
import ru.musicalgreetings.gateway.error.GatewayAuthenticationEntryPoint;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
            "invalid_token",
            "JWT claims are invalid",
            null
    );

    @Bean
    SecretKey gatewayJwtSecretKey(@Value("${security.jwt.secret}") String encodedSecret) {
        byte[] secret;
        try {
            secret = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT_SECRET must be valid Base64", exception);
        }
        if (secret.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 256 bits");
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    @Bean
    ReactiveJwtDecoder gatewayJwtDecoder(SecretKey secretKey, GatewayJwtProperties properties) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(properties.clockSkew());
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(properties.audience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        OAuth2TokenValidator<Jwt> identityValidator = jwt -> validUuid(jwt.getSubject())
                && validUuid(jwt.getClaimAsString("sid"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_TOKEN);

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                new JwtIssuerValidator(properties.issuer()),
                audienceValidator,
                identityValidator
        ));
        return decoder;
    }

    @Bean
    @Order(1)
    SecurityWebFilterChain protectedApiSecurityWebFilterChain(
            ServerHttpSecurity http,
            GatewayRouteCatalog catalog,
            GatewayAuthenticationEntryPoint authenticationEntryPoint
    ) {
        return http
                .securityMatcher(apiOperations(catalog, AccessType.PROTECTED))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .exceptionHandling(spec -> spec.authenticationEntryPoint(authenticationEntryPoint))
                .authorizeExchange(exchanges -> exchanges.anyExchange().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(authenticationEntryPoint)
                )
                .build();
    }

    @Bean
    @Order(2)
    SecurityWebFilterChain publicApiSecurityWebFilterChain(
            ServerHttpSecurity http,
            GatewayRouteCatalog catalog
    ) {
        return http
                .securityMatcher(apiOperations(catalog, AccessType.PUBLIC))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .build();
    }

    @Bean
    @Order(3)
    SecurityWebFilterChain fallbackSecurityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .build();
    }

    private static ServerWebExchangeMatcher apiOperations(
            GatewayRouteCatalog catalog,
            AccessType accessType
    ) {
        ServerWebExchangeMatcher[] matchers = catalog.routes().stream()
                .filter(route -> route.accessType() == accessType)
                .flatMap(route -> route.methods().stream()
                        .map(method -> ServerWebExchangeMatchers.pathMatchers(
                                method, route.paths().toArray(String[]::new))))
                .toArray(ServerWebExchangeMatcher[]::new);
        return ServerWebExchangeMatchers.matchers(matchers);
    }

    private static boolean validUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
