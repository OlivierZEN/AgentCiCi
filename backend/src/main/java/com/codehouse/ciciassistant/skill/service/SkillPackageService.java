package com.codehouse.ciciassistant.skill.service;

import com.codehouse.ciciassistant.ai.service.AliyunBailianClient;
import com.codehouse.ciciassistant.ai.service.ModelRouterService;
import com.codehouse.ciciassistant.kb.domain.KnowledgeBaseEntity;
import com.codehouse.ciciassistant.kb.domain.KnowledgeBaseRepository;
import com.codehouse.ciciassistant.platform.service.PlatformGovernanceService;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillEditPolicy;
import com.codehouse.ciciassistant.skill.domain.SkillSourceType;
import com.codehouse.ciciassistant.skill.domain.SkillVersionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillVersionRepository;
import com.codehouse.ciciassistant.tool.domain.ToolDefinitionEntity;
import com.codehouse.ciciassistant.tool.domain.ToolDefinitionRepository;
import com.codehouse.ciciassistant.tool.service.BuiltinToolCatalog.ToolCatalogItem;
import com.codehouse.ciciassistant.tool.service.ToolNameNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SkillPackageService {

    private static final Logger log = LoggerFactory.getLogger(SkillPackageService.class);
    private static final long MAX_PACKAGE_BYTES = 5L * 1024L * 1024L;
    private static final int MAX_FILE_TEXT_LENGTH = 40_000;
    private static final String PACKAGE_SPEC_FILENAME = "PACKAGE_SPEC.md";
    private static final List<String> ALLOWED_FILES = List.of(
            "manifest.json", "SKILL.md", "cici-skill.md", "prompt.md", "contract.json", "resources.json",
            PACKAGE_SPEC_FILENAME, "README.md"
    );
    private static final Pattern INLINE_SECRET_PATTERN = Pattern.compile(
            "(?i)(api[_-]?key|secret|token|password)\\s*[:=]\\s*[\"'][^\"'\\n\\r]{8,}[\"']");
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile("(?i)bearer\\s+[a-z0-9._\\-]{20,}");
    private static final Pattern OPENAI_KEY_PATTERN = Pattern.compile("(?i)sk-[a-z0-9]{20,}");

    private final SkillDefinitionService skillDefinitionService;
    private final SkillVersionRepository skillVersionRepository;
    private final ToolDefinitionRepository toolDefinitionRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final PlatformGovernanceService platformGovernanceService;
    private final SkillSpecSchemaValidator schemaValidator;
    private final ModelRouterService modelRouterService;
    private final AliyunBailianClient aliyunBailianClient;
    private final ObjectMapper objectMapper;
    private final Map<String, ExportArtifact> exportArtifacts = new ConcurrentHashMap<>();
    private final Map<String, ImportJob> importJobs = new ConcurrentHashMap<>();

    public SkillPackageService(SkillDefinitionService skillDefinitionService,
                               SkillVersionRepository skillVersionRepository,
                               ToolDefinitionRepository toolDefinitionRepository,
                               KnowledgeBaseRepository knowledgeBaseRepository,
                               PlatformGovernanceService platformGovernanceService,
                               SkillSpecSchemaValidator schemaValidator,
                               ModelRouterService modelRouterService,
                               AliyunBailianClient aliyunBailianClient,
                               ObjectMapper objectMapper) {
        this.skillDefinitionService = skillDefinitionService;
        this.skillVersionRepository = skillVersionRepository;
        this.toolDefinitionRepository = toolDefinitionRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.platformGovernanceService = platformGovernanceService;
        this.schemaValidator = schemaValidator;
        this.modelRouterService = modelRouterService;
        this.aliyunBailianClient = aliyunBailianClient;
        this.objectMapper = objectMapper;
    }

    public ExportJob exportSkill(String orgId, Long skillId, ExportCommand command) {
        SkillDefinitionEntity skill = skillDefinitionService.getSkill(orgId, skillId);
        if (skill.getSourceType() != SkillSourceType.TENANT_CUSTOM || skill.getEditPolicy() != SkillEditPolicy.EDITABLE) {
            throw new IllegalArgumentException("Only tenant custom editable skills can be exported");
        }
        SkillVersionEntity version = resolveExportVersion(orgId, skill, command == null || !Boolean.TRUE.equals(command.allowDraft()));
        StandardizedPackage standardized = standardizePackage(orgId, skill, version, command == null ? "system" : command.actorUserId());
        try {
            validateExportPackage(standardized.files());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Export package validation failed: " + ex.getMessage());
        }
        byte[] zipBytes = zip(standardized.files());
        String exportId = UUID.randomUUID().toString();
        String filename = skill.getSkillCode() + "-skill-package.zip";
        exportArtifacts.put(exportId, new ExportArtifact(exportId, filename, zipBytes, standardized.files()));
        return new ExportJob(
                exportId,
                "READY",
                filename,
                version.getId(),
                version.getVersionNo(),
                standardized.standardizationEngine(),
                standardized.warnings()
        );
    }

    public ExportArtifact download(String exportId) {
        ExportArtifact artifact = exportArtifacts.get(exportId);
        if (artifact == null) {
            throw new IllegalArgumentException("Export not found");
        }
        return artifact;
    }

    public ImportPreview importPackage(String orgId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Import file is required");
        }
        if (file.getSize() > MAX_PACKAGE_BYTES) {
            throw new IllegalArgumentException("Import package is too large");
        }
        try {
            Map<String, String> files = unzip(file.getBytes());
            ImportedDraftResolveResult resolved = mapImportedDraft(orgId, files);
            String importId = UUID.randomUUID().toString();
            ImportPreview preview = new ImportPreview(
                    importId,
                    "READY",
                    resolved.draft(),
                    files.keySet().stream().toList(),
                    resolved.warnings(),
                    resolved.resourceMapping()
            );
            importJobs.put(importId, new ImportJob(importId, files, preview));
            return preview;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Import package parse failed: " + ex.getMessage());
        }
    }

    @Transactional
    public SkillDefinitionEntity createImportedSkill(String orgId, String importId, String actorUserId, ImportCreateCommand command) {
        ImportJob job = importJobs.get(importId);
        if (job == null) {
            throw new IllegalArgumentException("Import not found");
        }
        ImportedDraft draft = resolveCreateDraft(orgId, job.preview().draft(), command == null ? null : command.draftOverride());
        return skillDefinitionService.createSkill(orgId, new SkillDefinitionService.UpsertCommand(
                draft.skillCode(),
                draft.name(),
                draft.description(),
                true,
                draft.promptFragment(),
                draft.draftSpecText(),
                draft.toolWhitelist(),
                draft.kbWhitelist(),
                draft.handoffRule(),
                draft.outputContract(),
                List.of(),
                draft.riskLevel(),
                "import",
                null,
                "importId=" + importId,
                "导入通用技能包",
                actorUserId == null || actorUserId.isBlank() ? "system" : actorUserId
        ));
    }

    private SkillVersionEntity resolveExportVersion(String orgId, SkillDefinitionEntity skill, boolean requirePublished) {
        if (skill.getCurrentPublishedVersionId() != null) {
            return skillVersionRepository.findById(skill.getCurrentPublishedVersionId())
                    .filter(item -> orgId.equals(item.getOrgId()) && skill.getId().equals(item.getSkillId()))
                    .orElseThrow(() -> new IllegalArgumentException("Published skill version not found"));
        }
        if (requirePublished) {
            throw new IllegalArgumentException("Skill has no published version; export draft must be explicitly allowed");
        }
        if (skill.getLatestDraftVersionId() != null) {
            return skillVersionRepository.findById(skill.getLatestDraftVersionId())
                    .filter(item -> orgId.equals(item.getOrgId()) && skill.getId().equals(item.getSkillId()))
                    .orElseThrow(() -> new IllegalArgumentException("Draft skill version not found"));
        }
        return skillVersionRepository.findTopByOrgIdAndSkillIdOrderByVersionNoDesc(orgId, skill.getId())
                .orElseThrow(() -> new IllegalArgumentException("Skill has no exportable version"));
    }

    private StandardizedPackage standardizePackage(String orgId, SkillDefinitionEntity skill, SkillVersionEntity version, String exportedBy) {
        StandardizedPackage modelPackage = tryStandardizeByModel(orgId, skill, version, exportedBy);
        StandardizedPackage standardized = modelPackage == null
                ? buildDeterministicPackage(skill, version, exportedBy)
                : modelPackage;
        return withPortableOptimizationSpec(standardized, skill);
    }

    private StandardizedPackage tryStandardizeByModel(String orgId, SkillDefinitionEntity skill, SkillVersionEntity version, String exportedBy) {
        try {
            Map<String, String> route = modelRouterService.route(orgId, "skill-authoring");
            String provider = trimToNull(route.get("provider"));
            String modelName = trimToNull(route.get("modelName"));
            if (modelName == null || "cici-default".equalsIgnoreCase(modelName) || "mock".equalsIgnoreCase(provider)) {
                return null;
            }
            List<Map<String, Object>> messages = List.of(
                    Map.of("role", "system", "content", buildExportModelSystemPrompt()),
                    Map.of("role", "user", "content", buildExportModelUserPrompt(skill, version, exportedBy))
            );
            AliyunBailianClient.ChatCompletionResult result =
                    aliyunBailianClient.chatCompletion(modelName, messages, null, true);
            String content = trimToNull(result.content());
            if (content == null || content.startsWith("Aliyun API key") || content.startsWith("Model call failed")) {
                return null;
            }
            JsonNode root = parseModelJson(content);
            if (root == null || !root.isObject()) {
                return null;
            }
            String manifestJson = text(root, "manifestJson", "");
            String skillMarkdown = text(root, "skillMarkdown", "");
            String promptMarkdown = text(root, "promptMarkdown", "");
            String contractJson = text(root, "contractJson", "");
            String resourcesJson = text(root, "resourcesJson", "");
            String readmeMarkdown = text(root, "readmeMarkdown", "");
            if (manifestJson.isBlank() || skillMarkdown.isBlank()) {
                return null;
            }
            Map<String, String> files = new LinkedHashMap<>();
            files.put("manifest.json", manifestJson);
            files.put("cici-skill.md", skillMarkdown);
            files.put("prompt.md", promptMarkdown);
            files.put("contract.json", contractJson);
            files.put("resources.json", resourcesJson);
            files.put("README.md", readmeMarkdown);
            return new StandardizedPackage(
                    files,
                    "model-standardizer",
                    List.of("已通过模型整理为 universal-skill-package@1.0，并通过 schema 与安全校验。")
            );
        } catch (Exception ex) {
            log.warn("Skill export model standardization failed, fallback to deterministic packaging: {}", ex.getMessage());
            return null;
        }
    }

    private StandardizedPackage buildDeterministicPackage(SkillDefinitionEntity skill, SkillVersionEntity version, String exportedBy) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("format", "universal-skill-package");
        manifest.put("formatVersion", "1.0");
        manifest.put("packageId", skill.getSkillCode());
        manifest.put("name", skill.getName());
        manifest.put("description", skill.getDescription());
        manifest.put("language", "zh-CN");
        manifest.put("sourceSystem", "cc-cici-assistant");
        manifest.put("exportedAt", Instant.now().toString());
        manifest.put("exportedBy", exportedBy == null || exportedBy.isBlank() ? "system" : exportedBy);
        manifest.put("skill", Map.of(
                "code", skill.getSkillCode(),
                "riskLevel", version.getRiskLevel(),
                "versionNo", version.getVersionNo(),
                "publishStatus", version.getPublishStatus(),
                "changeLog", version.getChangeLog() == null ? "" : version.getChangeLog()
        ));

        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("outputContract", nullToEmpty(skill.getOutputContract()));
        contract.put("riskLevel", version.getRiskLevel());
        contract.put("triggerHints", List.of(skill.getName(), skill.getSkillCode()));
        contract.put("userIntentExamples", List.of("执行" + skill.getName()));

        Map<String, Object> resources = new LinkedHashMap<>();
        resources.put("tools", splitCsv(version.getEffectiveToolWhitelist()).stream()
                .map(name -> Map.of("name", name, "displayName", name, "required", true, "matchStrategy", "byName"))
                .toList());
        resources.put("knowledgeBases", splitCsv(version.getEffectiveKbWhitelist()).stream()
                .map(name -> Map.of("name", name, "required", false, "matchStrategy", "byName"))
                .toList());

        Map<String, String> files = new LinkedHashMap<>();
        files.put("manifest.json", toJson(manifest));
        files.put("cici-skill.md", buildCiciSkillMarkdown(skill, version));
        files.put("prompt.md", nullToEmpty(version.getCompiledPromptFragment()));
        files.put("contract.json", toJson(contract));
        files.put("resources.json", toJson(resources));
        files.put("README.md", buildDefaultReadme(skill));
        return new StandardizedPackage(
                files,
                "schema-standardizer-fallback",
                List.of("模型标准化不可用，已回退到确定性标准化并通过 schema 与安全校验。")
        );
    }

    private StandardizedPackage withPortableOptimizationSpec(StandardizedPackage source, SkillDefinitionEntity skill) {
        Map<String, String> sourceFiles = source.files() == null ? Map.of() : source.files();
        Map<String, String> files = new LinkedHashMap<>();
        copyIfPresent(sourceFiles, files, "manifest.json");
        String ciciSkillMarkdown = sourceFiles.getOrDefault("cici-skill.md", "");
        files.put("SKILL.md", buildExternalSkillMarkdown(skill, ciciSkillMarkdown));
        copyIfPresent(sourceFiles, files, "cici-skill.md");
        copyIfPresent(sourceFiles, files, "prompt.md");
        copyIfPresent(sourceFiles, files, "contract.json");
        copyIfPresent(sourceFiles, files, "resources.json");
        files.put(PACKAGE_SPEC_FILENAME, buildPackageSpec());
        files.put("README.md", augmentReadme(sourceFiles.get("README.md"), skill));
        sourceFiles.forEach((name, value) -> {
            if (!files.containsKey(name)) {
                files.put(name, value);
            }
        });
        return new StandardizedPackage(files, source.standardizationEngine(), source.warnings());
    }

    private void copyIfPresent(Map<String, String> source, Map<String, String> target, String name) {
        if (source.containsKey(name)) {
            target.put(name, source.get(name));
        }
    }

    private String augmentReadme(String rawReadme, SkillDefinitionEntity skill) {
        String readme = trimToNull(rawReadme);
        if (readme == null) {
            readme = buildDefaultReadme(skill);
        }
        if (readme.contains("cici-skill-package-optimizer")) {
            return readme.endsWith("\n") ? readme : readme + "\n";
        }
        return readme.stripTrailing()
                + "\n\n## External Optimization\n\n"
                + "This package uses `universal-skill-package@1.0` and can be optimized by an external agent that has loaded "
                + "`cici-skill-package-optimizer/SKILL.md`. The optimizer must follow `PACKAGE_SPEC.md`, keep all files at the zip root, "
                + "and avoid adding secrets, credentials, tool runtime configuration, or knowledge-base content.\n\n"
                + "优化后请保持 zip 根目录文件结构不变，再导入 Cici Assistant。导入后仍会先落为租户自定义技能草稿，发布需在系统内完成。\n";
    }

    private String buildDefaultReadme(SkillDefinitionEntity skill) {
        return "# " + nullToEmpty(skill.getName()) + "\n\n通用技能包，格式 universal-skill-package@1.0。\n";
    }

    private String buildCiciSkillMarkdown(SkillDefinitionEntity skill, SkillVersionEntity version) {
        return "# " + nullToEmpty(skill.getName())
                + "\n\n## Capability\n\n" + nullToEmpty(skill.getDescription())
                + "\n\n## Instructions\n\n" + nullToEmpty(version.getSpecText())
                + "\n\n## Escalation\n\n" + nullToEmpty(skill.getHandoffRule())
                + "\n";
    }

    private String buildExternalSkillMarkdown(SkillDefinitionEntity skill, String ciciSkillMarkdown) {
        return """
                ---
                name: %s
                description: %s
                ---

                # %s

                This is the external-agent entrypoint for a Cici Assistant `universal-skill-package@1.0` package.

                ## How To Use This Package

                - Read `PACKAGE_SPEC.md` before editing or repackaging this skill.
                - Treat `cici-skill.md` as the Cici Assistant skill specification that will be imported into Cici as `draftSpecText`.
                - Treat `prompt.md` as the runtime prompt fragment that will be imported into Cici as `promptFragment`.
                - Treat `contract.json` as the output contract, risk level, trigger hints, and user intent examples.
                - Treat `resources.json` as dependency names and matching hints only. Do not add credentials or runtime configuration.

                ## Safety Rules

                - Preserve `manifest.json` fields `format`, `formatVersion`, and `packageId` unless the user explicitly asks to create a different skill.
                - Keep JSON files valid.
                - Do not add secrets, tokens, passwords, API keys, connection strings, private credentials, tool runtime configuration, or knowledge-base content.
                - Repackage files at the zip root so Cici Assistant can import the package.

                ## Cici Skill Summary

                %s
                """.formatted(
                nullToEmpty(skill.getSkillCode()),
                nullToEmpty(skill.getDescription()),
                nullToEmpty(skill.getName()),
                trimToNull(ciciSkillMarkdown) == null ? nullToEmpty(skill.getDescription()) : ciciSkillMarkdown.strip()
        );
    }

    private String buildPackageSpec() {
        return """
                # universal-skill-package@1.0

                ## Purpose

                This package is an exchange format for Cici Assistant tenant custom skills. It is intended for review, optimization, and re-import into Cici Assistant.
                External agents should load `cici-skill-package-optimizer/SKILL.md` before editing this package.

                这是 Cici Assistant 租户自定义技能的交换格式，用于外部审阅、优化和反向导入。
                外部智能体优化前应先加载 `cici-skill-package-optimizer/SKILL.md`。

                ## Files

                - `manifest.json`: package identity and metadata.
                - `SKILL.md`: external-agent entrypoint for understanding and using this package.
                - `cici-skill.md`: Cici Assistant skill specification imported as draft spec text.
                - `prompt.md`: runtime prompt fragment and the primary optimization target.
                - `contract.json`: output contract, risk level, trigger hints, and examples.
                - `resources.json`: external resource dependencies by name only.
                - `README.md`: human-facing package instructions.
                - `PACKAGE_SPEC.md`: this package format specification.

                ## Editing Rules

                - Preserve `manifest.format = "universal-skill-package"`.
                - Preserve `manifest.formatVersion = "1.0"`.
                - Preserve `manifest.packageId` unless the user explicitly wants a different skill code.
                - Keep all JSON files valid UTF-8 JSON.
                - Optimize `prompt.md` for clarity, sequencing, tool-use discipline, and safety.
                - Optimize `SKILL.md` for external-agent readability and usage guidance.
                - Optimize `cici-skill.md` for Cici capability boundaries and operator readability.
                - Optimize `contract.json` for clear output expectations and trigger examples.
                - `resources.json` may list resource names and matching hints only.
                - Do not add secrets, tokens, passwords, API keys, connection strings, private credentials, tool runtime configuration, or knowledge-base content.

                ## Re-import Contract

                After optimization, zip these files at the package root and import the zip into Cici Assistant. Cici Assistant will map tools and knowledge bases by name in the importing organization. Missing resources must be resolved inside Cici Assistant after import.

                优化完成后，请把这些文件重新打包在 zip 根目录，再导入 Cici Assistant。工具和知识库会在导入组织内按名称映射，缺失资源需在系统内处理。
                """;
    }

    private ImportedDraftResolveResult mapImportedDraft(String orgId, Map<String, String> files) throws Exception {
        JsonNode manifest = parseJson(files.get("manifest.json"));
        JsonNode contract = parseJson(files.get("contract.json"));
        JsonNode resources = parseJson(files.get("resources.json"));
        String packageId = text(manifest, "packageId", "imported-skill");
        String skillCode = sanitizeSkillCode(packageId);
        String name = text(manifest, "name", "导入技能");
        String description = text(manifest, "description", "");
        String prompt = files.getOrDefault("prompt.md", "");
        String spec = files.getOrDefault("cici-skill.md", "");
        String outputContract = text(contract, "outputContract", "");
        String riskLevel = normalizeRiskLevel(text(contract, "riskLevel", text(manifest.path("skill"), "riskLevel", "MEDIUM")));

        List<String> requestedTools = new ArrayList<>();
        resources.path("tools").forEach(node -> {
            String value = text(node, "name", "");
            if (!value.isBlank()) {
                requestedTools.add(value);
            }
        });
        List<String> requestedKbs = new ArrayList<>();
        resources.path("knowledgeBases").forEach(node -> {
            String value = text(node, "name", "");
            if (!value.isBlank()) {
                requestedKbs.add(value);
            }
        });

        ResourceMapping mapping = mapImportedResources(orgId, requestedTools, requestedKbs);
        List<String> warnings = new ArrayList<>();
        warnings.add("已从通用技能包映射为租户自定义技能草稿。");
        warnings.addAll(buildResourceMappingWarnings(mapping));
        ImportedDraft draft = new ImportedDraft(
                skillCode,
                name,
                description,
                prompt,
                spec,
                mapping.matchedToolNames(),
                mapping.matchedKnowledgeBaseIds(),
                "涉及权限、承诺或事实不足时转人工确认。",
                outputContract,
                riskLevel,
                List.copyOf(warnings)
        );
        return new ImportedDraftResolveResult(draft, List.copyOf(warnings), mapping);
    }

    private ResourceMapping mapImportedResources(String orgId, List<String> requestedTools, List<String> requestedKnowledgeBases) {
        Set<String> enabledToolNames = loadEnabledToolNames(orgId);
        Map<String, String> kbNameToId = new LinkedHashMap<>();
        Set<String> kbIds = new LinkedHashSet<>();
        for (KnowledgeBaseEntity item : knowledgeBaseRepository.findByOrgIdAndStatusNotOrderByIdDesc(orgId, "DELETED")) {
            String id = String.valueOf(item.getId());
            kbIds.add(id);
            kbNameToId.put(item.getName().toLowerCase(Locale.ROOT), id);
        }

        List<ResourceMappingItem> toolMappings = new ArrayList<>();
        LinkedHashSet<String> matchedTools = new LinkedHashSet<>();
        for (String item : dedupTrimmed(requestedTools)) {
            String canonical = ToolNameNormalizer.canonicalize(item);
            String matched = null;
            if (canonical != null && enabledToolNames.contains(canonical)) {
                matched = canonical;
            } else if (enabledToolNames.contains(item)) {
                matched = item;
            }
            if (matched == null) {
                toolMappings.add(new ResourceMappingItem(item, null, false, "工具不存在或未启用"));
            } else {
                matchedTools.add(matched);
                toolMappings.add(new ResourceMappingItem(item, matched, true, "已匹配"));
            }
        }

        List<ResourceMappingItem> kbMappings = new ArrayList<>();
        LinkedHashSet<String> matchedKbIds = new LinkedHashSet<>();
        for (String item : dedupTrimmed(requestedKnowledgeBases)) {
            String matchedId = null;
            if (kbIds.contains(item)) {
                matchedId = item;
            } else {
                matchedId = kbNameToId.get(item.toLowerCase(Locale.ROOT));
            }
            if (matchedId == null) {
                kbMappings.add(new ResourceMappingItem(item, null, false, "知识库不存在或已删除"));
            } else {
                matchedKbIds.add(matchedId);
                kbMappings.add(new ResourceMappingItem(item, matchedId, true, "已匹配"));
            }
        }

        List<String> unmatchedTools = toolMappings.stream().filter(item -> !item.matched()).map(ResourceMappingItem::requested).toList();
        List<String> unmatchedKbs = kbMappings.stream().filter(item -> !item.matched()).map(ResourceMappingItem::requested).toList();

        return new ResourceMapping(
                List.copyOf(toolMappings),
                List.copyOf(kbMappings),
                List.copyOf(matchedTools),
                List.copyOf(matchedKbIds),
                unmatchedTools,
                unmatchedKbs,
                !unmatchedTools.isEmpty() || !unmatchedKbs.isEmpty()
        );
    }

    private Set<String> loadEnabledToolNames(String orgId) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (ToolCatalogItem item : platformGovernanceService.listEffectiveBuiltinTools(orgId)) {
            String canonical = ToolNameNormalizer.canonicalize(item.toolName());
            if (canonical != null && !canonical.isBlank()) {
                names.add(canonical);
            }
        }
        for (ToolDefinitionEntity item : toolDefinitionRepository.findByOrgIdAndEnabledTrue(orgId)) {
            String canonical = ToolNameNormalizer.canonicalize(item.getToolName());
            if (canonical != null && !canonical.isBlank()) {
                names.add(canonical);
            }
        }
        return Set.copyOf(names);
    }

    private List<String> buildResourceMappingWarnings(ResourceMapping mapping) {
        List<String> warnings = new ArrayList<>();
        if (!mapping.unmatchedToolNames().isEmpty()) {
            warnings.add("未匹配工具：" + String.join("、", mapping.unmatchedToolNames()));
        }
        if (!mapping.unmatchedKnowledgeBaseNames().isEmpty()) {
            warnings.add("未匹配知识库：" + String.join("、", mapping.unmatchedKnowledgeBaseNames()));
        }
        if (!mapping.hasUnmatchedResources()) {
            warnings.add("导入资源均已映射到当前组织可用资源。");
        }
        return warnings;
    }

    private ImportedDraft resolveCreateDraft(String orgId, ImportedDraft baseDraft, ImportedDraftOverride overrideDraft) {
        if (overrideDraft == null) {
            return baseDraft;
        }
        ResourceMapping overrideMapping = mapImportedResources(
                orgId,
                overrideDraft.toolWhitelist() == null ? List.of() : overrideDraft.toolWhitelist(),
                overrideDraft.kbWhitelist() == null ? List.of() : overrideDraft.kbWhitelist()
        );
        List<String> warnings = new ArrayList<>(baseDraft.warnings() == null ? List.of() : baseDraft.warnings());
        warnings.addAll(buildResourceMappingWarnings(overrideMapping));
        return new ImportedDraft(
                schemaValidator.sanitizeSkillCode(fallback(overrideDraft.skillCode(), baseDraft.skillCode())),
                fallback(trimToNull(overrideDraft.name()), baseDraft.name()),
                fallback(trimToNull(overrideDraft.description()), baseDraft.description()),
                fallback(trimToNull(overrideDraft.promptFragment()), baseDraft.promptFragment()),
                fallback(trimToNull(overrideDraft.draftSpecText()), baseDraft.draftSpecText()),
                overrideMapping.matchedToolNames(),
                overrideMapping.matchedKnowledgeBaseIds(),
                fallback(trimToNull(overrideDraft.handoffRule()), baseDraft.handoffRule()),
                fallback(trimToNull(overrideDraft.outputContract()), baseDraft.outputContract()),
                normalizeRiskLevel(fallback(trimToNull(overrideDraft.riskLevel()), baseDraft.riskLevel())),
                List.copyOf(warnings)
        );
    }

    private void validateExportPackage(Map<String, String> files) throws Exception {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Export package is empty");
        }
        Set<String> names = new HashSet<>(files.keySet());
        if (!names.containsAll(ALLOWED_FILES)) {
            throw new IllegalArgumentException("Export package missing required files");
        }
        validateJsonFile(files.get("manifest.json"), "manifest.json");
        validateJsonFile(files.get("contract.json"), "contract.json");
        validateJsonFile(files.get("resources.json"), "resources.json");
        JsonNode manifest = parseJson(files.get("manifest.json"));
        String format = text(manifest, "format", "");
        String formatVersion = text(manifest, "formatVersion", "");
        if (!"universal-skill-package".equals(format) || !"1.0".equals(formatVersion)) {
            throw new IllegalArgumentException("manifest format mismatch");
        }
        for (Map.Entry<String, String> entry : files.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            if (value.length() > MAX_FILE_TEXT_LENGTH) {
                throw new IllegalArgumentException("file too large: " + entry.getKey());
            }
            ensureNoSecrets(value, entry.getKey());
        }
    }

    private void validateJsonFile(String raw, String filename) throws Exception {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(filename + " is required");
        }
        parseJson(raw);
    }

    private void ensureNoSecrets(String content, String filename) {
        if (content == null || content.isBlank()) {
            return;
        }
        if (INLINE_SECRET_PATTERN.matcher(content).find()
                || BEARER_TOKEN_PATTERN.matcher(content).find()
                || OPENAI_KEY_PATTERN.matcher(content).find()) {
            throw new IllegalArgumentException("sensitive content detected in " + filename);
        }
    }

    private Map<String, String> unzip(byte[] bytes) throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory()) {
                    continue;
                }
                if (name.contains("..") || name.startsWith("/") || !ALLOWED_FILES.contains(name)) {
                    throw new IllegalArgumentException("Unsupported zip entry: " + name);
                }
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                zis.transferTo(buffer);
                files.put(name, buffer.toString(StandardCharsets.UTF_8));
            }
        }
        if (!files.containsKey("manifest.json") || !files.containsKey("cici-skill.md")) {
            throw new IllegalArgumentException("manifest.json and cici-skill.md are required");
        }
        return files;
    }

    private byte[] zip(Map<String, String> files) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, String> entry : files.entrySet()) {
                    zos.putNextEntry(new ZipEntry(entry.getKey()));
                    zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }
            }
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Export package build failed");
        }
    }

    private JsonNode parseJson(String raw) throws Exception {
        if (raw == null || raw.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(raw);
    }

    private JsonNode parseModelJson(String content) {
        String raw = trimToNull(content);
        if (raw == null) {
            return null;
        }
        String candidate = raw;
        if (candidate.startsWith("```")) {
            int firstLine = candidate.indexOf('\n');
            int lastFence = candidate.lastIndexOf("```");
            if (firstLine > 0 && lastFence > firstLine) {
                candidate = candidate.substring(firstLine + 1, lastFence).trim();
            }
        }
        try {
            return objectMapper.readTree(candidate);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String buildExportModelSystemPrompt() {
        return """
                你是企业技能包标准化编译器。请把输入技能定义转换成 universal-skill-package@1.0。
                严格输出一个 JSON 对象，不要 markdown 代码块。顶层字段必须是：
                manifestJson, skillMarkdown, promptMarkdown, contractJson, resourcesJson, readmeMarkdown。
                其中 manifestJson、contractJson、resourcesJson 必须是可解析 JSON 字符串。
                skillMarkdown 会被保存为 cici-skill.md；系统会额外生成通用外部智能体入口 SKILL.md。
                禁止输出任何密钥、token、密码、连接串或凭据。
                资源只保留工具名和知识库标识，不输出内部配置或机密信息。
                """;
    }

    private String buildExportModelUserPrompt(SkillDefinitionEntity skill, SkillVersionEntity version, String exportedBy) {
        return """
                skillCode: %s
                name: %s
                description: %s
                riskLevel: %s
                versionNo: %s
                publishStatus: %s
                changeLog: %s
                promptFragment:
                %s

                specText:
                %s

                toolWhitelist(csv):
                %s

                kbWhitelist(csv):
                %s

                handoffRule:
                %s

                outputContract:
                %s

                exportedBy: %s
                language: zh-CN
                """.formatted(
                nullToEmpty(skill.getSkillCode()),
                nullToEmpty(skill.getName()),
                nullToEmpty(skill.getDescription()),
                nullToEmpty(version.getRiskLevel()),
                version.getVersionNo() == null ? "" : String.valueOf(version.getVersionNo()),
                nullToEmpty(version.getPublishStatus()),
                nullToEmpty(version.getChangeLog()),
                nullToEmpty(version.getCompiledPromptFragment()),
                nullToEmpty(version.getSpecText()),
                nullToEmpty(version.getEffectiveToolWhitelist()),
                nullToEmpty(version.getEffectiveKbWhitelist()),
                nullToEmpty(skill.getHandoffRule()),
                nullToEmpty(skill.getOutputContract()),
                nullToEmpty(exportedBy)
        );
    }

    private String sanitizeSkillCode(String raw) {
        String normalized = (raw == null ? "imported-skill" : raw).trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-_]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        if (normalized.length() < 2) {
            normalized = "imported-skill";
        }
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }

    private String text(JsonNode node, String field, String fallback) {
        if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) {
            return fallback;
        }
        String value = node.path(field).asText("");
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String normalizeRiskLevel(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "LOW", "MEDIUM", "HIGH" -> value;
            default -> "MEDIUM";
        };
    }

    private List<String> dedupTrimmed(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String item : raw) {
            String cleaned = trimToNull(item);
            if (cleaned != null) {
                out.add(cleaned);
            }
        }
        return List.copyOf(out);
    }

    private String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String fallback(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private String nullToEmpty(String raw) {
        return raw == null ? "" : raw;
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

    public record ExportCommand(Boolean allowDraft, String actorUserId) {
    }

    public record ExportJob(String exportId,
                            String status,
                            String filename,
                            Long skillVersionId,
                            Integer versionNo,
                            String standardizationEngine,
                            List<String> warnings) {
    }

    public record ExportArtifact(String exportId, String filename, byte[] bytes, Map<String, String> files) {
        public String base64() {
            return Base64.getEncoder().encodeToString(bytes);
        }
    }

    public record ImportPreview(String importId,
                                String status,
                                ImportedDraft draft,
                                List<String> files,
                                List<String> warnings,
                                ResourceMapping resourceMapping) {
    }

    public record ImportedDraft(String skillCode,
                                String name,
                                String description,
                                String promptFragment,
                                String draftSpecText,
                                List<String> toolWhitelist,
                                List<String> kbWhitelist,
                                String handoffRule,
                                String outputContract,
                                String riskLevel,
                                List<String> warnings) {
    }

    public record ImportedDraftOverride(String skillCode,
                                        String name,
                                        String description,
                                        String promptFragment,
                                        String draftSpecText,
                                        List<String> toolWhitelist,
                                        List<String> kbWhitelist,
                                        String handoffRule,
                                        String outputContract,
                                        String riskLevel) {
    }

    public record ImportCreateCommand(ImportedDraftOverride draftOverride) {
    }

    public record ResourceMappingItem(String requested,
                                      String resolved,
                                      boolean matched,
                                      String note) {
    }

    public record ResourceMapping(List<ResourceMappingItem> toolMappings,
                                  List<ResourceMappingItem> knowledgeBaseMappings,
                                  List<String> matchedToolNames,
                                  List<String> matchedKnowledgeBaseIds,
                                  List<String> unmatchedToolNames,
                                  List<String> unmatchedKnowledgeBaseNames,
                                  boolean hasUnmatchedResources) {
    }

    private record ImportedDraftResolveResult(ImportedDraft draft, List<String> warnings, ResourceMapping resourceMapping) {
    }

    private record StandardizedPackage(Map<String, String> files, String standardizationEngine, List<String> warnings) {
    }

    private record ImportJob(String importId, Map<String, String> files, ImportPreview preview) {
    }
}
