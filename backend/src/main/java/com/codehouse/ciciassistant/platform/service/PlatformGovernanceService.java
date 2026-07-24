package com.codehouse.ciciassistant.platform.service;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowSkillRefEntity;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowSkillRefRepository;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionRepository;
import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.platform.domain.PlatformPolicyBundleEntity;
import com.codehouse.ciciassistant.platform.domain.PlatformPolicyBundleRepository;
import com.codehouse.ciciassistant.platform.domain.PlatformSkillTemplateEntity;
import com.codehouse.ciciassistant.platform.domain.PlatformSkillTemplateRepository;
import com.codehouse.ciciassistant.platform.domain.PlatformSkillTemplateVersionEntity;
import com.codehouse.ciciassistant.platform.domain.PlatformSkillTemplateVersionRepository;
import com.codehouse.ciciassistant.platform.domain.PlatformToolDefinitionEntity;
import com.codehouse.ciciassistant.platform.domain.PlatformToolDefinitionRepository;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingEntity;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingRepository;
import com.codehouse.ciciassistant.skill.domain.SkillBindingPolicy;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionRepository;
import com.codehouse.ciciassistant.skill.domain.SkillSourceType;
import com.codehouse.ciciassistant.skill.domain.SkillUpdatePolicy;
import com.codehouse.ciciassistant.skill.domain.SkillVersionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillVersionRepository;
import com.codehouse.ciciassistant.skill.domain.SkillVisibility;
import com.codehouse.ciciassistant.skill.service.SkillDefinitionService;
import com.codehouse.ciciassistant.spec.SpecCompilerService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.codehouse.ciciassistant.tool.service.BuiltinToolCatalog;
import com.codehouse.ciciassistant.tool.service.BuiltinToolCatalog.ToolCatalogItem;
import com.codehouse.ciciassistant.tool.service.ToolNameNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformGovernanceService {

    private static final String CORE_POLICY_BUNDLE_CODE = "core-default";
    private static final List<String> CORE_POLICY_SKILL_CODES = List.of(
            "conversation-core",
            "knowledge-first",
            "safe-handoff"
    );

    private final SkillDefinitionService skillDefinitionService;
    private final SkillDefinitionRepository skillDefinitionRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final AgentSkillBindingRepository agentSkillBindingRepository;
    private final AgentDefinitionRepository agentDefinitionRepository;
    private final AgentWorkflowVersionRepository agentWorkflowVersionRepository;
    private final AgentWorkflowSkillRefRepository agentWorkflowSkillRefRepository;
    private final PlatformPolicyBundleRepository platformPolicyBundleRepository;
    private final PlatformSkillTemplateRepository platformSkillTemplateRepository;
    private final PlatformSkillTemplateVersionRepository platformSkillTemplateVersionRepository;
    private final PlatformToolDefinitionRepository platformToolDefinitionRepository;
    private final PlatformAuditService platformAuditService;
    private final SpecCompilerService specCompilerService;
    private final ObjectMapper objectMapper;

    public PlatformGovernanceService(SkillDefinitionService skillDefinitionService,
                                     SkillDefinitionRepository skillDefinitionRepository,
                                     SkillVersionRepository skillVersionRepository,
                                     AgentSkillBindingRepository agentSkillBindingRepository,
                                     AgentDefinitionRepository agentDefinitionRepository,
                                     AgentWorkflowVersionRepository agentWorkflowVersionRepository,
                                     AgentWorkflowSkillRefRepository agentWorkflowSkillRefRepository,
                                     PlatformPolicyBundleRepository platformPolicyBundleRepository,
                                     PlatformSkillTemplateRepository platformSkillTemplateRepository,
                                     PlatformSkillTemplateVersionRepository platformSkillTemplateVersionRepository,
                                     PlatformToolDefinitionRepository platformToolDefinitionRepository,
                                     PlatformAuditService platformAuditService,
                                     SpecCompilerService specCompilerService,
                                     ObjectMapper objectMapper) {
        this.skillDefinitionService = skillDefinitionService;
        this.skillDefinitionRepository = skillDefinitionRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.agentSkillBindingRepository = agentSkillBindingRepository;
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.agentWorkflowVersionRepository = agentWorkflowVersionRepository;
        this.agentWorkflowSkillRefRepository = agentWorkflowSkillRefRepository;
        this.platformPolicyBundleRepository = platformPolicyBundleRepository;
        this.platformSkillTemplateRepository = platformSkillTemplateRepository;
        this.platformSkillTemplateVersionRepository = platformSkillTemplateVersionRepository;
        this.platformToolDefinitionRepository = platformToolDefinitionRepository;
        this.platformAuditService = platformAuditService;
        this.specCompilerService = specCompilerService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void ensurePlatformAssets(String companyId) {
        skillDefinitionService.ensurePhaseOneDefaults(companyId);
        List<SkillDefinitionEntity> platformSkills = skillDefinitionRepository.findByCompanyIdOrderByBuiltinDescNameAsc(companyId)
                .stream()
                .filter(item -> item.getSourceType() == SkillSourceType.PLATFORM_STANDARD)
                .toList();
        for (SkillDefinitionEntity skill : platformSkills) {
            ensureTemplateForSkill(companyId, skill);
        }
        for (ToolCatalogItem item : BuiltinToolCatalog.list()) {
            platformToolDefinitionRepository.findByCompanyIdAndToolName(companyId, item.toolName())
                    .orElseGet(() -> platformToolDefinitionRepository.save(new PlatformToolDefinitionEntity(
                            companyId,
                            item.toolName(),
                            item.displayName(),
                            item.description(),
                            normalizeRiskLevel(item.riskLevel()),
                            normalizeCategory(item.category()),
                            true
                    )));
        }
        ensureCorePolicyBundle(companyId);
    }

    public List<PlatformSkillView> listPlatformSkills(String companyId) {
        ensurePlatformAssets(companyId);
        List<SkillDefinitionEntity> allSkills = skillDefinitionRepository.findByCompanyIdOrderByBuiltinDescNameAsc(companyId);
        List<SkillDefinitionEntity> platformSkills = allSkills.stream()
                .filter(item -> item.getSourceType() == SkillSourceType.PLATFORM_STANDARD)
                .toList();
        Map<String, Long> derivedCountByTemplateCode = allSkills.stream()
                .filter(item -> item.getSourceType() == SkillSourceType.TENANT_DERIVED)
                .collect(Collectors.groupingBy(
                        item -> safeTemplateCode(item),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
        List<Long> skillIds = platformSkills.stream().map(SkillDefinitionEntity::getId).toList();
        Map<Long, Long> bindingCountBySkillId = agentSkillBindingRepository.findByCompanyIdAndSkillIdInAndEnabledTrue(companyId, skillIds)
                .stream()
                .collect(Collectors.groupingBy(
                        AgentSkillBindingEntity::getSkillId,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
        return platformSkills.stream()
                .map(skill -> toSkillView(companyId, skill,
                        derivedCountByTemplateCode.getOrDefault(safeTemplateCode(skill), 0L).intValue(),
                        bindingCountBySkillId.getOrDefault(skill.getId(), 0L).intValue()))
                .toList();
    }

    public List<PlatformSkillVersionView> listPlatformSkillVersions(String companyId, Long skillId) {
        ensurePlatformAssets(companyId);
        SkillDefinitionEntity skill = requirePlatformSkill(companyId, skillId);
        String templateCode = safeTemplateCode(skill);
        PlatformSkillTemplateEntity template = requireTemplate(companyId, templateCode);
        List<PlatformSkillTemplateVersionEntity> versions =
                platformSkillTemplateVersionRepository.findByCompanyIdAndTemplateCodeOrderByVersionNoDesc(companyId, templateCode);
        PlatformSkillTemplateVersionEntity currentVersion = versions.stream()
                .filter(item -> Objects.equals(item.getVersionNo(), template.getCurrentVersionNo()))
                .findFirst()
                .orElse(versions.isEmpty() ? null : versions.get(0));
        TemplateUsage templateUsage = summarizeTemplateUsage(companyId, templateCode, template.getCurrentVersionNo());
        return versions
                .stream()
                .map(version -> new PlatformSkillVersionView(
                        version.getId(),
                        version.getVersionNo(),
                        version.getName(),
                        version.getDescription(),
                        version.getPromptFragment(),
                        splitCsv(version.getToolWhitelist()),
                        splitCsv(version.getKbWhitelist()),
                        version.getHandoffRule(),
                        version.getOutputContract(),
                        version.getRiskLevel(),
                        version.getPublishStatus(),
                        version.getChangelog(),
                        version.getCreatedBy(),
                        version.getCreatedAt() == null ? null : version.getCreatedAt().toString(),
                        version.getPublishedAt() == null ? null : version.getPublishedAt().toString(),
                        buildVersionImpact(version, currentVersion, template.getCurrentVersionNo(), templateUsage)
                ))
                .toList();
    }

    public PlatformPolicyBundleView getCorePolicyBundleSummary(String companyId) {
        ensurePlatformAssets(companyId);
        PlatformPolicyBundleEntity entity = platformPolicyBundleRepository
                .findTopByCompanyIdAndBundleCodeAndPublishStatusOrderByVersionNoDesc(companyId, CORE_POLICY_BUNDLE_CODE, "PUBLISHED")
                .orElseThrow(() -> new IllegalArgumentException("Platform policy bundle not found"));
        List<PlatformPolicyBundleEntity> versions =
                platformPolicyBundleRepository.findByCompanyIdAndBundleCodeOrderByVersionNoDesc(companyId, CORE_POLICY_BUNDLE_CODE);
        PublishedAgentUsage usage = summarizePublishedAgentUsage(companyId);
        Integer latestDraftVersionNo = versions.stream()
                .filter(item -> "DRAFT".equalsIgnoreCase(item.getPublishStatus()))
                .map(PlatformPolicyBundleEntity::getVersionNo)
                .max(Integer::compareTo)
                .orElse(null);
        return new PlatformPolicyBundleView(
                entity.getBundleCode(),
                entity.getVersionNo(),
                entity.getName(),
                entity.getDescription(),
                entity.getPublishStatus(),
                parseSourceSkillCodes(entity.getPolicyJson()),
                splitTextBlock(entity.getHandoffRules()),
                usage.livePublishedAgentCount(),
                splitTextBlock(entity.getPromptFragment()).size(),
                versions.size(),
                latestDraftVersionNo,
                usage.sampleAgentIds(),
                "当前 Policy Bundle 会直接注入所有聊天与调试运行时；它不像标准 Skill 那样随 Agent 发布 pin 住，变更前应先在 debug trace 验证。",
                "回滚 Policy Bundle 会立即改变所有聊天与调试运行时；建议先用少量样例 Agent 做 debug trace 复核，再执行全局回滚。",
                entity.getUpdatedAt() == null ? entity.getCreatedAt().toString() : entity.getUpdatedAt().toString()
        );
    }

    public List<PlatformPolicyBundleVersionView> listCorePolicyBundleVersions(String companyId) {
        ensurePlatformAssets(companyId);
        List<PlatformPolicyBundleEntity> versions =
                platformPolicyBundleRepository.findByCompanyIdAndBundleCodeOrderByVersionNoDesc(companyId, CORE_POLICY_BUNDLE_CODE);
        PlatformPolicyBundleEntity currentVersion = versions.stream()
                .filter(item -> "PUBLISHED".equalsIgnoreCase(item.getPublishStatus()))
                .findFirst()
                .orElse(versions.isEmpty() ? null : versions.get(0));
        PublishedAgentUsage usage = summarizePublishedAgentUsage(companyId);
        return versions.stream()
                .map(version -> new PlatformPolicyBundleVersionView(
                        version.getId(),
                        version.getVersionNo(),
                        version.getName(),
                        version.getDescription(),
                        version.getPromptFragment(),
                        splitTextBlock(version.getHandoffRules()),
                        parseSourceSkillCodes(version.getPolicyJson()),
                        version.getPublishStatus(),
                        version.getCreatedBy(),
                        version.getCreatedAt() == null ? null : version.getCreatedAt().toString(),
                        version.getPublishedAt() == null ? null : version.getPublishedAt().toString(),
                        buildPolicyBundleVersionImpact(version, currentVersion, usage)
                ))
                .toList();
    }

    @Transactional
    public PlatformPolicyBundleView saveCorePolicyBundleDraft(String companyId, PolicyBundleDraftCommand command) {
        ensurePlatformAssets(companyId);
        List<PlatformPolicyBundleEntity> versions =
                platformPolicyBundleRepository.findByCompanyIdAndBundleCodeOrderByVersionNoDesc(companyId, CORE_POLICY_BUNDLE_CODE);
        PlatformPolicyBundleEntity baseline = versions.stream()
                .filter(item -> "PUBLISHED".equalsIgnoreCase(item.getPublishStatus()))
                .findFirst()
                .orElse(versions.isEmpty() ? null : versions.get(0));
        int nextVersionNo = versions.stream()
                .map(PlatformPolicyBundleEntity::getVersionNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .map(item -> item + 1)
                .orElse(1);
        List<String> sourceSkillCodes = normalizePolicySourceSkillCodes(
                command.sourceSkillCodes(),
                baseline == null ? List.of() : parseSourceSkillCodes(baseline.getPolicyJson())
        );
        PlatformPolicyBundleEntity created = platformPolicyBundleRepository.save(new PlatformPolicyBundleEntity(
                companyId,
                CORE_POLICY_BUNDLE_CODE,
                requireText(command.name(), "name"),
                trimToNull(command.description()),
                nextVersionNo,
                trimToNull(command.promptFragment()) == null && baseline != null ? baseline.getPromptFragment() : trimToNull(command.promptFragment()),
                joinLines(command.handoffRules()) == null && baseline != null ? baseline.getHandoffRules() : joinLines(command.handoffRules()),
                writePolicyBundleJson(sourceSkillCodes),
                baseline == null ? "{\"maxToolCallsPolicy\":\"published-manifest-first\"}" : baseline.getToolPolicyJson(),
                baseline == null ? "{\"allowExternalWebSearchWhenSkillEnabled\":true}" : baseline.getDataEgressPolicyJson(),
                "DRAFT",
                currentActorId(),
                null
        ));
        logAudit(companyId,
                "platform.policy.version.create",
                "PLATFORM_POLICY_BUNDLE_VERSION",
                created.getBundleCode() + "@v" + created.getVersionNo(),
                "draft policy bundle version created");
        return getCorePolicyBundleSummary(companyId);
    }

    @Transactional
    public PlatformPolicyBundleView publishCorePolicyBundleVersion(String companyId, Integer versionNo) {
        ensurePlatformAssets(companyId);
        PlatformPolicyBundleEntity version = requireCorePolicyBundleVersion(companyId, versionNo);
        for (PlatformPolicyBundleEntity item : platformPolicyBundleRepository
                .findByCompanyIdAndBundleCodeOrderByVersionNoDesc(companyId, CORE_POLICY_BUNDLE_CODE)) {
            if (Objects.equals(item.getId(), version.getId())) {
                item.markPublished();
            } else if ("PUBLISHED".equalsIgnoreCase(item.getPublishStatus())) {
                item.markSuperseded();
            }
        }
        logAudit(companyId,
                "platform.policy.publish",
                "PLATFORM_POLICY_BUNDLE",
                version.getBundleCode() + "@v" + version.getVersionNo(),
                "published platform policy bundle version");
        return getCorePolicyBundleSummary(companyId);
    }

    @Transactional
    public PlatformPolicyBundleView rollbackCorePolicyBundleVersion(String companyId, Integer versionNo) {
        ensurePlatformAssets(companyId);
        PlatformPolicyBundleEntity version = requireCorePolicyBundleVersion(companyId, versionNo);
        for (PlatformPolicyBundleEntity item : platformPolicyBundleRepository
                .findByCompanyIdAndBundleCodeOrderByVersionNoDesc(companyId, CORE_POLICY_BUNDLE_CODE)) {
            if (Objects.equals(item.getId(), version.getId())) {
                item.markPublished();
            } else if ("PUBLISHED".equalsIgnoreCase(item.getPublishStatus())) {
                item.markSuperseded();
            }
        }
        logAudit(companyId,
                "platform.policy.rollback",
                "PLATFORM_POLICY_BUNDLE",
                version.getBundleCode() + "@v" + version.getVersionNo(),
                "rolled back platform policy bundle version");
        return getCorePolicyBundleSummary(companyId);
    }

    @Transactional
    public PlatformSkillView savePlatformSkillDraft(String companyId, Long skillId, SkillTemplateDraftCommand command) {
        ensurePlatformAssets(companyId);
        SkillDefinitionEntity skill = requirePlatformSkill(companyId, skillId);
        PlatformSkillTemplateEntity template = requireTemplate(companyId, safeTemplateCode(skill));
        int nextVersionNo = platformSkillTemplateVersionRepository
                .findTopByCompanyIdAndTemplateCodeOrderByVersionNoDesc(companyId, template.getTemplateCode())
                .map(item -> item.getVersionNo() + 1)
                .orElse(1);
        PlatformSkillTemplateVersionEntity created = platformSkillTemplateVersionRepository.save(new PlatformSkillTemplateVersionEntity(
                companyId,
                template.getTemplateCode(),
                nextVersionNo,
                requireText(command.name(), "name"),
                trimToNull(command.description()),
                trimToNull(command.promptFragment()),
                joinCsv(command.toolWhitelist()),
                joinCsv(command.kbWhitelist()),
                trimToNull(command.handoffRule()),
                trimToNull(command.outputContract()),
                normalizeRiskLevel(command.riskLevel()),
                trimToNull(command.changelog()),
                "DRAFT",
                currentActorId(),
                null
        ));
        template.updateMetadata(created.getName(), inferSkillCategory(skill, created.getToolWhitelist()), created.getDescription(), "ACTIVE");
        platformSkillTemplateRepository.save(template);
        logAudit(companyId,
                "platform.skill.version.create",
                "PLATFORM_SKILL_TEMPLATE_VERSION",
                template.getTemplateCode() + "@v" + created.getVersionNo(),
                "draft version created");
        return getPlatformSkillView(companyId, skill.getId());
    }

    @Transactional
    public PlatformSkillView publishPlatformSkillVersion(String companyId, Long skillId, Integer versionNo, SkillGovernanceCommand command) {
        ensurePlatformAssets(companyId);
        SkillDefinitionEntity skill = requirePlatformSkill(companyId, skillId);
        PlatformSkillTemplateEntity template = requireTemplate(companyId, safeTemplateCode(skill));
        PlatformSkillTemplateVersionEntity version = requireTemplateVersion(companyId, template.getTemplateCode(), versionNo);
        for (PlatformSkillTemplateVersionEntity item : platformSkillTemplateVersionRepository
                .findByCompanyIdAndTemplateCodeOrderByVersionNoDesc(companyId, template.getTemplateCode())) {
            if (Objects.equals(item.getId(), version.getId())) {
                item.markPublished();
            } else if ("PUBLISHED".equalsIgnoreCase(item.getPublishStatus())) {
                item.markSuperseded();
            }
        }
        applyPublishedTemplate(skill, version, command);
        template.updateMetadata(version.getName(), inferSkillCategory(skill, version.getToolWhitelist()), version.getDescription(), "ACTIVE");
        template.setCurrentVersionNo(version.getVersionNo());
        platformSkillTemplateRepository.save(template);
        logAudit(companyId,
                "platform.skill.publish",
                "PLATFORM_SKILL_TEMPLATE",
                template.getTemplateCode() + "@v" + version.getVersionNo(),
                "published platform skill template version");
        return getPlatformSkillView(companyId, skill.getId());
    }

    @Transactional
    public PlatformSkillView rollbackPlatformSkillVersion(String companyId, Long skillId, Integer versionNo, SkillGovernanceCommand command) {
        ensurePlatformAssets(companyId);
        SkillDefinitionEntity skill = requirePlatformSkill(companyId, skillId);
        PlatformSkillTemplateEntity template = requireTemplate(companyId, safeTemplateCode(skill));
        PlatformSkillTemplateVersionEntity version = requireTemplateVersion(companyId, template.getTemplateCode(), versionNo);
        for (PlatformSkillTemplateVersionEntity item : platformSkillTemplateVersionRepository
                .findByCompanyIdAndTemplateCodeOrderByVersionNoDesc(companyId, template.getTemplateCode())) {
            if (Objects.equals(item.getId(), version.getId())) {
                item.markPublished();
            } else if ("PUBLISHED".equalsIgnoreCase(item.getPublishStatus())) {
                item.markSuperseded();
            }
        }
        applyPublishedTemplate(skill, version, command);
        template.setCurrentVersionNo(version.getVersionNo());
        platformSkillTemplateRepository.save(template);
        logAudit(companyId,
                "platform.skill.rollback",
                "PLATFORM_SKILL_TEMPLATE",
                template.getTemplateCode() + "@v" + version.getVersionNo(),
                "rolled back platform skill template version");
        return getPlatformSkillView(companyId, skill.getId());
    }

    public List<PlatformToolView> listPlatformTools(String companyId) {
        ensurePlatformAssets(companyId);
        List<PlatformToolDefinitionEntity> tools = platformToolDefinitionRepository.findByCompanyIdOrderByCategoryAscDisplayNameAsc(companyId);
        List<SkillDefinitionEntity> allSkills = skillDefinitionRepository.findByCompanyIdOrderByBuiltinDescNameAsc(companyId);
        return tools.stream()
                .map(tool -> toToolView(companyId, tool, allSkills))
                .toList();
    }

    @Transactional
    public PlatformToolView updatePlatformTool(String companyId, String toolName, ToolGovernanceCommand command) {
        ensurePlatformAssets(companyId);
        PlatformToolDefinitionEntity tool = platformToolDefinitionRepository.findByCompanyIdAndToolName(companyId, toolName)
                .orElseThrow(() -> new IllegalArgumentException("Platform tool not found"));
        tool.update(
                requireText(command.displayName(), "displayName"),
                trimToNull(command.description()),
                normalizeRiskLevel(command.riskLevel()),
                normalizeCategory(command.category()),
                command.enabled() == null || command.enabled()
        );
        platformToolDefinitionRepository.save(tool);
        logAudit(companyId,
                "platform.tool.update",
                "PLATFORM_TOOL",
                tool.getToolName(),
                "updated platform tool governance");
        return toToolView(companyId, tool, skillDefinitionRepository.findByCompanyIdOrderByBuiltinDescNameAsc(companyId));
    }

    public List<ToolCatalogItem> listEffectiveBuiltinTools(String companyId) {
        ensurePlatformAssets(companyId);
        return platformToolDefinitionRepository.findByCompanyIdOrderByCategoryAscDisplayNameAsc(companyId).stream()
                .filter(PlatformToolDefinitionEntity::isEnabled)
                .map(item -> new ToolCatalogItem(
                        item.getToolName(),
                        item.getDisplayName(),
                        item.getDescription(),
                        toChineseRiskLabel(item.getRiskLevel()),
                        item.getCategory()
                ))
                .toList();
    }

    public RuntimePolicyBundle resolvePublishedPolicyBundle(String companyId) {
        ensurePlatformAssets(companyId);
        return platformPolicyBundleRepository
                .findTopByCompanyIdAndBundleCodeAndPublishStatusOrderByVersionNoDesc(companyId, CORE_POLICY_BUNDLE_CODE, "PUBLISHED")
                .map(this::toRuntimePolicyBundle)
                .orElse(RuntimePolicyBundle.EMPTY);
    }

    public List<String> filterRuntimeAllowedToolNames(String companyId, List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return List.of();
        }
        ensurePlatformAssets(companyId);
        LinkedHashSet<String> filtered = new LinkedHashSet<>();
        for (String toolName : ToolNameNormalizer.canonicalizeAll(toolNames)) {
            if (isRuntimeToolEnabled(companyId, toolName)) {
                filtered.add(toolName);
            }
        }
        return List.copyOf(filtered);
    }

    public boolean isRuntimeToolEnabled(String companyId, String toolName) {
        String canonicalToolName = ToolNameNormalizer.canonicalize(toolName);
        if (canonicalToolName == null || canonicalToolName.isBlank()) {
            return false;
        }
        if (!isBuiltinTool(canonicalToolName)) {
            return true;
        }
        ensurePlatformAssets(companyId);
        return platformToolDefinitionRepository.findByCompanyIdAndToolName(companyId, canonicalToolName)
                .map(PlatformToolDefinitionEntity::isEnabled)
                .orElse(true);
    }

    private PlatformSkillView getPlatformSkillView(String companyId, Long skillId) {
        return listPlatformSkills(companyId).stream()
                .filter(item -> Objects.equals(item.id(), skillId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Platform skill not found"));
    }

    private PlatformSkillView toSkillView(String companyId,
                                          SkillDefinitionEntity skill,
                                          int derivedSkillCount,
                                          int agentBindingCount) {
        String templateCode = safeTemplateCode(skill);
        PlatformSkillTemplateEntity template = requireTemplate(companyId, templateCode);
        List<PlatformSkillTemplateVersionEntity> versions =
                platformSkillTemplateVersionRepository.findByCompanyIdAndTemplateCodeOrderByVersionNoDesc(companyId, templateCode);
        TemplateUsage templateUsage = summarizeTemplateUsage(companyId, templateCode, template.getCurrentVersionNo());
        Integer latestDraftVersionNo = versions.stream()
                .filter(item -> "DRAFT".equalsIgnoreCase(item.getPublishStatus()))
                .map(PlatformSkillTemplateVersionEntity::getVersionNo)
                .max(Integer::compareTo)
                .orElse(null);
        return new PlatformSkillView(
                skill.getId(),
                skill.getSkillCode(),
                skill.getName(),
                skill.getDescription(),
                skill.isEnabled(),
                skill.getRiskLevel(),
                skill.getSourceType().name(),
                skill.getVisibility().name(),
                skill.getBindingPolicy().name(),
                skill.getUpdatePolicy().name(),
                templateCode,
                template.getCurrentVersionNo(),
                derivedSkillCount,
                agentBindingCount,
                versions.size(),
                latestDraftVersionNo,
                skill.getUpdatedAt().toString(),
                new PlatformSkillImpactView(
                        agentBindingCount,
                        derivedSkillCount,
                        templateUsage.publishedWorkflowCount(),
                        templateUsage.currentVersionPinnedWorkflowCount(),
                        templateUsage.historicalPinnedWorkflowCount(),
                        templateUsage.sampleAgentIds(),
                        "后续重新发布的 Agent 会默认固化到当前模板版本；已发布 Agent 会继续命中各自的 pinned skill snapshot，形成天然灰度缓冲。",
                        "回滚模板版本后，只会影响后续重新发布的 Agent；线上已发布版本仍保持各自的 pinned skill snapshot。"
                )
        );
    }

    private PlatformSkillVersionImpactView buildVersionImpact(PlatformSkillTemplateVersionEntity version,
                                                              PlatformSkillTemplateVersionEntity currentVersion,
                                                              Integer currentTemplateVersionNo,
                                                              TemplateUsage templateUsage) {
        VersionUsage versionUsage = templateUsage.byVersion().getOrDefault(version.getVersionNo(), VersionUsage.EMPTY);
        List<String> summaryLines = new ArrayList<>();
        if (currentVersion == null || Objects.equals(version.getVersionNo(), currentVersion.getVersionNo())) {
            summaryLines.add("当前线上默认模板版本。");
        } else {
            appendDiffSummary(summaryLines, "工具范围",
                    splitCsv(currentVersion.getToolWhitelist()),
                    splitCsv(version.getToolWhitelist()));
            appendDiffSummary(summaryLines, "知识库范围",
                    splitCsv(currentVersion.getKbWhitelist()),
                    splitCsv(version.getKbWhitelist()));
            if (!Objects.equals(trimToNull(currentVersion.getRiskLevel()), trimToNull(version.getRiskLevel()))) {
                summaryLines.add("风险等级变化：" + fallback(currentVersion.getRiskLevel(), "MEDIUM")
                        + " -> " + fallback(version.getRiskLevel(), "MEDIUM"));
            }
            if (!Objects.equals(trimToNull(currentVersion.getHandoffRule()), trimToNull(version.getHandoffRule()))) {
                summaryLines.add("转人工规则发生变化。");
            }
            if (!Objects.equals(trimToNull(currentVersion.getOutputContract()), trimToNull(version.getOutputContract()))) {
                summaryLines.add("输出约束发生变化。");
            }
            if (!Objects.equals(trimToNull(currentVersion.getPromptFragment()), trimToNull(version.getPromptFragment()))) {
                summaryLines.add("Prompt fragment 已变化，建议先走 debug trace 复核。");
            }
        }
        if (versionUsage.workflowCount() > 0) {
            summaryLines.add("当前有 " + versionUsage.workflowCount() + " 个已发布 workflow 固定在此模板版本。");
        }
        String rolloutStage;
        boolean rollbackReady = false;
        if ("DRAFT".equalsIgnoreCase(version.getPublishStatus())) {
            rolloutStage = "DRAFT_PENDING";
            summaryLines.add("发布后只会影响后续重新发布的 Agent，可先挑少量 Agent 复发验证。");
        } else if (Objects.equals(version.getVersionNo(), currentTemplateVersionNo)) {
            rolloutStage = "CURRENT_DEFAULT";
            summaryLines.add("后续重新发布的 Agent 默认会固定到这个模板版本。");
            rollbackReady = currentTemplateVersionNo != null && currentTemplateVersionNo > 1;
        } else {
            rolloutStage = "ROLLBACK_TARGET";
            rollbackReady = true;
            summaryLines.add("可作为回滚目标；回滚后只影响后续重新发布的 Agent。");
        }
        if (summaryLines.isEmpty()) {
            summaryLines.add("此版本与当前线上模板无差异。");
        }
        return new PlatformSkillVersionImpactView(
                versionUsage.workflowCount(),
                versionUsage.agentCount(),
                versionUsage.sampleAgentIds(),
                List.copyOf(summaryLines),
                rolloutStage,
                rollbackReady
        );
    }

    private TemplateUsage summarizeTemplateUsage(String companyId, String templateCode, Integer currentTemplateVersionNo) {
        List<AgentWorkflowSkillRefEntity> refs = agentWorkflowSkillRefRepository
                .findByCompanyIdAndTemplateCodeOrderByTemplateVersionNoDescIdAsc(companyId, templateCode);
        if (refs.isEmpty()) {
            return TemplateUsage.EMPTY;
        }
        LinkedHashSet<Long> workflowIds = refs.stream()
                .map(AgentWorkflowSkillRefEntity::getWorkflowVersionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, AgentWorkflowVersionEntity> workflowById = agentWorkflowVersionRepository.findAllById(workflowIds)
                .stream()
                .collect(Collectors.toMap(
                        AgentWorkflowVersionEntity::getId,
                        item -> item,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<Integer, LinkedHashSet<Long>> workflowIdsByVersion = new LinkedHashMap<>();
        Map<Integer, LinkedHashSet<String>> agentIdsByVersion = new LinkedHashMap<>();
        LinkedHashSet<String> sampleAgentIds = new LinkedHashSet<>();
        for (AgentWorkflowSkillRefEntity ref : refs) {
            Integer templateVersionNo = ref.getTemplateVersionNo();
            if (templateVersionNo == null || ref.getWorkflowVersionId() == null) {
                continue;
            }
            workflowIdsByVersion.computeIfAbsent(templateVersionNo, ignored -> new LinkedHashSet<>())
                    .add(ref.getWorkflowVersionId());
            AgentWorkflowVersionEntity workflow = workflowById.get(ref.getWorkflowVersionId());
            if (workflow != null && workflow.getAgentId() != null && !workflow.getAgentId().isBlank()) {
                agentIdsByVersion.computeIfAbsent(templateVersionNo, ignored -> new LinkedHashSet<>())
                        .add(workflow.getAgentId());
                if (sampleAgentIds.size() < 5) {
                    sampleAgentIds.add(workflow.getAgentId());
                }
            }
        }
        Map<Integer, VersionUsage> byVersion = new LinkedHashMap<>();
        int publishedWorkflowCount = 0;
        for (Map.Entry<Integer, LinkedHashSet<Long>> entry : workflowIdsByVersion.entrySet()) {
            LinkedHashSet<String> versionAgents = agentIdsByVersion.getOrDefault(entry.getKey(), new LinkedHashSet<>());
            publishedWorkflowCount += entry.getValue().size();
            byVersion.put(entry.getKey(), new VersionUsage(
                    entry.getValue().size(),
                    versionAgents.size(),
                    versionAgents.stream().limit(5).toList()
            ));
        }
        int currentVersionPinnedWorkflowCount = currentTemplateVersionNo == null
                ? 0
                : byVersion.getOrDefault(currentTemplateVersionNo, VersionUsage.EMPTY).workflowCount();
        return new TemplateUsage(
                byVersion,
                publishedWorkflowCount,
                currentVersionPinnedWorkflowCount,
                Math.max(0, publishedWorkflowCount - currentVersionPinnedWorkflowCount),
                sampleAgentIds.stream().limit(5).toList()
        );
    }

    private PublishedAgentUsage summarizePublishedAgentUsage(String companyId) {
        List<AgentDefinitionEntity> publishedAgents = agentDefinitionRepository.findByCompanyIdAndEnabledTrueOrderByBuiltinDescUpdatedAtDesc(companyId)
                .stream()
                .filter(item -> item.getPublishedVersionId() != null)
                .toList();
        return new PublishedAgentUsage(
                publishedAgents.size(),
                publishedAgents.stream()
                        .map(AgentDefinitionEntity::getAgentId)
                        .filter(Objects::nonNull)
                        .filter(item -> !item.isBlank())
                        .distinct()
                        .limit(5)
                        .toList()
        );
    }

    private PlatformPolicyBundleVersionImpactView buildPolicyBundleVersionImpact(PlatformPolicyBundleEntity version,
                                                                                PlatformPolicyBundleEntity currentVersion,
                                                                                PublishedAgentUsage usage) {
        List<String> summaryLines = new ArrayList<>();
        if (currentVersion == null || Objects.equals(version.getVersionNo(), currentVersion.getVersionNo())) {
            summaryLines.add("当前全局生效的核心策略包版本。");
        } else {
            appendDiffSummary(summaryLines, "来源策略 Skill",
                    currentVersion == null ? List.of() : parseSourceSkillCodes(currentVersion.getPolicyJson()),
                    parseSourceSkillCodes(version.getPolicyJson()));
            appendDiffSummary(summaryLines, "转人工规则",
                    currentVersion == null ? List.of() : splitTextBlock(currentVersion.getHandoffRules()),
                    splitTextBlock(version.getHandoffRules()));
            if (!Objects.equals(trimToNull(currentVersion.getPromptFragment()), trimToNull(version.getPromptFragment()))) {
                summaryLines.add("Prompt fragment 已变化，发布前应先做 debug trace 复核。");
            }
        }
        if (usage.livePublishedAgentCount() > 0) {
            summaryLines.add("发布或回滚后会立即影响 " + usage.livePublishedAgentCount() + " 个已发布 Agent 的聊天与调试运行时。");
        } else {
            summaryLines.add("当前没有已发布 Agent，因此变更只影响后续调试与新发布链路。");
        }
        String rolloutStage;
        boolean rollbackReady = false;
        if ("DRAFT".equalsIgnoreCase(version.getPublishStatus())) {
            rolloutStage = "DRAFT_PENDING";
            summaryLines.add("建议先用样例 Agent 做 debug trace，对比 runtime governance 摘要后再发布。");
        } else if (currentVersion != null && Objects.equals(version.getVersionNo(), currentVersion.getVersionNo())) {
            rolloutStage = "CURRENT_PUBLISHED";
            rollbackReady = version.getVersionNo() != null && version.getVersionNo() > 1;
            summaryLines.add("这是当前全局即时生效版本，不存在已发布 Agent 的 pinned 缓冲层。");
        } else {
            rolloutStage = "ROLLBACK_TARGET";
            rollbackReady = true;
            summaryLines.add("可作为全局回滚目标；回滚后会立即切换所有运行时命中的核心策略。");
        }
        return new PlatformPolicyBundleVersionImpactView(
                usage.livePublishedAgentCount(),
                usage.sampleAgentIds(),
                List.copyOf(summaryLines),
                rolloutStage,
                rollbackReady
        );
    }

    private PlatformToolView toToolView(String companyId,
                                        PlatformToolDefinitionEntity tool,
                                        List<SkillDefinitionEntity> allSkills) {
        List<SkillDefinitionEntity> dependentSkills = allSkills.stream()
                .filter(item -> item.getSourceType() == SkillSourceType.PLATFORM_STANDARD)
                .filter(item -> splitCsv(item.getToolWhitelist()).contains(tool.getToolName()))
                .toList();
        List<Long> dependentSkillIds = dependentSkills.stream().map(SkillDefinitionEntity::getId).toList();
        int agentBindingCount = dependentSkillIds.isEmpty() ? 0
                : agentSkillBindingRepository.findByCompanyIdAndSkillIdInAndEnabledTrue(companyId, dependentSkillIds).size();
        return new PlatformToolView(
                tool.getToolName(),
                tool.getDisplayName(),
                tool.getDescription(),
                tool.getRiskLevel(),
                tool.getCategory(),
                tool.isEnabled(),
                dependentSkills.stream().map(SkillDefinitionEntity::getSkillCode).sorted().toList(),
                agentBindingCount,
                tool.getUpdatedAt().toString()
        );
    }

    private PlatformPolicyBundleEntity requireCorePolicyBundleVersion(String companyId, Integer versionNo) {
        return platformPolicyBundleRepository.findByCompanyIdAndBundleCodeAndVersionNo(companyId, CORE_POLICY_BUNDLE_CODE, versionNo)
                .orElseThrow(() -> new IllegalArgumentException("Platform policy bundle version not found"));
    }

    private void ensureCorePolicyBundle(String companyId) {
        if (!platformPolicyBundleRepository.findByCompanyIdAndBundleCodeOrderByVersionNoDesc(companyId, CORE_POLICY_BUNDLE_CODE).isEmpty()) {
            return;
        }
        Map<String, SkillDefinitionEntity> byCode = skillDefinitionRepository.findByCompanyIdOrderByBuiltinDescNameAsc(companyId)
                .stream()
                .collect(Collectors.toMap(SkillDefinitionEntity::getSkillCode, item -> item, (left, right) -> left, LinkedHashMap::new));
        List<SkillDefinitionEntity> coreSkills = CORE_POLICY_SKILL_CODES.stream()
                .map(byCode::get)
                .filter(Objects::nonNull)
                .filter(SkillDefinitionEntity::isPlatformCorePolicyCandidate)
                .toList();
        if (coreSkills.isEmpty()) {
            return;
        }
        platformPolicyBundleRepository.save(new PlatformPolicyBundleEntity(
                companyId,
                CORE_POLICY_BUNDLE_CODE,
                "Platform Core Policy Bundle",
                "Runtime-injected core safety and conversation policy bundle.",
                1,
                composeCorePolicyPrompt(coreSkills),
                joinLines(collectCorePolicyHandoffRules(coreSkills)),
                writePolicyBundleJson(coreSkills.stream().map(SkillDefinitionEntity::getSkillCode).toList()),
                "{\"maxToolCallsPolicy\":\"published-manifest-first\"}",
                "{\"allowExternalWebSearchWhenSkillEnabled\":true}",
                "PUBLISHED",
                "platform-system",
                Instant.now()
        ));
    }

    private RuntimePolicyBundle toRuntimePolicyBundle(PlatformPolicyBundleEntity entity) {
        return new RuntimePolicyBundle(
                entity.getBundleCode(),
                entity.getVersionNo(),
                entity.getPromptFragment(),
                splitTextBlock(entity.getHandoffRules())
        );
    }

    private String composeCorePolicyPrompt(List<SkillDefinitionEntity> coreSkills) {
        List<String> lines = new ArrayList<>();
        lines.add("Always-on platform core policy bundle:");
        for (SkillDefinitionEntity skill : coreSkills) {
            if (skill.getPromptFragment() == null || skill.getPromptFragment().isBlank()) {
                continue;
            }
            lines.add("- [" + skill.getSkillCode() + "] " + skill.getPromptFragment().trim());
        }
        return String.join("\n", lines);
    }

    private List<String> collectCorePolicyHandoffRules(List<SkillDefinitionEntity> coreSkills) {
        LinkedHashSet<String> rules = new LinkedHashSet<>();
        for (SkillDefinitionEntity skill : coreSkills) {
            String rule = trimToNull(skill.getHandoffRule());
            if (rule != null) {
                rules.add(rule);
            }
        }
        return List.copyOf(rules);
    }

    private boolean isBuiltinTool(String toolName) {
        return BuiltinToolCatalog.list().stream()
                .map(ToolCatalogItem::toolName)
                .map(ToolNameNormalizer::canonicalize)
                .filter(Objects::nonNull)
                .anyMatch(toolName::equals);
    }

    private void ensureTemplateForSkill(String companyId, SkillDefinitionEntity skill) {
        String templateCode = safeTemplateCode(skill);
        PlatformSkillTemplateEntity template = platformSkillTemplateRepository.findByCompanyIdAndTemplateCode(companyId, templateCode)
                .orElseGet(() -> platformSkillTemplateRepository.save(new PlatformSkillTemplateEntity(
                        companyId,
                        templateCode,
                        skill.getName(),
                        inferSkillCategory(skill, skill.getToolWhitelist()),
                        skill.getDescription(),
                        "ACTIVE",
                        1
                )));
        if (platformSkillTemplateVersionRepository.findTopByCompanyIdAndTemplateCodeOrderByVersionNoDesc(companyId, templateCode).isEmpty()) {
            platformSkillTemplateVersionRepository.save(new PlatformSkillTemplateVersionEntity(
                    companyId,
                    templateCode,
                    template.getCurrentVersionNo() == null ? 1 : template.getCurrentVersionNo(),
                    skill.getName(),
                    skill.getDescription(),
                    skill.getPromptFragment(),
                    skill.getToolWhitelist(),
                    skill.getKbWhitelist(),
                    skill.getHandoffRule(),
                    skill.getOutputContract(),
                    normalizeRiskLevel(skill.getRiskLevel()),
                    "seed from builtin skill definition",
                    "PUBLISHED",
                    "system",
                    Instant.now()
            ));
        }
    }

    private SkillDefinitionEntity requirePlatformSkill(String companyId, Long skillId) {
        SkillDefinitionEntity skill = skillDefinitionRepository.findByIdAndCompanyId(skillId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Platform skill not found"));
        if (skill.getSourceType() != SkillSourceType.PLATFORM_STANDARD) {
            throw new IllegalArgumentException("Platform skill not found");
        }
        return skill;
    }

    private PlatformSkillTemplateEntity requireTemplate(String companyId, String templateCode) {
        return platformSkillTemplateRepository.findByCompanyIdAndTemplateCode(companyId, templateCode)
                .orElseThrow(() -> new IllegalArgumentException("Platform skill template not found"));
    }

    private PlatformSkillTemplateVersionEntity requireTemplateVersion(String companyId, String templateCode, Integer versionNo) {
        return platformSkillTemplateVersionRepository.findByCompanyIdAndTemplateCodeAndVersionNo(companyId, templateCode, versionNo)
                .orElseThrow(() -> new IllegalArgumentException("Platform skill template version not found"));
    }

    private void applyPublishedTemplate(SkillDefinitionEntity skill,
                                        PlatformSkillTemplateVersionEntity version,
                                        SkillGovernanceCommand command) {
        skill.update(
                skill.getSkillCode(),
                version.getName(),
                version.getDescription(),
                command.enabled() == null ? skill.isEnabled() : command.enabled(),
                version.getPromptFragment(),
                version.getPromptFragment(),
                version.getToolWhitelist(),
                version.getKbWhitelist(),
                version.getHandoffRule(),
                version.getOutputContract(),
                skill.getRuntimeApiDraftJson(),
                normalizeRiskLevel(version.getRiskLevel())
        );
        if (command.visibility() != null) {
            skill.setVisibility(SkillVisibility.valueOf(command.visibility().trim().toUpperCase()));
        }
        if (command.bindingPolicy() != null) {
            skill.setBindingPolicy(SkillBindingPolicy.valueOf(command.bindingPolicy().trim().toUpperCase()));
        }
        skill.setUpdatePolicy(SkillUpdatePolicy.AUTO);
        SkillVersionEntity publishedVersion = createPublishedSkillVersion(skill, version);
        skill.setCurrentPublishedVersionId(publishedVersion.getId());
        skill.setLatestDraftVersionId(publishedVersion.getId());
        skillDefinitionRepository.save(skill);
    }

    private SkillVersionEntity createPublishedSkillVersion(SkillDefinitionEntity skill,
                                                           PlatformSkillTemplateVersionEntity version) {
        Integer nextVersionNo = skillVersionRepository.findTopByCompanyIdAndSkillIdOrderByVersionNoDesc(skill.getCompanyId(), skill.getId())
                .map(item -> item.getVersionNo() + 1)
                .orElse(1);
        SpecCompilerService.SpecCompilation compiled = specCompilerService.compile(new SpecCompilerService.SpecCompileCommand(
                "skill-policy",
                version.getName(),
                fallback(version.getPromptFragment(), version.getDescription()),
                splitCsv(version.getToolWhitelist()),
                splitCsv(version.getKbWhitelist()),
                version.getHandoffRule(),
                normalizeRiskLevel(version.getRiskLevel())
        ));
        return skillVersionRepository.save(new SkillVersionEntity(
                skill.getCompanyId(),
                skill.getId(),
                nextVersionNo,
                fallback(version.getPromptFragment(), version.getDescription()),
                "policy",
                "platform-template",
                toPolicyJson(compiled.specIr()),
                "platformTemplate=" + version.getTemplateCode() + "@v" + version.getVersionNo(),
                version.getPromptFragment(),
                toPolicyJson(compiled.specIr()),
                version.getToolWhitelist(),
                version.getKbWhitelist(),
                normalizeRiskLevel(version.getRiskLevel()),
                String.join("\n", compiled.compileSummary()),
                String.join("\n", compiled.warnings()),
                "PUBLISHED"
        ));
    }

    private String inferSkillCategory(SkillDefinitionEntity skill, String toolWhitelist) {
        String code = skill.getSkillCode();
        if (code.startsWith("crm-") || splitCsv(toolWhitelist).stream().anyMatch(item -> item.startsWith("cloudcc_"))) {
            return "crm";
        }
        if (code.contains("approval")) {
            return "approval";
        }
        if (splitCsv(toolWhitelist).stream().anyMatch(item -> item.startsWith("tavily_"))) {
            return "web";
        }
        return "general";
    }

    private String safeTemplateCode(SkillDefinitionEntity item) {
        return trimToNull(item.getTemplateCode()) == null ? item.getSkillCode() : item.getTemplateCode().trim();
    }

    private String normalizeRiskLevel(String riskLevel) {
        String normalized = fallback(riskLevel, "MEDIUM").trim();
        if ("低风险".equals(normalized)) {
            return "LOW";
        }
        if ("中风险".equals(normalized)) {
            return "MEDIUM";
        }
        if ("高风险".equals(normalized)) {
            return "HIGH";
        }
        normalized = normalized.toUpperCase();
        if (!Set.of("LOW", "MEDIUM", "HIGH").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported riskLevel: " + riskLevel);
        }
        return normalized;
    }

    private String normalizeCategory(String category) {
        String normalized = fallback(category, "general").trim().toLowerCase();
        return normalized.isBlank() ? "general" : normalized;
    }

    private List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private String joinCsv(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                normalized.add(trimmed);
            }
        }
        return normalized.isEmpty() ? null : String.join(",", normalized);
    }

    private String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String requireText(String value, String field) {
        String cleaned = trimToNull(value);
        if (cleaned == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return cleaned;
    }

    private String fallback(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }

    private void appendDiffSummary(List<String> summaryLines, String label, List<String> currentValues, List<String> targetValues) {
        LinkedHashSet<String> current = new LinkedHashSet<>(currentValues == null ? List.of() : currentValues);
        LinkedHashSet<String> target = new LinkedHashSet<>(targetValues == null ? List.of() : targetValues);
        LinkedHashSet<String> added = new LinkedHashSet<>(target);
        added.removeAll(current);
        LinkedHashSet<String> removed = new LinkedHashSet<>(current);
        removed.removeAll(target);
        if (added.isEmpty() && removed.isEmpty()) {
            return;
        }
        List<String> parts = new ArrayList<>();
        if (!added.isEmpty()) {
            parts.add("+" + String.join(", ", added));
        }
        if (!removed.isEmpty()) {
            parts.add("-" + String.join(", ", removed));
        }
        summaryLines.add(label + "变化：" + String.join(" / ", parts));
    }

    private String toPolicyJson(SpecCompilerService.SpecIr specIr) {
        try {
            return objectMapper.writeValueAsString(specIr);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private List<String> splitTextBlock(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split("\n"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private List<String> parseSourceSkillCodes(String policyJson) {
        if (policyJson == null || policyJson.isBlank()) {
            return CORE_POLICY_SKILL_CODES;
        }
        try {
            Map<?, ?> payload = objectMapper.readValue(policyJson, Map.class);
            Object raw = payload.get("sourceSkillCodes");
            if (raw instanceof List<?> list) {
                return list.stream()
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .map(String::trim)
                        .filter(item -> !item.isBlank())
                        .distinct()
                        .toList();
            }
        } catch (Exception ignored) {
            // keep seed fallback if policy_json cannot be parsed
        }
        return CORE_POLICY_SKILL_CODES;
    }

    private List<String> normalizePolicySourceSkillCodes(List<String> requested, List<String> fallbackValues) {
        List<String> normalized = (requested == null ? List.<String>of() : requested).stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!normalized.isEmpty()) {
            return normalized;
        }
        if (fallbackValues != null && !fallbackValues.isEmpty()) {
            return fallbackValues.stream()
                    .map(this::trimToNull)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }
        return CORE_POLICY_SKILL_CODES;
    }

    private String writePolicyBundleJson(List<String> sourceSkillCodes) {
        Map<String, Object> policyJson = new LinkedHashMap<>();
        policyJson.put("sourceSkillCodes", normalizePolicySourceSkillCodes(sourceSkillCodes, CORE_POLICY_SKILL_CODES));
        policyJson.put("policyType", "platform-core-bundle");
        policyJson.put("migrationMode", "runtime-injected");
        return writeJson(policyJson);
    }

    private String joinLines(List<String> lines) {
        return lines == null || lines.isEmpty() ? null : String.join("\n", lines);
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

    private void logAudit(String companyId, String eventType, String resourceType, String resourceKey, String detail) {
        platformAuditService.log(
                companyId,
                currentActorId(),
                currentPlatformRole(),
                eventType,
                resourceType,
                resourceKey,
                detail
        );
    }

    private String toChineseRiskLabel(String riskLevel) {
        return switch (normalizeRiskLevel(riskLevel)) {
            case "LOW" -> "低风险";
            case "MEDIUM" -> "中风险";
            case "HIGH" -> "高风险";
            default -> "中风险";
        };
    }

    public record PlatformSkillView(
            Long id,
            String skillCode,
            String name,
            String description,
            boolean enabled,
            String riskLevel,
            String sourceType,
            String visibility,
            String bindingPolicy,
            String updatePolicy,
            String templateCode,
            Integer currentTemplateVersionNo,
            Integer derivedSkillCount,
            Integer agentBindingCount,
            Integer versionCount,
            Integer latestDraftVersionNo,
            String updatedAt,
            PlatformSkillImpactView impact
    ) {
    }

    public record PlatformSkillVersionView(
            Long id,
            Integer versionNo,
            String name,
            String description,
            String promptFragment,
            List<String> toolWhitelist,
            List<String> kbWhitelist,
            String handoffRule,
            String outputContract,
            String riskLevel,
            String publishStatus,
            String changelog,
            String createdBy,
            String createdAt,
            String publishedAt,
            PlatformSkillVersionImpactView impact
    ) {
    }

    public record PlatformSkillImpactView(
            Integer boundAgentCount,
            Integer derivedSkillCount,
            Integer publishedWorkflowCount,
            Integer currentVersionPinnedWorkflowCount,
            Integer historicalPinnedWorkflowCount,
            List<String> sampleAgentIds,
            String rolloutHint,
            String rollbackHint
    ) {
    }

    public record PlatformSkillVersionImpactView(
            Integer pinnedWorkflowCount,
            Integer pinnedAgentCount,
            List<String> sampleAgentIds,
            List<String> summaryLines,
            String rolloutStage,
            boolean rollbackReady
    ) {
    }

    public record PlatformPolicyBundleView(
            String bundleCode,
            Integer versionNo,
            String name,
            String description,
            String publishStatus,
            List<String> sourceSkillCodes,
            List<String> handoffRules,
            Integer livePublishedAgentCount,
            Integer promptLineCount,
            Integer versionCount,
            Integer latestDraftVersionNo,
            List<String> sampleAgentIds,
            String rolloutHint,
            String rollbackHint,
            String updatedAt
    ) {
    }

    public record PlatformPolicyBundleVersionView(
            Long id,
            Integer versionNo,
            String name,
            String description,
            String promptFragment,
            List<String> handoffRules,
            List<String> sourceSkillCodes,
            String publishStatus,
            String createdBy,
            String createdAt,
            String publishedAt,
            PlatformPolicyBundleVersionImpactView impact
    ) {
    }

    public record PlatformPolicyBundleVersionImpactView(
            Integer livePublishedAgentCount,
            List<String> sampleAgentIds,
            List<String> summaryLines,
            String rolloutStage,
            boolean rollbackReady
    ) {
    }

    public record PlatformToolView(
            String toolName,
            String displayName,
            String description,
            String riskLevel,
            String category,
            boolean enabled,
            List<String> dependentSkillCodes,
            Integer agentBindingCount,
            String updatedAt
    ) {
    }

    public record SkillTemplateDraftCommand(
            String name,
            String description,
            String promptFragment,
            List<String> toolWhitelist,
            List<String> kbWhitelist,
            String handoffRule,
            String outputContract,
            String riskLevel,
            String changelog
    ) {
    }

    public record PolicyBundleDraftCommand(
            String name,
            String description,
            String promptFragment,
            List<String> handoffRules,
            List<String> sourceSkillCodes
    ) {
    }

    public record SkillGovernanceCommand(
            Boolean enabled,
            String visibility,
            String bindingPolicy
    ) {
    }

    public record ToolGovernanceCommand(
            String displayName,
            String description,
            String riskLevel,
            String category,
            Boolean enabled
    ) {
    }

    public record RuntimePolicyBundle(
            String bundleCode,
            Integer versionNo,
            String promptFragment,
            List<String> handoffRules
    ) {
        public static final RuntimePolicyBundle EMPTY =
                new RuntimePolicyBundle("", null, "", List.of());
    }

    private record TemplateUsage(
            Map<Integer, VersionUsage> byVersion,
            int publishedWorkflowCount,
            int currentVersionPinnedWorkflowCount,
            int historicalPinnedWorkflowCount,
            List<String> sampleAgentIds
    ) {
        private static final TemplateUsage EMPTY =
                new TemplateUsage(Map.of(), 0, 0, 0, List.of());
    }

    private record VersionUsage(
            int workflowCount,
            int agentCount,
            List<String> sampleAgentIds
    ) {
        private static final VersionUsage EMPTY = new VersionUsage(0, 0, List.of());
    }

    private record PublishedAgentUsage(
            int livePublishedAgentCount,
            List<String> sampleAgentIds
    ) {
    }
}
