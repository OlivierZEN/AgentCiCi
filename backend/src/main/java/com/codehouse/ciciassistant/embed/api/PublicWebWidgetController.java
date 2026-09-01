package com.codehouse.ciciassistant.embed.api;

import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.embed.service.PublicWebWidgetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/web-widgets")
public class PublicWebWidgetController {

    private final PublicWebWidgetService service;

    public PublicWebWidgetController(PublicWebWidgetService service) {
        this.service = service;
    }

    @GetMapping("/{widgetKey}")
    public ApiResponse<Map<String, Object>> config(@PathVariable String widgetKey) {
        return ApiResponse.ok(service.publicConfig(widgetKey));
    }

    @PostMapping("/{widgetKey}/tokens")
    public ApiResponse<PublicWebWidgetService.TokenView> token(@PathVariable String widgetKey,
                                                               @Valid @RequestBody TokenRequest request,
                                                               HttpServletRequest servletRequest) {
        return ApiResponse.ok(service.issueToken(widgetKey, new PublicWebWidgetService.TokenCommand(
                request.visitorId(), request.visitId(), request.parentOrigin(), request.pagePath(), request.locale()), servletRequest));
    }

    public record TokenRequest(
            @NotBlank @Size(max = 64) String visitorId,
            @Size(max = 64) String visitId,
            @NotBlank @Size(max = 256) String parentOrigin,
            @NotBlank @Size(max = 160) String pagePath,
            @Size(max = 16) String locale) { }
}
