package com.codehouse.ciciassistant.platform.api;

import com.codehouse.ciciassistant.auth.RequirePlatformRole;
import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.platform.service.InternalApplicationRegistryService;
import com.codehouse.ciciassistant.platform.service.InternalApplicationProviderConnectionService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/internal-applications")
@RequirePlatformRole
public class PlatformInternalApplicationController {

    private final InternalApplicationRegistryService registry;
    private final InternalApplicationProviderConnectionService providerConnections;

    public PlatformInternalApplicationController(InternalApplicationRegistryService registry,
                                                 InternalApplicationProviderConnectionService providerConnections) {
        this.registry = registry;
        this.providerConnections = providerConnections;
    }

    @GetMapping
    public ApiResponse<List<InternalApplicationRegistryService.ApplicationSummaryView>> list() {
        return ApiResponse.ok(registry.list());
    }

    @GetMapping("/{appCode}")
    public ApiResponse<InternalApplicationRegistryService.ApplicationDetailView> get(
            @PathVariable @Pattern(regexp = "^[a-z][a-z0-9-]{1,63}$") String appCode) {
        return ApiResponse.ok(registry.get(appCode));
    }

    @PostMapping
    @RequirePlatformRole(RoleCodes.PLATFORM_ADMIN)
    public ApiResponse<InternalApplicationRegistryService.ApplicationDetailView> create(
            @Valid @RequestBody ApplicationRequest request) {
        return ApiResponse.ok(registry.create(request.toCommand(), actorId(), actorRole()),
                "Internal application draft created");
    }

    @PutMapping("/{appCode}")
    @RequirePlatformRole(RoleCodes.PLATFORM_ADMIN)
    public ApiResponse<InternalApplicationRegistryService.ApplicationDetailView> update(
            @PathVariable @Pattern(regexp = "^[a-z][a-z0-9-]{1,63}$") String appCode,
            @Valid @RequestBody ApplicationRequest request) {
        return ApiResponse.ok(registry.update(appCode, request.toCommand(), actorId(), actorRole()),
                "Internal application updated");
    }

    @PostMapping("/{appCode}/versions")
    @RequirePlatformRole(RoleCodes.PLATFORM_ADMIN)
    public ApiResponse<InternalApplicationRegistryService.VersionView> createVersion(
            @PathVariable @Pattern(regexp = "^[a-z][a-z0-9-]{1,63}$") String appCode,
            @Valid @RequestBody VersionRequest request) {
        return ApiResponse.ok(registry.createVersion(appCode, request.toCommand(), actorId(), actorRole()),
                "Internal application version draft created");
    }

    @PostMapping("/{appCode}/versions/{version}/validations")
    @RequirePlatformRole(RoleCodes.PLATFORM_ADMIN)
    public ApiResponse<InternalApplicationRegistryService.ValidationView> validateVersion(
            @PathVariable @Pattern(regexp = "^[a-z][a-z0-9-]{1,63}$") String appCode,
            @PathVariable @Pattern(regexp = "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$") String version) {
        return ApiResponse.ok(registry.validateVersion(appCode, version, actorId(), actorRole()),
                "Internal application version validated");
    }

    @PostMapping("/{appCode}/versions/{version}/publications")
    @RequirePlatformRole(RoleCodes.PLATFORM_ADMIN)
    public ApiResponse<InternalApplicationRegistryService.ApplicationDetailView> publishVersion(
            @PathVariable @Pattern(regexp = "^[a-z][a-z0-9-]{1,63}$") String appCode,
            @PathVariable @Pattern(regexp = "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$") String version) {
        return ApiResponse.ok(registry.publishVersion(appCode, version, actorId(), actorRole()),
                "Internal application version published");
    }

    @PatchMapping("/{appCode}/status")
    @RequirePlatformRole(RoleCodes.PLATFORM_ADMIN)
    public ApiResponse<InternalApplicationRegistryService.ApplicationDetailView> changeStatus(
            @PathVariable @Pattern(regexp = "^[a-z][a-z0-9-]{1,63}$") String appCode,
            @Valid @RequestBody StatusRequest request) {
        return ApiResponse.ok(registry.changeStatus(appCode, request.status(), actorId(), actorRole()),
                "Internal application status updated");
    }

    @GetMapping("/{appCode}/connections")
    @RequirePlatformRole(RoleCodes.PLATFORM_ADMIN)
    public ApiResponse<List<InternalApplicationProviderConnectionService.ConnectionView>> listConnections(
            @PathVariable @Pattern(regexp = "^[a-z][a-z0-9-]{1,63}$") String appCode) {
        return ApiResponse.ok(providerConnections.list(appCode));
    }

    @PostMapping("/{appCode}/connections")
    @RequirePlatformRole(RoleCodes.PLATFORM_ADMIN)
    public ApiResponse<InternalApplicationProviderConnectionService.ConnectionView> createConnectionRevision(
            @PathVariable @Pattern(regexp = "^[a-z][a-z0-9-]{1,63}$") String appCode,
            @Valid @RequestBody ProviderConnectionRequest request) {
        return ApiResponse.ok(providerConnections.createRevision(
                appCode, request.toCommand(), actorId(), actorRole()),
                "Provider connection revision created");
    }

    @PostMapping("/{appCode}/connections/{bindingKey}/tests")
    @RequirePlatformRole(RoleCodes.PLATFORM_ADMIN)
    public ApiResponse<InternalApplicationProviderConnectionService.ConnectionTestView> testConnection(
            @PathVariable @Pattern(regexp = "^[a-z][a-z0-9-]{1,63}$") String appCode,
            @PathVariable @Pattern(regexp = "^[a-z][a-z0-9._-]{1,127}$") String bindingKey) {
        return ApiResponse.ok(providerConnections.test(appCode, bindingKey, actorId(), actorRole()),
                "Provider connection test completed");
    }

    @PostMapping("/{appCode}/connections/{bindingKey}/activations")
    @RequirePlatformRole(RoleCodes.PLATFORM_ADMIN)
    public ApiResponse<InternalApplicationProviderConnectionService.ConnectionView> activateConnection(
            @PathVariable @Pattern(regexp = "^[a-z][a-z0-9-]{1,63}$") String appCode,
            @PathVariable @Pattern(regexp = "^[a-z][a-z0-9._-]{1,127}$") String bindingKey) {
        return ApiResponse.ok(providerConnections.activate(appCode, bindingKey, actorId(), actorRole()),
                "Provider connection activated");
    }

    @PostMapping("/{appCode}/connections/{bindingKey}/disabling")
    @RequirePlatformRole(RoleCodes.PLATFORM_ADMIN)
    public ApiResponse<InternalApplicationProviderConnectionService.ConnectionView> disableConnection(
            @PathVariable @Pattern(regexp = "^[a-z][a-z0-9-]{1,63}$") String appCode,
            @PathVariable @Pattern(regexp = "^[a-z][a-z0-9._-]{1,127}$") String bindingKey) {
        return ApiResponse.ok(providerConnections.disable(appCode, bindingKey, actorId(), actorRole()),
                "Provider connection disabled");
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

    public record ApplicationRequest(
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9-]{1,63}$") String appCode,
            @NotBlank @Size(max = 128) String displayName,
            @NotBlank @Size(max = 500) String summary,
            @NotBlank @Size(max = 64) String iconKey,
            @NotBlank @Size(max = 128) String ownerTeam,
            @NotBlank String tenantMode,
            @Size(max = 64) String trustedAppCode,
            @NotBlank String launchMode,
            @Size(max = 128) String launchRouteKey) {

        private InternalApplicationRegistryService.ApplicationCommand toCommand() {
            return new InternalApplicationRegistryService.ApplicationCommand(
                    appCode, displayName, summary, iconKey, ownerTeam, tenantMode,
                    trustedAppCode, launchMode, launchRouteKey);
        }
    }

    public record VersionRequest(
            @NotBlank @Pattern(regexp = "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$") String version,
            @Size(max = 128) String providerBindingKey,
            @NotBlank String initializationEngine,
            @Size(max = 32) List<@Valid StepRequest> steps,
            @Size(max = 32) List<@Valid DependencyRequest> dependencies,
            @Size(max = 16) List<@Valid McpProviderRequest> mcpProviders) {

        private InternalApplicationRegistryService.VersionCommand toCommand() {
            return new InternalApplicationRegistryService.VersionCommand(
                    version,
                    providerBindingKey,
                    initializationEngine,
                    steps == null ? List.of() : steps.stream().map(StepRequest::toCommand).toList(),
                    dependencies == null ? List.of() : dependencies.stream().map(DependencyRequest::toCommand).toList(),
                    mcpProviders == null ? List.of() : mcpProviders.stream().map(McpProviderRequest::toCommand).toList());
        }
    }

    public record McpProviderRequest(
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9._-]{1,127}$") String providerKey,
            @NotBlank String transportType,
            @NotBlank String authType,
            @Size(max = 256) String audience,
            @Size(max = 256) String requiredScope,
            @Size(min = 1, max = 128) List<@Valid McpToolRequest> tools) {
        private InternalApplicationRegistryService.McpProviderCommand toCommand() {
            return new InternalApplicationRegistryService.McpProviderCommand(providerKey, transportType, authType,
                    audience, requiredScope, tools == null ? List.of() : tools.stream().map(McpToolRequest::toCommand).toList());
        }
    }

    public record McpToolRequest(
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9._-]{1,127}$") String name,
            @Pattern(regexp = "^$|^[0-9a-f]{64}$") String schemaDigest,
            @NotBlank String riskLevel,
            boolean confirmationRequired) {
        private InternalApplicationRegistryService.McpToolCommand toCommand() {
            return new InternalApplicationRegistryService.McpToolCommand(name, schemaDigest, riskLevel, confirmationRequired);
        }
    }

    public record StepRequest(
            @NotBlank @Size(max = 128) String code,
            @NotBlank String type,
            @NotBlank @Size(max = 128) String capability,
            @NotBlank @Size(max = 128) String contractVersion) {

        private InternalApplicationRegistryService.StepCommand toCommand() {
            return new InternalApplicationRegistryService.StepCommand(code, type, capability, contractVersion);
        }
    }

    public record DependencyRequest(
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9-]{1,63}$") String appCode,
            @NotBlank @Size(max = 64) String versionConstraint,
            @NotBlank String dependencyType,
            @NotBlank String activationPolicy) {

        private InternalApplicationRegistryService.DependencyCommand toCommand() {
            return new InternalApplicationRegistryService.DependencyCommand(
                    appCode, versionConstraint, dependencyType, activationPolicy);
        }
    }

    public record StatusRequest(@NotBlank String status) {
    }

    public record ProviderConnectionRequest(
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9._-]{1,127}$") String bindingKey,
            @NotBlank @Size(max = 128) String displayName,
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_-]{1,63}$") String environmentKey,
            @NotBlank String networkScope,
            @NotBlank @Size(max = 1024) String baseUrl,
            @NotBlank @Size(max = 64) String contractVersion,
            @NotBlank String authType,
            @Size(max = 128) String secretRef,
            @NotBlank @Size(max = 256) String healthPath,
            @NotBlank @Size(max = 256) String activatePath,
            @Size(max = 256) String reconcilePath,
            @Size(max = 256) String suspendPath,
            @Size(max = 256) String resumePath,
            @Size(max = 256) String upgradePath,
            Integer timeoutMs,
            Integer maxAttempts) {

        private InternalApplicationProviderConnectionService.ConnectionCommand toCommand() {
            return new InternalApplicationProviderConnectionService.ConnectionCommand(
                    bindingKey, displayName, environmentKey, networkScope, baseUrl, contractVersion,
                    authType, secretRef, healthPath, activatePath, reconcilePath, suspendPath,
                    resumePath, upgradePath, timeoutMs, maxAttempts);
        }
    }
}
