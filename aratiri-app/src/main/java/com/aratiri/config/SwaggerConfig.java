package com.aratiri.config;

import com.aratiri.infrastructure.web.context.AratiriCtx;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Parameter;
import java.util.Arrays;

@Configuration
public class SwaggerConfig {

    private static final String BEARER_AUTH_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Aratiri API")
                        .description("Aratiri API Documentation")
                        .version("1.0.0"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH_SCHEME, new SecurityScheme()
                                .name(BEARER_AUTH_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT Bearer token authentication")));
    }

    @Bean
    public OperationCustomizer aratiriContextSecurityCustomizer() {
        return (operation, handlerMethod) -> {
            Parameter[] parameters = handlerMethod.getMethod().getParameters();
            boolean hasAratiriCtx = Arrays.stream(parameters)
                    .anyMatch(param -> Arrays.stream(param.getAnnotations())
                            .anyMatch(annotation -> annotation.annotationType().equals(AratiriCtx.class)));
            if (hasAratiriCtx) {
                operation.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH_SCHEME));
            }
            return operation;
        };
    }
}