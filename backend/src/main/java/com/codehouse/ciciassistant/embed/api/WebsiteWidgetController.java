package com.codehouse.ciciassistant.embed.api;

import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.embed.service.PublicWebWidgetService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class WebsiteWidgetController {

    private final PublicWebWidgetService service;
    private final String defaultWidgetKey;

    public WebsiteWidgetController(PublicWebWidgetService service,
                                   @Value("${app.website-widget.default-key:}") String defaultWidgetKey) {
        this.service = service;
        this.defaultWidgetKey = defaultWidgetKey == null ? "" : defaultWidgetKey.trim();
    }

    @GetMapping("/public/website-widget")
    public ApiResponse<Map<String, Object>> config() {
        if (defaultWidgetKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Website widget is not configured");
        }
        return ApiResponse.ok(service.publicConfig(defaultWidgetKey));
    }
}
