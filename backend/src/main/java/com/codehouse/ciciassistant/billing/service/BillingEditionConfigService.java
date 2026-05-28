package com.codehouse.ciciassistant.billing.service;

import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.billing.domain.BillingEditionConfigEntity;
import com.codehouse.ciciassistant.billing.domain.BillingEditionConfigRepository;
import com.codehouse.ciciassistant.platform.service.PlatformAuditService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingEditionConfigService {

    private static final Set<String> ITEM_TYPES = Set.of(
            "PLAN",
            "CAPACITY_PACK",
            "MODULE_PACK",
            "SERVICE_PACK",
            "SLA_TIER",
            "CREDITS_POLICY"
    );
    private static final Set<String> DEPLOYMENT_MODES = Set.of("saas", "private_deployment", "all");
    private static final Set<String> BILLING_TYPE_POLICIES = Set.of("platform_paid", "customer_paid", "included", "non_billable");
    private static final Set<String> OVERAGE_MODES = Set.of("auto_charge", "soft_limit", "hard_limit");

    private final BillingEditionConfigRepository repository;
    private final PlatformAuditService platformAuditService;

    public BillingEditionConfigService(BillingEditionConfigRepository repository,
                                       PlatformAuditService platformAuditService) {
        this.repository = repository;
        this.platformAuditService = platformAuditService;
    }

    @Transactional
    public List<BillingEditionConfigView> list(String orgId) {
        ensureDefaultAssets(orgId);
        List<BillingEditionConfigEntity> rows = repository.findByOrgIdOrderByItemTypeAscItemCodeAscVersionNoDesc(orgId);
        Map<String, List<BillingEditionConfigEntity>> versionsByKey = rows.stream()
                .collect(Collectors.groupingBy(
                        item -> item.getItemType() + ":" + item.getItemCode(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        return rows.stream()
                .map(item -> toView(item, versionsByKey.get(item.getItemType() + ":" + item.getItemCode())))
                .toList();
    }

    @Transactional
    public BillingEditionConfigView createDraft(String orgId, BillingEditionConfigCommand command) {
        ensureDefaultAssets(orgId);
        String itemType = normalizeItemType(command.itemType());
        String itemCode = requireCode(command.itemCode(), "itemCode");
        String reason = requireReason(command.changeReason());
        List<BillingEditionConfigEntity> versions = repository.findByOrgIdAndItemTypeAndItemCodeOrderByVersionNoDesc(
                orgId,
                itemType,
                itemCode
        );
        int nextVersionNo = versions.stream()
                .map(BillingEditionConfigEntity::getVersionNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .map(item -> item + 1)
                .orElse(1);
        BillingEditionConfigEntity draft = new BillingEditionConfigEntity(
                orgId,
                itemType,
                itemCode,
                requireText(command.displayName(), "displayName"),
                normalizeDeploymentMode(command.deploymentMode()),
                nextVersionNo,
                "DRAFT",
                command.enabled() == null || command.enabled(),
                normalizeBillingTypePolicy(command.billingTypePolicy()),
                nonNegative(command.includedCredits(), "includedCredits"),
                nonNegative(command.operationSeatLimit(), "operationSeatLimit"),
                nonNegative(command.builderSeatLimit(), "builderSeatLimit"),
                nonNegative(command.agentLimit(), "agentLimit"),
                nonNegative(command.skillWorkflowLimit(), "skillWorkflowLimit"),
                nonNegative(command.knowledgeCapacityGb(), "knowledgeCapacityGb"),
                nonNegative(command.openApiQps(), "openApiQps"),
                nonNegative(command.openApiConcurrency(), "openApiConcurrency"),
                nonNegative(command.openApiCredentialLimit(), "openApiCredentialLimit"),
                nonNegative(command.connectorLimit(), "connectorLimit"),
                nonNegative(command.meetingConcurrency(), "meetingConcurrency"),
                nonNegative(command.traceRetentionDays(), "traceRetentionDays"),
                nonNegative(command.auditRetentionDays(), "auditRetentionDays"),
                nonNegative(command.environmentLimit(), "environmentLimit"),
                normalizeOverageMode(command.overageMode()),
                trimToNull(command.slaTierCode()),
                trimToNull(command.addonCategory()),
                trimToNull(command.pricingUnit()),
                trimToNull(command.policyJson()),
                reason,
                currentActorId(),
                null
        );
        BillingEditionConfigEntity saved = repository.save(draft);
        logAudit(orgId,
                "platform.billing.version.create",
                saved.getItemType() + ":" + saved.getItemCode() + "@v" + saved.getVersionNo(),
                reason);
        return toView(saved, repository.findByOrgIdAndItemTypeAndItemCodeOrderByVersionNoDesc(orgId, itemType, itemCode));
    }

    @Transactional
    public BillingEditionConfigView updateAsDraft(String orgId, Long id, BillingEditionConfigCommand command) {
        ensureDefaultAssets(orgId);
        BillingEditionConfigEntity base = requireConfig(orgId, id);
        String reason = requireReason(command.changeReason());
        BillingEditionConfigEntity draft = "DRAFT".equalsIgnoreCase(base.getPublishStatus())
                ? base
                : base.nextDraft(nextVersionNo(orgId, base), reason, currentActorId());
        draft.replaceConfig(
                requireText(command.displayName(), "displayName"),
                normalizeDeploymentMode(command.deploymentMode()),
                command.enabled() == null || command.enabled(),
                normalizeBillingTypePolicy(command.billingTypePolicy()),
                nonNegative(command.includedCredits(), "includedCredits"),
                nonNegative(command.operationSeatLimit(), "operationSeatLimit"),
                nonNegative(command.builderSeatLimit(), "builderSeatLimit"),
                nonNegative(command.agentLimit(), "agentLimit"),
                nonNegative(command.skillWorkflowLimit(), "skillWorkflowLimit"),
                nonNegative(command.knowledgeCapacityGb(), "knowledgeCapacityGb"),
                nonNegative(command.openApiQps(), "openApiQps"),
                nonNegative(command.openApiConcurrency(), "openApiConcurrency"),
                nonNegative(command.openApiCredentialLimit(), "openApiCredentialLimit"),
                nonNegative(command.connectorLimit(), "connectorLimit"),
                nonNegative(command.meetingConcurrency(), "meetingConcurrency"),
                nonNegative(command.traceRetentionDays(), "traceRetentionDays"),
                nonNegative(command.auditRetentionDays(), "auditRetentionDays"),
                nonNegative(command.environmentLimit(), "environmentLimit"),
                normalizeOverageMode(command.overageMode()),
                trimToNull(command.slaTierCode()),
                trimToNull(command.addonCategory()),
                trimToNull(command.pricingUnit()),
                trimToNull(command.policyJson()),
                reason
        );
        BillingEditionConfigEntity saved = repository.save(draft);
        logAudit(orgId,
                "platform.billing.version.update",
                saved.getItemType() + ":" + saved.getItemCode() + "@v" + saved.getVersionNo(),
                reason);
        return toView(saved, repository.findByOrgIdAndItemTypeAndItemCodeOrderByVersionNoDesc(
                orgId,
                saved.getItemType(),
                saved.getItemCode()
        ));
    }

    @Transactional
    public BillingEditionConfigView publish(String orgId, Long id, String changeReason) {
        ensureDefaultAssets(orgId);
        BillingEditionConfigEntity target = requireConfig(orgId, id);
        String reason = requireReason(changeReason);
        for (BillingEditionConfigEntity item : repository.findByOrgIdAndItemTypeAndItemCodeOrderByVersionNoDesc(
                orgId,
                target.getItemType(),
                target.getItemCode()
        )) {
            if (Objects.equals(item.getId(), target.getId())) {
                item.markPublished();
                item.setEnabled(target.isEnabled(), reason);
            } else if ("PUBLISHED".equalsIgnoreCase(item.getPublishStatus())) {
                item.markSuperseded();
            }
        }
        logAudit(orgId,
                "platform.billing.version.publish",
                target.getItemType() + ":" + target.getItemCode() + "@v" + target.getVersionNo(),
                reason);
        return toView(target, repository.findByOrgIdAndItemTypeAndItemCodeOrderByVersionNoDesc(
                orgId,
                target.getItemType(),
                target.getItemCode()
        ));
    }

    @Transactional
    public BillingEditionConfigView setEnabled(String orgId, Long id, boolean enabled, String changeReason) {
        ensureDefaultAssets(orgId);
        BillingEditionConfigEntity target = requireConfig(orgId, id);
        String reason = requireReason(changeReason);
        target.setEnabled(enabled, reason);
        BillingEditionConfigEntity saved = repository.save(target);
        logAudit(orgId,
                enabled ? "platform.billing.enable" : "platform.billing.disable",
                saved.getItemType() + ":" + saved.getItemCode() + "@v" + saved.getVersionNo(),
                reason);
        return toView(saved, repository.findByOrgIdAndItemTypeAndItemCodeOrderByVersionNoDesc(
                orgId,
                saved.getItemType(),
                saved.getItemCode()
        ));
    }

    private void ensureDefaultAssets(String orgId) {
        if (!repository.findByOrgIdOrderByItemTypeAscItemCodeAscVersionNoDesc(orgId).isEmpty()) {
            return;
        }
        Instant publishedAt = Instant.now();
        for (BillingEditionConfigEntity seed : List.of(
                seed(orgId, "PLAN", "saas_team", "团队版", "saas", "platform_paid", 20000, 10, 3, 8, 30, 20, 20, 5, 3, 8, 1, 30, 180, 1, "soft_limit", "standard", null, "org_month", "{\"creditsPolicy\":\"included_then_top_up\"}", "seed SaaS team edition", publishedAt),
                seed(orgId, "PLAN", "saas_business", "商业版", "saas", "platform_paid", 100000, 80, 15, 40, 160, 200, 100, 25, 12, 40, 4, 90, 365, 2, "auto_charge", "business", null, "org_month", "{\"creditsPolicy\":\"included_top_up_and_contract_overage\"}", "seed SaaS business edition", publishedAt),
                seed(orgId, "PLAN", "saas_enterprise", "企业版", "saas", "platform_paid", 500000, null, null, null, null, null, 500, 100, 50, null, 12, 365, 1095, 4, "auto_charge", "enterprise", null, "org_year", "{\"creditsPolicy\":\"contract_allowance\",\"sso\":true}", "seed SaaS enterprise edition", publishedAt),
                seed(orgId, "PLAN", "private_department", "部门版", "private_deployment", "customer_paid", 0, 30, 8, 20, 80, 100, 50, 15, 6, 20, 2, 90, 365, 1, "soft_limit", "standard", null, "license_year", "{\"localModelTokenDoubleCharge\":false}", "seed private department edition", publishedAt),
                seed(orgId, "PLAN", "private_enterprise", "企业版", "private_deployment", "customer_paid", 0, 200, 50, 120, 500, 1000, 200, 50, 20, 100, 8, 180, 1095, 3, "soft_limit", "business", null, "license_year", "{\"localModelTokenDoubleCharge\":false}", "seed private enterprise edition", publishedAt),
                seed(orgId, "PLAN", "private_group", "集团版", "private_deployment", "customer_paid", 0, null, null, null, null, null, 500, 120, 50, null, 20, 365, 1825, 8, "auto_charge", "enterprise", null, "license_year", "{\"localModelTokenDoubleCharge\":false,\"multiInstance\":true}", "seed private group edition", publishedAt),
                seed(orgId, "CAPACITY_PACK", "capacity_agents_50", "智能体容量包 50", "all", "included", 0, null, null, 50, null, null, null, null, null, null, null, null, null, null, "soft_limit", null, "agent_capacity", "pack_year", "{\"increment\":{\"agentLimit\":50}}", "seed capacity pack", publishedAt),
                seed(orgId, "MODULE_PACK", "module_open_api", "Open API 生产模块包", "all", "platform_paid", 0, null, null, null, null, null, 100, 20, 10, null, null, 90, 365, null, "auto_charge", null, "open_api", "module_year", "{\"features\":[\"open_api_production_access\"]}", "seed module pack", publishedAt),
                seed(orgId, "SERVICE_PACK", "service_implementation", "实施运维服务包", "private_deployment", "non_billable", 0, null, null, null, null, null, null, null, null, null, null, null, null, null, "soft_limit", "business", "implementation", "service_project", "{\"serviceItems\":[\"implementation\",\"training\"]}", "seed service pack", publishedAt),
                seed(orgId, "SLA_TIER", "sla_enterprise", "企业 SLA", "all", "non_billable", 0, null, null, null, null, null, null, null, null, null, null, 365, 1095, null, "soft_limit", "enterprise", "sla", "tier_year", "{\"responseHours\":{\"p1\":4,\"p2\":8}}", "seed SLA tier", publishedAt),
                seed(orgId, "CREDITS_POLICY", "credits_saas_default", "SaaS Credits 策略", "saas", "platform_paid", 0, null, null, null, null, null, null, null, null, null, null, null, null, null, "auto_charge", null, "credits", "policy", "{\"topUpEnabled\":true,\"platformPaidResources\":true}", "seed SaaS credits policy", publishedAt),
                seed(orgId, "CREDITS_POLICY", "credits_private_default", "私有化 Credits 策略", "private_deployment", "customer_paid", 0, null, null, null, null, null, null, null, null, null, null, null, null, null, "soft_limit", null, "credits", "policy", "{\"topUpEnabled\":false,\"localModelTokenDoubleCharge\":false}", "seed private credits policy", publishedAt)
        )) {
            repository.save(seed);
        }
    }

    private BillingEditionConfigEntity seed(String orgId,
                                            String itemType,
                                            String itemCode,
                                            String displayName,
                                            String deploymentMode,
                                            String billingTypePolicy,
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
                                            String overageMode,
                                            String slaTierCode,
                                            String addonCategory,
                                            String pricingUnit,
                                            String policyJson,
                                            String changeReason,
                                            Instant publishedAt) {
        return new BillingEditionConfigEntity(
                orgId,
                itemType,
                itemCode,
                displayName,
                deploymentMode,
                1,
                "PUBLISHED",
                true,
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
                changeReason,
                "platform-system",
                publishedAt
        );
    }

    private int nextVersionNo(String orgId, BillingEditionConfigEntity base) {
        return repository.findByOrgIdAndItemTypeAndItemCodeOrderByVersionNoDesc(orgId, base.getItemType(), base.getItemCode())
                .stream()
                .map(BillingEditionConfigEntity::getVersionNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .map(item -> item + 1)
                .orElse(1);
    }

    private BillingEditionConfigEntity requireConfig(String orgId, Long id) {
        return repository.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Billing configuration item not found"));
    }

    private BillingEditionConfigView toView(BillingEditionConfigEntity item, List<BillingEditionConfigEntity> versions) {
        List<BillingEditionConfigEntity> safeVersions = versions == null ? List.of() : versions;
        Integer latestVersionNo = safeVersions.stream()
                .map(BillingEditionConfigEntity::getVersionNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(item.getVersionNo());
        Integer publishedVersionNo = safeVersions.stream()
                .filter(row -> "PUBLISHED".equalsIgnoreCase(row.getPublishStatus()))
                .map(BillingEditionConfigEntity::getVersionNo)
                .findFirst()
                .orElse(null);
        return new BillingEditionConfigView(
                item.getId(),
                item.getItemType(),
                item.getItemCode(),
                item.getDisplayName(),
                item.getDeploymentMode(),
                item.getVersionNo(),
                item.getPublishStatus(),
                item.isEnabled(),
                item.getBillingTypePolicy(),
                item.getIncludedCredits(),
                item.getOperationSeatLimit(),
                item.getBuilderSeatLimit(),
                item.getAgentLimit(),
                item.getSkillWorkflowLimit(),
                item.getKnowledgeCapacityGb(),
                item.getOpenApiQps(),
                item.getOpenApiConcurrency(),
                item.getOpenApiCredentialLimit(),
                item.getConnectorLimit(),
                item.getMeetingConcurrency(),
                item.getTraceRetentionDays(),
                item.getAuditRetentionDays(),
                item.getEnvironmentLimit(),
                item.getOverageMode(),
                item.getSlaTierCode(),
                item.getAddonCategory(),
                item.getPricingUnit(),
                item.getPolicyJson(),
                item.getChangeReason(),
                item.getCreatedBy(),
                item.getCreatedAt().toString(),
                item.getUpdatedAt().toString(),
                item.getPublishedAt() == null ? null : item.getPublishedAt().toString(),
                latestVersionNo,
                publishedVersionNo,
                safeVersions.size()
        );
    }

    private String normalizeItemType(String itemType) {
        String normalized = requireText(itemType, "itemType").trim().toUpperCase();
        if (!ITEM_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported itemType: " + itemType);
        }
        return normalized;
    }

    private String normalizeDeploymentMode(String deploymentMode) {
        String normalized = requireText(deploymentMode, "deploymentMode").trim().toLowerCase();
        if (!DEPLOYMENT_MODES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported deploymentMode: " + deploymentMode);
        }
        return normalized;
    }

    private String normalizeBillingTypePolicy(String billingTypePolicy) {
        String normalized = requireText(billingTypePolicy, "billingTypePolicy").trim().toLowerCase();
        if (!BILLING_TYPE_POLICIES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported billingTypePolicy: " + billingTypePolicy);
        }
        return normalized;
    }

    private String normalizeOverageMode(String overageMode) {
        String normalized = requireText(overageMode, "overageMode").trim().toLowerCase();
        if (!OVERAGE_MODES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported overageMode: " + overageMode);
        }
        return normalized;
    }

    private Integer nonNegative(Integer value, String field) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
        return value;
    }

    private String requireReason(String value) {
        String cleaned = requireText(value, "changeReason");
        if (cleaned.length() < 5) {
            throw new IllegalArgumentException("changeReason must explain the billing configuration change");
        }
        return cleaned;
    }

    private String requireCode(String value, String field) {
        String cleaned = requireText(value, field).trim();
        if (!cleaned.matches("[a-z0-9_\\-]{2,64}")) {
            throw new IllegalArgumentException(field + " must use lowercase code characters");
        }
        return cleaned;
    }

    private String requireText(String value, String field) {
        String cleaned = trimToNull(value);
        if (cleaned == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return cleaned;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String currentActorId() {
        return TenantContext.getUserId().orElse("platform-system");
    }

    private String currentPlatformRole() {
        return TenantContext.getRoles().stream()
                .filter(RoleCodes::isPlatformRole)
                .findFirst()
                .orElse(RoleCodes.PLATFORM_ADMIN);
    }

    private void logAudit(String orgId, String eventType, String resourceKey, String detail) {
        platformAuditService.log(
                orgId,
                currentActorId(),
                currentPlatformRole(),
                eventType,
                "BILLING_EDITION_CONFIG",
                resourceKey,
                detail
        );
    }

    public record BillingEditionConfigCommand(
            String itemType,
            String itemCode,
            String displayName,
            String deploymentMode,
            Boolean enabled,
            String billingTypePolicy,
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
            String overageMode,
            String slaTierCode,
            String addonCategory,
            String pricingUnit,
            String policyJson,
            String changeReason
    ) {
    }

    public record BillingEditionConfigView(
            Long id,
            String itemType,
            String itemCode,
            String displayName,
            String deploymentMode,
            Integer versionNo,
            String publishStatus,
            boolean enabled,
            String billingTypePolicy,
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
            String overageMode,
            String slaTierCode,
            String addonCategory,
            String pricingUnit,
            String policyJson,
            String changeReason,
            String createdBy,
            String createdAt,
            String updatedAt,
            String publishedAt,
            Integer latestVersionNo,
            Integer publishedVersionNo,
            Integer versionCount
    ) {
    }
}
