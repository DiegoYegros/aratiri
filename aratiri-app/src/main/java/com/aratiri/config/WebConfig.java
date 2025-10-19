package com.aratiri.config;

import com.aratiri.context.AratiriContextArgumentResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AratiriContextArgumentResolver aratiriContextArgumentResolver;

    public WebConfig(AratiriContextArgumentResolver aratiriContextArgumentResolver) {
        this.aratiriContextArgumentResolver = aratiriContextArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(aratiriContextArgumentResolver);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}