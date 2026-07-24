package com.codehouse.ciciassistant.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codehouse.ciciassistant.skill.domain.SkillDefinitionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionRepository;
import com.codehouse.ciciassistant.integration.service.CloudccAccessTokenService;
import com.codehouse.ciciassistant.integration.service.IntegrationAppService;
import com.codehouse.ciciassistant.cloudcc.CloudccOpenApiService;
import com.codehouse.ciciassistant.skill.service.BuiltinSkillDocumentService;
import com.codehouse.ciciassistant.skill.service.BuiltinSkillRuntimeConfigService;
import com.codehouse.ciciassistant.skill.service.FileBackedBuiltinSkillCatalog;
import com.codehouse.ciciassistant.skill.service.SkillDefinitionService;
import com.codehouse.ciciassistant.skill.service.SkillPromptAssembler;
import com.codehouse.ciciassistant.skill.service.SkillResolverService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class FileBackedBuiltinSkillIntegrationTest {

    private static final String SKILL_CODE = "cloudcc-customization-expert-common";
    private static final String CRM_ANALYSIS_SKILL_CODE = "crm-business-analysis";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FileBackedBuiltinSkillCatalog catalog;

    @Autowired
    private SkillDefinitionRepository skillDefinitionRepository;

    @Autowired
    private SkillDefinitionService skillDefinitionService;

    @Autowired
    private SkillResolverService skillResolverService;

    @Autowired
    private BuiltinSkillDocumentService builtinSkillDocumentService;

    @Autowired
    private SkillPromptAssembler skillPromptAssembler;

    @Autowired
    private BuiltinSkillRuntimeConfigService builtinSkillRuntimeConfigService;

    @Autowired
    private IntegrationAppService integrationAppService;

    @Autowired
    private CloudccAccessTokenService cloudccAccessTokenService;

    @Autowired
    private CloudccOpenApiService cloudccOpenApiService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldScanCloudccFileBackedBundle() {
        FileBackedBuiltinSkillCatalog.FileBackedBuiltinSkillBundle bundle = catalog.findBundle(SKILL_CODE).orElseThrow();

        assertThat(bundle.resourceUri()).isEqualTo("classpath:/builtin-skills/" + SKILL_CODE);
        assertThat(bundle.bundleChecksum()).hasSize(64);
        assertThat(bundle.entrypointChecksum()).hasSize(64);
        assertThat(bundle.manifest().modules()).extracting(FileBackedBuiltinSkillCatalog.FileBackedBuiltinSkillManifest.Module::code)
                .contains("object", "fields", "triggers", "classes");
        assertThat(catalog.readModuleDocument(SKILL_CODE, "object", "api.md")).isPresent();
        assertThat(catalog.readModuleDocument(SKILL_CODE, "../object", "api.md")).isEmpty();
    }

    @Test
    void shouldParseRuntimePoliciesFromFileBackedManifest() throws Exception {
        FileBackedBuiltinSkillCatalog.FileBackedBuiltinSkillManifest manifest = objectMapper.readValue("""
                {
                  "schemaVersion": 1,
                  "skillCode": "crm-business-analysis",
                  "name": "CRM 经营分析",
                  "description": "稳定分析 CRM 经营数据",
                  "category": "CRM",
                  "sourceType": "PLATFORM_STANDARD",
                  "visibility": "VISIBLE",
                  "editPolicy": "CONFIGURABLE",
                  "bindingPolicy": "OPTIONAL",
                  "updatePolicy": "AUTO",
                  "riskLevel": "LOW",
                  "version": 1,
                  "documentRoot": ".",
                  "entrypoint": "SKILL.md",
                  "defaultActivationMode": "always-on",
                  "toolWhitelist": ["crm_product_sales_rank"],
                  "kbWhitelist": [],
                  "handoffRule": "查询不可用时说明原因，不猜测销量。",
                  "outputContract": "必须包含统计口径、时间范围和数据截止时间。",
                  "modules": []
                }
                """, FileBackedBuiltinSkillCatalog.FileBackedBuiltinSkillManifest.class);

        assertThat(manifest.toolWhitelist()).containsExactly("crm_product_sales_rank");
        assertThat(manifest.kbWhitelist()).isEmpty();
        assertThat(manifest.handoffRule()).contains("不猜测销量");
        assertThat(manifest.outputContract()).contains("统计口径");
    }

    @Test
    void shouldSyncAndAlwaysActivateCrmBusinessAnalysisSkillWithOnlyHighLevelTool() {
        FileBackedBuiltinSkillCatalog.FileBackedBuiltinSkillBundle bundle =
                catalog.findBundle(CRM_ANALYSIS_SKILL_CODE).orElseThrow();
        assertThat(bundle.manifest().toolWhitelist()).containsExactly("crm_product_sales_rank");
        assertThat(bundle.manifest().defaultActivationMode()).isEqualTo("always-on");

        skillDefinitionService.ensurePhaseOneDefaults("demo-org");
        SkillDefinitionEntity skill = skillDefinitionRepository
                .findByCompanyIdAndSkillCode("demo-org", CRM_ANALYSIS_SKILL_CODE)
                .orElseThrow();
        assertThat(skill.getToolWhitelist()).isEqualTo("crm_product_sales_rank");
        assertThat(skill.getKbWhitelist()).isNullOrEmpty();
        assertThat(skill.getHandoffRule()).contains("不猜测");
        assertThat(skill.getOutputContract()).contains("统计口径");

        String activationMode = jdbcTemplate.queryForObject("""
                SELECT activation_mode
                FROM agent_skill_binding
                WHERE company_id = ? AND agent_id = 'cici-system' AND skill_id = ?
                """, String.class, "demo-org", skill.getId());
        assertThat(activationMode).isEqualTo("always-on");

        SkillResolverService.ResolvedSkillContext context = skillResolverService.resolve(
                "demo-org", "cici-system", "crm-analysis-default-binding-test");
        assertThat(context.skillCodes()).contains(CRM_ANALYSIS_SKILL_CODE);
        assertThat(context.allowedToolNames()).contains("crm_product_sales_rank");
    }

    @Test
    void shouldExposeFileBackedSkillAsReadonlyPlatformSkillWithoutPersistingDocs() throws Exception {
        String token = loginToken("13800138111");

        MvcResult listed = mockMvc.perform(get("/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode skills = objectMapper.readTree(listed.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        JsonNode cloudcc = findByField(skills, "skillCode", SKILL_CODE);
        assertThat(cloudcc.isMissingNode()).isFalse();
        assertThat(cloudcc.path("sourceType").asText()).isEqualTo("PLATFORM_STANDARD");
        assertThat(cloudcc.path("editPolicy").asText()).isEqualTo("CONFIGURABLE");
        assertThat(cloudcc.path("resourceType").asText()).isEqualTo("FILE_BACKED");
        assertThat(cloudcc.path("bundleChecksum").asText()).hasSize(64);

        long skillId = cloudcc.path("id").asLong();
        mockMvc.perform(get("/skills/{id}/builtin-docs", skillId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resourceType").value("FILE_BACKED"))
                .andExpect(jsonPath("$.data.readonlyNotice").isString())
                .andExpect(jsonPath("$.data.modules[?(@.code=='object')]").exists())
                .andExpect(jsonPath("$.data.modules[?(@.code=='fields')]").exists());

        String moduleManifest = jdbcTemplate.queryForObject("""
                SELECT module_manifest_json
                FROM platform_skill_template_version
                WHERE company_id = ? AND template_code = ? AND version_no = 1
                """, String.class, "demo-org", SKILL_CODE);
        assertThat(moduleManifest).contains("\"object\"").contains("\"checksum\"");
        assertThat(moduleManifest).doesNotContain("请求体示例");
    }

    @Test
    void shouldResolveAndInjectOnlyRelevantModuleDocumentsAtRuntime() throws Exception {
        loginToken("13800138111");
        skillDefinitionService.ensurePhaseOneDefaults("demo-org");
        SkillDefinitionEntity skill = skillDefinitionRepository.findByCompanyIdAndSkillCode("demo-org", SKILL_CODE).orElseThrow();
        jdbcTemplate.update("DELETE FROM agent_skill_binding WHERE company_id = ? AND agent_id = ? AND skill_id = ?",
                "demo-org", "cici-system", skill.getId());
        jdbcTemplate.update("""
                INSERT INTO agent_skill_binding (
                    company_id, agent_id, skill_id, activation_mode, activation_condition, priority, enabled, created_at
                )
                VALUES (?, ?, ?, 'intent-route', NULL, 90, TRUE, CURRENT_TIMESTAMP)
                """, "demo-org", "cici-system", skill.getId());

        String question = "CloudCC 自定义对象怎么创建？请给出 object API 参数和请求体。";
        SkillResolverService.ResolvedSkillContext context = skillResolverService.resolve(
                "demo-org",
                "cici-system",
                "file-backed-doc-test",
                Optional.of(SKILL_CODE)
        );
        BuiltinSkillDocumentService.ResolvedBuiltinSkillDocs docs =
                builtinSkillDocumentService.resolveDocs(context, question);
        String prompt = skillPromptAssembler.assemble("base", context, docs);

        assertThat(docs.refs()).extracting(BuiltinSkillDocumentService.DocRef::module)
                .contains("object")
                .doesNotContain("fields");
        assertThat(docs.refs()).extracting(BuiltinSkillDocumentService.DocRef::file)
                .contains("introduction.md", "api.md");
        assertThat(prompt).contains("Reference documents for active builtin skill");
        assertThat(prompt).contains(SKILL_CODE + "/object/introduction.md@v1");
        assertThat(prompt).contains(SKILL_CODE + "/object/api.md@v1");
        assertThat(prompt).doesNotContain(SKILL_CODE + "/fields/api.md@v1");
    }

    @Test
    void shouldDeriveSetupSvcFromCloudccGatewayVariants() {
        assertThat(CloudccAccessTokenService.deriveSetupSvc("https://ap10.apis.cloudcc.cn/lightningapi"))
                .isEqualTo("https://ap10.apis.cloudcc.cn/setup");
        assertThat(CloudccAccessTokenService.deriveSetupSvc("https://ap10.apis.cloudcc.cn/lightningapi/"))
                .isEqualTo("https://ap10.apis.cloudcc.cn/setup");
        assertThat(CloudccAccessTokenService.deriveSetupSvc("https://ap10.apis.cloudcc.cn"))
                .isEqualTo("https://ap10.apis.cloudcc.cn/setup");
        assertThat(CloudccAccessTokenService.deriveSetupSvc("ap10.apis.cloudcc.cn"))
                .isEqualTo("https://ap10.apis.cloudcc.cn/setup");
        assertThat(CloudccAccessTokenService.deriveSetupSvc("https://yundong.lightning.cloudcc.cn/ccdomaingateway/apisvc"))
                .isEqualTo("https://yundong.lightning.cloudcc.cn/ccdomaingateway/setup");
    }

    @Test
    void shouldInjectCloudccSetupSvcRuntimeConfigWithoutLeakingToken() throws Exception {
        loginToken("13800138111");
        skillDefinitionService.ensurePhaseOneDefaults("demo-org");
        SkillDefinitionEntity skill = skillDefinitionRepository.findByCompanyIdAndSkillCode("demo-org", SKILL_CODE).orElseThrow();
        jdbcTemplate.update("DELETE FROM agent_skill_binding WHERE company_id = ? AND agent_id = ? AND skill_id = ?",
                "demo-org", "cici-system", skill.getId());
        jdbcTemplate.update("""
                INSERT INTO agent_skill_binding (
                    company_id, agent_id, skill_id, activation_mode, activation_condition, priority, enabled, created_at
                )
                VALUES (?, ?, ?, 'intent-route', NULL, 90, TRUE, CURRENT_TIMESTAMP)
                """, "demo-org", "cici-system", skill.getId());

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

        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/domain", exchange -> {
            String payload = """
                    {"result":true,"orgapi_address":"http://localhost:%d/lightningapi"}
                    """.formatted(server.getAddress().getPort());
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.createContext("/lightningapi/api/cauth/token", exchange -> {
            byte[] bytes = """
                    {"result":true,"data":{"accessToken":"cloudcc-runtime-secret-token"}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        AtomicReference<String> setupAccessTokenHeader = new AtomicReference<>("");
        server.createContext("/setup/api/customObject/standardObjList", exchange -> {
            setupAccessTokenHeader.set(exchange.getRequestHeaders().getFirst("accessToken"));
            byte[] bytes = """
                    {"result":true,"data":[{"objname":"客户","label":"Customer__c","objprefix":"001"}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.start();
        try {
            integrationAppService.update("demo-org", IntegrationAppService.APP_CODE_CLOUDCC_CRM,
                    true, "cloudcc", Map.of(
                            "companyId", "cloudcc-org",
                            "orgapi_switch_address", "http://localhost:%d/domain".formatted(server.getAddress().getPort()),
                            "clientId", "cloudcc-client",
                            "secretKey", "cloudcc-secret"
                    ));
            cloudccAccessTokenService.invalidateSessionContext("demo-org", cloudccUserId);

            String question = "CloudCC 自定义对象怎么创建？请给出 object API 参数和请求体。";
            SkillResolverService.ResolvedSkillContext context = skillResolverService.resolve(
                    "demo-org",
                    "cici-system",
                    "file-backed-runtime-config-test",
                    Optional.of(SKILL_CODE)
            );
            BuiltinSkillDocumentService.ResolvedBuiltinSkillDocs docs =
                    builtinSkillDocumentService.resolveDocs(context, question);
            BuiltinSkillRuntimeConfigService.ResolvedBuiltinSkillRuntimeConfig runtimeConfig =
                    builtinSkillRuntimeConfigService.resolve(context, docs, "demo-org", cloudccUserId);
            String prompt = skillPromptAssembler.assemble("base", context, docs, runtimeConfig);

            assertThat(prompt).contains("Runtime configuration for active builtin skill");
            assertThat(prompt).contains("setupSvc: http://localhost:%d/setup".formatted(server.getAddress().getPort()));
            assertThat(prompt).contains("accessToken: available through the server-side CloudCC credential binding");
            assertThat(prompt).doesNotContain("cloudcc-runtime-secret-token");

            String toolResult = cloudccOpenApiService.getStandardObjects("demo-org", cloudccUserId);
            assertThat(toolResult).contains("客户").contains("Customer__c");
            assertThat(setupAccessTokenHeader.get()).isEqualTo("cloudcc-runtime-secret-token");
        } finally {
            server.stop(0);
        }
    }

    private JsonNode findByField(JsonNode nodes, String field, String value) {
        for (JsonNode node : nodes) {
            if (value.equals(node.path(field).asText())) {
                return node;
            }
        }
        return objectMapper.missingNode();
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
