package com.aratiri.infrastructure.configuration.security;

import com.aratiri.auth.infrastructure.security.AratiriJwtAuthenticationConverter;
import com.aratiri.auth.infrastructure.security.ChainedJwtDecoder;
import com.aratiri.infrastructure.configuration.AratiriProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(AratiriSecurityProperties.class)
public class SecurityConfig {

    private final AuthenticationEntryPoint authenticationEntryPoint;
    private final AratiriJwtAuthenticationConverter jwtAuthenticationConverter;

    public SecurityConfig(AuthenticationEntryPoint authenticationEntryPoint,
                          AratiriJwtAuthenticationConverter jwtAuthenticationConverter) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v1/auth/login",
                                "/v1/auth/register",
                                "/v1/auth/verify",
                                "/v1/auth/forgot-password",
                                "/v1/auth/reset-password",
                                "/v1/auth/refresh",
                                "/v1/auth/exchange",
                                "/h2-console/**",
                                "/.well-known/lnurlp/**",
                                "/lnurl/callback/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/v1/auth/sso/google",
                                "/swagger-resources/**",
                                "/webjars/**",
                                "/v1/notifications/subscribe"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::disable)
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .exceptionHandling(exception -> exception.authenticationEntryPoint(authenticationEntryPoint))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                )
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("ROLE_SUPERADMIN > ROLE_ADMIN \n" +
                "ROLE_ADMIN > ROLE_VIEWER \n" +
                "ROLE_VIEWER > ROLE_USER");
    }

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
        expressionHandler.setRoleHierarchy(roleHierarchy);
        return expressionHandler;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtDecoder jwtDecoder(AratiriProperties aratiriProperties, AratiriSecurityProperties securityProperties) {
        List<JwtDecoder> decoders = new ArrayList<>();

        if (aratiriProperties.getJwtSecret() != null && !aratiriProperties.getJwtSecret().isEmpty()) {
            SecretKeySpec secretKey = new SecretKeySpec(aratiriProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            NimbusJwtDecoder localDecoder = NimbusJwtDecoder.withSecretKey(secretKey).build();
            localDecoder.setJwtValidator(JwtValidators.createDefault());
            decoders.add(localDecoder);
        }

        if (securityProperties.getTrustedIssuers() != null) {
            securityProperties.getTrustedIssuers().forEach(trustedIssuer -> {
                JwtDecoder decoder = buildTrustedIssuerDecoder(trustedIssuer);
                if (decoder != null) {
                    decoders.add(decoder);
                }
            });
        }

        if (decoders.isEmpty()) {
            throw new IllegalStateException("No JWT decoders configured. Provide a JWT secret or trusted issuer configuration.");
        }

        return new ChainedJwtDecoder(decoders);
    }

    private JwtDecoder buildTrustedIssuerDecoder(AratiriSecurityProperties.TrustedIssuer trustedIssuer) {
        NimbusJwtDecoder decoder;
        if (trustedIssuer.getJwkSetUri() != null && !trustedIssuer.getJwkSetUri().isEmpty()) {
            decoder = NimbusJwtDecoder.withJwkSetUri(trustedIssuer.getJwkSetUri()).build();
        } else if (trustedIssuer.getIssuerUri() != null && !trustedIssuer.getIssuerUri().isEmpty()) {
            JwtDecoder resolved = JwtDecoders.fromIssuerLocation(trustedIssuer.getIssuerUri());
            if (resolved instanceof NimbusJwtDecoder nimbusJwtDecoder) {
                decoder = nimbusJwtDecoder;
            } else {
                return resolved;
            }
        } else {
            return null;
        }

        OAuth2TokenValidator<Jwt> validator;
        if (trustedIssuer.getIssuer() != null && !trustedIssuer.getIssuer().isEmpty()) {
            validator = JwtValidators.createDefaultWithIssuer(trustedIssuer.getIssuer());
        } else if (trustedIssuer.getIssuerUri() != null && !trustedIssuer.getIssuerUri().isEmpty()) {
            validator = JwtValidators.createDefaultWithIssuer(trustedIssuer.getIssuerUri());
        } else {
            validator = JwtValidators.createDefault();
        }
        decoder.setJwtValidator(validator);
        return decoder;
    }
}