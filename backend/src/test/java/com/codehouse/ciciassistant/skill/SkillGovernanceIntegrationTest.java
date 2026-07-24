package com.codehouse.ciciassistant.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import com.codehouse.ciciassistant.ai.service.ToolOrchestratorService;
import com.codehouse.ciciassistant.integration.service.IntegrationAppService;
import com.codehouse.ciciassistant.skill.domain.SkillApiToolRepository;
import com.codehouse.ciciassistant.skill.domain.SkillVersionRepository;
import com.codehouse.ciciassistant.skill.service.SkillResolverService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "app.skill-api.allowed-hosts=api.example.com,localhost",
        "app.skill-api.allow-localhost=true"
})
@AutoConfigureMockMvc
class SkillGovernanceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SkillVersionRepository skillVersionRepository;

    @Autowired
    private SkillApiToolRepository skillApiToolRepository;

    @Autowired
    private SkillResolverService skillResolverService;

    @Autowired
    private ToolOrchestratorService toolOrchestratorService;

    @Autowired
    private IntegrationAppService integrationAppService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldHidePlatformCoreSkillsAndBlockStandardSkillEditing() throws Exception {
        String token = loginToken("13800138111");

        MvcResult listResult = mockMvc.perform(get("/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode skills = objectMapper.readTree(listResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertThat(skills.isArray()).isTrue();
        assertThat(skills).extracting(node -> node.path("skillCode").asText())
                .doesNotContain("conversation-core", "knowledge-first", "safe-handoff")
                .contains("general-assistant");

        JsonNode generalAssistant = null;
        JsonNode aiMeetingNotetaker = null;
        for (JsonNode node : skills) {
            if ("general-assistant".equals(node.path("skillCode").asText())) {
                generalAssistant = node;
            }
            if ("ai-meeting-notetaker".equals(node.path("skillCode").asText())) {
                aiMeetingNotetaker = node;
            }
        }
        assertThat(generalAssistant).isNotNull();
        assertThat(aiMeetingNotetaker).isNotNull();
        assertThat(aiMeetingNotetaker.path("promptFragment").asText()).contains("本次沟通重点", "CRM记录");
        assertThat(aiMeetingNotetaker.path("draftSpecText").asText()).contains("客户拜访会议纪要及后续行动管理");

        mockMvc.perform(put("/skills/{id}", generalAssistant.path("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skillCode": "general-assistant",
                                  "name": "通用助手（租户改名尝试）",
                                  "description": "should be ignored",
                                  "enabled": false,
                                  "riskLevel": "HIGH"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.name").value("通用助手"))
                .andExpect(jsonPath("$.data.editPolicy").value("CONFIGURABLE"));

        mockMvc.perform(post("/skills/{id}/derive", generalAssistant.path("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skillCode": "general-assistant-derived",
                                  "name": "通用助手派生版"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldPublishVersionExportImportAndDeleteTenantCustomSkill() throws Exception {
        String token = loginToken("13800138111");

        MvcResult createdResult = mockMvc.perform(post("/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skillCode": "feat014-custom-skill",
                                  "name": "FEAT014 自定义技能",
                                  "description": "用于验证版本发布导入导出删除。",
                                  "enabled": true,
                                  "promptFragment": "处理 FEAT014 验证请求，输出结构化结果。",
                                  "draftSpecText": "1. 读取输入。\\n2. 输出验证结果。",
                                  "toolWhitelist": [],
                                  "kbWhitelist": [],
                                  "riskLevel": "MEDIUM",
                                  "changeLog": "创建初始草稿"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceType").value("TENANT_CUSTOM"))
                .andExpect(jsonPath("$.data.latestVersionPublishStatus").value("DRAFT"))
                .andReturn();

        JsonNode created = objectMapper.readTree(createdResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        long skillId = created.path("id").asLong();

        mockMvc.perform(post("/skills/{id}/publish", skillId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"changeLog": "发布 v2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.currentPublishedVersionId").isNumber());

        MvcResult versionsResult = mockMvc.perform(get("/skills/{id}/versions", skillId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode versions = objectMapper.readTree(versionsResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).path("publishStatus").asText()).isEqualTo("PUBLISHED");
        assertThat(versions.get(0).path("changeLog").asText()).contains("发布");

        MvcResult exportResult = mockMvc.perform(post("/skills/{id}/exports", skillId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.exportId").isString())
                .andReturn();
        String exportId = objectMapper.readTree(exportResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("exportId").asText();

        MvcResult download = mockMvc.perform(get("/skills/exports/{exportId}/download", exportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(download.getResponse().getContentAsByteArray()).isNotEmpty();
        assertThat(download.getResponse().getContentType()).isEqualTo("application/zip");
        assertThat(download.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).contains("skill-package.zip");
        Map<String, String> exportedFiles = unzipDownload(download.getResponse().getContentAsByteArray());
        assertThat(exportedFiles.keySet()).containsExactly(
                "manifest.json", "SKILL.md", "cici-skill.md", "prompt.md", "contract.json", "resources.json", "PACKAGE_SPEC.md", "README.md"
        );
        assertThat(exportedFiles.get("SKILL.md")).contains("external-agent entrypoint", "cici-skill.md", "PACKAGE_SPEC.md");
        assertThat(exportedFiles.get("cici-skill.md")).contains("FEAT014 自定义技能", "Instructions");
        assertThat(exportedFiles.get("PACKAGE_SPEC.md")).contains("universal-skill-package@1.0", "cici-skill-package-optimizer");
        assertThat(exportedFiles.get("README.md")).contains("cici-skill-package-optimizer/SKILL.md", "PACKAGE_SPEC.md");
        JsonNode manifest = objectMapper.readTree(exportedFiles.get("manifest.json"));
        assertThat(manifest.path("format").asText()).isEqualTo("universal-skill-package");
        assertThat(manifest.path("formatVersion").asText()).isEqualTo("1.0");

        MvcResult directDownload = mockMvc.perform(get("/skills/exports/{exportId}/download", exportId))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(directDownload.getResponse().getContentType()).isEqualTo("application/zip");
        assertThat(unzipDownload(directDownload.getResponse().getContentAsByteArray()).keySet()).containsAll(exportedFiles.keySet());

        mockMvc.perform(get("/skills"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/skills/{id}/delete-impact", skillId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canDelete").value(true));

        mockMvc.perform(delete("/skills/{id}", skillId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "测试完成"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELETED"));

        MockMultipartFile sameCodeFile = new MockMultipartFile(
                "file",
                "feat014-custom-skill.zip",
                "application/zip",
                buildImportZip("feat014-custom-skill")
        );
        MvcResult sameCodeImportResult = mockMvc.perform(multipart("/skills/imports")
                        .file(sameCodeFile)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.draft.skillCode").value("feat014-custom-skill"))
                .andReturn();
        String sameCodeImportId = objectMapper.readTree(sameCodeImportResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("importId").asText();
        mockMvc.perform(post("/skills/imports/{importId}/create", sameCodeImportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skillCode").value("feat014-custom-skill"))
                .andExpect(jsonPath("$.data.lifecycleStatus").value("DRAFT"));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "feat014-import.zip",
                "application/zip",
                buildImportZip("feat014-imported-skill")
        );
        mockMvc.perform(multipart("/skills/imports")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.draft.skillCode").value("feat014-imported-skill"))
                .andExpect(jsonPath("$.data.resourceMapping.hasUnmatchedResources").value(false));
    }

    @Test
    void shouldPersistUpdatedSkillCodeBeforePublishingTenantCustomSkill() throws Exception {
        String token = loginToken("13800138111");
        String suffix = Long.toString(System.nanoTime(), 36);
        String originalCode = "rename-code-" + suffix;
        String renamedCode = "renamed-code-" + suffix;

        MvcResult createdResult = mockMvc.perform(post("/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skillCode": "%s",
                                  "name": "技能代码改名回归",
                                  "description": "用于验证保存草稿后发布使用新技能代码。",
                                  "enabled": true,
                                  "promptFragment": "处理技能代码改名回归请求，并输出结构化结果。",
                                  "draftSpecText": "1. 读取输入。\\n2. 输出技能代码。",
                                  "toolWhitelist": [],
                                  "kbWhitelist": [],
                                  "riskLevel": "MEDIUM",
                                  "changeLog": "创建初始草稿"
                                }
                                """.formatted(originalCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skillCode").value(originalCode))
                .andReturn();
        long skillId = objectMapper.readTree(createdResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();

        mockMvc.perform(put("/skills/{id}", skillId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skillCode": "%s",
                                  "name": "技能代码改名回归",
                                  "description": "用于验证保存草稿后发布使用新技能代码。",
                                  "enabled": true,
                                  "promptFragment": "处理技能代码改名回归请求，并输出结构化结果。",
                                  "draftSpecText": "1. 读取输入。\\n2. 输出新的技能代码。",
                                  "toolWhitelist": [],
                                  "kbWhitelist": [],
                                  "riskLevel": "MEDIUM",
                                  "changeLog": "保存改名草稿"
                                }
                                """.formatted(renamedCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skillCode").value(renamedCode))
                .andExpect(jsonPath("$.data.latestVersionPublishStatus").value("DRAFT"));

        mockMvc.perform(post("/skills/{id}/publish", skillId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"changeLog": "发布改名版本"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skillCode").value(renamedCode))
                .andExpect(jsonPath("$.data.lifecycleStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.currentPublishedVersionId").isNumber());

        MvcResult reloadedResult = mockMvc.perform(get("/skills/{id}", skillId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skillCode").value(renamedCode))
                .andReturn();
        assertThat(objectMapper.readTree(reloadedResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("skillCode").asText()).isEqualTo(renamedCode);

        MvcResult versionsResult = mockMvc.perform(get("/skills/{id}/versions", skillId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode versions = objectMapper.readTree(versionsResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertThat(versions.get(0).path("publishStatus").asText()).isEqualTo("PUBLISHED");
        assertThat(versions.get(0).path("diffSummary").asText()).contains("技能：" + renamedCode);
    }

    @Test
    void shouldOnlyBlockDeleteWhenCurrentPublishedRuntimeReferencesSkill() throws Exception {
        String token = loginToken("13800138111");
        String suffix = Long.toString(System.nanoTime(), 36);
        String skillCode = "delete-impact-runtime-" + suffix;
        String agentId = "delete-impact-agent-" + suffix;

        MvcResult createdResult = mockMvc.perform(post("/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skillCode": "%s",
                                  "name": "删除影响运行时校验",
                                  "description": "用于验证历史发布快照不阻止删除。",
                                  "enabled": true,
                                  "promptFragment": "运行时删除校验。",
                                  "draftSpecText": "1. 验证删除影响。",
                                  "toolWhitelist": [],
                                  "kbWhitelist": [],
                                  "riskLevel": "LOW",
                                  "changeLog": "创建测试草稿"
                                }
                                """.formatted(skillCode)))
                .andExpect(status().isOk())
                .andReturn();
        long skillId = objectMapper.readTree(createdResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();

        jdbcTemplate.update("""
                INSERT INTO agent_definition (
                    company_id, agent_id, name, summary, greeting, model, system_prompt, handoff_rule,
                    safety_level, execution_mode, version_label, builtin, enabled, published_version_id,
                    created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                "demo-org", agentId, "删除影响 Agent", "test", "hello", "cici-default", "",
                "", "MEDIUM", "MANUAL", "v1");
        jdbcTemplate.update("""
                INSERT INTO agent_workflow_version (
                    company_id, agent_id, version_no, version_label, spec_text, workflow_code, workflow_manifest,
                    workflow_preview, compile_summary, warnings, dependencies, publish_status, created_at
                )
                VALUES (?, ?, 1, 'v1', '', '', '', '', '', '', '', 'PUBLISHED', CURRENT_TIMESTAMP)
                """, "demo-org", agentId);
        Long v1 = jdbcTemplate.queryForObject("""
                SELECT id FROM agent_workflow_version WHERE company_id = ? AND agent_id = ? AND version_no = 1
                """, Long.class, "demo-org", agentId);
        jdbcTemplate.update("""
                INSERT INTO agent_workflow_skill_ref (
                    company_id, workflow_version_id, skill_id, skill_version_id, template_code, template_version_no,
                    reference_mode, created_at
                )
                VALUES (?, ?, ?, NULL, NULL, NULL, 'always-on', CURRENT_TIMESTAMP)
                """, "demo-org", v1, skillId);
        jdbcTemplate.update("""
                UPDATE agent_definition SET published_version_id = ?, updated_at = CURRENT_TIMESTAMP
                WHERE company_id = ? AND agent_id = ?
                """, v1, "demo-org", agentId);

        mockMvc.perform(get("/skills/{id}/delete-impact", skillId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canDelete").value(false))
                .andExpect(jsonPath("$.data.hasRuntimePins").value(true))
                .andExpect(jsonPath("$.data.blockers[0]").value("仍有已发布运行时版本引用该技能"));

        jdbcTemplate.update("""
                INSERT INTO agent_workflow_version (
                    company_id, agent_id, version_no, version_label, spec_text, workflow_code, workflow_manifest,
                    workflow_preview, compile_summary, warnings, dependencies, publish_status, created_at
                )
                VALUES (?, ?, 2, 'v2', '', '', '', '', '', '', '', 'PUBLISHED', CURRENT_TIMESTAMP)
                """, "demo-org", agentId);
        Long v2 = jdbcTemplate.queryForObject("""
                SELECT id FROM agent_workflow_version WHERE company_id = ? AND agent_id = ? AND version_no = 2
                """, Long.class, "demo-org", agentId);
        jdbcTemplate.update("""
                UPDATE agent_workflow_version SET publish_status = 'ARCHIVED'
                WHERE company_id = ? AND agent_id = ? AND version_no = 1
                """, "demo-org", agentId);
        jdbcTemplate.update("""
                UPDATE agent_definition SET published_version_id = ?, updated_at = CURRENT_TIMESTAMP
                WHERE company_id = ? AND agent_id = ?
                """, v2, "demo-org", agentId);

        mockMvc.perform(get("/skills/{id}/delete-impact", skillId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canDelete").value(true))
                .andExpect(jsonPath("$.data.hasRuntimePins").value(false));
    }

    @Test
    void shouldKeepPinnedOldVersionAsProtectedRuntimeAfterRetentionPrune() throws Exception {
        String token = loginToken("13800138111");

        MvcResult createdResult = mockMvc.perform(post("/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skillCode": "feat014-pin-protect",
                                  "name": "FEAT014 pin protect",
                                  "description": "retention pin regression",
                                  "enabled": true,
                                  "promptFragment": "初始版本",
                                  "draftSpecText": "1. 初始。",
                                  "toolWhitelist": [],
                                  "kbWhitelist": [],
                                  "riskLevel": "MEDIUM",
                                  "changeLog": "create v1"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode created = objectMapper.readTree(createdResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        long skillId = created.path("id").asLong();
        long v1Id = skillVersionRepository.findByCompanyIdAndSkillIdAndVersionNo("demo-org", skillId, 1)
                .orElseThrow(() -> new IllegalStateException("missing v1"))
                .getId();

        jdbcTemplate.update(
                "insert into agent_workflow_skill_ref(company_id, workflow_version_id, skill_id, skill_version_id, template_code, template_version_no, reference_mode, created_at) values (?,?,?,?,?,?,?, now())",
                "demo-org", 99001L, skillId, v1Id, null, null, "PINNED"
        );

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(put("/skills/{id}", skillId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "skillCode": "feat014-pin-protect",
                                      "name": "FEAT014 pin protect",
                                      "description": "retention round %d",
                                      "enabled": true,
                                      "promptFragment": "版本 %d",
                                      "draftSpecText": "1. round %d",
                                      "toolWhitelist": [],
                                      "kbWhitelist": [],
                                      "riskLevel": "MEDIUM",
                                      "changeLog": "save round %d"
                                    }
                                    """.formatted(i + 1, i + 2, i + 1, i + 1)))
                    .andExpect(status().isOk());
        }

        MvcResult versionsResult = mockMvc.perform(get("/skills/{id}/versions", skillId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode versions = objectMapper.readTree(versionsResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertThat(versions).hasSize(3);

        var v1 = skillVersionRepository.findById(v1Id).orElseThrow(() -> new IllegalStateException("missing v1 after prune"));
        assertThat(v1.getRetentionState()).isEqualTo("PROTECTED_RUNTIME");
        assertThat(v1.getRestoreVisible()).isFalse();
    }

    @Test
    void shouldPublishDeclarativeSkillApiAndInjectOnlyWhenSkillIsActive() throws Exception {
        String token = loginToken("13800138111");
        String suffix = Long.toString(System.nanoTime(), 36);
        String skillCode = "runtime-api-" + suffix;
        String agentId = "runtime-api-agent-" + suffix;

        MvcResult previewResult = mockMvc.perform(post("/skills/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skillCode": "%s",
                                  "name": "声明式 API Skill",
                                  "promptFragment": "当用户要查询线索时调用私有 API，返回简洁结果。",
                                  "specText": "1. 识别查询关键词。\\n2. 调用内嵌 API。",
                                  "toolWhitelist": [],
                                  "kbWhitelist": [],
                                  "riskLevel": "LOW",
                                  "runtimeApis": [
                                    {
                                      "apiCode": "query_leads",
                                      "displayName": "查询线索",
                                      "description": "按关键词查询潜在客户线索。",
                                      "riskLevel": "LOW",
                                      "triggerMode": "model_decide",
                                      "method": "POST",
                                      "url": "https://api.example.com/leads/search",
                                      "inputSchema": {
                                        "type": "object",
                                        "properties": {
                                          "keyword": {"type": "string", "description": "搜索关键词"},
                                          "pageSize": {"type": "integer", "default": 10}
                                        },
                                        "required": ["keyword"]
                                      },
                                      "request": {
                                        "headers": {"Content-Type": "application/json"},
                                        "body": {"q": "{{keyword}}", "limit": "{{pageSize}}"}
                                      },
                                      "response": {"resultPath": "$.data.records", "maxItems": 5, "redactPaths": ["$..token"]}
                                    }
                                  ]
                                }
                                """.formatted(skillCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runtimeApiPreview.toolDefinitions[0].function.name")
                        .value("skillapi__" + skillCode.replace("-", "_") + "__query_leads"))
                .andReturn();
        JsonNode preview = objectMapper.readTree(previewResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertThat(preview.path("runtimeApiPreview").path("errors")).hasSize(0);

        mockMvc.perform(post("/skills/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skillCode": "%s",
                                  "name": "SSRF 校验",
                                  "promptFragment": "测试。",
                                  "specText": "测试。",
                                  "runtimeApis": [
                                    {
                                      "apiCode": "bad_local",
                                      "displayName": "本地地址",
                                      "description": "不应通过。",
                                      "method": "GET",
                                      "url": "http://127.0.0.1:8080/private",
                                      "inputSchema": {"type": "object", "properties": {}, "required": []}
                                    }
                                  ]
                                }
                """.formatted(skillCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runtimeApiPreview.errors[0]").value(org.hamcrest.Matchers.containsString("127.0.0.1")));

        MvcResult createdResult = mockMvc.perform(post("/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skillCode": "%s",
                                  "name": "声明式 API Skill",
                                  "description": "发布内嵌 API。",
                                  "enabled": true,
                                  "promptFragment": "当用户要查询线索时调用私有 API，返回简洁结果。",
                                  "draftSpecText": "1. 识别查询关键词。\\n2. 调用内嵌 API。",
                                  "toolWhitelist": [],
                                  "kbWhitelist": [],
                                  "riskLevel": "LOW",
                                  "runtimeApis": [
                                    {
                                      "apiCode": "query_leads",
                                      "displayName": "查询线索",
                                      "description": "按关键词查询潜在客户线索。",
                                      "riskLevel": "LOW",
                                      "method": "POST",
                                      "url": "https://api.example.com/leads/search",
                                      "inputSchema": {
                                        "type": "object",
                                        "properties": {"keyword": {"type": "string"}},
                                        "required": ["keyword"]
                                      },
                                      "request": {"body": {"q": "{{keyword}}"}},
                                      "response": {"resultPath": "$.data.records", "maxItems": 5}
                                    }
                                  ]
                                }
                                """.formatted(skillCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runtimeApis[0].apiCode").value("query_leads"))
                .andReturn();
        long skillId = objectMapper.readTree(createdResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();

        MvcResult publishResult = mockMvc.perform(post("/skills/{id}/publish", skillId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changeLog\":\"发布声明式 API\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentPublishedVersionId").isNumber())
                .andReturn();
        long publishedVersionId = objectMapper.readTree(publishResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("currentPublishedVersionId").asLong();
        String toolName = "skillapi__" + skillCode.replace("-", "_") + "__query_leads";
        assertThat(skillApiToolRepository.findByCompanyIdAndToolNameAndEnabledTrue("demo-org", toolName)).isPresent();

        jdbcTemplate.update("""
                INSERT INTO agent_definition (
                    company_id, agent_id, name, summary, greeting, model, system_prompt, handoff_rule,
                    safety_level, execution_mode, version_label, builtin, enabled, published_version_id,
                    created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "demo-org", agentId, "声明式 API Agent", "test", "hello", "cici-default", "",
                "", "MEDIUM", "MANUAL", "v1");
        jdbcTemplate.update("""
                INSERT INTO agent_skill_binding (
                    company_id, agent_id, skill_id, activation_mode, activation_condition, priority, enabled, created_at
                )
                VALUES (?, ?, ?, 'manual', NULL, 10, TRUE, CURRENT_TIMESTAMP)
                """, "demo-org", agentId, skillId);

        SkillResolverService.ResolvedSkillContext inactive = skillResolverService.resolve(
                "demo-org", agentId, "runtime-api-inactive-" + suffix, Optional.empty());
        assertThat(inactive.allowedToolNames()).doesNotContain(toolName);

        SkillResolverService.ResolvedSkillContext active = skillResolverService.resolve(
                "demo-org", agentId, "runtime-api-active-" + suffix, Optional.of(skillCode));
        assertThat(active.allowedToolNames()).contains(toolName);
        assertThat(active.skillApiTools()).extracting(item -> item.toolName()).containsExactly(toolName);
        assertThat(active.skillApiTools()).extracting(item -> item.skillVersionId()).containsExactly(publishedVersionId);

        List<Map<String, Object>> definitions = toolOrchestratorService.getToolDefinitions(
                "demo-org", active.allowedToolNames(), active.skillApiTools());
        assertThat(definitions.stream()
                .map(item -> ((Map<?, ?>) item.get("function")).get("name"))
                .anyMatch(toolName::equals)).isTrue();

        String denied = toolOrchestratorService.executeTool(
                "demo-org", "user-1", toolName, "{\"keyword\":\"张三\"}", List.of(), List.of());
        assertThat(denied).contains("not active");

        integrationAppService.updatePlatformManaged(IntegrationAppService.APP_CODE_TAVILY,
                true, "tavily", Map.of("apiKey", "runtime-test-key"));
        AtomicReference<String> authHeader = new AtomicReference<>("");
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/leads/search", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            byte[] bytes = """
                    {"data":{"records":[{"name":"张三","token":"secret-token"}]}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.start();
        try {
            String authSkillCode = "runtime-auth-api-" + suffix;
            MvcResult authCreatedResult = mockMvc.perform(post("/skills")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "skillCode": "%s",
                                      "name": "声明式 API Auth Skill",
                                      "description": "发布带鉴权引用的内嵌 API。",
                                      "enabled": true,
                                      "promptFragment": "调用本技能私有 API 查询线索。",
                                      "draftSpecText": "1. 识别查询关键词。\\n2. 调用内嵌 API。",
                                      "toolWhitelist": [],
                                      "kbWhitelist": [],
                                      "riskLevel": "LOW",
                                      "runtimeApis": [
                                        {
                                          "apiCode": "query_leads",
                                          "displayName": "查询线索",
                                          "description": "按关键词查询潜在客户线索。",
                                          "riskLevel": "LOW",
                                          "method": "POST",
                                          "url": "http://localhost:%d/leads/search",
                                          "authRef": "integration:tavily.apiKey",
                                          "inputSchema": {
                                            "type": "object",
                                            "properties": {"keyword": {"type": "string"}},
                                            "required": ["keyword"]
                                          },
                                          "request": {"body": {"q": "{{keyword}}"}},
                                          "response": {"resultPath": "$.data.records", "maxItems": 5, "redactPaths": ["$..token"]}
                                        }
                                      ]
                                    }
                                    """.formatted(authSkillCode, server.getAddress().getPort())))
                    .andExpect(status().isOk())
                    .andReturn();
            long authSkillId = objectMapper.readTree(authCreatedResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .path("data").path("id").asLong();
            mockMvc.perform(post("/skills/{id}/publish", authSkillId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"changeLog\":\"发布带鉴权引用的声明式 API\"}"))
                    .andExpect(status().isOk());
            jdbcTemplate.update("""
                    INSERT INTO agent_skill_binding (
                        company_id, agent_id, skill_id, activation_mode, activation_condition, priority, enabled, created_at
                    )
                    VALUES (?, ?, ?, 'manual', NULL, 20, TRUE, CURRENT_TIMESTAMP)
                    """, "demo-org", agentId, authSkillId);
            String authToolName = "skillapi__" + authSkillCode.replace("-", "_") + "__query_leads";
            SkillResolverService.ResolvedSkillContext authActive = skillResolverService.resolve(
                    "demo-org", agentId, "runtime-api-auth-" + suffix, Optional.of(authSkillCode));
            String invoked = toolOrchestratorService.executeTool(
                    "demo-org", "user-1", authToolName, "{\"keyword\":\"张三\"}",
                    authActive.allowedToolNames(), List.of());
            assertThat(authHeader.get()).isEqualTo("Bearer runtime-test-key");
            assertThat(invoked).contains("张三").contains("[REDACTED]");
        } finally {
            server.stop(0);
        }

        String cloudccUserId = jdbcTemplate.queryForObject(
                """
                        SELECT m.id
                        FROM company_member m
                        JOIN user_account a ON a.id = m.account_id
                        WHERE m.company_id = ? AND a.primary_mobile = ?
                        """,
                String.class,
                "demo-org",
                "13800138111");
        jdbcTemplate.update("""
                UPDATE company_member
                SET cc_username = ?, cc_safetymark = ?
                WHERE id = ?
                """, "cloudcc-user", "cloudcc-safety", cloudccUserId);
        AtomicReference<String> cloudccTokenHeader = new AtomicReference<>("");
        HttpServer cloudccServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        cloudccServer.createContext("/domain", exchange -> {
            String payload = """
                    {"result":true,"orgapi_address":"http://localhost:%d/lightningapi"}
                    """.formatted(cloudccServer.getAddress().getPort());
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        cloudccServer.createContext("/lightningapi/api/cauth/token", exchange -> {
            byte[] bytes = """
                    {"result":true,"data":{"accessToken":"cloudcc-user-runtime-token"}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        cloudccServer.createContext("/cloudcc/leads/search", exchange -> {
            cloudccTokenHeader.set(exchange.getRequestHeaders().getFirst("accessToken"));
            byte[] bytes = """
                    {"data":{"records":[{"name":"李四","accessToken":"should-redact"}]}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        cloudccServer.start();
        try {
            integrationAppService.update("demo-org", IntegrationAppService.APP_CODE_CLOUDCC_CRM,
                    true, "cloudcc", Map.of(
                            "companyId", "cloudcc-org",
                            "orgapi_switch_address", "http://localhost:%d/domain".formatted(cloudccServer.getAddress().getPort()),
                            "clientId", "cloudcc-client",
                            "secretKey", "cloudcc-secret"
                    ));
            String cloudccSkillCode = "runtime-cloudcc-api-" + suffix;
            MvcResult cloudccCreatedResult = mockMvc.perform(post("/skills")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "skillCode": "%s",
                                      "name": "声明式 API CloudCC Auth Skill",
                                      "description": "发布带 CloudCC 用户态鉴权引用的内嵌 API。",
                                      "enabled": true,
                                      "promptFragment": "调用本技能私有 API 查询 CloudCC 线索。",
                                      "draftSpecText": "1. 识别查询关键词。\\n2. 调用内嵌 API。",
                                      "toolWhitelist": [],
                                      "kbWhitelist": [],
                                      "riskLevel": "LOW",
                                      "runtimeApis": [
                                        {
                                          "apiCode": "query_cloudcc_leads",
                                          "displayName": "查询 CloudCC 线索",
                                          "description": "按关键词查询 CloudCC 线索。",
                                          "riskLevel": "LOW",
                                          "method": "POST",
                                          "url": "http://localhost:%d/cloudcc/leads/search",
                                          "authRef": "integration:cloudcc.accessToken",
                                          "inputSchema": {
                                            "type": "object",
                                            "properties": {"keyword": {"type": "string"}},
                                            "required": ["keyword"]
                                          },
                                          "request": {"body": {"q": "{{keyword}}"}},
                                          "response": {"resultPath": "$.data.records", "maxItems": 5, "redactPaths": ["$..accessToken"]}
                                        }
                                      ]
                                    }
                                    """.formatted(cloudccSkillCode, cloudccServer.getAddress().getPort())))
                    .andExpect(status().isOk())
                    .andReturn();
            long cloudccSkillId = objectMapper.readTree(cloudccCreatedResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .path("data").path("id").asLong();
            mockMvc.perform(post("/skills/{id}/publish", cloudccSkillId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"changeLog\":\"发布带 CloudCC 用户态鉴权引用的声明式 API\"}"))
                    .andExpect(status().isOk());
            jdbcTemplate.update("""
                    INSERT INTO agent_skill_binding (
                        company_id, agent_id, skill_id, activation_mode, activation_condition, priority, enabled, created_at
                    )
                    VALUES (?, ?, ?, 'manual', NULL, 30, TRUE, CURRENT_TIMESTAMP)
                    """, "demo-org", agentId, cloudccSkillId);
            String cloudccToolName = "skillapi__" + cloudccSkillCode.replace("-", "_") + "__query_cloudcc_leads";
            SkillResolverService.ResolvedSkillContext cloudccActive = skillResolverService.resolve(
                    "demo-org", agentId, "runtime-api-cloudcc-" + suffix, Optional.of(cloudccSkillCode));
            String invoked = toolOrchestratorService.executeTool(
                    "demo-org", cloudccUserId, cloudccToolName, "{\"keyword\":\"李四\"}",
                    cloudccActive.allowedToolNames(), List.of());
            assertThat(cloudccTokenHeader.get()).isEqualTo("cloudcc-user-runtime-token");
            assertThat(invoked).contains("李四").contains("[REDACTED]");
        } finally {
            cloudccServer.stop(0);
        }
    }

    private byte[] buildImportZip(String code) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            addZipEntry(zip, "manifest.json", """
                    {"format":"universal-skill-package","formatVersion":"1.0","packageId":"%s","name":"导入技能","description":"导入验证","skill":{"riskLevel":"MEDIUM"}}
                    """.formatted(code));
            addZipEntry(zip, "SKILL.md", """
                    ---
                    name: %s
                    description: 导入验证
                    ---

                    # 导入技能

                    Read cici-skill.md and prompt.md before importing into Cici Assistant.
                    """.formatted(code));
            addZipEntry(zip, "cici-skill.md", "# 导入技能\n\n## Capability\n导入验证\n");
            addZipEntry(zip, "prompt.md", "按导入包要求输出。");
            addZipEntry(zip, "contract.json", "{\"outputContract\":\"输出导入结果\",\"riskLevel\":\"MEDIUM\"}");
            addZipEntry(zip, "resources.json", "{\"tools\":[],\"knowledgeBases\":[]}");
            addZipEntry(zip, "PACKAGE_SPEC.md", "# universal-skill-package@1.0\n");
            addZipEntry(zip, "README.md", "# 导入技能\n");
        }
        return out.toByteArray();
    }

    private Map<String, String> unzipDownload(byte[] bytes) throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    zip.transferTo(buffer);
                    files.put(entry.getName(), buffer.toString(StandardCharsets.UTF_8));
                }
            }
        }
        return files;
    }

    private void addZipEntry(ZipOutputStream zip, String name, String body) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(body.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String loginToken(String mobile) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyId": "demo-org",
                                  "mobile": "%s",
                                  "password": "szyd1234"
                                }
                                """.formatted(mobile)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(loginResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data").path("token").asText();
    }
}
