package com.aratiri.aratiri.config;

import com.aratiri.aratiri.context.AratiriContextArgumentResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private AratiriContextArgumentResolver aratiriContextArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(aratiriContextArgumentResolver);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}