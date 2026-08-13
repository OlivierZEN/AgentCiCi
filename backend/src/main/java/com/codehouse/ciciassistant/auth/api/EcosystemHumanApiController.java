package com.codehouse.ciciassistant.auth.api;

import com.codehouse.ciciassistant.auth.service.EcosystemHumanApiService;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.common.error.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Stable HUMAN contract for trusted internal applications authenticated by Keycloak. */
@RestController
@RequestMapping("/openapi/v1/ecosystem")
public class EcosystemHumanApiController {

    private final EcosystemHumanApiService ecosystem;

    public EcosystemHumanApiController(EcosystemHumanApiService ecosystem) {
        this.ecosystem = ecosystem;
    }

    @GetMapping("/companies")
    public ApiResponse<EcosystemHumanApiService.CompanyDirectoryView> companies(HttpServletRequest request) {
        return ApiResponse.ok(ecosystem.companies(accessToken(request)));
    }

    @PostMapping("/company-context")
    public ApiResponse<EcosystemHumanApiService.CompanyContextView> companyContext(
            HttpServletRequest request,
            @Valid @RequestBody CompanyContextRequest body) {
        return ApiResponse.ok(ecosystem.companyContext(accessToken(request), body.companyId()),
                "Company context authorized");
    }

    private String accessToken(HttpServletRequest request) {
        Object value = request.getAttribute(EcosystemKeycloakBearerFilter.TOKEN_ATTRIBUTE);
        if (value instanceof String token && !token.isBlank()) {
            return token;
        }
        throw new UnauthorizedException("Keycloak HUMAN access token is required");
    }

    public record CompanyContextRequest(@NotBlank String companyId) {
    }
}
