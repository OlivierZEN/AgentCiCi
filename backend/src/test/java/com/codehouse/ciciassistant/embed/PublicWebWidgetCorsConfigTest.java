package com.codehouse.ciciassistant.embed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.codehouse.ciciassistant.embed.config.PublicWebWidgetCorsConfig;
import com.codehouse.ciciassistant.embed.service.PublicWebWidgetService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.filter.CorsFilter;

class PublicWebWidgetCorsConfigTest {

    @Test
    void shouldRegisterOnlyForPublicWebWidgetPaths() {
        FilterRegistrationBean<CorsFilter> registration = new PublicWebWidgetCorsConfig()
                .publicWebWidgetCorsFilter(mock(PublicWebWidgetService.class));

        assertThat(registration.getUrlPatterns()).containsExactly("/public/web-widgets/*");
    }
}
