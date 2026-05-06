package com.codehouse.ciciassistant.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.agent.domain.AgentToolBindingEntity;
import com.codehouse.ciciassistant.agent.domain.AgentToolBindingRepository;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowSkillRefRepository;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionRepository;
import com.codehouse.ciciassistant.agent.service.AgentCompileService;
import com.codehouse.ciciassistant.agent.service.AgentDefinitionService;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingEntity;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingRepository;
import com.codehouse.ciciassistant.skill.domain.SkillBindingPolicy;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionRepository;
import com.codehouse.ciciassistant.skill.domain.SkillEditPolicy;
import com.codehouse.ciciassistant.skill.domain.SkillSourceType;
import com.codehouse.ciciassistant.skill.domain.SkillUpdatePolicy;
import com.codehouse.ciciassistant.skill.domain.SkillVersionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillVersionRepository;
import com.codehouse.ciciassistant.skill.domain.SkillVisibility;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@TestPropertySource(properties = "spring.profiles.active=default")
@AutoConfigureMockMvc
class OrchestratorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AgentDefinitionRepository agentDefinitionRepository;

    @Autowired
    private AgentWorkflowVersionRepository agentWorkflowVersionRepository;

    @Autowired
    private AgentWorkflowSkillRefRepository agentWorkflowSkillRefRepository;

    @Autowired
    private AgentCompileService agentCompileService;

    @Autowired
    private AgentDefinitionService agentDefinitionService;

    @Autowired
    private SkillDefinitionRepository skillDefinitionRepository;

    @Autowired
    private SkillVersionRepository skillVersionRepository;

    @Autowired
    private AgentToolBindingRepository agentToolBindingRepository;

    @Autowired
    private AgentSkillBindingRepository agentSkillBindingRepository;

    @Test
    void shouldRunChatWithRagAndToolsAndExposeOpsMetrics() throws Exception {
        String token = loginToken("13800138006");

        mockMvc.perform(post("/kb/bootstrap-kb/chunks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Company leave policy allows 10 days annual leave.",
                                  "tags": "hr,policy"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "sessionId": "s-orch-1",
                                  "question": "what time is it and summarize leave policy",
                                  "knowledgeBaseIds": ["bootstrap-kb"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.model.modelName").value("cici-default"))
                .andExpect(jsonPath("$.data.ragContext").isArray())
                .andExpect(jsonPath("$.data.effectiveKnowledgeBaseIds[0]").value("bootstrap-kb"))
                .andExpect(jsonPath("$.data.runtimeContext.currentDate").value(LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()))
                .andExpect(jsonPath("$.data.runtimeContext.timezone").value("Asia/Shanghai"))
                .andExpect(jsonPath("$.data.answer").exists());

        // callCount asserts "at least 1" instead of exactly 1 because the Spring test context is
        // shared across test methods and the sibling test also drives a /ai/chat call.
        MvcResult metricsResult = mockMvc.perform(get("/ops/metrics/cost")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        int callCount = objectMapper.readTree(metricsResult.getResponse().getContentAsString())
                .path("data").path("callCount").asInt();
        assertThat(callCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldListDefaultSystemAgentSkillBindingsFromAgentEndpoint() throws Exception {
        String token = loginToken("13800138036");

        MvcResult result = mockMvc.perform(get("/me/agents/cici-system/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode bindings = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data")
                .path("bindings");
        assertThat(bindings.isArray()).isTrue();
        assertThat(bindings).extracting(item -> item.path("skillCode").asText())
                .contains("general-assistant", "web-search");
    }

    @Test
    void shouldResolvePhaseOneSkillsForSalesAgent() throws Exception {
        String token = loginToken("13800138116");

        MvcResult result = mockMvc.perform(post("/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "assistant-ui-sales-phase1",
                                  "agentId": "sales-agent",
                                  "question": "帮我查一下客户资料",
                                  "knowledgeBaseIds": []
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(root.path("agentId").asText()).isEqualTo("sales-agent");
        assertThat(root.path("resolvedSkills")).extracting(JsonNode::asText)
                .contains("sales-copilot")
                .doesNotContain("conversation-core", "knowledge-first", "safe-handoff");
        assertThat(root.path("effectiveToolNames")).extracting(JsonNode::asText)
                .contains("cloudcc_pageQuery");
        assertThat(root.path("runtimePolicy").path("policyBundleCode").asText()).isEqualTo("core-default");
    }

    @Test
    void shouldExposeCloudccDiscoveryToolsForDefaultCiciAgent() throws Exception {
        String token = loginToken("13800138119");

        MvcResult result = mockMvc.perform(post("/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "assistant-ui-cici-tool-scope",
                                  "agentId": "cici-system",
                                  "question": "帮我查一下客户资料，需要的话先确认可以查哪些对象和字段",
                                  "knowledgeBaseIds": []
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(root.path("agentId").asText()).isEqualTo("cici-system");
        assertThat(root.path("effectiveToolNames")).extracting(JsonNode::asText)
                .contains(
                        "cloudcc_pageQuery",
                        "cloudcc_getStandardObjects",
                        "cloudcc_getCustomObjects",
                        "cloudcc_getObjectFields"
                );
    }

    @Test
    void shouldNormalizeLegacyAgentToolIdsForApprovalAgent() throws Exception {
        String token = loginToken("13800138112");

        MvcResult result = mockMvc.perform(post("/agents/approval-agent/debug")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "input": "帮我看下待审批列表",
                                  "skillRefs": []
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(root.path("effectiveToolNames")).extracting(JsonNode::asText)
                .contains("get_pending_approvals")
                .doesNotContain("approval-fetch");
    }

    @Test
    void shouldUnionAgentDirectToolsAndSkillDeclaredToolsIncludingMcp() throws Exception {
        String orgId = "demo-org";
        String agentId = "whitelist-mcp-union";
        String token = loginToken("13800138127");

        AgentDefinitionEntity agent = agentDefinitionRepository.findByOrgIdAndAgentId(orgId, agentId)
                .orElseGet(() -> agentDefinitionRepository.save(new AgentDefinitionEntity(
                        orgId, agentId, "Whitelist MCP Agent", "test", "hello",
                        "cici-default", "", "", "MEDIUM", "MANUAL", "v1", null, false, true)));

        SkillDefinitionEntity skill = skillDefinitionRepository.findByOrgIdAndSkillCode(orgId, "whitelist-mcp-only")
                .orElseGet(() -> skillDefinitionRepository.save(new SkillDefinitionEntity(
                        orgId, "whitelist-mcp-only", "Whitelist MCP Only", "test",
                        false, true, "prompt", "spec", "get_object_list", "",
                        "", "", "LOW",
                        SkillSourceType.TENANT_CUSTOM,
                        SkillVisibility.VISIBLE,
                        SkillEditPolicy.EDITABLE,
                        SkillBindingPolicy.OPTIONAL,
                        SkillUpdatePolicy.MANUAL,
                        null,
                        null)));

        agentToolBindingRepository.deleteByOrgIdAndAgentId(orgId, agentId);
        agentSkillBindingRepository.deleteByOrgIdAndAgentId(orgId, agentId);
        agentToolBindingRepository.save(new AgentToolBindingEntity(orgId, agentId, "tavily_search", 1, true));
        agentSkillBindingRepository.save(new AgentSkillBindingEntity(
                orgId, agentId, skill.getId(), "ALWAYS", "", 1, true));

        MvcResult result = mockMvc.perform(post("/agents/{agentId}/debug", agent.getAgentId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "input": "帮我查一下潜在客户",
                                  "skillRefs": []
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(root.path("effectiveToolNames")).extracting(JsonNode::asText)
                .contains("tavily_search", "get_object_list");
        assertThat(root.path("agentDirectToolNames")).extracting(JsonNode::asText).contains("tavily_search");
        assertThat(root.path("skillDeclaredToolNames")).extracting(JsonNode::asText).contains("get_object_list");
        assertThat(root.path("skillScopedToolNames")).extracting(JsonNode::asText).contains("get_object_list");
        assertThat(root.path("warnings").isArray()).isTrue();
    }

    @Test
    void shouldKeepPublishedAgentPinnedToSkillVersionAfterSkillEdits() throws Exception {
        String orgId = "demo-org";
        String agentId = "skill-pin-agent";
        String token = loginToken("13800138129");

        AgentDefinitionEntity agent = agentDefinitionRepository.findByOrgIdAndAgentId(orgId, agentId)
                .orElseGet(() -> agentDefinitionRepository.save(new AgentDefinitionEntity(
                        orgId, agentId, "Skill Pin Agent", "test", "hello",
                        "cici-default", "", "", "MEDIUM", "MANUAL", "v1", null, false, true)));

        SkillDefinitionEntity skill = skillDefinitionRepository.findByOrgIdAndSkillCode(orgId, "skill-pin-runtime")
                .orElseGet(() -> skillDefinitionRepository.save(new SkillDefinitionEntity(
                        orgId, "skill-pin-runtime", "Skill Pin Runtime", "test",
                        false, true, "Use tavily search only.", "Use tavily search only.", "tavily_search", "",
                        "", "", "LOW",
                        SkillSourceType.TENANT_CUSTOM,
                        SkillVisibility.VISIBLE,
                        SkillEditPolicy.EDITABLE,
                        SkillBindingPolicy.OPTIONAL,
                        SkillUpdatePolicy.MANUAL,
                        null,
                        null)));
        SkillVersionEntity v1 = skillVersionRepository.save(new SkillVersionEntity(
                orgId,
                skill.getId(),
                1,
                "Use tavily search only.",
                "policy",
                "manual",
                "{}",
                "seed v1",
                "Use tavily search only.",
                "{}",
                "tavily_search",
                "",
                "LOW",
                "v1",
                "",
                "DRAFT"
        ));
        skill.setLatestDraftVersionId(v1.getId());
        skillDefinitionRepository.save(skill);

        agentToolBindingRepository.deleteByOrgIdAndAgentId(orgId, agentId);
        agentSkillBindingRepository.deleteByOrgIdAndAgentId(orgId, agentId);
        agentSkillBindingRepository.save(new AgentSkillBindingEntity(
                orgId, agentId, skill.getId(), "always-on", "", 1, true));

        AgentCompileService.CompileResult compileResult = agentCompileService.compile(
                orgId,
                new AgentCompileService.CompileCommand(
                        agentId,
                        "Skill Pin Agent",
                        "pin runtime skill version",
                        "",
                        "gpt-4.1",
                        "",
                        "用于验证已发布 Agent 固定 SkillVersion。",
                        List.of("web"),
                        List.of(),
                        List.of(),
                        List.of(),
                        "",
                        "BALANCED",
                        "copilot",
                        "v-skill-pin"
                )
        );
        int versionNo = compileResult.draftVersionNo() == null ? 0 : compileResult.draftVersionNo();
        assertThat(versionNo).isGreaterThan(0);
        agentDefinitionService.publishVersion(orgId, agentId, versionNo);

        Long publishedVersionId = agentDefinitionRepository.findByOrgIdAndAgentId(orgId, agentId)
                .map(AgentDefinitionEntity::getPublishedVersionId)
                .orElseThrow();
        assertThat(agentWorkflowSkillRefRepository.findByOrgIdAndWorkflowVersionIdOrderByIdAsc(orgId, publishedVersionId))
                .hasSize(1)
                .first()
                .extracting(item -> item.getSkillVersionId())
                .isEqualTo(v1.getId());

        skill.update(
                skill.getSkillCode(),
                "Skill Pin Runtime",
                "updated",
                true,
                "Use object list only.",
                "Use object list only.",
                "get_object_list",
                "",
                "",
                "",
                null,
                "LOW"
        );
        skillDefinitionRepository.save(skill);
        skillVersionRepository.save(new SkillVersionEntity(
                orgId,
                skill.getId(),
                2,
                "Use object list only.",
                "policy",
                "manual",
                "{}",
                "seed v2",
                "Use object list only.",
                "{}",
                "get_object_list",
                "",
                "LOW",
                "v2",
                "",
                "DRAFT"
        ));

        MvcResult chatResult = mockMvc.perform(post("/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "s-skill-pin-runtime",
                                  "agentId": "skill-pin-agent",
                                  "question": "帮我继续执行已发布版本",
                                  "knowledgeBaseIds": []
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode chatRoot = objectMapper.readTree(chatResult.getResponse().getContentAsString()).path("data");
        assertThat(chatRoot.path("effectiveToolNames")).extracting(JsonNode::asText)
                .contains("tavily_search")
                .doesNotContain("get_object_list");
        assertThat(chatRoot.path("skillDeclaredToolNames")).extracting(JsonNode::asText)
                .contains("tavily_search")
                .doesNotContain("get_object_list");
        assertThat(chatRoot.path("resolvedSkillVersions").get(0).path("skillVersionId").asLong()).isEqualTo(v1.getId());
        assertThat(chatRoot.path("resolvedSkillVersions").get(0).path("skillVersionNo").asInt()).isEqualTo(1);

        MvcResult debugResult = mockMvc.perform(post("/agents/{agentId}/debug", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "input": "调试已发布版本",
                                  "skillRefs": []
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode debugRoot = objectMapper.readTree(debugResult.getResponse().getContentAsString()).path("data");
        assertThat(debugRoot.path("skillDeclaredToolNames")).extracting(JsonNode::asText)
                .contains("tavily_search")
                .doesNotContain("get_object_list");
    }

    @Test
    void shouldPersistSessionStateAfterUserIntentHint() throws Exception {
        String token = loginToken("13800138113");
        String sessionId = "s-session-state-1";

        mockMvc.perform(post("/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "%s",
                                  "question": "先添加名单，先不要发邮件"
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk());

        MvcResult stateResult = mockMvc.perform(get("/ai/sessions/{sessionId}/state", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(true))
                .andReturn();

        JsonNode root = objectMapper.readTree(stateResult.getResponse().getContentAsString()).path("data");
        assertThat(root.path("summary").asText()).contains("hold_action");
        assertThat(root.path("stateJson").asText()).contains("hold_action");
    }

    @Test
    void shouldKeepSessionStateAcrossSecondTurn() throws Exception {
        String token = loginToken("13800138117");
        String sessionId = "s-session-state-2";

        mockMvc.perform(post("/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "%s",
                                  "question": "先添加名单，先不要发邮件"
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "%s",
                                  "question": "继续上一步，不要再重复问我活动范围"
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk());

        MvcResult stateResult = mockMvc.perform(get("/ai/sessions/{sessionId}/state", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(true))
                .andReturn();
        JsonNode stateRoot = objectMapper.readTree(stateResult.getResponse().getContentAsString()).path("data");
        assertThat(stateRoot.path("stateJson").asText())
                .contains("hold_action")
                .contains("continue_current_plan");

        MvcResult messagesResult = mockMvc.perform(get("/ai/sessions/{sessionId}/messages", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode messages = objectMapper.readTree(messagesResult.getResponse().getContentAsString()).path("data");
        assertThat(messages).hasSize(4);
        assertThat(messages.get(0).path("role").asText()).isEqualTo("user");
        assertThat(messages.get(2).path("role").asText()).isEqualTo("user");
    }

    @Test
    void shouldCaptureSessionFieldsAndNoRepeatConstraintAcrossTurns() throws Exception {
        String token = loginToken("13800138123");
        String sessionId = "s-session-state-3";

        mockMvc.perform(post("/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "%s",
                                  "question": "在春季金融活动里先添加名单，范围是保险经纪和商业银行，先不要发邮件"
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "%s",
                                  "question": "继续上一步，不要再重复问我活动范围"
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk());

        MvcResult stateResult = mockMvc.perform(get("/ai/sessions/{sessionId}/state", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(true))
                .andReturn();
        JsonNode stateRoot = objectMapper.readTree(stateResult.getResponse().getContentAsString()).path("data");
        String stateJson = stateRoot.path("stateJson").asText();
        JsonNode state = objectMapper.readTree(stateJson);
        assertThat(stateJson)
                .contains("current_object_type")
                .contains("campaign")
                .contains("target_segment_summary")
                .contains("no_repeat_confirmed_info")
                .contains("no_repeat_questions")
                .contains("execute_confirmed_action")
                .contains("send_email");
        assertThat(state.path("missing_fields")).extracting(JsonNode::asText).doesNotContain("target_segment");
    }

    @Test
    void shouldSwitchRuntimeDependenciesAcrossPublishStates() throws Exception {
        String token = loginToken("13800138111");
        String sessionId = "s-published-runtime-1";

        mockMvc.perform(put("/agents/cici-system/bindings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "knowledgeBaseIds": [],
                                  "toolIds": ["cloudcc_pageQuery"],
                                  "channels": ["web"]
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult compileV1Result = mockMvc.perform(post("/agents/cici-system/compile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "思思（CiCi）",
                                  "model": "gpt-4.1",
                                  "specText": "用于系统协作 v1",
                                  "skillRefs": ["sales-copilot"]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        int v1 = objectMapper.readTree(compileV1Result.getResponse().getContentAsString())
                .path("data").path("draftVersionNo").asInt();
        assertThat(v1).isGreaterThan(0);

        mockMvc.perform(post("/agents/cici-system/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "versionNo": %d
                                }
                                """.formatted(v1)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/agents/cici-system/bindings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "knowledgeBaseIds": [],
                                  "toolIds": ["get_pending_approvals"],
                                  "channels": ["web"]
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult chatAfterBindingChange = mockMvc.perform(post("/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "%s",
                                  "agentId": "cici-system",
                                  "question": "测试运行时依赖"
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rootAfterBindingChange = objectMapper.readTree(chatAfterBindingChange.getResponse().getContentAsString()).path("data");
        assertThat(rootAfterBindingChange.path("effectiveToolNames")).extracting(JsonNode::asText)
                .contains("cloudcc_pageQuery", "get_pending_approvals");

        MvcResult compileV2Result = mockMvc.perform(post("/agents/cici-system/compile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "思思（CiCi）",
                                  "model": "gpt-4.1",
                                  "specText": "用于系统协作 v2",
                                  "skillRefs": ["web-search"]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        int v2 = objectMapper.readTree(compileV2Result.getResponse().getContentAsString())
                .path("data").path("draftVersionNo").asInt();
        assertThat(v2).isGreaterThan(v1);

        mockMvc.perform(post("/agents/cici-system/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "versionNo": %d
                                }
                                """.formatted(v2)))
                .andExpect(status().isOk());

        MvcResult chatAfterPublishV2 = mockMvc.perform(post("/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "%s",
                                  "agentId": "cici-system",
                                  "question": "测试运行时依赖"
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rootAfterPublishV2 = objectMapper.readTree(chatAfterPublishV2.getResponse().getContentAsString()).path("data");
        assertThat(rootAfterPublishV2.path("effectiveToolNames")).extracting(JsonNode::asText)
                .contains("tavily_search", "get_pending_approvals");
        // cloudcc_pageQuery remains available via cici-system builtin augment (discovery tools), independent of manifest-only deps.

        mockMvc.perform(post("/agents/cici-system/rollback")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "versionNo": %d
                                }
                                """.formatted(v1)))
                .andExpect(status().isOk());

        MvcResult chatAfterRollback = mockMvc.perform(post("/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "%s",
                                  "agentId": "cici-system",
                                  "question": "测试运行时依赖"
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rootAfterRollback = objectMapper.readTree(chatAfterRollback.getResponse().getContentAsString()).path("data");
        assertThat(rootAfterRollback.path("effectiveToolNames")).extracting(JsonNode::asText)
                .contains("cloudcc_pageQuery", "get_pending_approvals");
    }

    @Test
    void shouldGracefullyHandleInvalidPublishedManifest() throws Exception {
        String token = loginToken("13800138118");
        String orgId = "demo-org";

        agentDefinitionService.replaceBindings(orgId, "cici-system",
                new AgentDefinitionService.ReplaceBindingsCommand(List.of(), List.of("cloudcc_pageQuery"), List.of("web")));
        AgentCompileService.CompileResult compileResult = agentCompileService.compile(
                orgId,
                new AgentCompileService.CompileCommand(
                        "cici-system",
                        "思思（CiCi）",
                        "异常清单测试",
                        "",
                        "gpt-4.1",
                        "",
                        "用于异常清单测试",
                        List.of("web"),
                        List.of(),
                        List.of(),
                        List.of("web-search"),
                        "",
                        "",
                        "",
                        "v-fallback"
                )
        );
        int versionNo = compileResult.draftVersionNo() == null ? 0 : compileResult.draftVersionNo();
        assertThat(versionNo).isGreaterThan(0);
        agentDefinitionService.publishVersion(orgId, "cici-system", versionNo);

        AgentDefinitionEntity definition = agentDefinitionRepository.findByOrgIdAndAgentId(orgId, "cici-system")
                .orElseThrow();
        Long publishedVersionId = definition.getPublishedVersionId();
        AgentWorkflowVersionEntity published = agentWorkflowVersionRepository.findById(publishedVersionId)
                .orElseThrow();
        published.setPublishStatus("PUBLISHED");
        // Force invalid JSON so runtime published-manifest parsing fails and should fallback.
        java.lang.reflect.Field manifestField = AgentWorkflowVersionEntity.class.getDeclaredField("workflowManifest");
        manifestField.setAccessible(true);
        manifestField.set(published, "{invalid-json");
        agentWorkflowVersionRepository.save(published);

        MvcResult chatResult = mockMvc.perform(post("/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "s-published-invalid-fallback",
                                  "agentId": "cici-system",
                                  "question": "测试回退路径"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(chatResult.getResponse().getContentAsString()).path("data");
        assertThat(root.path("answer").asText()).isNotBlank();
        assertThat(root.path("effectiveToolNames")).extracting(JsonNode::asText)
                .contains("tavily_search");
    }

    @Test
    void shouldExposePublishedRuntimePolicyInChatResponse() throws Exception {
        String token = loginToken("13800138119");
        String orgId = "demo-org";

        AgentCompileService.CompileResult compileResult = agentCompileService.compile(
                orgId,
                new AgentCompileService.CompileCommand(
                        "cici-system",
                        "思思（CiCi）",
                        "运行时策略验证",
                        "",
                        "gpt-4.1",
                        "",
                        "用于运行时策略验证",
                        List.of("web"),
                        List.of(),
                        List.of(),
                        List.of("web-search"),
                        "",
                        "BALANCED",
                        "auto",
                        "v-runtime-policy"
                )
        );
        int versionNo = compileResult.draftVersionNo() == null ? 0 : compileResult.draftVersionNo();
        assertThat(versionNo).isGreaterThan(0);
        agentDefinitionService.publishVersion(orgId, "cici-system", versionNo);

        MvcResult chatResult = mockMvc.perform(post("/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "s-runtime-policy-1",
                                  "agentId": "cici-system",
                                  "question": "测试运行时策略"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(chatResult.getResponse().getContentAsString()).path("data");
        assertThat(root.path("runtimePolicy").path("maxToolCalls").asInt()).isEqualTo(4);
        assertThat(root.path("runtimePolicy").path("publishedVersionId").asText()).isNotBlank();
        assertThat(root.path("runtimeExecution").path("status").asText()).isEqualTo("published-executed");
        assertThat(root.path("runtimeExecution").path("output").asText()).contains("Published workflow");
        assertThat(root.path("runtimeExecution").path("trace")).extracting(JsonNode::asText)
                .contains("workflow-node:start", "workflow-node:code:intent-classify", "workflow-node:end:published-executed");
        assertThat(root.path("runtimeGovernance").path("policyBundle").path("bundleCode").asText()).isEqualTo("core-default");
        assertThat(root.path("runtimeExecution").path("resolvedSkillVersions").isArray()).isTrue();
        assertThat(root.path("runtimeExecution").path("contextSnapshot").path("runtimeSource").asText()).isEqualTo("published");
        assertThat(root.path("runtimeExecution").path("contextSnapshot").path("intent").asText()).isEqualTo("classified");
        assertThat(root.path("runtimeExecution").path("contextSnapshot").path("branchHit").asText()).isNotBlank();
        assertThat(root.path("runtimeExecution").path("contextSnapshot").path("nodeMetrics").isArray()).isTrue();
        assertThat(root.path("runtimeExecution").path("contextSnapshot").path("replayHint").asText()).contains("Replay");
        assertThat(root.path("runtimeExecution").path("contextSnapshot").path("nodeMetrics").get(0).path("ioSummary").path("input").asText())
                .isNotBlank();
        assertThat(root.path("runtimeExecution").path("contextSnapshot").path("nodeMetrics").get(0).path("ioPayload").path("input").isObject())
                .isTrue();
    }

    @Test
    void shouldUsePublishedWorkflowInDebugRuntime() throws Exception {
        String token = loginToken("13800138120");
        String orgId = "demo-org";

        AgentCompileService.CompileResult compileResult = agentCompileService.compile(
                orgId,
                new AgentCompileService.CompileCommand(
                        "cici-system",
                        "思思（CiCi）",
                        "debug runtime workflow",
                        "",
                        "gpt-4.1",
                        "",
                        "用于 debug runtime workflow",
                        List.of("web"),
                        List.of(),
                        List.of(),
                        List.of("web-search"),
                        "",
                        "BALANCED",
                        "auto",
                        "v-debug-runtime"
                )
        );
        int versionNo = compileResult.draftVersionNo() == null ? 0 : compileResult.draftVersionNo();
        assertThat(versionNo).isGreaterThan(0);
        agentDefinitionService.publishVersion(orgId, "cici-system", versionNo);

        MvcResult result = mockMvc.perform(post("/agents/cici-system/debug")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "input": "请帮我执行流程",
                                  "skillRefs": []
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(root.path("runtimeSource").asText()).isEqualTo("published_version");
        assertThat(root.path("publishedVersionId").asLong()).isGreaterThan(0L);
        assertThat(root.path("workflowCodePreview").asText()).contains("runAgent");
        assertThat(root.path("executionStatus").asText()).isEqualTo("published-executed");
        assertThat(root.path("executionOutput").asText()).contains("Published workflow");
        assertThat(root.path("executionTrace")).extracting(JsonNode::asText)
                .contains("workflow-node:start", "workflow-node:code:intent-classify", "workflow-node:end:published-executed");
        assertThat(root.path("contextSnapshot").path("runtimeSource").asText()).isEqualTo("published");
        assertThat(root.path("contextSnapshot").path("responsePlanned").asBoolean()).isTrue();
        assertThat(root.path("policyBundle").path("bundleCode").asText()).isEqualTo("core-default");
        assertThat(root.path("runtimeGovernanceNotes")).extracting(JsonNode::asText)
                .anyMatch(item -> item.contains("Policy bundle: core-default@v"))
                .anyMatch(item -> item.contains("Published runtime keeps using pinned skill refs"));
        assertThat(root.path("resolvedSkillVersions").isArray()).isTrue();
        assertThat(root.path("contextSnapshot").path("nodeMetrics").isArray()).isTrue();
        assertThat(root.path("contextSnapshot").path("errorType").asText()).isEmpty();
        assertThat(root.path("contextSnapshot").path("replayHint").asText()).contains("Replay");
        assertThat(root.path("contextSnapshot").path("nodeMetrics").get(0).path("ioSummary").path("output").asText())
                .isNotBlank();
        assertThat(root.path("contextSnapshot").path("nodeMetrics").get(0).path("ioPayload").path("output").isObject())
                .isTrue();
    }

    @Test
    void shouldExposeFallbackReplayMetadataInDebugRuntime() throws Exception {
        String token = loginToken("13800138121");
        String orgId = "demo-org";

        agentDefinitionRepository.findByOrgIdAndAgentId(orgId, "cici-system").ifPresent(definition -> {
            definition.setPublishedVersionId(null);
            agentDefinitionRepository.save(definition);
        });

        MvcResult result = mockMvc.perform(post("/agents/cici-system/debug")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "input": "走 fallback 路径",
                                  "skillRefs": []
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(root.path("executionStatus").asText()).isEqualTo("fallback-executed");
        assertThat(root.path("contextSnapshot").path("runtimeSource").asText()).isEqualTo("fallback");
        assertThat(root.path("contextSnapshot").path("replayHint").asText()).contains("published version");
        assertThat(root.path("contextSnapshot").path("nodeMetrics").get(0).path("ioPayload").path("input").isObject())
                .isTrue();
    }

    @Test
    void shouldExposeInvalidReplayMetadataInDebugRuntime() throws Exception {
        String token = loginToken("13800138122");
        String orgId = "demo-org";

        AgentCompileService.CompileResult compileResult = agentCompileService.compile(
                orgId,
                new AgentCompileService.CompileCommand(
                        "cici-system",
                        "思思（CiCi）",
                        "invalid workflow debug",
                        "",
                        "gpt-4.1",
                        "",
                        "用于 invalid workflow debug",
                        List.of("web"),
                        List.of(),
                        List.of(),
                        List.of("web-search"),
                        "",
                        "BALANCED",
                        "auto",
                        "v-debug-invalid"
                )
        );
        int versionNo = compileResult.draftVersionNo() == null ? 0 : compileResult.draftVersionNo();
        assertThat(versionNo).isGreaterThan(0);
        agentDefinitionService.publishVersion(orgId, "cici-system", versionNo);

        AgentDefinitionEntity definition = agentDefinitionRepository.findByOrgIdAndAgentId(orgId, "cici-system")
                .orElseThrow();
        AgentWorkflowVersionEntity published = agentWorkflowVersionRepository.findById(definition.getPublishedVersionId())
                .orElseThrow();
        java.lang.reflect.Field workflowCodeField = AgentWorkflowVersionEntity.class.getDeclaredField("workflowCode");
        workflowCodeField.setAccessible(true);
        workflowCodeField.set(published, "export const x = 1;");
        agentWorkflowVersionRepository.save(published);

        MvcResult result = mockMvc.perform(post("/agents/cici-system/debug")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "input": "走 invalid 路径",
                                  "skillRefs": []
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(root.path("executionStatus").asText()).isEqualTo("published-invalid");
        assertThat(root.path("contextSnapshot").path("errorType").asText()).isEqualTo("missing-runAgent");
        assertThat(root.path("contextSnapshot").path("errorNode").asText()).isEqualTo("validate-entry");
        assertThat(root.path("contextSnapshot").path("replayHint").asText()).contains("runAgent");
        assertThat(root.path("contextSnapshot").path("nodeMetrics").get(0).path("ioPayload").path("output").isObject())
                .isTrue();
    }

    @Test
    void shouldExposeRuntimeExecutionsAndTriggersAfterDebug() throws Exception {
        String token = loginToken("13800138111");
        mockMvc.perform(post("/agents/cici-system/debug")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "input": "执行记录探针",
                                  "skillRefs": []
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult execList = mockMvc.perform(get("/agents/cici-system/runtime/executions?limit=20")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode execData = objectMapper.readTree(execList.getResponse().getContentAsString()).path("data");
        assertThat(execData.isArray()).isTrue();
        assertThat(execData.size()).isGreaterThan(0);
        assertThat(execData.get(0).path("source").asText()).isEqualTo("TRY_RUN");

        MvcResult trig = mockMvc.perform(get("/agents/cici-system/runtime/triggers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode trigData = objectMapper.readTree(trig.getResponse().getContentAsString()).path("data");
        assertThat(trigData.path("agentId").asText()).isEqualTo("cici-system");
        assertThat(trigData.path("lifecycle").asText()).isNotBlank();
        assertThat(trigData.path("channelTriggers").isArray()).isTrue();
    }

    @Test
    void shouldInferScheduleTriggersFromSpecAfterCompile() throws Exception {
        String token = loginToken("13800138006");
        String orgId = "demo-org";

        agentDefinitionRepository.findByOrgIdAndAgentId(orgId, "cici-system").ifPresent(definition -> {
            definition.setPublishedVersionId(null);
            agentDefinitionRepository.save(definition);
        });

        String spec = """
                1. 每天上午9点，发送当天AI简报
                2. 每周五下午3点，给高层发送产品周报
                3. 每月5号，给高层发送上月订阅收入台账
                """;

        AgentCompileService.CompileResult compileResult = agentCompileService.compile(
                orgId,
                new AgentCompileService.CompileCommand(
                        "cici-system",
                        "思思（CiCi）",
                        "调度推导测试",
                        "",
                        "gpt-4.1",
                        "",
                        spec,
                        List.of("web"),
                        List.of(),
                        List.of(),
                        List.of("web-search"),
                        "",
                        "BALANCED",
                        "auto",
                        "v-schedule-infer"
                ));
        assertThat(compileResult.draftVersionNo()).isNotNull().isGreaterThan(0);

        MvcResult trig = mockMvc.perform(get("/agents/cici-system/runtime/triggers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode trigData = objectMapper.readTree(trig.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertThat(trigData.path("lifecycle").asText()).isEqualTo("COMPILED_DRAFT");
        JsonNode schedules = trigData.path("scheduleTriggers");
        assertThat(schedules.isArray()).isTrue();
        assertThat(schedules.size()).isGreaterThanOrEqualTo(1);
        assertThat(schedules.get(0).path("stub").asBoolean()).isTrue();
        assertThat(schedules.get(0).path("title").asText()).isNotBlank();
    }

    @Test
    void shouldPersistScheduleTriggersAfterSyncEndpoint() throws Exception {
        String token = loginToken("13800138111");
        String orgId = "demo-org";

        agentDefinitionRepository.findByOrgIdAndAgentId(orgId, "cici-system").ifPresent(definition -> {
            definition.setPublishedVersionId(null);
            agentDefinitionRepository.save(definition);
        });
        agentCompileService.compile(
                orgId,
                new AgentCompileService.CompileCommand(
                        "cici-system",
                        "思思（CiCi）",
                        "调度持久化测试",
                        "",
                        "gpt-4.1",
                        "",
                        "1. 每天上午9点发送日报\n2. 每周五下午3点发送周报",
                        List.of("web"),
                        List.of(),
                        List.of(),
                        List.of("web-search"),
                        "",
                        "BALANCED",
                        "auto",
                        "v-schedule-sync"
                ));

        MvcResult sync = mockMvc.perform(post("/agents/cici-system/runtime/schedules/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode syncData = objectMapper.readTree(sync.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertThat(syncData.path("synced").asInt()).isGreaterThanOrEqualTo(2);

        MvcResult trig = mockMvc.perform(get("/agents/cici-system/runtime/triggers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode trigData = objectMapper.readTree(trig.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertThat(trigData.path("scheduleSource").asText()).isEqualTo("persisted");
        JsonNode schedules = trigData.path("scheduleTriggers");
        assertThat(schedules.isArray()).isTrue();
        assertThat(schedules.size()).isGreaterThanOrEqualTo(2);
        assertThat(schedules.get(0).path("source").asText()).isEqualTo("SPEC_SYNC");

        String triggerKey = schedules.get(0).path("id").asText();
        mockMvc.perform(put("/agents/cici-system/runtime/schedules/" + triggerKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/agents/cici-system/runtime/schedules/run-now")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "triggerKey": "%s"
                                }
                                """.formatted(triggerKey)))
                .andExpect(status().isOk());

        MvcResult execList = mockMvc.perform(get("/agents/cici-system/runtime/executions?limit=10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode execData = objectMapper.readTree(execList.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertThat(execData.isArray()).isTrue();
        assertThat(execData.get(0).path("source").asText()).isEqualTo("SCHEDULE_STUB");
    }

    @Test
    void shouldAutoSyncSchedulesAfterPublishWhenEnabled() throws Exception {
        String token = loginToken("13800138188");
        String orgId = "demo-org";

        AgentCompileService.CompileResult compiled = agentCompileService.compile(
                orgId,
                new AgentCompileService.CompileCommand(
                        "cici-system",
                        "思思（CiCi）",
                        "发布自动同步调度测试",
                        "",
                        "gpt-4.1",
                        "",
                        "1. 每天上午9点发送日报\n2. 每周五下午3点发送周报",
                        List.of("web"),
                        List.of(),
                        List.of(),
                        List.of("web-search"),
                        "",
                        "BALANCED",
                        "auto",
                        "v-auto-sync-on-publish"
                ));
        Integer draftVersion = compiled.draftVersionNo();
        assertThat(draftVersion).isNotNull().isGreaterThan(0);

        mockMvc.perform(put("/agents/cici-system/publish-configs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "publishConfigs": {
                                    "feishu": {
                                      "appId": "",
                                      "appSecret": "",
                                      "defaultAgentCode": "cici",
                                      "pairingCommandHint": "配对",
                                      "autoSyncSchedulesOnPublish": true
                                    }
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/agents/cici-system/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "versionNo": %d
                                }
                                """.formatted(draftVersion)))
                .andExpect(status().isOk());

        MvcResult trig = mockMvc.perform(get("/agents/cici-system/runtime/triggers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode trigData = objectMapper.readTree(trig.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertThat(trigData.path("scheduleSource").asText()).isEqualTo("persisted");
        JsonNode schedules = trigData.path("scheduleTriggers");
        assertThat(schedules.isArray()).isTrue();
        assertThat(schedules.size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldPreferLatestCompiledSpecTriggersEvenWhenPublishedVersionExists() throws Exception {
        String token = loginToken("13800138000");
        String orgId = "demo-org";

        AgentCompileService.CompileResult oldCompiled = agentCompileService.compile(
                orgId,
                new AgentCompileService.CompileCommand(
                        "cici-system",
                        "思思（CiCi）",
                        "旧发布版本",
                        "",
                        "gpt-4.1",
                        "",
                        "请处理常规问答",
                        List.of("web"),
                        List.of(),
                        List.of(),
                        List.of("web-search"),
                        "",
                        "BALANCED",
                        "auto",
                        "v-old-published"
                ));
        Integer oldVersion = oldCompiled.draftVersionNo();
        assertThat(oldVersion).isNotNull().isGreaterThan(0);
        mockMvc.perform(post("/agents/cici-system/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "versionNo": %d
                                }
                                """.formatted(oldVersion)))
                .andExpect(status().isOk());

        agentCompileService.compile(
                orgId,
                new AgentCompileService.CompileCommand(
                        "cici-system",
                        "思思（CiCi）",
                        "新草稿调度语义",
                        "",
                        "gpt-4.1",
                        "",
                        "1. 每天上午9点发送日报\n2. 每周五下午3点发送周报",
                        List.of("web"),
                        List.of(),
                        List.of(),
                        List.of("web-search"),
                        "",
                        "BALANCED",
                        "auto",
                        "v-new-draft"
                ));

        MvcResult trig = mockMvc.perform(get("/agents/cici-system/runtime/triggers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode trigData = objectMapper.readTree(trig.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertThat(trigData.path("lifecycle").asText()).isEqualTo("PUBLISHED");
        assertThat(trigData.path("scheduleSource").asText()).isIn("inferred", "persisted");
        JsonNode schedules = trigData.path("scheduleTriggers");
        assertThat(schedules.isArray()).isTrue();
        assertThat(schedules.size()).isGreaterThanOrEqualTo(1);
        assertThat(schedules.get(0).path("title").asText()).isNotBlank();
    }

    @Test
    void shouldSkipVersionCreationWhenCompileInputUnchangedAndKeepHistoryForChangedBuildsOnly() throws Exception {
        String token = loginToken("13800138188");

        String compilePayloadV1 = """
                {
                  "name": "思思（CiCi）",
                  "model": "gpt-4.1",
                  "summary": "版本历史去重测试",
                  "specText": "1. 每天上午9点发送日报\\n2. 每周五下午3点发送周报",
                  "skillRefs": ["web-search"],
                  "channels": ["web"],
                  "version": "history-dedup-test"
                }
                """;

        MvcResult compileV1Result = mockMvc.perform(post("/agents/cici-system/compile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compilePayloadV1))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode compileV1Data = objectMapper.readTree(compileV1Result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        int v1 = compileV1Data.path("draftVersionNo").asInt();
        assertThat(v1).isGreaterThan(0);
        assertThat(compileV1Data.path("changed").asBoolean()).isTrue();

        MvcResult compileNoChangeResult = mockMvc.perform(post("/agents/cici-system/compile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compilePayloadV1))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode compileNoChangeData = objectMapper.readTree(
                        compileNoChangeResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        assertThat(compileNoChangeData.path("changed").asBoolean()).isFalse();
        assertThat(compileNoChangeData.path("draftVersionNo").asInt()).isEqualTo(v1);
        assertThat(compileNoChangeData.path("compileMessage").asText()).contains("未检测到");

        MvcResult versionsAfterNoChange = mockMvc.perform(get("/agents/cici-system/versions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode versionsNoChangeData = objectMapper.readTree(
                        versionsAfterNoChange.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        assertThat(versionsNoChangeData.isArray()).isTrue();
        assertThat(versionsNoChangeData.get(0).path("versionNo").asInt()).isEqualTo(v1);

        String compilePayloadV2 = """
                {
                  "name": "思思（CiCi）",
                  "model": "gpt-4.1",
                  "summary": "版本历史去重测试（有变化）",
                  "specText": "1. 每天上午9点发送日报\\n2. 每周五下午3点发送周报\\n3. 每月1号上午10点发送月报",
                  "skillRefs": ["web-search"],
                  "channels": ["web"],
                  "version": "history-dedup-test"
                }
                """;

        MvcResult compileV2Result = mockMvc.perform(post("/agents/cici-system/compile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compilePayloadV2))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode compileV2Data = objectMapper.readTree(compileV2Result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        int v2 = compileV2Data.path("draftVersionNo").asInt();
        assertThat(compileV2Data.path("changed").asBoolean()).isTrue();
        assertThat(v2).isEqualTo(v1 + 1);

        MvcResult versionsAfterChange = mockMvc.perform(get("/agents/cici-system/versions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode versionsChangedData = objectMapper.readTree(
                        versionsAfterChange.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        assertThat(versionsChangedData.isArray()).isTrue();
        assertThat(versionsChangedData.get(0).path("versionNo").asInt()).isEqualTo(v2);
        assertThat(versionsChangedData.get(1).path("versionNo").asInt()).isEqualTo(v1);
        assertThat(versionsChangedData.get(0).path("changeLog").isArray()).isTrue();
        assertThat(versionsChangedData.get(0).path("changeLog").size()).isGreaterThan(0);
    }

    private String loginToken(String mobile) throws Exception {
        MvcResult sendResult = mockMvc.perform(post("/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "%s"
                                }
                                """.formatted(mobile)))
                .andReturn();
        if (sendResult.getResponse().getStatus() != 200) {
            throw new IllegalStateException("send sms failed: " + sendResult.getResponse().getContentAsString());
        }
        JsonNode send = objectMapper.readTree(sendResult.getResponse().getContentAsString());
        String code = send.path("data").path("devCode").asText();

        MvcResult loginResult = mockMvc.perform(post("/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "%s",
                                  "code": "%s"
                                }
                                """.formatted(mobile, code)))
                .andReturn();
        if (loginResult.getResponse().getStatus() != 200) {
            throw new IllegalStateException("login failed: " + loginResult.getResponse().getContentAsString());
        }
        return objectMapper.readTree(loginResult.getResponse().getContentAsString()).path("data").path("token").asText();
    }

}
