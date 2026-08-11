package com.codehouse.ciciassistant.billing.service;

import com.codehouse.ciciassistant.billing.config.BillingModeProperties;
import com.codehouse.ciciassistant.billing.domain.BillingConfigChangeLogEntity;
import com.codehouse.ciciassistant.billing.domain.BillingConfigChangeLogRepository;
import com.codehouse.ciciassistant.billing.domain.BillingEditionEntity;
import com.codehouse.ciciassistant.billing.domain.BillingEditionRepository;
import com.codehouse.ciciassistant.billing.domain.BillingPackageEntity;
import com.codehouse.ciciassistant.billing.domain.BillingPackageRepository;
import com.codehouse.ciciassistant.auth.config.PlatformAccountProperties;
import com.codehouse.ciciassistant.common.error.ResourceNotFoundException;
import com.codehouse.ciciassistant.platform.service.PlatformAuditService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingEditionConfigurationService {

    private static final List<String> OVERAGE_MODES = List.of("auto_charge", "soft_limit", "hard_limit", "contract_only");
    private static final List<String> BILLING_TYPES = List.of("customer_paid", "platform_paid", "included", "non_billable");
    private static final List<String> PACKAGE_TYPES = List.of("capacity", "module", "service", "sla", "credits");
    private static final BigDecimal WEBSITE_STANDARD_CREDITS = new BigDecimal("8000");
    private static final BigDecimal WEBSITE_PROFESSIONAL_CREDITS = new BigDecimal("35000");
    private static final BigDecimal WEBSITE_ENTERPRISE_CREDITS = new BigDecimal("100000");

    private final BillingEditionRepository editionRepository;
    private final BillingPackageRepository packageRepository;
    private final BillingConfigChangeLogRepository changeLogRepository;
    private final BillingModeProperties billingModeProperties;
    private final PlatformAuditService platformAuditService;
    private final PlatformAccountProperties platformAccountProperties;
    private final ObjectMapper objectMapper;

    public BillingEditionConfigurationService(BillingEditionRepository editionRepository,
                                              BillingPackageRepository packageRepository,
                                              BillingConfigChangeLogRepository changeLogRepository,
                                              BillingModeProperties billingModeProperties,
                                              PlatformAuditService platformAuditService,
                                              PlatformAccountProperties platformAccountProperties,
                                              ObjectMapper objectMapper) {
        this.editionRepository = editionRepository;
        this.packageRepository = packageRepository;
        this.changeLogRepository = changeLogRepository;
        this.billingModeProperties = billingModeProperties;
        this.platformAuditService = platformAuditService;
        this.platformAccountProperties = platformAccountProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public BillingCatalogView catalog(String deploymentMode, String packageType) {
        String mode = normalizeDeploymentMode(deploymentMode);
        String normalizedPackageType = normalizePackageType(packageType);
        List<BillingEditionView> editions = (mode == null
                ? editionRepository.findAllByOrderByDeploymentModeAscSortOrderAscEditionCodeAsc()
                : editionRepository.findByDeploymentModeOrderBySortOrderAscEditionCodeAsc(mode))
                .stream()
                .map(this::toEditionView)
                .toList();
        List<BillingPackageView> packages = listPackageEntities(mode, normalizedPackageType).stream()
                .map(this::toPackageView)
                .toList();
        return new BillingCatalogView(
                billingModeProperties.toView().deploymentMode(),
                billingModeProperties.toView().label(),
                editions,
                packages,
                OVERAGE_MODES,
                BILLING_TYPES,
                PACKAGE_TYPES
        );
    }

    @Transactional
    public BillingCatalogView ensureDefaultCatalog() {
        seedEdition(defaultSaasTeam());
        seedEdition(defaultSaasBusiness());
        seedEdition(defaultSaasEnterprise());
        seedEdition(defaultSaasCustom());
        seedEdition(defaultPrivateDepartment());
        seedEdition(defaultPrivateEnterprise());
        seedEdition(defaultPrivateGroup());

        seedPackage(defaultPackage("saas", "saas_credits_topup", "credits", "SaaS Credits 加购包", 10,
                "对应官网 Pricing 的 Credits 包，¥999 / 10,000 Credits 起购，年度预购底价不低于 ¥799 / 10,000 Credits。",
                mapOf("credits", 10000, "priceLabel", "¥999 / 10,000 Credits 起", "annualFloorPriceLabel", "¥799 / 10,000 Credits 起", "billingType", "platform_paid", "officialPricingItem", "Credits 包")));
        seedPackage(defaultPackage("saas", "saas_knowledge_capacity_pack", "capacity", "知识库容量包", 11,
                "对应官网 Pricing 的知识库容量包，扩展原文、向量索引、元数据、日志和备份保留。",
                mapOf("knowledgeStorageMb", 102400, "priceLabel", "¥299 / 100 GB / 月起", "officialPricingItem", "知识库容量包")));
        retireSystemPackage("saas_document_processing_pack", "已并入 Credits 包：普通文本、OCR 和扫描件处理按内部 rate card 消耗 Credits。");
        retireSystemPackage("saas_retrieval_rcu_pack", "已并入 Credits 包：高级检索、rerank 和候选切片按内部 rate card 消耗 Credits。");
        seedPackage(defaultPackage("saas", "saas_concurrency_builder_pack", "capacity", "并发与构建扩展", 14,
                "对应官网 Pricing 的并发与构建扩展，可增加并发运行数、构建席位和团队成员上限。",
                mapOf("openApiConcurrency", 5, "builderSeats", 1, "officialPricingItem", "并发与构建扩展")));
        seedPackage(defaultPackage("saas", "saas_launch_service_pack", "service", "上线服务包", 15,
                "对应官网 Pricing 的上线服务，包含场景梳理、知识库初始化、连接器配置、技能整理、培训和验收支持。",
                mapOf("implementation", true, "training", true, "officialPricingItem", "上线服务")));
        seedPackage(defaultPackage("saas", "saas_enterprise_sla", "sla", "SaaS 企业 SLA", 20,
                "企业版可选 SLA、专属支持和安全审计响应。",
                mapOf("responseHours", 4, "support", "enterprise")));
        seedPackage(defaultPackage("private_deployment", "private_capacity_pack", "capacity", "私有化容量包", 30,
                "扩展 Agent、Skill、知识库、Open API 并发和审计保留容量。",
                mapOf("agents", 20, "knowledgeStorageMb", 102400, "auditRetentionDays", 365)));
        seedPackage(defaultPackage("private_deployment", "private_module_pack", "module", "私有化模块包", 40,
                "增购会议听记、Open API、企业连接器或高级观测模块。",
                mapOf("modules", List.of("meeting_minutes", "open_api", "advanced_observability"))));
        seedPackage(defaultPackage("private_deployment", "private_service_pack", "service", "实施运维服务包", 50,
                "部署实施、模型接入、连接器适配、培训和年度维护服务。",
                mapOf("maintenanceRate", "15%-25%", "implementation", true)));
        return catalog(null, null);
    }

    @Transactional
    public BillingEditionView updateEdition(String editionCode, EditionUpdateCommand command, String actorId, String actorRole) {
        if (command.reason() == null || command.reason().isBlank()) {
            throw new IllegalArgumentException("变更原因不能为空");
        }
        BillingEditionEntity entity = editionRepository.findByEditionCode(editionCode)
                .orElseThrow(() -> new ResourceNotFoundException("Billing edition not found: " + editionCode));
        boolean oldEnabled = entity.isEnabled();
        String oldBillingTypePolicy = entity.getBillingTypePolicy();
        applyEditionUpdate(entity, command);
        entity.setVersionNo(entity.getVersionNo() + 1);
        entity.setChangeReason(command.reason().trim());
        entity.setUpdatedBy(actorId);
        entity.setUpdatedAt(Instant.now());
        BillingEditionEntity saved = editionRepository.save(entity);
        boolean highRisk = oldEnabled && !saved.isEnabled()
                || !oldBillingTypePolicy.equals(saved.getBillingTypePolicy())
                || ("private_deployment".equals(saved.getDeploymentMode()) && "platform_paid".equals(saved.getBillingTypePolicy()));
        writeChange("edition", saved.getEditionCode(), saved.getVersionNo(), highRisk, command.reason(), actorId, actorRole,
                toEditionSnapshot(saved));
        platformAuditService.log(platformScopeId(), actorId, actorRole, "platform.billing.edition.update",
                "billing_edition", saved.getEditionCode(), command.reason().trim());
        return toEditionView(saved);
    }

    @Transactional
    public BillingPackageView updatePackage(String packageCode, PackageUpdateCommand command, String actorId, String actorRole) {
        if (command.reason() == null || command.reason().isBlank()) {
            throw new IllegalArgumentException("变更原因不能为空");
        }
        BillingPackageEntity entity = packageRepository.findByPackageCode(packageCode)
                .orElseThrow(() -> new ResourceNotFoundException("Billing package not found: " + packageCode));
        boolean oldEnabled = entity.isEnabled();
        applyPackageUpdate(entity, command);
        entity.setVersionNo(entity.getVersionNo() + 1);
        entity.setChangeReason(command.reason().trim());
        entity.setUpdatedBy(actorId);
        entity.setUpdatedAt(Instant.now());
        BillingPackageEntity saved = packageRepository.save(entity);
        writeChange("package", saved.getPackageCode(), saved.getVersionNo(), oldEnabled && !saved.isEnabled(),
                command.reason(), actorId, actorRole, toPackageSnapshot(saved));
        platformAuditService.log(platformScopeId(), actorId, actorRole, "platform.billing.package.update",
                "billing_package", saved.getPackageCode(), command.reason().trim());
        return toPackageView(saved);
    }

    @Transactional(readOnly = true)
    public List<BillingChangeLogView> history(String configType, String configCode) {
        return changeLogRepository.findTop50ByConfigTypeAndConfigCodeOrderByVersionNoDescIdDesc(configType, configCode)
                .stream()
                .map(item -> new BillingChangeLogView(
                        item.getConfigType(),
                        item.getConfigCode(),
                        item.getVersionNo(),
                        item.isHighRisk(),
                        item.getReason(),
                        item.getActorId(),
                        item.getActorRole(),
                        item.getCreatedAt().toString()))
                .toList();
    }

    private List<BillingPackageEntity> listPackageEntities(String mode, String packageType) {
        if (mode == null) {
            return packageRepository.findAllByOrderByDeploymentModeAscPackageTypeAscSortOrderAscPackageCodeAsc();
        }
        if (packageType == null) {
            return packageRepository.findByDeploymentModeOrderByPackageTypeAscSortOrderAscPackageCodeAsc(mode);
        }
        return packageRepository.findByDeploymentModeAndPackageTypeOrderBySortOrderAscPackageCodeAsc(mode, packageType);
    }

    private void seedEdition(BillingEditionEntity entity) {
        if (!editionRepository.existsByEditionCode(entity.getEditionCode())) {
            BillingEditionEntity saved = editionRepository.save(entity);
            writeChange("edition", saved.getEditionCode(), saved.getVersionNo(), false, saved.getChangeReason(), "system",
                    "SYSTEM", toEditionSnapshot(saved));
            return;
        }
        editionRepository.findByEditionCode(entity.getEditionCode())
                .filter(this::isSystemSeed)
                .ifPresent(existing -> {
                    applySystemEditionDefaults(existing, entity);
                    BillingEditionEntity saved = editionRepository.save(existing);
                    writeChange("edition", saved.getEditionCode(), saved.getVersionNo(), false, saved.getChangeReason(), "system",
                            "SYSTEM", toEditionSnapshot(saved));
                });
    }

    private void seedPackage(BillingPackageEntity entity) {
        if (!packageRepository.existsByPackageCode(entity.getPackageCode())) {
            BillingPackageEntity saved = packageRepository.save(entity);
            writeChange("package", saved.getPackageCode(), saved.getVersionNo(), false, saved.getChangeReason(), "system",
                    "SYSTEM", toPackageSnapshot(saved));
            return;
        }
        packageRepository.findByPackageCode(entity.getPackageCode())
                .filter(this::isSystemSeed)
                .ifPresent(existing -> {
                    applySystemPackageDefaults(existing, entity);
                    BillingPackageEntity saved = packageRepository.save(existing);
                    writeChange("package", saved.getPackageCode(), saved.getVersionNo(), false, saved.getChangeReason(), "system",
                            "SYSTEM", toPackageSnapshot(saved));
                });
    }

    private void retireSystemPackage(String packageCode, String description) {
        packageRepository.findByPackageCode(packageCode)
                .filter(this::isSystemSeed)
                .filter(BillingPackageEntity::isEnabled)
                .ifPresent(existing -> {
                    existing.setEnabled(false);
                    existing.setDescription(description);
                    existing.setConfigJson(writeJson(Map.of(
                            "retired", true,
                            "replacementOfficialPricingItem", "Credits 包",
                            "reason", "执行型资源统一折算为 Credits")));
                    existing.setVersionNo(existing.getVersionNo() + 1);
                    existing.setChangeReason("initial seed aligned with unified credits billing");
                    existing.setUpdatedBy("system");
                    existing.setUpdatedAt(Instant.now());
                    BillingPackageEntity saved = packageRepository.save(existing);
                    writeChange("package", saved.getPackageCode(), saved.getVersionNo(), false, saved.getChangeReason(), "system",
                            "SYSTEM", toPackageSnapshot(saved));
                });
    }

    private boolean isSystemSeed(BillingEditionEntity entity) {
        return "system".equals(entity.getUpdatedBy()) && entity.getChangeReason() != null
                && entity.getChangeReason().startsWith("initial seed");
    }

    private boolean isSystemSeed(BillingPackageEntity entity) {
        return "system".equals(entity.getUpdatedBy()) && entity.getChangeReason() != null
                && entity.getChangeReason().startsWith("initial seed");
    }

    private void applySystemEditionDefaults(BillingEditionEntity target, BillingEditionEntity defaults) {
        target.setDisplayName(defaults.getDisplayName());
        target.setDescription(defaults.getDescription());
        target.setEnabled(defaults.isEnabled());
        target.setSortOrder(defaults.getSortOrder());
        target.setOperationSeatLimit(defaults.getOperationSeatLimit());
        target.setBuilderSeatLimit(defaults.getBuilderSeatLimit());
        target.setAgentLimit(defaults.getAgentLimit());
        target.setSkillLimit(defaults.getSkillLimit());
        target.setWorkflowLimit(defaults.getWorkflowLimit());
        target.setKnowledgeBaseLimit(defaults.getKnowledgeBaseLimit());
        target.setDocumentLimit(defaults.getDocumentLimit());
        target.setChunkLimit(defaults.getChunkLimit());
        target.setKnowledgeStorageMb(defaults.getKnowledgeStorageMb());
        target.setOpenApiQps(defaults.getOpenApiQps());
        target.setOpenApiConcurrency(defaults.getOpenApiConcurrency());
        target.setOpenApiCredentialLimit(defaults.getOpenApiCredentialLimit());
        target.setConnectorLimit(defaults.getConnectorLimit());
        target.setMeetingMinutesConcurrency(defaults.getMeetingMinutesConcurrency());
        target.setTraceRetentionDays(defaults.getTraceRetentionDays());
        target.setAuditRetentionDays(defaults.getAuditRetentionDays());
        target.setEnvironmentLimit(defaults.getEnvironmentLimit());
        target.setIncludedCredits(defaults.getIncludedCredits());
        target.setOverageMode(defaults.getOverageMode());
        target.setBillingTypePolicy(defaults.getBillingTypePolicy());
        target.setSlaTierCode(defaults.getSlaTierCode());
        target.setTopUpPolicy(defaults.getTopUpPolicy());
        target.setLocalModelTokenPolicy(defaults.getLocalModelTokenPolicy());
        target.setPlatformPaidResourcePolicy(defaults.getPlatformPaidResourcePolicy());
        target.setPackageCodes(defaults.getPackageCodes());
        target.setVersionNo(target.getVersionNo() + 1);
        target.setChangeReason("initial seed aligned with official pricing");
        target.setUpdatedBy("system");
        target.setUpdatedAt(Instant.now());
    }

    private void applySystemPackageDefaults(BillingPackageEntity target, BillingPackageEntity defaults) {
        target.setDeploymentMode(defaults.getDeploymentMode());
        target.setPackageType(defaults.getPackageType());
        target.setDisplayName(defaults.getDisplayName());
        target.setDescription(defaults.getDescription());
        target.setEnabled(defaults.isEnabled());
        target.setSortOrder(defaults.getSortOrder());
        target.setConfigJson(defaults.getConfigJson());
        target.setVersionNo(target.getVersionNo() + 1);
        target.setChangeReason("initial seed aligned with official pricing");
        target.setUpdatedBy("system");
        target.setUpdatedAt(Instant.now());
    }

    private BillingEditionEntity defaultSaasTeam() {
        BillingEditionEntity entity = new BillingEditionEntity("saas", "saas_team", "标准版", 110);
        applyDefaults(entity, "对应官网标准版：首个售后或会议智能体上线，按月发放 8,000 Credits。",
                20, 1, 3, 30, 10, 1, 1000, 10000, 5120, 10, 2, 3, 3, 1, 7, 180, 1,
                WEBSITE_STANDARD_CREDITS, "soft_limit", "platform_paid", "standard", "manual_top_up",
                "SaaS 由平台承担模型与托管连接器成本，按 credits 归集。",
                "平台代付资源进入 credits 额度或加购包。", List.of("saas_credits_topup", "saas_knowledge_capacity_pack", "saas_launch_service_pack"));
        return entity;
    }

    private BillingEditionEntity defaultSaasBusiness() {
        BillingEditionEntity entity = new BillingEditionEntity("saas", "saas_business", "专业版", 120);
        applyDefaults(entity, "对应官网专业版：客服、销售、运营多部门共同使用，按月发放 35,000 Credits。",
                100, 2, 10, 100, 30, 3, 8000, 80000, 30720, 50, 10, 10, 10, 2, 30, 365, 2,
                WEBSITE_PROFESSIONAL_CREDITS, "auto_charge", "platform_paid", "business", "enabled",
                "SaaS 平台代付成本进入 Work Credits 和超额策略。",
                "平台代付模型、云端语音、第三方搜索和托管连接器可扣减 credits。", List.of("saas_credits_topup", "saas_knowledge_capacity_pack", "saas_concurrency_builder_pack", "saas_launch_service_pack"));
        return entity;
    }

    private BillingEditionEntity defaultSaasEnterprise() {
        BillingEditionEntity entity = new BillingEditionEntity("saas", "saas_enterprise", "企业版", 130);
        applyDefaults(entity, "对应官网企业版：大型公司、严格治理和大用量场景，按月 100,000 Credits 起。",
                500, 5, 50, 300, 100, 10, 25000, 250000, 102400, 100, 50, 20, 20, 5, 90, 730, 3,
                WEBSITE_ENTERPRISE_CREDITS, "contract_only", "platform_paid", "enterprise", "contract_top_up",
                "SaaS 企业合同可约定 credits、超额、平台代付资源和 SLA。",
                "按合同启用平台代付资源和超额额度。", List.of("saas_credits_topup", "saas_knowledge_capacity_pack", "saas_concurrency_builder_pack", "saas_launch_service_pack", "saas_enterprise_sla"));
        return entity;
    }

    private BillingEditionEntity defaultSaasCustom() {
        BillingEditionEntity entity = new BillingEditionEntity("saas", "saas_custom", "Custom 定制版", 140);
        applyDefaults(entity, "对应官网 Custom 定制版：超大规模、本地化部署和专属治理场景。",
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, 365, 1095, null,
                BigDecimal.ZERO, "contract_only", "platform_paid", "enterprise", "contract_top_up",
                "按合同配置平台代付、客户自有资源和本地化资源边界。",
                "专属模型、向量库、连接器、并发资源池和 SLA 按合同配置。", List.of("saas_credits_topup", "saas_knowledge_capacity_pack", "saas_concurrency_builder_pack", "saas_launch_service_pack", "saas_enterprise_sla"));
        return entity;
    }

    private BillingEditionEntity defaultPrivateDepartment() {
        BillingEditionEntity entity = new BillingEditionEntity("private_deployment", "private_department", "部门版", 210);
        applyDefaults(entity, "年费授权，单组织或少量用户，基础 Agent、知识库和连接器。",
                50, 5, 20, 60, 20, 10, 5000, 250000, 102400, 50, 20, 10, 5, 1, 90, 365, 1,
                BigDecimal.ZERO, "soft_limit", "customer_paid", "standard", "disabled",
                "客户自有本地模型 token、GPU 和推理成本由客户承担，默认不二次收费。",
                "仅平台代付模型、云端语音或托管连接器进入 credits 或实际用量。", List.of("private_capacity_pack", "private_service_pack"));
        return entity;
    }

    private BillingEditionEntity defaultPrivateEnterprise() {
        BillingEditionEntity entity = new BillingEditionEntity("private_deployment", "private_enterprise", "企业版", 220);
        applyDefaults(entity, "年费授权，多组织、Open API、观测和容量包。",
                300, 30, 120, 300, 100, 50, 50000, 2500000, 1024000, 200, 100, 50, 20, 4, 180, 730, 3,
                BigDecimal.ZERO, "contract_only", "customer_paid", "business", "disabled",
                "本地模型 token 只做治理、归因和预算口径，不作为默认收费项。",
                "平台代付资源需单独配置 billing_type=platform_paid。", List.of("private_capacity_pack", "private_module_pack", "private_service_pack"));
        return entity;
    }

    private BillingEditionEntity defaultPrivateGroup() {
        BillingEditionEntity entity = new BillingEditionEntity("private_deployment", "private_group", "集团版", 230);
        applyDefaults(entity, "多实例、生产/测试/灾备环境、SSO/SLA 和高级审计。",
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, 365, 1095, null,
                BigDecimal.ZERO, "contract_only", "customer_paid", "enterprise", "disabled",
                "集团版默认不对客户自有本地模型 token 二次收费，按合同治理超大规模额度。",
                "平台代付资源和托管连接器按合同配置。", List.of("private_capacity_pack", "private_module_pack", "private_service_pack"));
        return entity;
    }

    private void applyDefaults(BillingEditionEntity entity,
                               String description,
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
                               String overageMode,
                               String billingTypePolicy,
                               String slaTierCode,
                               String topUpPolicy,
                               String localModelTokenPolicy,
                               String platformPaidResourcePolicy,
                               List<String> packageCodes) {
        entity.setDescription(description);
        entity.setOperationSeatLimit(operationSeatLimit);
        entity.setBuilderSeatLimit(builderSeatLimit);
        entity.setAgentLimit(agentLimit);
        entity.setSkillLimit(skillLimit);
        entity.setWorkflowLimit(workflowLimit);
        entity.setKnowledgeBaseLimit(knowledgeBaseLimit);
        entity.setDocumentLimit(documentLimit);
        entity.setChunkLimit(chunkLimit);
        entity.setKnowledgeStorageMb(knowledgeStorageMb);
        entity.setOpenApiQps(openApiQps);
        entity.setOpenApiConcurrency(openApiConcurrency);
        entity.setOpenApiCredentialLimit(openApiCredentialLimit);
        entity.setConnectorLimit(connectorLimit);
        entity.setMeetingMinutesConcurrency(meetingMinutesConcurrency);
        entity.setTraceRetentionDays(traceRetentionDays);
        entity.setAuditRetentionDays(auditRetentionDays);
        entity.setEnvironmentLimit(environmentLimit);
        entity.setIncludedCredits(includedCredits);
        entity.setOverageMode(overageMode);
        entity.setBillingTypePolicy(billingTypePolicy);
        entity.setSlaTierCode(slaTierCode);
        entity.setTopUpPolicy(topUpPolicy);
        entity.setLocalModelTokenPolicy(localModelTokenPolicy);
        entity.setPlatformPaidResourcePolicy(platformPaidResourcePolicy);
        entity.setPackageCodes(writeJson(packageCodes));
    }

    private BillingPackageEntity defaultPackage(String deploymentMode,
                                                String packageCode,
                                                String packageType,
                                                String displayName,
                                                int sortOrder,
                                                String description,
                                                Map<String, Object> config) {
        BillingPackageEntity entity = new BillingPackageEntity(deploymentMode, packageCode, packageType, displayName, sortOrder);
        entity.setDescription(description);
        entity.setConfigJson(writeJson(config));
        return entity;
    }

    private void applyEditionUpdate(BillingEditionEntity entity, EditionUpdateCommand command) {
        entity.setDisplayName(required(command.displayName(), "displayName"));
        entity.setDescription(blankToEmpty(command.description()));
        entity.setEnabled(Boolean.TRUE.equals(command.enabled()));
        entity.setOperationSeatLimit(nonNegative(command.operationSeatLimit(), "operationSeatLimit"));
        entity.setBuilderSeatLimit(nonNegative(command.builderSeatLimit(), "builderSeatLimit"));
        entity.setAgentLimit(nonNegative(command.agentLimit(), "agentLimit"));
        entity.setSkillLimit(nonNegative(command.skillLimit(), "skillLimit"));
        entity.setWorkflowLimit(nonNegative(command.workflowLimit(), "workflowLimit"));
        entity.setKnowledgeBaseLimit(nonNegative(command.knowledgeBaseLimit(), "knowledgeBaseLimit"));
        entity.setDocumentLimit(nonNegative(command.documentLimit(), "documentLimit"));
        entity.setChunkLimit(nonNegative(command.chunkLimit(), "chunkLimit"));
        entity.setKnowledgeStorageMb(nonNegative(command.knowledgeStorageMb(), "knowledgeStorageMb"));
        entity.setOpenApiQps(nonNegative(command.openApiQps(), "openApiQps"));
        entity.setOpenApiConcurrency(nonNegative(command.openApiConcurrency(), "openApiConcurrency"));
        entity.setOpenApiCredentialLimit(nonNegative(command.openApiCredentialLimit(), "openApiCredentialLimit"));
        entity.setConnectorLimit(nonNegative(command.connectorLimit(), "connectorLimit"));
        entity.setMeetingMinutesConcurrency(nonNegative(command.meetingMinutesConcurrency(), "meetingMinutesConcurrency"));
        entity.setTraceRetentionDays(nonNegative(command.traceRetentionDays(), "traceRetentionDays"));
        entity.setAuditRetentionDays(nonNegative(command.auditRetentionDays(), "auditRetentionDays"));
        entity.setEnvironmentLimit(nonNegative(command.environmentLimit(), "environmentLimit"));
        entity.setIncludedCredits(command.includedCredits() == null ? BigDecimal.ZERO : command.includedCredits().max(BigDecimal.ZERO));
        entity.setOverageMode(allowed(command.overageMode(), OVERAGE_MODES, "overageMode"));
        entity.setBillingTypePolicy(allowed(command.billingTypePolicy(), BILLING_TYPES, "billingTypePolicy"));
        entity.setSlaTierCode(required(command.slaTierCode(), "slaTierCode"));
        entity.setTopUpPolicy(required(command.topUpPolicy(), "topUpPolicy"));
        entity.setLocalModelTokenPolicy(required(command.localModelTokenPolicy(), "localModelTokenPolicy"));
        entity.setPlatformPaidResourcePolicy(blankToEmpty(command.platformPaidResourcePolicy()));
        entity.setPackageCodes(writeJson(command.packageCodes() == null ? List.of() : command.packageCodes()));
    }

    private void applyPackageUpdate(BillingPackageEntity entity, PackageUpdateCommand command) {
        entity.setDisplayName(required(command.displayName(), "displayName"));
        entity.setDescription(blankToEmpty(command.description()));
        entity.setEnabled(Boolean.TRUE.equals(command.enabled()));
        entity.setPackageType(allowed(command.packageType(), PACKAGE_TYPES, "packageType"));
        entity.setConfigJson(validJsonObject(command.configJson()));
    }

    private BillingEditionView toEditionView(BillingEditionEntity entity) {
        return new BillingEditionView(
                entity.getEditionCode(),
                entity.getDeploymentMode(),
                entity.getDisplayName(),
                entity.getDescription(),
                entity.isEnabled(),
                entity.getSortOrder(),
                entity.getOperationSeatLimit(),
                entity.getBuilderSeatLimit(),
                entity.getAgentLimit(),
                entity.getSkillLimit(),
                entity.getWorkflowLimit(),
                entity.getKnowledgeBaseLimit(),
                entity.getDocumentLimit(),
                entity.getChunkLimit(),
                entity.getKnowledgeStorageMb(),
                entity.getOpenApiQps(),
                entity.getOpenApiConcurrency(),
                entity.getOpenApiCredentialLimit(),
                entity.getConnectorLimit(),
                entity.getMeetingMinutesConcurrency(),
                entity.getTraceRetentionDays(),
                entity.getAuditRetentionDays(),
                entity.getEnvironmentLimit(),
                entity.getIncludedCredits(),
                entity.getOverageMode(),
                entity.getBillingTypePolicy(),
                entity.getSlaTierCode(),
                entity.getTopUpPolicy(),
                entity.getLocalModelTokenPolicy(),
                entity.getPlatformPaidResourcePolicy(),
                readStringList(entity.getPackageCodes()),
                entity.getVersionNo(),
                entity.getChangeReason(),
                entity.getUpdatedBy(),
                entity.getUpdatedAt().toString()
        );
    }

    private BillingPackageView toPackageView(BillingPackageEntity entity) {
        return new BillingPackageView(
                entity.getPackageCode(),
                entity.getDeploymentMode(),
                entity.getPackageType(),
                entity.getDisplayName(),
                entity.getDescription(),
                entity.isEnabled(),
                entity.getSortOrder(),
                entity.getConfigJson(),
                entity.getVersionNo(),
                entity.getChangeReason(),
                entity.getUpdatedBy(),
                entity.getUpdatedAt().toString()
        );
    }

    private Map<String, Object> toEditionSnapshot(BillingEditionEntity entity) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("editionCode", entity.getEditionCode());
        snapshot.put("deploymentMode", entity.getDeploymentMode());
        snapshot.put("displayName", entity.getDisplayName());
        snapshot.put("enabled", entity.isEnabled());
        snapshot.put("includedCredits", entity.getIncludedCredits());
        snapshot.put("overageMode", entity.getOverageMode());
        snapshot.put("billingTypePolicy", entity.getBillingTypePolicy());
        snapshot.put("slaTierCode", entity.getSlaTierCode());
        snapshot.put("packageCodes", readStringList(entity.getPackageCodes()));
        snapshot.put("versionNo", entity.getVersionNo());
        return snapshot;
    }

    private Map<String, Object> toPackageSnapshot(BillingPackageEntity entity) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("packageCode", entity.getPackageCode());
        snapshot.put("deploymentMode", entity.getDeploymentMode());
        snapshot.put("packageType", entity.getPackageType());
        snapshot.put("displayName", entity.getDisplayName());
        snapshot.put("enabled", entity.isEnabled());
        snapshot.put("config", readMap(entity.getConfigJson()));
        snapshot.put("versionNo", entity.getVersionNo());
        return snapshot;
    }

    private void writeChange(String type,
                             String code,
                             int versionNo,
                             boolean highRisk,
                             String reason,
                             String actorId,
                             String actorRole,
                             Map<String, Object> snapshot) {
        changeLogRepository.save(new BillingConfigChangeLogEntity(
                type,
                code,
                versionNo,
                highRisk,
                reason.trim(),
                actorId,
                actorRole,
                writeJson(snapshot)));
    }

    private String normalizeDeploymentMode(String raw) {
        if (raw == null || raw.isBlank() || "all".equalsIgnoreCase(raw)) {
            return null;
        }
        return BillingModeProperties.DeploymentMode.from(raw).code();
    }

    private String platformScopeId() {
        String configured = platformAccountProperties.getGovernanceCompanyId();
        return configured == null || configured.isBlank()
                ? PlatformAccountProperties.LEGACY_DEFAULT_GOVERNANCE_COMPANY_ID
                : configured.trim();
    }

    private String normalizePackageType(String raw) {
        if (raw == null || raw.isBlank() || "all".equalsIgnoreCase(raw)) {
            return null;
        }
        return allowed(raw, PACKAGE_TYPES, "packageType");
    }

    private String allowed(String raw, List<String> allowed, String field) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }

    private Integer nonNegative(Integer value, String field) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(field + " must be >= 0");
        }
        return value;
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value.trim();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String validJsonObject(String raw) {
        String json = raw == null || raw.isBlank() ? "{}" : raw.trim();
        Map<String, Object> parsed = readMap(json);
        return writeJson(parsed);
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("configJson must be a JSON object");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to serialize billing configuration");
        }
    }

    private Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return result;
    }

    public record BillingCatalogView(
            String currentDeploymentMode,
            String currentDeploymentModeLabel,
            List<BillingEditionView> editions,
            List<BillingPackageView> packages,
            List<String> overageModes,
            List<String> billingTypes,
            List<String> packageTypes
    ) {
    }

    public record BillingEditionView(
            String editionCode,
            String deploymentMode,
            String displayName,
            String description,
            boolean enabled,
            int sortOrder,
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
            String overageMode,
            String billingTypePolicy,
            String slaTierCode,
            String topUpPolicy,
            String localModelTokenPolicy,
            String platformPaidResourcePolicy,
            List<String> packageCodes,
            int versionNo,
            String changeReason,
            String updatedBy,
            String updatedAt
    ) {
    }

    public record BillingPackageView(
            String packageCode,
            String deploymentMode,
            String packageType,
            String displayName,
            String description,
            boolean enabled,
            int sortOrder,
            String configJson,
            int versionNo,
            String changeReason,
            String updatedBy,
            String updatedAt
    ) {
    }

    public record BillingChangeLogView(
            String configType,
            String configCode,
            int versionNo,
            boolean highRisk,
            String reason,
            String actorId,
            String actorRole,
            String createdAt
    ) {
    }

    public record EditionUpdateCommand(
            String displayName,
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
            String overageMode,
            String billingTypePolicy,
            String slaTierCode,
            String topUpPolicy,
            String localModelTokenPolicy,
            String platformPaidResourcePolicy,
            List<String> packageCodes,
            String reason
    ) {
    }

    public record PackageUpdateCommand(
            String displayName,
            String description,
            Boolean enabled,
            String packageType,
            String configJson,
            String reason
    ) {
    }
}
