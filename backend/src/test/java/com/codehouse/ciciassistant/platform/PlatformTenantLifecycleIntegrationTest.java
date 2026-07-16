package com.codehouse.ciciassistant.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codehouse.ciciassistant.platform.service.PlatformTenantLifecycleService;
import com.codehouse.ciciassistant.kb.service.VectorStoreClient;
import com.codehouse.ciciassistant.kb.service.VectorUpsertCommand;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.profiles.active=default",
        "app.lifecycle.purge-worker-initial-delay-ms=3600000",
        "app.lifecycle.purge-worker-delay-ms=3600000"
})
class PlatformTenantLifecycleIntegrationTest {

    private static final List<String> ONTOLOGY_TABLES = List.of(
            "ontology_query_audit",
            "ontology_version",
            "ontology_ai_proposal",
            "ontology_mapping",
            "ontology_physical_field",
            "ontology_physical_object",
            "ontology_data_source",
            "ontology_property",
            "ontology_relation",
            "ontology_metric",
            "ontology_action",
            "ontology_concept",
            "ontology_workspace"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTenantLifecycleService tenantLifecycleService;

    @Autowired
    private VectorStoreClient vectorStoreClient;

    @Test
    void shouldManageTenantRetentionAndCreateContentFreeDryRunManifest() throws Exception {
        String platformToken = platformToken();
        CreatedOrg createdOrg = registerOrg("13902402401", "生命周期测试组织");
        seedSensitiveRows(createdOrg.orgId(), createdOrg.memberId());
        seedOntologyRows(createdOrg.orgId(), createdOrg.memberId());

        MvcResult listResult = mockMvc.perform(get("/platform/tenants")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tenant = findByOrgId(objectMapper.readTree(listResult.getResponse().getContentAsString()).path("data"),
                createdOrg.orgId());
        assertThat(tenant).isNotNull();
        assertThat(tenant.path("memberCount").asLong()).isEqualTo(1);

        mockMvc.perform(patch("/platform/tenants/{orgId}/retention", createdOrg.orgId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "graceUntil": "2026-06-01T00:00:00Z",
                                  "suspendUntil": "2026-06-10T00:00:00Z",
                                  "exportDeadline": "2026-06-15T00:00:00Z",
                                  "purgeAfter": "2026-06-22T00:00:00Z",
                                  "legalHold": true,
                                  "policySource": "PLATFORM_TEST"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.retention.legalHold").value(true))
                .andExpect(jsonPath("$.data.retention.policySource").value("PLATFORM_TEST"));

        mockMvc.perform(post("/platform/tenants/{orgId}/suspend", createdOrg.orgId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "integration test suspension" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        mockMvc.perform(post("/platform/tenants/{orgId}/resume", createdOrg.orgId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "integration test resume" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(post("/platform/tenants/{orgId}/purge-jobs", createdOrg.orgId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "dryRun": false, "reason": "must be rejected" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tenant must be PENDING_PURGE before real purge"));

        MvcResult dryRunResult = mockMvc.perform(post("/platform/tenants/{orgId}/purge-jobs", createdOrg.orgId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "dryRun": true, "reason": "preview only" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dryRun").value(true))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.manifest.dryRun").value(true))
                .andExpect(jsonPath("$.data.manifest.unsupported[?(@.domain == 'file_storage')]").exists())
                .andExpect(jsonPath("$.data.manifest.unsupported[?(@.domain == 'vector_store')]").exists())
                .andExpect(jsonPath("$.data.manifest.orphanAudit.fileStorage.orphanFiles").value(1))
                .andExpect(jsonPath("$.data.manifest.orphanAudit.vectorStore.orphanVectors").value(1))
                .andReturn();

        JsonNode dryRun = objectMapper.readTree(dryRunResult.getResponse().getContentAsString()).path("data");
        assertThat(dryRun.path("manifest").path("totals").path("rows").asLong()).isGreaterThanOrEqualTo(4);
        assertThat(domainRows(dryRun.path("manifest"), "members")).isEqualTo(1);
        assertThat(tableRows(dryRun.path("manifest"), "chat", "chat_message")).isEqualTo(1);
        assertThat(tableRows(dryRun.path("manifest"), "memory", "user_memory")).isEqualTo(1);
        assertThat(domainRows(dryRun.path("manifest"), "ontology")).isEqualTo(ONTOLOGY_TABLES.size() + 1L);
        for (String table : ONTOLOGY_TABLES) {
            assertThat(tableRows(dryRun.path("manifest"), "ontology", table))
                    .as("dry-run row count for %s", table)
                    .isEqualTo("ontology_physical_field".equals(table) ? 2 : 1);
        }

        String responseText = dryRunResult.getResponse().getContentAsString();
        assertThat(responseText).doesNotContain("客户绝密消息");
        assertThat(responseText).doesNotContain("secret-memory-content");

        long jobId = dryRun.path("id").asLong();
        mockMvc.perform(get("/platform/tenants/{orgId}/purge-jobs/{jobId}", createdOrg.orgId(), jobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(jobId))
                .andExpect(jsonPath("$.data.manifest.totals.unsupported").value(2));

        MvcResult exportResult = mockMvc.perform(post("/platform/tenants/{orgId}/export-jobs", createdOrg.orgId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "customer export window" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andReturn();
        long exportJobId = objectMapper.readTree(exportResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(get("/platform/tenants/{orgId}/export-jobs/{jobId}/download", createdOrg.orgId(), exportJobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Platform operators can view export metadata but cannot download business-content archives"));

        MvcResult downloadResult = mockMvc.perform(get("/admin/organization/export-jobs/{jobId}/download", exportJobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createdOrg.token()))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, String> zipEntries = readZip(downloadResult.getResponse().getContentAsByteArray());
        assertThat(zipEntries).containsKey("manifest.json");
        assertThat(zipEntries).containsKey("tables/integration_app.jsonl");
        assertThat(zipEntries.get("tables/integration_app.jsonl")).contains("[REDACTED]");
        assertThat(zipEntries.get("tables/integration_app.jsonl")).doesNotContain("tenant-secret-token");
        for (String table : ONTOLOGY_TABLES) {
            assertThat(zipEntries).containsKey("tables/" + table + ".jsonl");
        }
        assertThat(zipEntries.get("tables/ontology_data_source.jsonl"))
                .contains("[REDACTED]")
                .contains("sample-project-alpha")
                .contains("visible-sample-owner")
                .doesNotContain("ontology-data-source-secret")
                .doesNotContain("ontology-sample-access-key-secret");
        assertThat(zipEntries.get("tables/ontology_version.jsonl"))
                .contains("sample-project-alpha")
                .contains("customer-tier-gold")
                .doesNotContain("ontology-snapshot-secret")
                .doesNotContain("ontology-nested-secret")
                .doesNotContain("ontology-snapshot-sample-password");
        assertThat(zipEntries.get("tables/ontology_physical_object.jsonl"))
                .contains("project-catalog-v1")
                .contains("delivery-metadata")
                .doesNotContain("ontology-object-metadata-credential");
        assertThat(zipEntries.get("tables/ontology_physical_field.jsonl"))
                .contains("Project Name")
                .doesNotContain("ontology-field-metadata-access-key")
                .doesNotContain("ontology-malformed-metadata-password");
        JsonNode malformedMetadataField = findJsonlRow(
                zipEntries.get("tables/ontology_physical_field.jsonl"), "field_key", "description");
        assertThat(malformedMetadataField).isNotNull();
        assertThat(malformedMetadataField.path("metadata_json").asText()).isEqualTo("[REDACTED]");
        assertThat(zipEntries).containsKey("files/lifecycle-source.txt");

        mockMvc.perform(post("/platform/tenants/{orgId}/pending-purge", createdOrg.orgId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "customer confirmed export" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_PURGE"));

        mockMvc.perform(post("/platform/tenants/{orgId}/purge-jobs", createdOrg.orgId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dryRun": false,
                                  "sourceDryRunJobId": %d,
                                  "confirmationText": "PURGE %s",
                                  "reason": "blocked by hold"
                                }
                                """.formatted(jobId, createdOrg.orgId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Legal hold is active; purge is blocked"));

        mockMvc.perform(patch("/platform/tenants/{orgId}/retention", createdOrg.orgId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "graceUntil": "2026-06-01T00:00:00Z",
                                  "suspendUntil": "2026-06-10T00:00:00Z",
                                  "exportDeadline": "2026-06-15T00:00:00Z",
                                  "purgeAfter": "2026-06-22T00:00:00Z",
                                  "legalHold": false,
                                  "policySource": "PLATFORM_TEST"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.retention.legalHold").value(false));

        MvcResult purgeResult = mockMvc.perform(post("/platform/tenants/{orgId}/purge-jobs", createdOrg.orgId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dryRun": false,
                                  "sourceDryRunJobId": %d,
                                  "confirmationText": "PURGE %s",
                                  "reason": "customer confirmed export"
                                }
                        """.formatted(jobId, createdOrg.orgId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dryRun").value(false))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.sourceDryRunJobId").value(jobId))
                .andReturn();
        long purgeJobId = objectMapper.readTree(purgeResult.getResponse().getContentAsString()).path("data").path("id").asLong();
        tenantLifecycleService.processQueuedPurgeJobs();
        MvcResult finishedPurgeResult = mockMvc.perform(get("/platform/tenants/{orgId}/purge-jobs/{jobId}", createdOrg.orgId(), purgeJobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.attemptCount").value(1))
                .andExpect(jsonPath("$.data.workerId").isNotEmpty())
                .andReturn();
        JsonNode purge = objectMapper.readTree(finishedPurgeResult.getResponse().getContentAsString()).path("data");
        assertThat(purge.path("result").path("remainingBusinessRows").asLong()).isZero();
        assertThat(countRows("chat_message", createdOrg.orgId())).isZero();
        assertThat(countRows("user_memory", createdOrg.orgId())).isZero();
        assertThat(countRows("knowledge_base", createdOrg.orgId())).isZero();
        assertThat(countRows("agent_api_credential", createdOrg.orgId())).isZero();
        assertThat(countRows("wecom_kf_account", createdOrg.orgId())).isZero();
        assertThat(countRows("integration_app", createdOrg.orgId())).isZero();
        assertThat(countRows("agent_run_trace", createdOrg.orgId())).isZero();
        for (String table : ONTOLOGY_TABLES) {
            assertThat(countRows(table, createdOrg.orgId()))
                    .as("purged row count for %s", table)
                    .isZero();
        }
        assertThat(countRows("organization_member", createdOrg.orgId())).isZero();
        assertThat(countRows("user_account", null)).isGreaterThan(0);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM org WHERE id = ?", String.class, createdOrg.orgId()))
                .isEqualTo("PURGED");
        assertThat(Files.exists(kbFilePath(createdOrg.orgId()))).isFalse();
        assertThat(Files.exists(kbOrphanFilePath(createdOrg.orgId()))).isFalse();
    }

    @Test
    void shouldRejectOrgAdminFromTenantLifecycleApis() throws Exception {
        String orgAdminToken = loginToken("13800138188");

        mockMvc.perform(get("/platform/tenants")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAdminToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/platform/tenants/demo-org/purge-jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "dryRun": true }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldProvisionTenantAndReuseExistingOwnerAccountFromPlatform() throws Exception {
        String platformToken = platformToken();
        String reusableMobile = uniqueMobile("13902402405");
        String reusableEmail = "tenant-a-" + UUID.randomUUID() + "@example.com";

        MvcResult firstProvisionResult = mockMvc.perform(post("/platform/tenants")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantName": "平台开通组织 A",
                                  "ownerMobile": "%s",
                                  "ownerDisplayName": "张三",
                                  "ownerEmail": "%s",
                                  "initialPassword": "tenantPass1",
                                  "provisionNote": "platform provisioning test"
                                }
                                """.formatted(reusableMobile, reusableEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orgId").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.ownerMemberId").isNotEmpty())
                .andExpect(jsonPath("$.data.ownerAccountId").isNotEmpty())
                .andExpect(jsonPath("$.data.reusedExistingAccount").value(false))
                .andReturn();
        JsonNode firstProvision = objectMapper.readTree(firstProvisionResult.getResponse().getContentAsString()).path("data");
        String firstOrgId = firstProvision.path("orgId").asText();
        String ownerAccountId = firstProvision.path("ownerAccountId").asText();
        assertThat(firstOrgId).matches("^org[a-z0-9]{17}$");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM organization_retention_policy WHERE org_id = ?",
                Long.class,
                firstOrgId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_audit_log WHERE org_id = ? AND event_type = 'platform.tenant.create'",
                Long.class,
                firstOrgId)).isEqualTo(1L);

        mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "%s",
                                  "mobile": "%s",
                                  "password": "tenantPass1"
                                }
                                """.formatted(firstOrgId, reusableMobile)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").value(ownerAccountId));

        MvcResult secondProvisionResult = mockMvc.perform(post("/platform/tenants")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantName": "平台开通组织 B",
                                  "ownerMobile": "%s",
                                  "ownerDisplayName": "张三复用",
                                  "provisionNote": "reuse existing owner"
                                }
                                """.formatted(reusableMobile)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reusedExistingAccount").value(true))
                .andExpect(jsonPath("$.data.ownerAccountId").value(ownerAccountId))
                .andReturn();

        String secondOrgId = objectMapper.readTree(secondProvisionResult.getResponse().getContentAsString()).path("data").path("orgId").asText();
        assertThat(secondOrgId).matches("^org[a-z0-9]{17}$");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM organization_member WHERE account_id = ? AND role_code = 'OWNER'",
                Long.class,
                ownerAccountId)).isGreaterThanOrEqualTo(2L);
    }

    @Test
    void shouldCancelQueuedRealPurgeJobBeforeWorkerRuns() throws Exception {
        String platformToken = platformToken();
        CreatedOrg createdOrg = registerOrg("13902402403", "销毁取消测试组织");
        seedSensitiveRows(createdOrg.orgId(), createdOrg.memberId());

        MvcResult dryRunResult = mockMvc.perform(post("/platform/tenants/{orgId}/purge-jobs", createdOrg.orgId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "dryRun": true, "reason": "cancel source manifest" }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long dryRunJobId = objectMapper.readTree(dryRunResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/platform/tenants/{orgId}/pending-purge", createdOrg.orgId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "cancel test pending purge" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_PURGE"));

        MvcResult queueResult = mockMvc.perform(post("/platform/tenants/{orgId}/purge-jobs", createdOrg.orgId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dryRun": false,
                                  "sourceDryRunJobId": %d,
                                  "confirmationText": "PURGE %s",
                                  "reason": "operator queued purge"
                                }
                                """.formatted(dryRunJobId, createdOrg.orgId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andReturn();
        long queuedJobId = objectMapper.readTree(queueResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/platform/tenants/{orgId}/purge-jobs/{jobId}/cancel", createdOrg.orgId(), queuedJobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "customer paused closure" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));

        tenantLifecycleService.processQueuedPurgeJobs();
        assertThat(countRows("chat_message", createdOrg.orgId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM org WHERE id = ?", String.class, createdOrg.orgId()))
                .isEqualTo("PENDING_PURGE");
    }

    @Test
    void shouldDeadLetterExpiredRunningRealPurgeJobWithoutDeletingData() throws Exception {
        String platformToken = platformToken();
        CreatedOrg createdOrg = registerOrg("13902402404", "销毁死信测试组织");
        seedSensitiveRows(createdOrg.orgId(), createdOrg.memberId());

        MvcResult dryRunResult = mockMvc.perform(post("/platform/tenants/{orgId}/purge-jobs", createdOrg.orgId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "dryRun": true, "reason": "dead-letter source manifest" }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long dryRunJobId = objectMapper.readTree(dryRunResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/platform/tenants/{orgId}/pending-purge", createdOrg.orgId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "dead-letter test pending purge" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_PURGE"));

        MvcResult queueResult = mockMvc.perform(post("/platform/tenants/{orgId}/purge-jobs", createdOrg.orgId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dryRun": false,
                                  "sourceDryRunJobId": %d,
                                  "confirmationText": "PURGE %s",
                                  "reason": "operator queued purge"
                                }
                                """.formatted(dryRunJobId, createdOrg.orgId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andReturn();
        long queuedJobId = objectMapper.readTree(queueResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        jdbcTemplate.update("""
                        UPDATE organization_purge_job
                        SET status = 'RUNNING',
                            worker_id = 'stale-worker',
                            locked_at = TIMESTAMP '2000-01-01 00:00:00',
                            lock_expires_at = TIMESTAMP '2000-01-01 00:01:00',
                            started_at = TIMESTAMP '2000-01-01 00:00:00',
                            attempt_count = 1,
                            updated_at = TIMESTAMP '2000-01-01 00:01:00'
                        WHERE id = ?
                        """,
                queuedJobId);

        tenantLifecycleService.processQueuedPurgeJobs();

        mockMvc.perform(get("/platform/tenants/{orgId}/purge-jobs/{jobId}", createdOrg.orgId(), queuedJobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DEAD_LETTER"))
                .andExpect(jsonPath("$.data.workerId").value("stale-worker"))
                .andExpect(jsonPath("$.data.attemptCount").value(1))
                .andExpect(jsonPath("$.data.deadLetterAt").isNotEmpty())
                .andExpect(jsonPath("$.data.result.workerId").value("stale-worker"));
        assertThat(countRows("chat_message", createdOrg.orgId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM org WHERE id = ?", String.class, createdOrg.orgId()))
                .isEqualTo("PENDING_PURGE");
    }

    @Test
    void shouldRetryFailedRealPurgeJobFromSourceDryRun() throws Exception {
        String platformToken = platformToken();
        CreatedOrg createdOrg = registerOrg("13902402402", "销毁重试测试组织");
        seedSensitiveRows(createdOrg.orgId(), createdOrg.memberId());

        MvcResult dryRunResult = mockMvc.perform(post("/platform/tenants/{orgId}/purge-jobs", createdOrg.orgId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "dryRun": true, "reason": "retry source manifest" }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long dryRunJobId = objectMapper.readTree(dryRunResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/platform/tenants/{orgId}/pending-purge", createdOrg.orgId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "retry test pending purge" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_PURGE"));

        Instant now = Instant.now();
        jdbcTemplate.update("""
                        INSERT INTO organization_purge_job(
                            org_id, dry_run, status, phase, requested_by, reason, started_at, finished_at,
                            error_message, manifest_json, source_dry_run_job_id, confirmation_text,
                            manifest_version, manifest_hash, result_json, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                createdOrg.orgId(),
                false,
                "PARTIAL_FAILED",
                "REAL_PURGE",
                "platform-test",
                "simulated partial failure",
                Timestamp.from(now),
                Timestamp.from(now),
                "vector cleanup failed",
                "{}",
                dryRunJobId,
                "PURGE " + createdOrg.orgId(),
                "v2",
                "failed-manifest-hash",
                "{\"failures\":[\"vector cleanup failed\"]}",
                Timestamp.from(now),
                Timestamp.from(now));
        Long failedJobId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM organization_purge_job WHERE org_id = ? AND status = 'PARTIAL_FAILED'",
                Long.class,
                createdOrg.orgId()
        );

        MvcResult retryResult = mockMvc.perform(post("/platform/tenants/{orgId}/purge-jobs/{jobId}/retry",
                        createdOrg.orgId(), failedJobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "confirmationText": "PURGE %s",
                                  "reason": "operator retry after cleanup fix"
                                }
                        """.formatted(createdOrg.orgId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dryRun").value(false))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.sourceDryRunJobId").value(dryRunJobId))
                .andReturn();

        JsonNode retry = objectMapper.readTree(retryResult.getResponse().getContentAsString()).path("data");
        assertThat(retry.path("id").asLong()).isNotEqualTo(failedJobId.longValue());
        tenantLifecycleService.processQueuedPurgeJobs();
        MvcResult finishedRetryResult = mockMvc.perform(get("/platform/tenants/{orgId}/purge-jobs/{jobId}",
                        createdOrg.orgId(), retry.path("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andReturn();
        JsonNode finishedRetry = objectMapper.readTree(finishedRetryResult.getResponse().getContentAsString()).path("data");
        assertThat(finishedRetry.path("result").path("remainingBusinessRows").asLong()).isZero();
        assertThat(countRows("chat_message", createdOrg.orgId())).isZero();
        assertThat(countRows("organization_member", createdOrg.orgId())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM org WHERE id = ?", String.class, createdOrg.orgId()))
                .isEqualTo("PURGED");
    }

    private CreatedOrg registerOrg(String mobile, String name) throws Exception {
        String mobileToRegister = uniqueMobile(mobile);
        MvcResult registerResult = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile": "%s",
                                  "password": "szyd1234",
                                  "organizationName": "%s"
                                }
                                """.formatted(mobileToRegister, name)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(registerResult.getResponse().getContentAsString()).path("data");
        return new CreatedOrg(data.path("orgId").asText(), data.path("memberId").asText(), data.path("token").asText());
    }

    private String uniqueMobile(String seedMobile) {
        String prefix = seedMobile == null || seedMobile.length() < 3 ? "139" : seedMobile.substring(0, 3);
        for (int attempt = 0; attempt < 10; attempt++) {
            String suffix = String.format("%08d",
                    Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 100_000_000L));
            String candidate = prefix + suffix;
            Long existing = jdbcTemplate.queryForObject("""
                            SELECT COUNT(*)
                            FROM account_login_identifier
                            WHERE identifier_type = 'MOBILE'
                              AND normalized_value = ?
                              AND status = 'ACTIVE'
                            """,
                    Long.class,
                    candidate);
            if (existing != null && existing == 0L) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to allocate a unique mobile fixture");
    }

    private void seedSensitiveRows(String orgId, String memberId) {
        Instant now = Instant.now();
        String sessionId = "retention-" + UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO chat_session(id, org_id, user_id, title, updated_at)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                sessionId, orgId, memberId, "Sensitive Session", Timestamp.from(now));
        jdbcTemplate.update("""
                        INSERT INTO chat_message(session_id, org_id, role_code, content, created_at)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                sessionId, orgId, "USER", "客户绝密消息", Timestamp.from(now));
        jdbcTemplate.update("""
                        INSERT INTO chat_session_state(session_id, org_id, agent_id, summary, state_json, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                sessionId, orgId, "cici-system", "Sensitive state", "{\"stage\":\"secret\"}", Timestamp.from(now));
        jdbcTemplate.update("""
                        INSERT INTO user_memory(org_id, user_id, agent_id, category, source, content, memory_key, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                orgId, memberId, "cici-system", "FACT", "MANUAL", "secret-memory-content", "retention.test",
                Timestamp.from(now), Timestamp.from(now));
        jdbcTemplate.update("""
                        INSERT INTO integration_app(org_id, app_code, app_name, description, enabled, config_json, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                orgId, "secret_app", "Secret App", "Export redaction test", true,
                "{\"accessToken\":\"tenant-secret-token\",\"safe\":\"visible\"}", Timestamp.from(now), Timestamp.from(now));
        jdbcTemplate.update("""
                        INSERT INTO knowledge_base(org_id, name, description, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                orgId, "Retention KB", "Sensitive KB", "ACTIVE", Timestamp.from(now), Timestamp.from(now));
        Long kbId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM knowledge_base WHERE org_id = ?", Long.class, orgId);
        Path file = kbFilePath(orgId);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "exportable source file", StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        jdbcTemplate.update("""
                        INSERT INTO kb_document(org_id, knowledge_base_id, name, content_type, storage_path, status, created_at, updated_at, file_size)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                orgId, kbId, "lifecycle-source.txt", "text/plain", file.toString(), "PUBLISHED",
                Timestamp.from(now), Timestamp.from(now), 22L);
        Long docId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM kb_document WHERE org_id = ?", Long.class, orgId);
        String registeredVectorId = vectorStoreClient.upsert(new VectorUpsertCommand(
                orgId,
                String.valueOf(kbId),
                docId,
                1L,
                0,
                "sensitive kb chunk",
                "hash",
                List.of(0.1f, 0.2f, 0.3f, 0.4f)
        ));
        vectorStoreClient.upsert(new VectorUpsertCommand(
                orgId,
                String.valueOf(kbId),
                999999L,
                999999L,
                1,
                "orphan vector chunk",
                "orphan-hash",
                List.of(0.4f, 0.3f, 0.2f, 0.1f)
        ));
        Path orphanFile = kbOrphanFilePath(orgId);
        try {
            Files.createDirectories(orphanFile.getParent());
            Files.writeString(orphanFile, "orphan source file", StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        jdbcTemplate.update("""
                        INSERT INTO kb_chunk(org_id, knowledge_base_id, document_id, chunk_index, content, tags, vector_id, content_hash, status, enabled)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                orgId, String.valueOf(kbId), docId, 0, "sensitive kb chunk", "retention", registeredVectorId, "hash", "ACTIVE", true);
        jdbcTemplate.update("""
                        INSERT INTO agent_api_credential(public_id, org_id, agent_id, name, key_prefix, key_hash, status, run_as_user_id,
                            allowed_ips_json, scopes_json, rate_limit_per_minute, daily_quota, max_prompt_chars, max_response_chars,
                            allow_stream, allow_trace_read, created_by, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                "pub" + UUID.randomUUID().toString().replace("-", "").substring(0, 12), orgId, "cici-system", "API key",
                "cici_ak", "secret-hash", "ACTIVE", memberId, "[]", "[\"chat\"]", 60, 100, 1000, 2000,
                true, false, memberId, Timestamp.from(now), Timestamp.from(now));
        jdbcTemplate.update("""
                        INSERT INTO wecom_kf_account(org_id, corp_id, open_kfid, name, secret_cipher, secret_iv, token,
                            encoding_aes_key_cipher, encoding_aes_key_iv, agent_id, run_as_user_id, enabled, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                orgId, "corp", "kf", "WeCom", "secret-cipher", "iv", "token", "aes-cipher", "aes-iv",
                "cici-system", memberId, true, Timestamp.from(now), Timestamp.from(now));
        jdbcTemplate.update("""
                        INSERT INTO agent_run_trace(trace_id, org_id, user_id, session_id, agent_id, channel, status, title, summary,
                            started_at, ended_at, elapsed_ms, model_call_count, tool_call_count, rag_context_count,
                            knowledge_base_names_json, skill_names_json, nodes_json, detail_json, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                "trace-" + UUID.randomUUID(), orgId, memberId, sessionId, "cici-system", "web", "SUCCESS", "trace", "summary",
                Timestamp.from(now), Timestamp.from(now), 1, 0, 0, 0, "[]", "[]", "[]", "{}", Timestamp.from(now));
    }

    private void seedOntologyRows(String orgId, String memberId) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                        INSERT INTO ontology_workspace(
                            org_id, key, name, description, status, draft_revision, created_by, updated_by,
                            created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                orgId, "lifecycle-ontology", "Lifecycle Ontology", "Lifecycle governance fixture", "PUBLISHED", 1L,
                memberId, memberId, Timestamp.from(now), Timestamp.from(now));
        Long workspaceId = jdbcTemplate.queryForObject(
                "SELECT id FROM ontology_workspace WHERE org_id = ? AND key = 'lifecycle-ontology'",
                Long.class,
                orgId);

        jdbcTemplate.update("""
                        INSERT INTO ontology_concept(
                            org_id, workspace_id, key, name, concept_type, queryable, enabled, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                orgId, workspaceId, "project", "Project", "ENTITY", true, true,
                Timestamp.from(now), Timestamp.from(now));
        Long conceptId = jdbcTemplate.queryForObject(
                "SELECT id FROM ontology_concept WHERE org_id = ? AND workspace_id = ? AND key = 'project'",
                Long.class,
                orgId,
                workspaceId);

        jdbcTemplate.update("""
                        INSERT INTO ontology_property(
                            org_id, workspace_id, concept_id, key, name, data_type, required, multiple,
                            sensitive, queryable, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                orgId, workspaceId, conceptId, "name", "Name", "STRING", true, false, false, true,
                Timestamp.from(now), Timestamp.from(now));
        jdbcTemplate.update("""
                        INSERT INTO ontology_relation(
                            org_id, workspace_id, key, name, source_concept_id, target_concept_id, cardinality,
                            queryable, enabled, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                orgId, workspaceId, "parent-project", "Parent Project", conceptId, conceptId, "MANY_TO_ONE",
                true, true, Timestamp.from(now), Timestamp.from(now));
        jdbcTemplate.update("""
                        INSERT INTO ontology_metric(
                            org_id, workspace_id, key, name, concept_id, aggregation, measure_property_key,
                            created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                orgId, workspaceId, "project-count", "Project Count", conceptId, "COUNT", "name",
                Timestamp.from(now), Timestamp.from(now));
        jdbcTemplate.update("""
                        INSERT INTO ontology_action(
                            org_id, workspace_id, key, name, concept_id, description, parameters_json,
                            created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                orgId, workspaceId, "review-project", "Review Project", conceptId, "Read-only modeled action", "[]",
                Timestamp.from(now), Timestamp.from(now));

        jdbcTemplate.update("""
                        INSERT INTO ontology_data_source(
                            org_id, workspace_id, key, name, source_type, config_json, sample_data_json,
                            status, created_by, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                orgId, workspaceId, "sample", "Sample Source", "INLINE_SAMPLE",
                "{\"accessToken\":\"ontology-data-source-secret\"}",
                "{\"projects\":[{\"id\":\"p-1\",\"name\":\"sample-project-alpha\",\"owner\":\"visible-sample-owner\",\"connector\":{\"accessKey\":\"ontology-sample-access-key-secret\"}}]}",
                "VALID", memberId, Timestamp.from(now), Timestamp.from(now));
        Long dataSourceId = jdbcTemplate.queryForObject(
                "SELECT id FROM ontology_data_source WHERE org_id = ? AND workspace_id = ? AND key = 'sample'",
                Long.class,
                orgId,
                workspaceId);

        jdbcTemplate.update("""
                        INSERT INTO ontology_physical_object(
                            org_id, workspace_id, data_source_id, object_key, name, object_type, metadata_json,
                            discovered_at, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                orgId, workspaceId, dataSourceId, "projects", "Projects", "OBJECT",
                "{\"catalog\":\"project-catalog-v1\",\"label\":\"delivery-metadata\",\"connection\":{\"credential\":\"ontology-object-metadata-credential\"}}",
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        Long objectId = jdbcTemplate.queryForObject(
                "SELECT id FROM ontology_physical_object WHERE org_id = ? AND data_source_id = ? AND object_key = 'projects'",
                Long.class,
                orgId,
                dataSourceId);
        jdbcTemplate.update("""
                        INSERT INTO ontology_physical_field(
                            org_id, workspace_id, physical_object_id, field_key, name, data_type, nullable,
                            multiple, metadata_json, discovered_at, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                orgId, workspaceId, objectId, "name", "Name", "STRING", false, false,
                "{\"businessLabel\":\"Project Name\",\"source\":{\"accessKey\":\"ontology-field-metadata-access-key\"}}",
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        jdbcTemplate.update("""
                        INSERT INTO ontology_physical_field(
                            org_id, workspace_id, physical_object_id, field_key, name, data_type, nullable,
                            multiple, metadata_json, discovered_at, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                orgId, workspaceId, objectId, "description", "Description", "STRING", true, false,
                "not-json ontology-malformed-metadata-password",
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        jdbcTemplate.update("""
                        INSERT INTO ontology_mapping(
                            org_id, workspace_id, target_type, target_key, data_source_id, physical_object_key,
                            physical_field_key, confidence, source, validation_status, last_validated_at,
                            created_by, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                orgId, workspaceId, "PROPERTY", "project.name", dataSourceId, "projects", "name", 1.0,
                "MANUAL", "VALID", Timestamp.from(now), memberId, Timestamp.from(now), Timestamp.from(now));
        jdbcTemplate.update("""
                        INSERT INTO ontology_ai_proposal(
                            org_id, workspace_id, proposal_type, status, instruction, payload_json, diff_json,
                            validation_json, created_by, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                orgId, workspaceId, "REFINE", "PENDING", "Refine project vocabulary", "{}", "[]", "{}",
                memberId, Timestamp.from(now), Timestamp.from(now));

        String snapshot = """
                {
                  "workspace": {"key": "lifecycle-ontology"},
                  "dataSources": [{
                    "key": "sample",
                    "configJson": {"accessToken": "ontology-snapshot-secret"},
                    "sampleDataJson": "{\\\"projects\\\":[{\\\"id\\\":\\\"p-1\\\",\\\"name\\\":\\\"sample-project-alpha\\\",\\\"tier\\\":\\\"customer-tier-gold\\\",\\\"credentials\\\":{\\\"password\\\":\\\"ontology-snapshot-sample-password\\\"}}]}"
                  }],
                  "secret": "ontology-nested-secret"
                }
                """;
        jdbcTemplate.update("""
                        INSERT INTO ontology_version(
                            org_id, workspace_id, version_no, source_draft_revision, content_hash, snapshot_json,
                            json_schema, graphql_sdl, query_contract_json, validation_summary_json,
                            published_by, published_at, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                orgId, workspaceId, 1, 1L, "lifecycle-hash", snapshot, "{}", "type Query { project: String }",
                "{}", "{}", memberId, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        Long versionId = jdbcTemplate.queryForObject(
                "SELECT id FROM ontology_version WHERE org_id = ? AND workspace_id = ? AND version_no = 1",
                Long.class,
                orgId,
                workspaceId);
        jdbcTemplate.update("""
                        INSERT INTO ontology_query_audit(
                            org_id, workspace_id, version_id, data_source_id, user_id, concept_key, query_json,
                            result_count, duration_ms, status, evidence_json, sensitive_values_redacted,
                            created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                orgId, workspaceId, versionId, dataSourceId, memberId, "project", "{}", 1, 3L, "SUCCESS", "{}", true,
                Timestamp.from(now), Timestamp.from(now));
    }

    private long countRows(String table, String orgId) {
        if (orgId == null) {
            return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        }
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE org_id = ?", Long.class, orgId);
    }

    private Path kbFilePath(String orgId) {
        return Path.of("data/kb-files").toAbsolutePath().normalize()
                .resolve(orgId)
                .resolve("retention")
                .resolve("lifecycle-source.txt");
    }

    private Path kbOrphanFilePath(String orgId) {
        return Path.of("data/kb-files").toAbsolutePath().normalize()
                .resolve(orgId)
                .resolve("orphan")
                .resolve("orphan-source.txt");
    }

    private Map<String, String> readZip(byte[] bytes) throws Exception {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    private JsonNode findJsonlRow(String jsonl, String key, String expectedValue) throws Exception {
        for (String line : jsonl.lines().toList()) {
            JsonNode row = objectMapper.readTree(line);
            if (expectedValue.equals(row.path(key).asText())) {
                return row;
            }
        }
        return null;
    }

    private JsonNode findByOrgId(JsonNode rows, String orgId) {
        if (rows == null || !rows.isArray()) {
            return null;
        }
        for (JsonNode row : rows) {
            if (orgId.equals(row.path("orgId").asText())) {
                return row;
            }
        }
        return null;
    }

    private long domainRows(JsonNode manifest, String domain) {
        for (JsonNode row : manifest.path("domains")) {
            if (domain.equals(row.path("domain").asText())) {
                return row.path("rows").asLong();
            }
        }
        return -1;
    }

    private long tableRows(JsonNode manifest, String domain, String table) {
        for (JsonNode row : manifest.path("domains")) {
            if (!domain.equals(row.path("domain").asText())) {
                continue;
            }
            for (JsonNode tableRow : row.path("tables")) {
                if (table.equals(tableRow.path("table").asText())) {
                    return tableRow.path("rows").asLong();
                }
            }
        }
        return -1;
    }

    private String loginToken(String mobile) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "%s",
                                  "password": "szyd1234"
                                }
                                """.formatted(mobile)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(loginResult.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private String platformToken() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/platform/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "admin@cloudcc.com",
                                  "password": "szyd1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(loginResult.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private record CreatedOrg(String orgId, String memberId, String token) {
    }
}
