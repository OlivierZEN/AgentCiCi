package com.codehouse.ciciassistant.billing.api;

import com.codehouse.ciciassistant.auth.RequirePlatformRole;
import com.codehouse.ciciassistant.auth.config.PlatformAccountProperties;
import com.codehouse.ciciassistant.billing.service.BillingEditionConfigService;
import com.codehouse.ciciassistant.billing.service.BillingEditionConfigService.BillingEditionConfigCommand;
import com.codehouse.ciciassistant.billing.service.BillingEditionConfigService.BillingEditionConfigView;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/billing")
@RequirePlatformRole
public class PlatformBillingController {

    private final BillingEditionConfigService billingEditionConfigService;
    private final PlatformAccountProperties platformAccountProperties;

    public PlatformBillingController(BillingEditionConfigService billingEditionConfigService,
                                     PlatformAccountProperties platformAccountProperties) {
        this.billingEditionConfigService = billingEditionConfigService;
        this.platformAccountProperties = platformAccountProperties;
    }

    @GetMapping("/plans")
    public ApiResponse<List<BillingEditionConfigView>> listBillingPlans() {
        return ApiResponse.ok(billingEditionConfigService.list(platformScopeId()));
    }

    @PostMapping("/plans")
    public ApiResponse<BillingEditionConfigView> createBillingPlanDraft(@Valid @RequestBody BillingConfigRequest request) {
        return ApiResponse.ok(billingEditionConfigService.createDraft(platformScopeId(), request.toCommand()));
    }

    @PutMapping("/plans/{id}")
    public ApiResponse<BillingEditionConfigView> updateBillingPlanDraft(@PathVariable Long id,
                                                                        @Valid @RequestBody BillingConfigRequest request) {
        return ApiResponse.ok(billingEditionConfigService.updateAsDraft(platformScopeId(), id, request.toCommand()));
    }

    @PostMapping("/plans/{id}/publish")
    public ApiResponse<BillingEditionConfigView> publishBillingPlan(@PathVariable Long id,
                                                                    @Valid @RequestBody BillingActionRequest request) {
        return ApiResponse.ok(billingEditionConfigService.publish(platformScopeId(), id, request.changeReason()));
    }

    @PostMapping("/plans/{id}/enabled")
    public ApiResponse<BillingEditionConfigView> setBillingPlanEnabled(@PathVariable Long id,
                                                                       @Valid @RequestBody BillingEnabledRequest request) {
        return ApiResponse.ok(billingEditionConfigService.setEnabled(platformScopeId(), id, request.enabled(), request.changeReason()));
    }

    private String platformScopeId() {
        String configured = platformAccountProperties.getGovernanceOrgId();
        return configured == null || configured.isBlank() ? "demo-org" : configured.trim();
    }

    public record BillingConfigRequest(
            @NotBlank String itemType,
            @NotBlank String itemCode,
            @NotBlank String displayName,
            @NotBlank String deploymentMode,
            Boolean enabled,
            @NotBlank String billingTypePolicy,
            Integer includedCredits,
            Integer operationSeatLimit,
            Integer builderSeatLimit,
            Integer agentLimit,
            Integer skillWorkflowLimit,
            Integer knowledgeCapacityGb,
            Integer openApiQps,
            Integer openApiConcurrency,
            Integer openApiCredentialLimit,
            Integer connectorLimit,
            Integer meetingConcurrency,
            Integer traceRetentionDays,
            Integer auditRetentionDays,
            Integer environmentLimit,
            @NotBlank String overageMode,
            String slaTierCode,
            String addonCategory,
            String pricingUnit,
            String policyJson,
            @NotBlank String changeReason
    ) {
        BillingEditionConfigCommand toCommand() {
            return new BillingEditionConfigCommand(
                    itemType,
                    itemCode,
                    displayName,
                    deploymentMode,
                    enabled,
                    billingTypePolicy,
                    includedCredits,
                    operationSeatLimit,
                    builderSeatLimit,
                    agentLimit,
                    skillWorkflowLimit,
                    knowledgeCapacityGb,
                    openApiQps,
                    openApiConcurrency,
                    openApiCredentialLimit,
                    connectorLimit,
                    meetingConcurrency,
                    traceRetentionDays,
                    auditRetentionDays,
                    environmentLimit,
                    overageMode,
                    slaTierCode,
                    addonCategory,
                    pricingUnit,
                    policyJson,
                    changeReason
            );
        }
    }

    public record BillingActionRequest(@NotBlank String changeReason) {
    }

    public record BillingEnabledRequest(boolean enabled, @NotBlank String changeReason) {
    }
}
