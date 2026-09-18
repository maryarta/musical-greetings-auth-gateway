package ru.musicalgreetings.gateway.ratelimit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@Configuration(proxyBeanMethods = false)
public class RateLimiterConfiguration {

    @Bean
    @Qualifier("globalRateLimiter")
    FailClosedRedisRateLimiter globalRateLimiter(
            ReactiveStringRedisTemplate redisTemplate,
            ConfigurationService configurationService,
            RateLimitMetrics metrics
    ) {
        return new FailClosedRedisRateLimiter(redisTemplate, configurationService, metrics);
    }
}
