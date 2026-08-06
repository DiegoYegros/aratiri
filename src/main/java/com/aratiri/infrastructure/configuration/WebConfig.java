package com.aratiri.infrastructure.configuration;

import com.aratiri.infrastructure.web.context.AratiriContextArgumentResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    public static final String OUTBOUND_USER_HTTP = "outboundUserHttp";

    private final AratiriContextArgumentResolver aratiriContextArgumentResolver;

    public WebConfig(AratiriContextArgumentResolver aratiriContextArgumentResolver) {
        this.aratiriContextArgumentResolver = aratiriContextArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(aratiriContextArgumentResolver);
    }

    /**
     * Default RestTemplate for non–user-influenced callers (e.g. config-templated currency APIs).
     * Follows redirects. Do not use for LNURL / NIP-05 / other user-influenced URLs.
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new RestTemplate(factory);
    }

    /**
     * RestTemplate for user-influenced outbound GETs (LNURL metadata/callback, Lightning Address,
     * NIP-05). Redirects are disabled so a validated public URL cannot bounce to a private target.
     * Inject with {@code @Qualifier(WebConfig.OUTBOUND_USER_HTTP)}.
     */
    @Bean(name = OUTBOUND_USER_HTTP)
    public RestTemplate outboundUserHttpRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new RestTemplate(factory);
    }
}
