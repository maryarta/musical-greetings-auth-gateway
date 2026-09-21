package ru.team22.musicalgreetings.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.session.DisableEncodeUrlFilter;

@Configuration
public class SecurityConfig {

    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    public SecurityConfig(RestAuthenticationEntryPoint authenticationEntryPoint) {
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new RequestIdFilter(), DisableEncodeUrlFilter.class)
                .addFilterAfter(new HeaderAuthenticationFilter(), SecurityContextHolderFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/congrats/history").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/holidays", "/api/v1/holidays/**",
                                "/api/v1/music-types").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/congrats/{surl}",
                                "/api/v1/congrats/{surl}/video").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint));
        return http.build();
    }
}
