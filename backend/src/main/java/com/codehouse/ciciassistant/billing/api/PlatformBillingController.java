package com.codehouse.ciciassistant.billing.api;

import com.codehouse.ciciassistant.auth.RequirePlatformRole;
import com.codehouse.ciciassistant.billing.service.BillingEditionConfigurationService;
import com.codehouse.ciciassistant.billing.service.BillingEditionConfigurationService.BillingCatalogView;
import com.codehouse.ciciassistant.billing.service.BillingEditionConfigurationService.BillingChangeLogView;
import com.codehouse.ciciassistant.billing.service.BillingEditionConfigurationService.BillingEditionView;
import com.codehouse.ciciassistant.billing.service.BillingEditionConfigurationService.BillingPackageView;
import com.codehouse.ciciassistant.billing.service.BillingEditionConfigurationService.EditionUpdateCommand;
import com.codehouse.ciciassistant.billing.service.BillingEditionConfigurationService.PackageUpdateCommand;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/billing")
@RequirePlatformRole
public class PlatformBillingController {

    private final BillingEditionConfigurationService billingEditionConfigurationService;

    public PlatformBillingController(BillingEditionConfigurationService billingEditionConfigurationService) {
        this.billingEditionConfigurationService = billingEditionConfigurationService;
    }

    @GetMapping("/catalog")
    public ApiResponse<BillingCatalogView> catalog(@RequestParam(required = false) String deploymentMode,
                                                   @RequestParam(required = false) String packageType) {
        return ApiResponse.ok(billingEditionConfigurationService.catalog(deploymentMode, packageType));
    }

    @PutMapping("/editions/{editionCode}")
    public ApiResponse<BillingEditionView> updateEdition(@PathVariable String editionCode,
                                                         @Valid @RequestBody EditionUpdateRequest request) {
        return ApiResponse.ok(billingEditionConfigurationService.updateEdition(editionCode,
                new EditionUpdateCommand(
                        request.displayName(),
                        request.description(),
                        request.enabled(),
                        request.operationSeatLimit(),
                        request.builderSeatLimit(),
                        request.agentLimit(),
                        request.skillLimit(),
                        request.workflowLimit(),
                        request.knowledgeBaseLimit(),
                        request.documentLimit(),
                        request.chunkLimit(),
                        request.knowledgeStorageMb(),
                        request.openApiQps(),
                        request.openApiConcurrency(),
                        request.openApiCredentialLimit(),
                        request.connectorLimit(),
                        request.meetingMinutesConcurrency(),
                        request.traceRetentionDays(),
                        request.auditRetentionDays(),
                        request.environmentLimit(),
                        request.includedCredits(),
                        request.overageMode(),
                        request.billingTypePolicy(),
                        request.slaTierCode(),
                        request.topUpPolicy(),
                        request.localModelTokenPolicy(),
                        request.platformPaidResourcePolicy(),
                        request.packageCodes(),
                        request.reason()
                ),
                actorId(),
                actorRole()));
    }

    @PutMapping("/packages/{packageCode}")
    public ApiResponse<BillingPackageView> updatePackage(@PathVariable String packageCode,
                                                         @Valid @RequestBody PackageUpdateRequest request) {
        return ApiResponse.ok(billingEditionConfigurationService.updatePackage(packageCode,
                new PackageUpdateCommand(
                        request.displayName(),
                        request.description(),
                        request.enabled(),
                        request.packageType(),
                        request.configJson(),
                        request.reason()
                ),
                actorId(),
                actorRole()));
    }

    @GetMapping("/history/{configType}/{configCode}")
    public ApiResponse<List<BillingChangeLogView>> history(@PathVariable String configType,
                                                           @PathVariable String configCode) {
        return ApiResponse.ok(billingEditionConfigurationService.history(configType, configCode));
    }

    private String actorId() {
        return TenantContext.getUserId().orElse("platform");
    }

    private String actorRole() {
        return TenantContext.getRoles().stream()
                .filter(role -> role.startsWith("PLATFORM_"))
                .findFirst()
                .orElse("PLATFORM");
    }

    public record EditionUpdateRequest(
            @NotBlank String displayName,
            String description,
            Boolean enabled,
            Integer operationSeatLimit,
            Integer builderSeatLimit,
            Integer agentLimit,
            Integer skillLimit,
            Integer workflowLimit,
            Integer knowledgeBaseLimit,
            Integer documentLimit,
            Integer chunkLimit,
            Integer knowledgeStorageMb,
            Integer openApiQps,
            Integer openApiConcurrency,
            Integer openApiCredentialLimit,
            Integer connectorLimit,
            Integer meetingMinutesConcurrency,
            Integer traceRetentionDays,
            Integer auditRetentionDays,
            Integer environmentLimit,
            BigDecimal includedCredits,
            @NotBlank String overageMode,
            @NotBlank String billingTypePolicy,
            @NotBlank String slaTierCode,
            @NotBlank String topUpPolicy,
            @NotBlank String localModelTokenPolicy,
            String platformPaidResourcePolicy,
            List<String> packageCodes,
            @NotBlank String reason
    ) {
    }

    public record PackageUpdateRequest(
            @NotBlank String displayName,
            String description,
            Boolean enabled,
            @NotBlank String packageType,
            @NotBlank String configJson,
            @NotBlank String reason
    ) {
    }
}
