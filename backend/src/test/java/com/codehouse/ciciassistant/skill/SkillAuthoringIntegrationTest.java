package com.codehouse.ciciassistant.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class SkillAuthoringIntegrationTest {

    private static final String ADMIN_MOBILE = "13800138188";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldGenerateStructuredSkillDraftWithoutExposingHiddenBuiltinCreator() throws Exception {
        String token = loginToken(ADMIN_MOBILE);

        MvcResult generated = mockMvc.perform(post("/skills/authoring/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceText": "我想做一个审批前风险检查技能。当销售提交特殊折扣、非标准合同条款、实施交付承诺时，先帮我判断有没有风险。输出里要包含风险等级、原因和建议动作。"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(
                generated.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertThat(body.path("sessionId").asText()).isNotBlank();
        JsonNode skillSpec = body.path("skillSpec");
        JsonNode preview = body.path("preview");

        assertThat(skillSpec.path("skillCode").asText()).isNotBlank();
        assertThat(skillSpec.path("name").asText()).contains("审批前风险检查");
        assertThat(skillSpec.path("riskLevel").asText()).isEqualTo("HIGH");
        assertThat(skillSpec.path("draftSpecText").asText()).contains("特殊折扣");
        assertThat(skillSpec.path("draftSpecText").asText()).contains("实施交付承诺");
        assertThat(skillSpec.path("warnings").isArray()).isTrue();
        assertThat(preview.path("promptPreview").asText()).contains("严格按照当前需求描述执行");
        assertThat(preview.path("compileSummary").isArray()).isTrue();

        MvcResult listed = mockMvc.perform(get("/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode skills = objectMapper.readTree(listed.getResponse().getContentAsString()).path("data");
        assertThat(findByField(skills, "skillCode", "skill-creator").isMissingNode()).isTrue();
        assertThat(findByField(skills, "skillCode", "builtin-skill-creator").isMissingNode()).isTrue();
    }

    @Test
    void shouldRefineDraftAndCreateSkillFromAuthoringFlow() throws Exception {
        String token = loginToken("13800138000");

        MvcResult generated = mockMvc.perform(post("/skills/authoring/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceText": "我想做一个邮件回复助手，帮助我整理客户邮件并给出回复建议。"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode generatedData = objectMapper.readTree(generated.getResponse().getContentAsString()).path("data");
        String sessionId = generatedData.path("sessionId").asText();
        assertThat(sessionId).isNotBlank();
        JsonNode generatedSpec = generatedData.path("skillSpec");

        MvcResult refined = mockMvc.perform(post("/skills/authoring/refine")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "%s",
                                  "sourceText": "请把输出改成包含负责人和建议动作，并强调涉及对外发送时必须人工确认。",
                                  "currentSkillSpec": %s,
                                  "clarificationAnswers": []
                                }
                                """.formatted(sessionId, objectMapper.writeValueAsString(generatedSpec))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode refinedBody = objectMapper.readTree(refined.getResponse().getContentAsString()).path("data");
        JsonNode refinedSpec = refinedBody.path("skillSpec");
        assertThat(refinedSpec.path("riskLevel").asText()).isEqualTo("HIGH");
        assertThat(refinedSpec.path("outputContract").asText())
                .isNotEqualTo(generatedSpec.path("outputContract").asText());
        assertThat(refinedSpec.path("handoffRule").asText())
                .isNotEqualTo(generatedSpec.path("handoffRule").asText());

        MvcResult created = mockMvc.perform(post("/skills/authoring/create")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceText": "请按当前草稿创建技能",
                                  "sessionId": "%s",
                                  "skillSpec": %s
                                }
                                """.formatted(sessionId, objectMapper.writeValueAsString(refinedSpec))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createdBody = objectMapper.readTree(created.getResponse().getContentAsString()).path("data");
        assertThat(createdBody.path("createdSkill").path("id").asLong()).isPositive();
        assertThat(createdBody.path("createdSkill").path("skillCode").asText()).isEqualTo(refinedSpec.path("skillCode").asText());

        MvcResult listed = mockMvc.perform(get("/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode skills = objectMapper.readTree(listed.getResponse().getContentAsString()).path("data");
        assertThat(findByField(skills, "skillCode", refinedSpec.path("skillCode").asText()).isMissingNode()).isFalse();
    }

    @Test
    void shouldMergeClarificationAnswersWithinAuthoringSession() throws Exception {
        String token = loginToken("13800138002");

        MvcResult generated = mockMvc.perform(post("/skills/authoring/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceText": "散文润色小助手，只改文字表达，不用任何外部系统。"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode generatedData = objectMapper.readTree(
                generated.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        String sessionId = generatedData.path("sessionId").asText();
        assertThat(sessionId).isNotBlank();
        JsonNode generatedSpec = generatedData.path("skillSpec");
        assertThat(generatedSpec.path("clarificationQuestions").isArray()).isTrue();
        assertThat(generatedSpec.path("clarificationQuestions").size()).isPositive();
        String question = generatedSpec.path("clarificationQuestions").get(0).asText();

        ObjectNode refineBody = objectMapper.createObjectNode();
        refineBody.put("sessionId", sessionId);
        refineBody.put("sourceText", "");
        refineBody.set("currentSkillSpec", generatedSpec);
        ArrayNode answers = refineBody.putArray("clarificationAnswers");
        ObjectNode pair = answers.addObject();
        pair.put("question", question);
        pair.put("answer", "确认：仅做文本润色，不绑定工具与知识库。");

        MvcResult refined = mockMvc.perform(post("/skills/authoring/refine")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refineBody)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode refinedData = objectMapper.readTree(
                refined.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        JsonNode refinedSpec = refinedData.path("skillSpec");
        String mergedDraft = refinedSpec.path("draftSpecText").asText();
        assertThat(mergedDraft).contains("追问：");
        assertThat(mergedDraft).contains("答复：");
        assertThat(mergedDraft).contains("确认：仅做文本润色，不绑定工具与知识库。");
        assertThat(refinedData.path("sessionId").asText()).isEqualTo(sessionId);
    }

    @Test
    void shouldRespectExplicitCampaignFlowAndToolNamesWhenGeneratingDraft() throws Exception {
        String token = loginToken("13800138003");
        createCustomTool(token, "insert_campaign_data_with_role_right", "创建市场活动并写入活动基础信息", "HIGH");
        createCustomTool(token, "get_lead_data", "按条件筛选潜在客户名单", "MEDIUM");
        createCustomTool(token, "add_campaign_member", "把潜在客户导入市场活动成员", "HIGH");

        MvcResult generated = mockMvc.perform(post("/skills/authoring/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceText": "当用户想进行一次邮件市场营销活动时，可以通过此技能进行，具体流程如下：\\n\\n1. 创建一个市场活动，根据客户要求构思这次市场活动内容，创建前可以先做一次市场调研分析，然后调用insert_campaign_data_with_role_right 的mcp工具创建市场活动\\n2. 根据用户描述的活动目标，筛选出符合条件的潜在客户，可以直接调用 get_lead_data MCP工具。\\n3. 将符合条件的潜在客户名单，插入本次市场活动成员，调用 add_campaign_member MCP工具。\\n4. 制作本次市场活动需要的邮件模版，HTML格式。\\n5. 调用内置发邮件工具email_send，给市场活动成员发送邮件，每发送成功一封，更新对应的市场活动成员状态。"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(
                generated.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        JsonNode skillSpec = body.path("skillSpec");

        assertThat(skillSpec.path("name").asText()).contains("邮件市场营销活动");
        assertThat(skillSpec.path("skillCode").asText()).isNotBlank();
        assertThat(skillSpec.path("description").asText()).contains("业务场景");
        assertThat(skillSpec.path("description").asText()).doesNotContain("线索分诊");
        assertThat(jsonArrayTexts(skillSpec.path("toolWhitelist")))
                .contains(
                        "insert_campaign_data_with_role_right",
                        "get_lead_data",
                        "add_campaign_member",
                        "email_send");
        assertThat(skillSpec.path("draftSpecText").asText()).contains("创建一个市场活动");
        assertThat(skillSpec.path("draftSpecText").asText()).contains("筛选出符合条件的潜在客户");
        assertThat(skillSpec.path("draftSpecText").asText()).contains("调用内置发邮件工具email_send");
        assertThat(skillSpec.path("promptFragment").asText()).contains("不要改写成别的行业场景");
    }

    private void createCustomTool(String token, String toolName, String description, String riskLevel) throws Exception {
        mockMvc.perform(post("/tools")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toolName": "%s",
                                  "description": "%s",
                                  "riskLevel": "%s"
                                }
                                """.formatted(toolName, description, riskLevel)))
                .andExpect(status().isOk());
    }

    private JsonNode findByField(JsonNode arr, String field, String value) {
        for (JsonNode row : arr) {
            if (value.equals(row.path(field).asText())) {
                return row;
            }
        }
        return objectMapper.missingNode();
    }

    private java.util.List<String> jsonArrayTexts(JsonNode arr) {
        java.util.List<String> values = new java.util.ArrayList<>();
        for (JsonNode item : arr) {
            values.add(item.asText());
        }
        return values;
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
                .andExpect(status().isOk())
                .andReturn();
        String code = objectMapper.readTree(sendResult.getResponse().getContentAsString())
                .path("data").path("devCode").asText();

        MvcResult loginResult = mockMvc.perform(post("/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "%s",
                                  "code": "%s"
                                }
                                """.formatted(mobile, code)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }
}
