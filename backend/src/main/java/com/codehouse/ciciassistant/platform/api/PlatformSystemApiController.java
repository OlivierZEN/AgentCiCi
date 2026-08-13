package com.codehouse.ciciassistant.platform.api;

import com.codehouse.ciciassistant.auth.RequirePlatformRole;
import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.platform.service.EcosystemApplicationTrustService;
import com.codehouse.ciciassistant.platform.service.SystemApiCatalogService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/system-apis")
@RequirePlatformRole
public class PlatformSystemApiController {

    private final SystemApiCatalogService catalogService;
    private final EcosystemApplicationTrustService applicationTrust;

    public PlatformSystemApiController(SystemApiCatalogService catalogService,
                                       EcosystemApplicationTrustService applicationTrust) {
        this.catalogService = catalogService;
        this.applicationTrust = applicationTrust;
    }

    @GetMapping
    public ApiResponse<SystemApiCatalogService.CatalogView> catalog() {
        return ApiResponse.ok(catalogService.catalog());
    }

    @GetMapping("/applications")
    public ApiResponse<List<EcosystemApplicationTrustService.TrustedApplicationView>> applications() {
        return ApiResponse.ok(applicationTrust.list());
    }

    @PutMapping("/applications/{appCode}")
    @RequirePlatformRole(RoleCodes.PLATFORM_ADMIN)
    public ApiResponse<EcosystemApplicationTrustService.TrustedApplicationView> upsertApplication(
            @PathVariable @Pattern(regexp = "^[a-z][a-z0-9-]{1,63}$") String appCode,
            @Valid @RequestBody TrustedApplicationRequest request) {
        return ApiResponse.ok(applicationTrust.upsert(
                new EcosystemApplicationTrustService.TrustedApplicationCommand(
                        appCode,
                        request.displayName(),
                        request.keycloakClientId(),
                        request.allowedScopes(),
                        request.status()),
                actorId(), actorRole()), "Trusted application saved");
    }

    @PatchMapping("/applications/{appCode}/status")
    @RequirePlatformRole(RoleCodes.PLATFORM_ADMIN)
    public ApiResponse<EcosystemApplicationTrustService.TrustedApplicationView> changeApplicationStatus(
            @PathVariable @Pattern(regexp = "^[a-z][a-z0-9-]{1,63}$") String appCode,
            @Valid @RequestBody TrustedApplicationStatusRequest request) {
        return ApiResponse.ok(applicationTrust.changeStatus(
                appCode, request.status(), actorId(), actorRole()), "Trusted application status updated");
    }

    private String actorId() {
        return TenantContext.getUserId().orElse("platform");
    }

    private String actorRole() {
        return TenantContext.getRoles().stream()
                .filter(RoleCodes::isPlatformRole)
                .findFirst()
                .orElse(RoleCodes.PLATFORM_ADMIN);
    }

    public record TrustedApplicationRequest(
            @NotBlank String displayName,
            @NotBlank String keycloakClientId,
            @NotEmpty List<@NotBlank String> allowedScopes,
            String status) {
    }

    public record TrustedApplicationStatusRequest(@NotBlank String status) {
    }
}
