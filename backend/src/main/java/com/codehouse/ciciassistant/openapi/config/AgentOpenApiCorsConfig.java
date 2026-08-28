package com.codehouse.ciciassistant.openapi.config;

import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class AgentOpenApiCorsConfig {

    @Bean
    public FilterRegistrationBean<CorsFilter> agentOpenApiCorsFilter(AgentOpenApiProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.getCorsAllowedOrigins());
        configuration.setAllowedOriginPatterns(properties.getCorsAllowedOriginPatterns());
        configuration.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Cici-Api-Key",
                "X-Company-Id",
                "Idempotency-Key",
                "Last-Event-ID"
        ));
        configuration.setExposedHeaders(List.of(
                "Content-Type",
                "Cache-Control",
                "X-Accel-Buffering"
        ));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(properties.getCorsMaxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/openapi/v1/**", configuration);
        source.registerCorsConfiguration("/auth/cloudcc-sso/**", configuration);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("agentOpenApiCorsFilter");
        registration.addUrlPatterns("/openapi/v1/*", "/auth/cloudcc-sso/*");
        return registration;
    }
}
