package com.codehouse.ciciassistant.embed.config;

import com.codehouse.ciciassistant.embed.service.PublicWebWidgetService;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class PublicWebWidgetCorsConfig {

    @Bean
    public FilterRegistrationBean<CorsFilter> publicWebWidgetCorsFilter(PublicWebWidgetService service) {
        CorsConfigurationSource source = request -> {
            String path = request.getRequestURI();
            String origin = request.getHeader("Origin");
            String widgetKey = widgetKey(path);
            if (widgetKey == null || origin == null || !service.originAllowedForRequest(widgetKey, origin)) {
                return null;
            }
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOrigins(List.of(origin));
            configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
            configuration.setAllowedHeaders(List.of("Content-Type", "Accept"));
            configuration.setExposedHeaders(List.of("Content-Type"));
            configuration.setAllowCredentials(false);
            configuration.setMaxAge(600L);
            return configuration;
        };
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.setName("publicWebWidgetCorsFilter");
        registration.addUrlPatterns("/public/web-widgets/*");
        return registration;
    }

    private String widgetKey(String path) {
        String prefix = "/public/web-widgets/";
        if (path == null || !path.startsWith(prefix)) return null;
        String remainder = path.substring(prefix.length());
        int slash = remainder.indexOf('/');
        return slash < 0 ? remainder : remainder.substring(0, slash);
    }
}
