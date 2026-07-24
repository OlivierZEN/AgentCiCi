package com.codehouse.ciciassistant.skill.service;

import com.codehouse.ciciassistant.platform.domain.PlatformSkillTemplateEntity;
import com.codehouse.ciciassistant.platform.domain.PlatformSkillTemplateRepository;
import com.codehouse.ciciassistant.platform.domain.PlatformSkillTemplateVersionEntity;
import com.codehouse.ciciassistant.platform.domain.PlatformSkillTemplateVersionRepository;
import com.codehouse.ciciassistant.skill.domain.SkillBindingPolicy;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionRepository;
import com.codehouse.ciciassistant.skill.domain.SkillEditPolicy;
import com.codehouse.ciciassistant.skill.domain.SkillSourceType;
import com.codehouse.ciciassistant.skill.domain.SkillUpdatePolicy;
import com.codehouse.ciciassistant.skill.domain.SkillVersionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillVersionRepository;
import com.codehouse.ciciassistant.skill.domain.SkillVisibility;
import com.codehouse.ciciassistant.skill.service.FileBackedBuiltinSkillCatalog.FileBackedBuiltinSkillBundle;
import com.codehouse.ciciassistant.skill.service.FileBackedBuiltinSkillCatalog.FileBackedBuiltinSkillManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileBackedBuiltinSkillSyncService {

    private final FileBackedBuiltinSkillCatalog catalog;
    private final PlatformSkillTemplateRepository templateRepository;
    private final PlatformSkillTemplateVersionRepository templateVersionRepository;
    private final SkillDefinitionRepository skillDefinitionRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final ObjectMapper objectMapper;

    public FileBackedBuiltinSkillSyncService(FileBackedBuiltinSkillCatalog catalog,
                                             PlatformSkillTemplateRepository templateRepository,
                                             PlatformSkillTemplateVersionRepository templateVersionRepository,
                                             SkillDefinitionRepository skillDefinitionRepository,
                                             SkillVersionRepository skillVersionRepository,
                                             ObjectMapper objectMapper) {
        this.catalog = catalog;
        this.templateRepository = templateRepository;
        this.templateVersionRepository = templateVersionRepository;
        this.skillDefinitionRepository = skillDefinitionRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void syncOrg(String companyId) {
        for (FileBackedBuiltinSkillBundle bundle : catalog.listBundles()) {
            syncBundle(companyId, bundle);
        }
    }

    private void syncBundle(String companyId, FileBackedBuiltinSkillBundle bundle) {
        FileBackedBuiltinSkillManifest manifest = bundle.manifest();
        int versionNo = manifest.version() == null ? 1 : manifest.version();
        String promptFragment = catalog.readEntrypoint(bundle).trim();
        String moduleManifestJson = writeJson(moduleManifestPayload(bundle));
        String toolWhitelist = joinCsv(manifest.toolWhitelist());
        String kbWhitelist = joinCsv(manifest.kbWhitelist());
        String handoffRule = policyOrDefault(
                manifest.handoffRule(),
                "信息不足或上游不可用时明确说明，不补造事实。"
        );
        String outputContract = policyOrDefault(
                manifest.outputContract(),
                "优先输出可落地的 CloudCC 二次开发方案；涉及接口、参数或代码时必须依据已加载的官方模块文档。"
        );

        PlatformSkillTemplateEntity template = templateRepository.findByCompanyIdAndTemplateCode(companyId, manifest.skillCode())
                .orElseGet(() -> templateRepository.save(new PlatformSkillTemplateEntity(
                        companyId,
                        manifest.skillCode(),
                        manifest.name(),
                        manifest.category(),
                        manifest.description(),
                        "ACTIVE",
                        versionNo
                )));
        template.updateMetadata(manifest.name(), manifest.category(), manifest.description(), "ACTIVE");
        template.updateResource("FILE_BACKED", bundle.resourceUri(), bundle.bundleChecksum());
        if (template.getCurrentVersionNo() == null || versionNo > template.getCurrentVersionNo()) {
            template.setCurrentVersionNo(versionNo);
        }
        templateRepository.save(template);

        PlatformSkillTemplateVersionEntity templateVersion = templateVersionRepository
                .findByCompanyIdAndTemplateCodeAndVersionNo(companyId, manifest.skillCode(), versionNo)
                .orElse(null);
        if (templateVersion != null && templateVersion.getBundleChecksum() != null
                && !templateVersion.getBundleChecksum().equals(bundle.bundleChecksum())) {
            throw new IllegalStateException("File-backed builtin skill changed without version bump: " + manifest.skillCode());
        }
        if (templateVersion == null) {
            templateVersion = new PlatformSkillTemplateVersionEntity(
                    companyId,
                    manifest.skillCode(),
                    versionNo,
                    manifest.name(),
                    manifest.description(),
                    promptFragment,
                    toolWhitelist,
                    kbWhitelist,
                    handoffRule,
                    outputContract,
                    manifest.riskLevel(),
                    "sync from file-backed builtin skill bundle",
                    "PUBLISHED",
                    "system",
                    Instant.now()
            );
            templateVersion.attachFileBackedResource(
                    bundle.resourceUri(),
                    bundle.bundleChecksum(),
                    bundle.entrypointChecksum(),
                    moduleManifestJson
            );
            templateVersionRepository.save(templateVersion);
        }

        SkillDefinitionEntity skill = skillDefinitionRepository.findByCompanyIdAndSkillCode(companyId, manifest.skillCode())
                .orElseGet(() -> skillDefinitionRepository.save(new SkillDefinitionEntity(
                        companyId,
                        manifest.skillCode(),
                        manifest.name(),
                        manifest.description(),
                        true,
                        true,
                        promptFragment,
                        buildDraftSummary(manifest, bundle),
                        toolWhitelist,
                        kbWhitelist,
                        handoffRule,
                        outputContract,
                        normalizeRiskLevel(manifest.riskLevel()),
                        SkillSourceType.PLATFORM_STANDARD,
                        SkillVisibility.VISIBLE,
                        SkillEditPolicy.CONFIGURABLE,
                        SkillBindingPolicy.OPTIONAL,
                        SkillUpdatePolicy.AUTO,
                        manifest.skillCode(),
                        versionNo
                )));
        skill.applyPlatformManagedSnapshot(
                manifest.name(),
                manifest.description(),
                promptFragment,
                buildDraftSummary(manifest, bundle),
                toolWhitelist,
                kbWhitelist,
                handoffRule,
                outputContract,
                normalizeRiskLevel(manifest.riskLevel()),
                manifest.skillCode(),
                versionNo
        );
        skill = skillDefinitionRepository.save(skill);

        Long skillId = skill.getId();
        SkillVersionEntity skillVersion = skillVersionRepository
                .findByCompanyIdAndSkillIdAndVersionNo(companyId, skillId, versionNo)
                .orElseGet(() -> {
                    SkillVersionEntity created = new SkillVersionEntity(
                            companyId,
                            skillId,
                            versionNo,
                            buildDraftSummary(manifest, bundle),
                            "policy",
                            "file_backed",
                            "{}",
                            "resourceUri=" + bundle.resourceUri() + "\nbundleChecksum=" + bundle.bundleChecksum(),
                            promptFragment,
                            writeJson(Map.of(
                                    "handoffRule", handoffRule,
                                    "outputContract", outputContract
                            )),
                            toolWhitelist,
                            kbWhitelist,
                            normalizeRiskLevel(manifest.riskLevel()),
                            "file-backed builtin skill bundle synced; modules=" + manifest.modules().size(),
                            "",
                            "PUBLISHED"
                    );
                    created.applyGovernance(
                            "同步文件型内置标准技能",
                            "resourceUri=" + bundle.resourceUri() + "\nbundleChecksum=" + bundle.bundleChecksum(),
                            "FILE_BACKED",
                            "system",
                            false,
                            "PROTECTED_RUNTIME",
                            null,
                            moduleManifestJson
                    );
                    created.markPublished();
                    return skillVersionRepository.save(created);
                });
        skill.markPublished(skillVersion.getId(), "system");
        skillDefinitionRepository.save(skill);
    }

    private Map<String, Object> moduleManifestPayload(FileBackedBuiltinSkillBundle bundle) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", bundle.manifest().schemaVersion());
        payload.put("skillCode", bundle.skillCode());
        payload.put("version", bundle.manifest().version());
        payload.put("resourceUri", bundle.resourceUri());
        payload.put("bundleChecksum", bundle.bundleChecksum());
        payload.put("entrypointChecksum", bundle.entrypointChecksum());
        payload.put("modules", bundle.manifest().modules().stream().map(module -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", module.code());
            item.put("name", module.name());
            item.put("files", module.files().stream().map(file -> {
                Map<String, Object> fileItem = new LinkedHashMap<>();
                fileItem.put("name", file);
                FileBackedBuiltinSkillCatalog.FileBackedModuleFile moduleFile =
                        bundle.moduleFiles().get(module.code() + "/" + file);
                fileItem.put("checksum", moduleFile == null ? "" : moduleFile.checksum());
                return fileItem;
            }).toList());
            item.put("triggerHints", module.triggerHints() == null ? List.of() : module.triggerHints());
            return item;
        }).toList());
        return payload;
    }

    private String buildDraftSummary(FileBackedBuiltinSkillManifest manifest, FileBackedBuiltinSkillBundle bundle) {
        return String.join("\n",
                "# " + manifest.name(),
                "",
                manifest.description(),
                "",
                "- resourceType: FILE_BACKED",
                "- resourceUri: " + bundle.resourceUri(),
                "- version: " + manifest.version(),
                "- bundleChecksum: " + bundle.bundleChecksum(),
                "- modules: " + manifest.modules().stream().map(FileBackedBuiltinSkillManifest.Module::code).toList(),
                "",
                "官方模块文档正文保存在 classpath 文件资源中，数据库只保存该摘要、版本索引、checksum 和模块清单。"
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String normalizeRiskLevel(String value) {
        if (value == null || value.isBlank()) {
            return "MEDIUM";
        }
        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "LOW", "MEDIUM", "HIGH" -> normalized;
            default -> "MEDIUM";
        };
    }

    private String joinCsv(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String joined = values.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.joining(","));
        return joined.isBlank() ? null : joined;
    }

    private String policyOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
