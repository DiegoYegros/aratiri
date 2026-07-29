package com.aratiri.infrastructure.configuration.security;

import com.aratiri.infrastructure.filter.AuthRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AuthRateLimitConfig {

    @Bean
    public AuthRateLimiter authRateLimiter(AratiriSecurityProperties securityProperties, Clock clock) {
        AratiriSecurityProperties.AuthRateLimit config = securityProperties.getAuthRateLimit();
        config.validate();
        return new AuthRateLimiter(config, clock);
    }
}
