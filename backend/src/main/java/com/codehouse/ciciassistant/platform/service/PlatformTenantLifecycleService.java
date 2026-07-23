package com.codehouse.ciciassistant.platform.service;

import com.codehouse.ciciassistant.auth.domain.OrgEntity;
import com.codehouse.ciciassistant.auth.domain.OrgRepository;
import com.codehouse.ciciassistant.auth.domain.UserAccountEntity;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.service.OrganizationProvisioningService;
import com.codehouse.ciciassistant.common.security.SecretKeyMatcher;
import com.codehouse.ciciassistant.kb.service.VectorDeleteResult;
import com.codehouse.ciciassistant.kb.service.VectorStoreAuditResult;
import com.codehouse.ciciassistant.kb.service.VectorStoreClient;
import com.codehouse.ciciassistant.platform.domain.OrganizationExportJobEntity;
import com.codehouse.ciciassistant.platform.domain.OrganizationExportJobRepository;
import com.codehouse.ciciassistant.platform.domain.OrganizationPurgeJobEntity;
import com.codehouse.ciciassistant.platform.domain.OrganizationPurgeJobRepository;
import com.codehouse.ciciassistant.platform.domain.OrganizationRetentionPolicyEntity;
import com.codehouse.ciciassistant.platform.domain.OrganizationRetentionPolicyRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PlatformTenantLifecycleService {

    private static final List<DomainGroup> MANIFEST_DOMAINS = List.of(
            new DomainGroup("members", "组织成员", List.of("organization_member")),
            new DomainGroup("chat", "会话与消息", List.of("chat_session", "chat_message", "chat_session_state")),
            new DomainGroup("memory", "专属记忆", List.of("user_memory")),
            new DomainGroup("agent_memory", "通用主体记忆与受控向量索引", List.of(
                    "memory_evidence", "memory_vector_fragment", "memory_candidate", "memory_conversation_snapshot",
                    "memory_record", "memory_subject"
            )),
            new DomainGroup("user_workflows", "个人工作流与快捷指令", List.of(
                    "user_agent_profile",
                    "user_workflow_spec",
                    "user_workflow_version",
                    "user_workflow_trigger",
                    "user_workflow_execution",
                    "user_quick_command"
            )),
            new DomainGroup("knowledge_base", "知识库、文档、Chunk 与检索日志", List.of(
                    "knowledge_base",
                    "kb_document",
                    "kb_chunk",
                    "kb_metadata_field",
                    "kb_document_metadata",
                    "kb_retrieval_log",
                    "kb_eval_suite",
                    "kb_eval_case",
                    "kb_eval_run",
                    "kb_eval_case_result",
                    "kb_data_source",
                    "kb_sync_job",
                    "kb_source_document_map",
                    "kb_quality_rule",
                    "kb_quality_run",
                    "kb_quality_issue",
                    "kb_annotation_suggestion",
                    "kb_chunk_annotation"
            )),
            new DomainGroup("agents", "Agent、工作流、发布与调度", List.of(
                    "agent_definition",
                    "agent_spec",
                    "agent_workflow_version",
                    "agent_kb_binding",
                    "agent_tool_binding",
                    "agent_channel_binding",
                    "agent_publish_config",
                    "agent_runtime_schedule_trigger",
                    "agent_workflow_execution_log",
                    "agent_workflow_skill_ref"
            )),
            new DomainGroup("skills", "Skill 与运行时 API 工具", List.of(
                    "skill_definition",
                    "agent_skill_binding",
                    "skill_version",
                    "skill_authoring_session",
                    "skill_api_tool"
            )),
            new DomainGroup("integrations", "集成、模型、工具、MCP 与邮箱", List.of(
                    "integration_app",
                    "model_provider_config",
                    "org_model_config",
                    "tool_definition",
                    "mcp_server",
                    "email_account"
            )),
            new DomainGroup("external_channels", "飞书与企业微信客服", List.of(
                    "feishu_bot_binding",
                    "wecom_kf_account",
                    "wecom_kf_conversation",
                    "wecom_kf_message"
            )),
            new DomainGroup("open_api", "Agent Open API 凭证、会话、调用与用量", List.of(
                    "agent_api_credential",
                    "agent_api_memory_binding",
                    "agent_api_session_map",
                    "agent_api_call_log",
                    "agent_api_usage_daily"
            )),
            new DomainGroup("observability", "运行链路与组织审计", List.of(
                    "agent_run_trace",
                    "audit_log"
            )),
            new DomainGroup("ontology", "本体建模、映射目录、版本与语义查询审计", List.of(
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
            )),
            new DomainGroup("platform_governance", "平台治理扩展数据", List.of(
                    "platform_audit_log",
                    "platform_skill_template",
                    "platform_skill_template_version",
                    "platform_tool_definition",
                    "platform_policy_bundle",
                    "organization_retention_policy",
                    "organization_purge_job",
                    "organization_export_job"
            ))
    );

    private static final List<Map<String, String>> UNSUPPORTED_MANIFEST_DOMAINS = List.of(
            Map.of(
                    "domain", "file_storage",
                    "label", "文件与对象存储",
                    "reason", "Dry-run 会巡检 KB storage root 内未登记文件；其他外部对象存储仍需后续接入。"
            ),
            Map.of(
                    "domain", "vector_store",
                    "label", "向量库物理点位",
                    "reason", "Dry-run 会通过 VectorStoreClient 只读巡检 org-scoped 孤儿点位；跨系统外部向量库仍需后续接入。"
            )
    );

    private static final Set<String> EXPORT_TABLES = new HashSet<>(MANIFEST_DOMAINS.stream()
            .map(DomainGroup::tables)
            .flatMap(Collection::stream)
            .filter(table -> !Set.of("platform_audit_log", "organization_purge_job").contains(table))
            .toList());

    private static final List<String> PURGE_DELETE_TABLES = List.of(
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
            "ontology_workspace",
            "agent_workflow_skill_ref",
            "agent_runtime_schedule_trigger",
            "agent_workflow_execution_log",
            "agent_publish_config",
            "agent_channel_binding",
            "agent_tool_binding",
            "agent_kb_binding",
            "agent_workflow_version",
            "agent_spec",
            "agent_definition",
            "agent_skill_binding",
            "skill_api_tool",
            "skill_authoring_session",
            "skill_version",
            "skill_definition",
            "kb_source_document_map",
            "kb_chunk_annotation",
            "kb_annotation_suggestion",
            "kb_quality_issue",
            "kb_quality_run",
            "kb_quality_rule",
            "kb_sync_job",
            "kb_data_source",
            "kb_eval_case_result",
            "kb_eval_run",
            "kb_eval_case",
            "kb_eval_suite",
            "kb_document_metadata",
            "kb_metadata_field",
            "kb_retrieval_log",
            "kb_chunk",
            "kb_document",
            "knowledge_base",
            "chat_message",
            "chat_session_state",
            "chat_session",
            "memory_evidence",
            "memory_vector_fragment",
            "memory_candidate",
            "memory_conversation_snapshot",
            "memory_record",
            "memory_subject",
            "user_memory",
            "user_quick_command",
            "user_workflow_execution",
            "user_workflow_trigger",
            "user_workflow_version",
            "user_workflow_spec",
            "user_agent_profile",
            "agent_api_call_log",
            "agent_api_session_map",
            "agent_api_usage_daily",
            "agent_api_memory_binding",
            "agent_api_credential",
            "wecom_kf_message",
            "wecom_kf_conversation",
            "wecom_kf_account",
            "feishu_bot_binding",
            "email_account",
            "mcp_server",
            "tool_definition",
            "org_model_config",
            "model_provider_config",
            "integration_app",
            "agent_run_trace",
            "audit_log",
            "platform_policy_bundle",
            "platform_tool_definition",
            "platform_skill_template_version",
            "platform_skill_template",
            "organization_export_job",
            "organization_member"
    );

    private final OrgRepository orgRepository;
    private final OrganizationProvisioningService organizationProvisioningService;
    private final OrganizationRetentionPolicyRepository retentionPolicyRepository;
    private final OrganizationPurgeJobRepository purgeJobRepository;
    private final OrganizationExportJobRepository exportJobRepository;
    private final PlatformAuditService platformAuditService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final VectorStoreClient vectorStoreClient;
    private final TransactionTemplate transactionTemplate;
    private final Path kbStorageRoot;
    private final Path exportRoot;
    private final String purgeWorkerId;
    private final long purgeWorkerLeaseMinutes;

    public PlatformTenantLifecycleService(OrgRepository orgRepository,
                                          OrganizationProvisioningService organizationProvisioningService,
                                          OrganizationRetentionPolicyRepository retentionPolicyRepository,
                                          OrganizationPurgeJobRepository purgeJobRepository,
                                          OrganizationExportJobRepository exportJobRepository,
                                          PlatformAuditService platformAuditService,
                                          JdbcTemplate jdbcTemplate,
                                          ObjectMapper objectMapper,
                                          VectorStoreClient vectorStoreClient,
                                          PlatformTransactionManager transactionManager,
                                          @Value("${app.kb.storage-dir:./data/kb-files}") String kbStorageDir,
                                          @Value("${app.lifecycle.export-dir:./data/org-exports}") String exportDir,
                                          @Value("${app.lifecycle.purge-worker-id:}") String configuredPurgeWorkerId,
                                          @Value("${app.lifecycle.purge-worker-lease-minutes:60}") long purgeWorkerLeaseMinutes) {
        this.orgRepository = orgRepository;
        this.organizationProvisioningService = organizationProvisioningService;
        this.retentionPolicyRepository = retentionPolicyRepository;
        this.purgeJobRepository = purgeJobRepository;
        this.exportJobRepository = exportJobRepository;
        this.platformAuditService = platformAuditService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.vectorStoreClient = vectorStoreClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.kbStorageRoot = Path.of(kbStorageDir).toAbsolutePath().normalize();
        this.exportRoot = Path.of(exportDir).toAbsolutePath().normalize();
        this.purgeWorkerId = configuredPurgeWorkerId == null || configuredPurgeWorkerId.isBlank()
                ? defaultWorkerId()
                : configuredPurgeWorkerId.trim();
        this.purgeWorkerLeaseMinutes = Math.max(5L, purgeWorkerLeaseMinutes);
    }

    public List<TenantLifecycleView> listTenants() {
        return orgRepository.findAllByOrderByIdAsc().stream()
                .map(this::toTenantView)
                .toList();
    }

    @Transactional
    public TenantProvisionView createTenant(TenantProvisionCommand command, String actorId, String actorRole) {
        String tenantName = requireText(command.tenantName(), "租户名称不能为空");
        String ownerMobile = requireMobile(command.ownerMobile());
        String ownerEmail = trimToNull(command.ownerEmail());
        if (ownerEmail != null && !ownerEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }

        UserAccountEntity existingAccount = organizationProvisioningService.findMobileAccount(ownerMobile).orElse(null);
        if (existingAccount == null) {
            String initialPassword = requireText(command.initialPassword(), "首次 Owner 账号需要初始密码");
            if (initialPassword.length() < 8) {
                throw new IllegalArgumentException("初始密码至少需要 8 位");
            }
        }

        OrgEntity org = organizationProvisioningService.createOrganization(tenantName);
        UserAccountEntity account = existingAccount != null
                ? existingAccount
                : organizationProvisioningService.createMobileAccount(ownerMobile, command.ownerDisplayName(), ownerEmail);
        if (existingAccount == null) {
            organizationProvisioningService.assignPasswordCredential(account, command.initialPassword().trim());
        }
        UserEntity owner = organizationProvisioningService.createOwnerMembership(org, account, command.ownerDisplayName());
        retentionPolicyRepository.findById(org.getId())
                .orElseGet(() -> retentionPolicyRepository.save(new OrganizationRetentionPolicyEntity(org.getId())));
        platformAuditService.log(
                org.getId(),
                actorId,
                actorRole,
                "platform.tenant.create",
                "tenant",
                org.getId(),
                buildTenantCreateAuditDetail(account, existingAccount != null, command.provisionNote()));
        return new TenantProvisionView(
                org.getId(),
                org.getName(),
                org.getStatus(),
                owner.getId(),
                account.getId(),
                existingAccount != null
        );
    }

    @Transactional
    public TenantRetentionDetailView getRetentionDetail(String orgId) {
        OrgEntity org = requireOrg(orgId);
        OrganizationRetentionPolicyEntity policy = retentionPolicyRepository
                .findById(org.getId())
                .orElseGet(() -> retentionPolicyRepository.save(new OrganizationRetentionPolicyEntity(org.getId())));
        return toRetentionDetail(org, policy);
    }

    @Transactional
    public TenantRetentionDetailView updateRetention(String orgId, RetentionUpdateCommand command,
                                                    String actorId, String actorRole) {
        OrgEntity org = requireOrg(orgId);
        OrganizationRetentionPolicyEntity policy = retentionPolicyRepository
                .findById(org.getId())
                .orElseGet(() -> new OrganizationRetentionPolicyEntity(org.getId()));
        boolean legalHold = command.legalHold() != null && command.legalHold();
        policy.update(
                parseInstant(command.graceUntil(), "graceUntil"),
                parseInstant(command.suspendUntil(), "suspendUntil"),
                parseInstant(command.exportDeadline(), "exportDeadline"),
                parseInstant(command.purgeAfter(), "purgeAfter"),
                legalHold,
                blankToDefault(command.policySource(), "PLATFORM_MANUAL"),
                command.legalHoldReason(),
                blankToDefault(command.legalHoldApprovedBy(), actorId),
                legalHold ? parseInstant(command.legalHoldApprovedAt(), "legalHoldApprovedAt") : null,
                legalHold ? parseInstant(command.legalHoldReviewAt(), "legalHoldReviewAt") : null
        );
        OrganizationRetentionPolicyEntity saved = retentionPolicyRepository.save(policy);
        platformAuditService.log(org.getId(), actorId, actorRole, "platform.tenant.retention.update",
                "tenant", org.getId(), "Updated tenant retention policy");
        return toRetentionDetail(org, saved);
    }

    @Transactional
    public TenantLifecycleView suspendTenant(String orgId, String actorId, String actorRole, String reason) {
        OrgEntity org = requireOrg(orgId);
        org.setStatus("SUSPENDED");
        OrgEntity saved = orgRepository.save(org);
        platformAuditService.log(saved.getId(), actorId, actorRole, "platform.tenant.suspend",
                "tenant", saved.getId(), blankToDefault(reason, "Tenant suspended from platform lifecycle console"));
        return toTenantView(saved);
    }

    @Transactional
    public TenantLifecycleView resumeTenant(String orgId, String actorId, String actorRole, String reason) {
        OrgEntity org = requireOrg(orgId);
        org.setStatus("ACTIVE");
        OrgEntity saved = orgRepository.save(org);
        platformAuditService.log(saved.getId(), actorId, actorRole, "platform.tenant.resume",
                "tenant", saved.getId(), blankToDefault(reason, "Tenant resumed from platform lifecycle console"));
        return toTenantView(saved);
    }

    @Transactional
    public TenantLifecycleView markPendingPurge(String orgId, String actorId, String actorRole, String reason) {
        OrgEntity org = requireOrg(orgId);
        org.setStatus("PENDING_PURGE");
        OrgEntity saved = orgRepository.save(org);
        platformAuditService.log(saved.getId(), actorId, actorRole, "platform.tenant.pending_purge",
                "tenant", saved.getId(), blankToDefault(reason, "Tenant marked pending purge"));
        return toTenantView(saved);
    }

    @Transactional
    public PurgeJobView createPurgeJob(String orgId, PurgeJobCreateCommand command,
                                       String actorId, String actorRole) {
        if (command.dryRun() == null || command.dryRun()) {
            return createDryRunPurgeJob(orgId, command, actorId, actorRole);
        }
        OrgEntity org = requireOrg(orgId);
        OrganizationRetentionPolicyEntity policy = retentionPolicyRepository.findById(org.getId()).orElse(null);
        validateRealPurgeRequest(org, policy, command);
        validateNoActiveRealPurge(org.getId());
        OrganizationPurgeJobEntity sourceDryRun = purgeJobRepository
                .findByIdAndOrgIdAndDryRunTrueAndStatus(command.sourceDryRunJobId(), orgId, OrganizationPurgeJobEntity.STATUS_SUCCEEDED)
                .orElseThrow(() -> new IllegalArgumentException("A successful source dry-run job is required"));
        if (sourceDryRun.getCreatedAt() == null || sourceDryRun.getCreatedAt().isBefore(Instant.now().minus(24, ChronoUnit.HOURS))) {
            throw new IllegalArgumentException("Source dry-run job is too old; generate a new manifest first");
        }
        Map<String, Object> manifest = buildDryRunManifest(org);
        String manifestJson = serializeManifest(manifest);
        OrganizationPurgeJobEntity job = purgeJobRepository.save(OrganizationPurgeJobEntity.realPurge(
                org.getId(),
                actorId,
                command.reason(),
                sourceDryRun.getId(),
                command.confirmationText(),
                manifestJson,
                sha256(manifestJson)
        ));
        platformAuditService.log(org.getId(), actorId, actorRole, "platform.tenant.purge.queue",
                "tenant", org.getId(), "Queued guarded tenant purge");
        return toJobView(job, true);
    }

    public PurgeJobView getPurgeJob(String orgId, Long jobId) {
        return toJobView(purgeJobRepository.findByIdAndOrgId(jobId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Purge job not found")), true);
    }

    @Transactional
    public PurgeJobView retryPurgeJob(String orgId, Long jobId, PurgeJobRetryCommand command,
                                      String actorId, String actorRole) {
        OrgEntity org = requireOrg(orgId);
        OrganizationRetentionPolicyEntity policy = retentionPolicyRepository.findById(org.getId()).orElse(null);
        OrganizationPurgeJobEntity failedJob = purgeJobRepository.findByIdAndOrgId(jobId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Purge job not found"));
        validatePurgeRetryRequest(org, policy, failedJob, command);
        validateNoActiveRealPurge(org.getId());
        OrganizationPurgeJobEntity sourceDryRun = purgeJobRepository
                .findByIdAndOrgIdAndDryRunTrueAndStatus(failedJob.getSourceDryRunJobId(), orgId, OrganizationPurgeJobEntity.STATUS_SUCCEEDED)
                .orElseThrow(() -> new IllegalArgumentException("A successful source dry-run job is required"));
        if (sourceDryRun.getCreatedAt() == null || sourceDryRun.getCreatedAt().isBefore(Instant.now().minus(24, ChronoUnit.HOURS))) {
            throw new IllegalArgumentException("Source dry-run job is too old; generate a new manifest first");
        }
        Map<String, Object> manifest = buildDryRunManifest(org);
        String manifestJson = serializeManifest(manifest);
        OrganizationPurgeJobEntity retryJob = purgeJobRepository.save(OrganizationPurgeJobEntity.realPurge(
                org.getId(),
                actorId,
                blankToDefault(command.reason(), "Retry purge job #" + failedJob.getId()),
                sourceDryRun.getId(),
                command.confirmationText(),
                manifestJson,
                sha256(manifestJson)
        ));
        platformAuditService.log(org.getId(), actorId, actorRole, "platform.tenant.purge.retry.queue",
                "tenant", org.getId(), "Queued retry for tenant purge job #" + failedJob.getId());
        return toJobView(retryJob, true);
    }

    @Transactional
    public PurgeJobView cancelPurgeJob(String orgId, Long jobId, String actorId, String actorRole, String reason) {
        requireOrg(orgId);
        OrganizationPurgeJobEntity job = purgeJobRepository.findByIdAndOrgId(jobId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Purge job not found"));
        if (job.isDryRun() || !OrganizationPurgeJobEntity.PHASE_REAL_PURGE.equals(job.getPhase())) {
            throw new IllegalArgumentException("Only real purge jobs can be canceled");
        }
        if (!OrganizationPurgeJobEntity.STATUS_QUEUED.equals(job.getStatus())) {
            throw new IllegalArgumentException("Only queued purge jobs can be canceled");
        }
        job.markCanceled(blankToDefault(reason, "Canceled by platform operator"));
        OrganizationPurgeJobEntity saved = purgeJobRepository.save(job);
        platformAuditService.log(orgId, actorId, actorRole, "platform.tenant.purge.cancel",
                "tenant", orgId, "Canceled queued tenant purge job #" + job.getId());
        return toJobView(saved, true);
    }

    @Scheduled(
            fixedDelayString = "${app.lifecycle.purge-worker-delay-ms:30000}",
            initialDelayString = "${app.lifecycle.purge-worker-initial-delay-ms:30000}"
    )
    public synchronized void processQueuedPurgeJobs() {
        markExpiredRunningPurgeJobs();
        List<OrganizationPurgeJobEntity> queuedJobs = purgeJobRepository
                .findTop5ByStatusAndDryRunFalseOrderByCreatedAtAsc(OrganizationPurgeJobEntity.STATUS_QUEUED);
        for (OrganizationPurgeJobEntity job : queuedJobs) {
            if (claimQueuedPurgeJob(job.getId())) {
                executeClaimedPurgeJob(job.getId());
            }
        }
    }

    @Transactional
    public ExportJobView createExportJob(String orgId, String actorId, String actorRole, String reason) {
        OrgEntity org = requireOrg(orgId);
        OrganizationExportJobEntity job = exportJobRepository.save(new OrganizationExportJobEntity(
                org.getId(),
                actorId,
                reason
        ));
        try {
            Map<String, Object> manifest = buildExportManifest(org, job.getId());
            Path archive = writeExportArchive(org, job.getId(), manifest);
            job.markSucceeded(archive.toString(), serializeManifest(manifest));
            OrganizationExportJobEntity saved = exportJobRepository.save(job);
            platformAuditService.log(org.getId(), actorId, actorRole, "platform.tenant.export.create",
                    "tenant", org.getId(), "Created organization export archive");
            return toExportJobView(saved, true, false);
        } catch (Exception ex) {
            job.markFailed(ex.getMessage(), serializeManifest(Map.of(
                    "orgId", org.getId(),
                    "generatedAt", Instant.now().toString(),
                    "error", ex.getMessage()
            )));
            return toExportJobView(exportJobRepository.save(job), true, false);
        }
    }

    public List<ExportJobView> listExportJobs(String orgId, boolean includeManifest) {
        requireOrg(orgId);
        return exportJobRepository.findTop20ByOrgIdOrderByCreatedAtDesc(orgId).stream()
                .map(job -> toExportJobView(job, includeManifest, false))
                .toList();
    }

    public ExportJobView getExportJob(String orgId, Long jobId, boolean includeManifest) {
        return toExportJobView(requireExportJob(orgId, jobId), includeManifest, false);
    }

    public ExportArtifact downloadExport(String orgId, Long jobId) {
        OrganizationExportJobEntity job = requireExportJob(orgId, jobId);
        if (!OrganizationExportJobEntity.STATUS_SUCCEEDED.equals(job.getStatus())) {
            throw new IllegalArgumentException("Export job is not ready");
        }
        Path path = safeExportPath(job);
        try {
            return new ExportArtifact(path.getFileName().toString(), Files.readAllBytes(path));
        } catch (IOException ex) {
            throw new IllegalArgumentException("Export archive cannot be read");
        }
    }

    private PurgeJobView createDryRunPurgeJob(String orgId, PurgeJobCreateCommand command,
                                             String actorId, String actorRole) {
        OrgEntity org = requireOrg(orgId);
        String manifestJson = serializeManifest(buildDryRunManifest(org));
        OrganizationPurgeJobEntity job = purgeJobRepository.save(new OrganizationPurgeJobEntity(
                org.getId(),
                actorId,
                command.reason(),
                manifestJson
        ));
        platformAuditService.log(org.getId(), actorId, actorRole, "platform.tenant.purge.dry_run",
                "tenant", org.getId(), "Generated dry-run purge manifest");
        return toJobView(job, true);
    }

    private TenantLifecycleView toTenantView(OrgEntity org) {
        return new TenantLifecycleView(
                org.getId(),
                org.getName(),
                org.getStatus(),
                countRows("organization_member", org.getId()),
                retentionPolicyRepository.findById(org.getId()).map(this::toRetentionView).orElse(null),
                purgeJobRepository.findTopByOrgIdOrderByCreatedAtDesc(org.getId())
                        .map(job -> toJobView(job, false))
                        .orElse(null)
        );
    }

    private TenantRetentionDetailView toRetentionDetail(OrgEntity org, OrganizationRetentionPolicyEntity policy) {
        return new TenantRetentionDetailView(
                toTenantView(org),
                toRetentionView(policy),
                purgeJobRepository.findTop20ByOrgIdOrderByCreatedAtDesc(org.getId())
                        .stream()
                        .map(job -> toJobView(job, true))
                        .toList(),
                exportJobRepository.findTop20ByOrgIdOrderByCreatedAtDesc(org.getId())
                        .stream()
                        .map(job -> toExportJobView(job, true, false))
                        .toList()
        );
    }

    private RetentionPolicyView toRetentionView(OrganizationRetentionPolicyEntity policy) {
        return new RetentionPolicyView(
                policy.getOrgId(),
                iso(policy.getGraceUntil()),
                iso(policy.getSuspendUntil()),
                iso(policy.getExportDeadline()),
                iso(policy.getPurgeAfter()),
                policy.isLegalHold(),
                policy.getPolicySource(),
                policy.getLegalHoldReason(),
                policy.getLegalHoldApprovedBy(),
                iso(policy.getLegalHoldApprovedAt()),
                iso(policy.getLegalHoldReviewAt()),
                iso(policy.getCreatedAt()),
                iso(policy.getUpdatedAt())
        );
    }

    private PurgeJobView toJobView(OrganizationPurgeJobEntity job, boolean includeManifest) {
        Map<String, Object> manifest = parseManifest(job.getManifestJson());
        Map<String, Object> totals = manifestValue(manifest, "totals");
        Long totalRows = longValue(totals.get("rows"));
        Long unsupportedCount = longValue(totals.get("unsupported"));
        return new PurgeJobView(
                job.getId(),
                job.getOrgId(),
                job.isDryRun(),
                job.getStatus(),
                job.getPhase(),
                job.getRequestedBy(),
                job.getReason(),
                iso(job.getStartedAt()),
                iso(job.getFinishedAt()),
                job.getErrorMessage(),
                totalRows,
                unsupportedCount,
                includeManifest ? manifest : null,
                parseManifest(job.getResultJson()),
                job.getSourceDryRunJobId(),
                job.getManifestHash(),
                job.getWorkerId(),
                iso(job.getLockExpiresAt()),
                job.getAttemptCount(),
                iso(job.getDeadLetterAt()),
                iso(job.getCreatedAt())
        );
    }

    private ExportJobView toExportJobView(OrganizationExportJobEntity job, boolean includeManifest, boolean includeFilePath) {
        return new ExportJobView(
                job.getId(),
                job.getOrgId(),
                job.getStatus(),
                job.getRequestedBy(),
                job.getReason(),
                includeFilePath ? job.getFilePath() : null,
                includeManifest ? parseManifest(job.getManifestJson()) : null,
                job.getErrorMessage(),
                iso(job.getStartedAt()),
                iso(job.getFinishedAt()),
                iso(job.getCreatedAt()),
                iso(job.getUpdatedAt())
        );
    }

    private Map<String, Object> buildDryRunManifest(OrgEntity org) {
        List<Map<String, Object>> domains = new ArrayList<>();
        long totalRows = 0L;
        for (DomainGroup group : MANIFEST_DOMAINS) {
            List<Map<String, Object>> tables = new ArrayList<>();
            long domainRows = 0L;
            for (String table : group.tables()) {
                long rows = countRows(table, org.getId());
                domainRows += rows;
                tables.add(Map.of("table", table, "rows", rows));
            }
            totalRows += domainRows;
            Map<String, Object> domain = new LinkedHashMap<>();
            domain.put("domain", group.domain());
            domain.put("label", group.label());
            domain.put("rows", domainRows);
            domain.put("tables", tables);
            domains.add(domain);
        }

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("rows", totalRows);
        totals.put("domains", domains.size());
        totals.put("unsupported", UNSUPPORTED_MANIFEST_DOMAINS.size());

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("orgId", org.getId());
        manifest.put("orgName", org.getName());
        manifest.put("manifestVersion", "v2");
        manifest.put("dryRun", true);
        manifest.put("generatedAt", Instant.now().toString());
        manifest.put("warning", "Dry-run manifest only contains row counts and unsupported domains; it does not include business content or secrets.");
        manifest.put("totals", totals);
        manifest.put("domains", domains);
        manifest.put("delete", domains);
        manifest.put("redact", List.of(Map.of(
                "domain", "export_archive",
                "label", "导出包敏感字段脱敏",
                "fields", SecretKeyMatcher.sensitiveKeyHints()
        )));
        manifest.put("orphanAudit", buildOrphanAudit(org.getId()));
        manifest.put("retain_summary", List.of(
                Map.of("table", "org", "reason", "保留组织 ID、名称和 PURGED 状态用于审计与账务摘要。"),
                Map.of("table", "user_account", "reason", "全局账号可属于其他组织，不随单个组织销毁。"),
                Map.of("table", "organization_retention_policy", "reason", "保留生命周期策略和 legal hold 审计元数据。"),
                Map.of("table", "organization_purge_job", "reason", "保留 purge manifest 和执行摘要。"),
                Map.of("table", "platform_audit_log", "reason", "保留最小平台审计摘要，不含业务正文。")
        ));
        manifest.put("unsupported", UNSUPPORTED_MANIFEST_DOMAINS);
        return manifest;
    }

    private Map<String, Object> buildOrphanAudit(String orgId) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("generatedAt", Instant.now().toString());
        audit.put("fileStorage", auditKbFileStorage(orgId));
        audit.put("vectorStore", auditVectorStore(orgId));
        audit.put("mode", "read_only");
        return audit;
    }

    private Map<String, Object> auditKbFileStorage(String orgId) {
        Set<Path> registered = registeredKbFilePaths(orgId);
        List<Path> actualFiles = scanOrgKbFiles(orgId);
        List<String> orphanFiles = actualFiles.stream()
                .filter(file -> !registered.contains(file))
                .map(file -> kbStorageRoot.relativize(file).toString())
                .sorted()
                .limit(50)
                .toList();
        int orphanCount = (int) actualFiles.stream()
                .filter(file -> !registered.contains(file))
                .count();
        return Map.of(
                "status", "SCANNED",
                "storageRoot", kbStorageRoot.toString(),
                "registeredFiles", registered.size(),
                "scannedFiles", actualFiles.size(),
                "orphanFiles", orphanCount,
                "sample", orphanFiles
        );
    }

    private Map<String, Object> auditVectorStore(String orgId) {
        List<String> registeredVectorIds = new ArrayList<>(registeredVectorIds(orgId));
        registeredVectorIds.addAll(memoryVectorIds(orgId));
        VectorStoreAuditResult audit = vectorStoreClient.auditOrgVectors(orgId, registeredVectorIds);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", audit.success() ? "SCANNED" : "FAILED");
        out.put("registeredVectors", audit.registeredCount());
        out.put("scannedVectors", audit.scannedCount());
        out.put("orphanVectors", audit.orphanCount());
        out.put("sample", audit.orphanVectorIds());
        if (!audit.success()) {
            out.put("message", audit.message());
        }
        return out;
    }

    private Set<Path> registeredKbFilePaths(String orgId) {
        Set<Path> files = new HashSet<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT storage_path FROM kb_document WHERE org_id = ? AND storage_path IS NOT NULL", orgId);
        for (Map<String, Object> row : rows) {
            Object raw = row.get("storage_path");
            if (raw == null || raw.toString().isBlank()) {
                continue;
            }
            Path path = Path.of(raw.toString()).toAbsolutePath().normalize();
            if (path.startsWith(kbStorageRoot)) {
                files.add(path);
            }
        }
        return files;
    }

    private List<Path> scanOrgKbFiles(String orgId) {
        Path orgRoot = kbStorageRoot.resolve(orgId).normalize();
        if (!orgRoot.startsWith(kbStorageRoot) || !Files.isDirectory(orgRoot)) {
            return List.of();
        }
        try (var paths = Files.walk(orgRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.toAbsolutePath().normalize())
                    .filter(path -> path.startsWith(orgRoot))
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    private List<String> registeredVectorIds(String orgId) {
        return jdbcTemplate.queryForList(
                        "SELECT DISTINCT vector_id FROM kb_chunk WHERE org_id = ? AND vector_id IS NOT NULL", orgId)
                .stream()
                .map(row -> row.get("vector_id"))
                .filter(value -> value != null && !value.toString().isBlank())
                .map(Object::toString)
                .toList();
    }

    private Map<String, Object> buildExportManifest(OrgEntity org, Long jobId) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("orgId", org.getId());
        manifest.put("orgName", org.getName());
        manifest.put("jobId", jobId);
        manifest.put("manifestVersion", "v2");
        manifest.put("generatedAt", Instant.now().toString());
        manifest.put("redaction", "Secret, token, password, credential, encrypted and key-like fields are redacted in JSONL exports.");
        manifest.put("domains", buildDryRunManifest(org).get("domains"));
        manifest.put("files", listKbFiles(org.getId()).stream().map(Path::getFileName).map(Path::toString).toList());
        return manifest;
    }

    private Path writeExportArchive(OrgEntity org, Long jobId, Map<String, Object> manifest) throws IOException {
        Path orgExportDir = exportRoot.resolve(org.getId()).normalize();
        if (!orgExportDir.startsWith(exportRoot)) {
            throw new IllegalArgumentException("Invalid export path");
        }
        Files.createDirectories(orgExportDir);
        Path archive = orgExportDir.resolve("org-export-" + org.getId() + "-" + jobId + ".zip").normalize();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            writeZipEntry(zip, "manifest.json", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest));
            for (DomainGroup group : MANIFEST_DOMAINS) {
                for (String table : group.tables()) {
                    if (EXPORT_TABLES.contains(table)) {
                        writeTableExport(zip, org.getId(), table);
                    }
                }
            }
            for (Path file : listKbFiles(org.getId())) {
                zip.putNextEntry(new ZipEntry("files/" + file.getFileName()));
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
        return archive;
    }

    private void writeTableExport(ZipOutputStream zip, String orgId, String table) throws IOException {
        zip.putNextEntry(new ZipEntry("tables/" + table + ".jsonl"));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM " + table + " WHERE org_id = ?", orgId);
        for (Map<String, Object> row : rows) {
            zip.write(objectMapper.writeValueAsString(redactRow(table, row)).getBytes(StandardCharsets.UTF_8));
            zip.write('\n');
        }
        zip.closeEntry();
    }

    private Map<String, Object> redactRow(String table, Map<String, Object> row) {
        Map<String, Object> redacted = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey();
            if (shouldRecursivelyRedactOntologyJson(table, key)) {
                redacted.put(key, redactOntologyJson(entry.getValue()));
            } else if (SecretKeyMatcher.matches(key)) {
                redacted.put(key, "[REDACTED]");
            } else {
                redacted.put(key, entry.getValue());
            }
        }
        return redacted;
    }

    private boolean shouldRecursivelyRedactOntologyJson(String table, String key) {
        return ("ontology_data_source".equals(table) && "sample_data_json".equalsIgnoreCase(key))
                || ("ontology_version".equals(table) && "snapshot_json".equalsIgnoreCase(key))
                || (Set.of("ontology_physical_object", "ontology_physical_field").contains(table)
                && "metadata_json".equalsIgnoreCase(key));
    }

    private String redactOntologyJson(Object rawJson) {
        if (rawJson == null) {
            return null;
        }
        try {
            JsonNode json = objectMapper.readTree(rawJson.toString());
            return objectMapper.writeValueAsString(redactOntologyJsonNode(json));
        } catch (Exception ex) {
            return "[REDACTED]";
        }
    }

    private JsonNode redactOntologyJsonNode(JsonNode node) {
        if (node == null || node.isNull() || node.isValueNode()) {
            return node == null ? objectMapper.nullNode() : node.deepCopy();
        }
        if (node.isArray()) {
            ArrayNode redacted = objectMapper.createArrayNode();
            node.forEach(value -> redacted.add(redactOntologyJsonNode(value)));
            return redacted;
        }
        ObjectNode redacted = objectMapper.createObjectNode();
        node.fields().forEachRemaining(entry -> {
            String compactKey = SecretKeyMatcher.normalize(entry.getKey());
            if (SecretKeyMatcher.matches(entry.getKey())) {
                redacted.put(entry.getKey(), "[REDACTED]");
            } else if ("sampledatajson".equals(compactKey) && entry.getValue().isTextual()) {
                redacted.put(entry.getKey(), redactOntologyJson(entry.getValue().textValue()));
            } else {
                redacted.set(entry.getKey(), redactOntologyJsonNode(entry.getValue()));
            }
        });
        return redacted;
    }

    private List<Path> listKbFiles(String orgId) {
        List<Path> files = new ArrayList<>();
        for (Path path : registeredKbFilePaths(orgId)) {
            if (path.startsWith(kbStorageRoot) && Files.isRegularFile(path)) {
                files.add(path);
            }
        }
        return files;
    }

    private void writeZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private void validateRealPurgeRequest(OrgEntity org, OrganizationRetentionPolicyEntity policy, PurgeJobCreateCommand command) {
        if (!"PENDING_PURGE".equalsIgnoreCase(org.getStatus())) {
            throw new IllegalArgumentException("Tenant must be PENDING_PURGE before real purge");
        }
        if (policy != null && policy.isLegalHold()) {
            throw new IllegalArgumentException("Legal hold is active; purge is blocked");
        }
        if (!("PURGE " + org.getId()).equals(command.confirmationText())) {
            throw new IllegalArgumentException("confirmationText must be PURGE " + org.getId());
        }
        if (command.sourceDryRunJobId() == null) {
            throw new IllegalArgumentException("sourceDryRunJobId is required");
        }
    }

    private void validateNoActiveRealPurge(String orgId) {
        if (purgeJobRepository.existsByOrgIdAndDryRunFalseAndStatusIn(orgId, List.of(
                OrganizationPurgeJobEntity.STATUS_QUEUED,
                OrganizationPurgeJobEntity.STATUS_RUNNING
        ))) {
            throw new IllegalArgumentException("A real purge job is already queued or running");
        }
    }

    private void validatePurgeRetryRequest(OrgEntity org,
                                           OrganizationRetentionPolicyEntity policy,
                                           OrganizationPurgeJobEntity failedJob,
                                           PurgeJobRetryCommand command) {
        if (failedJob.isDryRun() || !OrganizationPurgeJobEntity.PHASE_REAL_PURGE.equals(failedJob.getPhase())) {
            throw new IllegalArgumentException("Only real purge jobs can be retried");
        }
        if (!Set.of(OrganizationPurgeJobEntity.STATUS_FAILED, OrganizationPurgeJobEntity.STATUS_PARTIAL_FAILED)
                .contains(failedJob.getStatus())) {
            throw new IllegalArgumentException("Only failed purge jobs can be retried");
        }
        if (failedJob.getSourceDryRunJobId() == null) {
            throw new IllegalArgumentException("Failed purge job has no source dry-run");
        }
        if (!"PENDING_PURGE".equalsIgnoreCase(org.getStatus())) {
            throw new IllegalArgumentException("Tenant must be PENDING_PURGE before retry");
        }
        if (policy != null && policy.isLegalHold()) {
            throw new IllegalArgumentException("Legal hold is active; retry is blocked");
        }
        if (!("PURGE " + org.getId()).equals(command.confirmationText())) {
            throw new IllegalArgumentException("confirmationText must be PURGE " + org.getId());
        }
    }

    private boolean claimQueuedPurgeJob(Long jobId) {
        Instant lockedAt = Instant.now();
        Instant lockExpiresAt = lockedAt.plus(purgeWorkerLeaseMinutes, ChronoUnit.MINUTES);
        Integer updated = transactionTemplate.execute(status -> purgeJobRepository.claimQueuedJob(
                jobId,
                OrganizationPurgeJobEntity.STATUS_QUEUED,
                OrganizationPurgeJobEntity.STATUS_RUNNING,
                purgeWorkerId,
                lockedAt,
                lockExpiresAt
        ));
        return updated != null && updated == 1;
    }

    private void executeClaimedPurgeJob(Long jobId) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                OrganizationPurgeJobEntity job = purgeJobRepository.findById(jobId)
                        .orElseThrow(() -> new IllegalArgumentException("Purge job not found"));
                if (!OrganizationPurgeJobEntity.STATUS_RUNNING.equals(job.getStatus())
                        || !purgeWorkerId.equals(job.getWorkerId())) {
                    return;
                }
                OrgEntity org = requireOrg(job.getOrgId());
                OrganizationRetentionPolicyEntity policy = retentionPolicyRepository.findById(org.getId()).orElse(null);
                validateQueuedPurgeCanRun(org, policy, job);
                PurgeExecutionResult result = executeRealPurge(org.getId());
                job.markFinished(result.status(), serializeManifest(result.result()), result.errorMessage());
                purgeJobRepository.save(job);
                if (OrganizationPurgeJobEntity.STATUS_SUCCEEDED.equals(result.status())) {
                    org.setStatus("PURGED");
                    orgRepository.save(org);
                }
                platformAuditService.log(org.getId(), job.getRequestedBy(), "PLATFORM_WORKER", "platform.tenant.purge.execute",
                        "tenant", org.getId(), "Executed queued tenant purge job #" + job.getId()
                                + " with worker " + purgeWorkerId);
            });
        } catch (Exception ex) {
            markClaimedPurgeJobFailed(jobId, ex);
        }
    }

    private void markClaimedPurgeJobFailed(Long jobId, Exception ex) {
        transactionTemplate.executeWithoutResult(status -> {
            OrganizationPurgeJobEntity job = purgeJobRepository.findById(jobId)
                    .orElseThrow(() -> new IllegalArgumentException("Purge job not found"));
            job.markFinished(OrganizationPurgeJobEntity.STATUS_FAILED, serializeManifest(Map.of(
                    "orgId", job.getOrgId(),
                    "jobId", job.getId(),
                    "workerId", purgeWorkerId,
                    "error", ex.getMessage() == null ? "Queued purge failed" : ex.getMessage()
            )), ex.getMessage());
            purgeJobRepository.save(job);
            platformAuditService.log(job.getOrgId(), job.getRequestedBy(), "PLATFORM_WORKER", "platform.tenant.purge.failed",
                    "tenant", job.getOrgId(), "Queued tenant purge job failed before completion");
        });
    }

    private void markExpiredRunningPurgeJobs() {
        Instant now = Instant.now();
        List<OrganizationPurgeJobEntity> expiredJobs = purgeJobRepository
                .findTop10ByStatusAndDryRunFalseAndLockExpiresAtBeforeOrderByLockExpiresAtAsc(
                        OrganizationPurgeJobEntity.STATUS_RUNNING, now);
        for (OrganizationPurgeJobEntity expiredJob : expiredJobs) {
            transactionTemplate.executeWithoutResult(status -> {
                OrganizationPurgeJobEntity job = purgeJobRepository.findById(expiredJob.getId())
                        .orElseThrow(() -> new IllegalArgumentException("Purge job not found"));
                if (!OrganizationPurgeJobEntity.STATUS_RUNNING.equals(job.getStatus())
                        || job.getLockExpiresAt() == null
                        || !job.getLockExpiresAt().isBefore(now)) {
                    return;
                }
                String reason = "Purge worker lease expired before completion; manual inspection is required before retry";
                job.markDeadLetter(serializeManifest(Map.of(
                        "orgId", job.getOrgId(),
                        "jobId", job.getId(),
                        "workerId", job.getWorkerId(),
                        "lockExpiresAt", job.getLockExpiresAt().toString(),
                        "error", reason
                )), reason);
                purgeJobRepository.save(job);
                platformAuditService.log(job.getOrgId(), job.getRequestedBy(), "PLATFORM_WORKER", "platform.tenant.purge.dead_letter",
                        "tenant", job.getOrgId(), "Marked stale tenant purge job #" + job.getId() + " as dead-letter");
            });
        }
    }

    private void validateQueuedPurgeCanRun(OrgEntity org,
                                           OrganizationRetentionPolicyEntity policy,
                                           OrganizationPurgeJobEntity job) {
        if (!"PENDING_PURGE".equalsIgnoreCase(org.getStatus())) {
            throw new IllegalArgumentException("Tenant must be PENDING_PURGE before queued purge can run");
        }
        if (policy != null && policy.isLegalHold()) {
            throw new IllegalArgumentException("Legal hold is active; queued purge is blocked");
        }
        if (!("PURGE " + org.getId()).equals(job.getConfirmationText())) {
            throw new IllegalArgumentException("confirmationText must be PURGE " + org.getId());
        }
        if (job.getSourceDryRunJobId() == null) {
            throw new IllegalArgumentException("sourceDryRunJobId is required");
        }
        OrganizationPurgeJobEntity sourceDryRun = purgeJobRepository
                .findByIdAndOrgIdAndDryRunTrueAndStatus(job.getSourceDryRunJobId(), org.getId(), OrganizationPurgeJobEntity.STATUS_SUCCEEDED)
                .orElseThrow(() -> new IllegalArgumentException("A successful source dry-run job is required"));
        if (sourceDryRun.getCreatedAt() == null || sourceDryRun.getCreatedAt().isBefore(Instant.now().minus(24, ChronoUnit.HOURS))) {
            throw new IllegalArgumentException("Source dry-run job is too old; generate a new manifest first");
        }
    }

    private PurgeExecutionResult executeRealPurge(String orgId) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();
        deleteVectors(orgId, result, failures);
        deleteKbFiles(orgId, result, failures);
        deleteExportArchives(orgId, result, failures);
        for (String table : PURGE_DELETE_TABLES) {
            try {
                int deleted = jdbcTemplate.update("DELETE FROM " + table + " WHERE org_id = ?", orgId);
                result.put(table, deleted);
            } catch (Exception ex) {
                failures.add(table + ": " + ex.getMessage());
            }
        }
        long remainingRows = countRemainingBusinessRows(orgId);
        result.put("remainingBusinessRows", remainingRows);
        if (remainingRows > 0 && failures.isEmpty()) {
            failures.add("business rows remain after purge");
        }
        String status = failures.isEmpty()
                ? OrganizationPurgeJobEntity.STATUS_SUCCEEDED
                : OrganizationPurgeJobEntity.STATUS_PARTIAL_FAILED;
        if (!failures.isEmpty()) {
            result.put("failures", failures);
        }
        return new PurgeExecutionResult(status, result, failures.isEmpty() ? null : String.join("; ", failures));
    }

    private void deleteVectors(String orgId, Map<String, Object> result, List<String> failures) {
        List<String> vectorIds = new ArrayList<>(registeredVectorIds(orgId));
        vectorIds.addAll(memoryVectorIds(orgId));
        if (!vectorIds.isEmpty()) {
            VectorDeleteResult byIds = vectorStoreClient.deleteByVectorIds(orgId, vectorIds);
            result.put("vectorDeleteByIds", Map.of(
                    "requested", byIds.requestedCount(),
                    "deleted", byIds.deletedCount(),
                    "success", byIds.success()
            ));
            if (!byIds.success()) {
                failures.add("vector ids: " + byIds.message());
            }
        }
        List<Map<String, Object>> kbs = jdbcTemplate.queryForList("SELECT id FROM knowledge_base WHERE org_id = ?", orgId);
        for (Map<String, Object> row : kbs) {
            Object rawKbId = row.get("id");
            if (rawKbId == null) {
                continue;
            }
            VectorDeleteResult byKb = vectorStoreClient.deleteByKnowledgeBase(orgId, rawKbId.toString());
            if (!byKb.success()) {
                failures.add("vector kb " + rawKbId + ": " + byKb.message());
            }
        }
    }

    private List<String> memoryVectorIds(String orgId) {
        return jdbcTemplate.queryForList("SELECT vector_id FROM memory_vector_fragment WHERE org_id = ? AND status = 'ACTIVE'", String.class, orgId);
    }

    private void deleteKbFiles(String orgId, Map<String, Object> result, List<String> failures) {
        int deleted = 0;
        for (Path file : listKbFiles(orgId)) {
            try {
                if (Files.deleteIfExists(file)) {
                    deleted++;
                }
            } catch (IOException ex) {
                failures.add("file " + file.getFileName() + ": " + ex.getMessage());
            }
        }
        result.put("deletedFiles", deleted);
        int deletedOrphans = 0;
        for (Path file : scanOrgKbFiles(orgId)) {
            try {
                if (Files.deleteIfExists(file)) {
                    deletedOrphans++;
                }
            } catch (IOException ex) {
                failures.add("orphan file " + file.getFileName() + ": " + ex.getMessage());
            }
        }
        result.put("deletedOrphanFiles", deletedOrphans);
    }

    private void deleteExportArchives(String orgId, Map<String, Object> result, List<String> failures) {
        int deleted = 0;
        for (OrganizationExportJobEntity job : exportJobRepository.findAllByOrgId(orgId)) {
            if (job.getFilePath() == null || job.getFilePath().isBlank()) {
                continue;
            }
            try {
                Path path = safeExportPath(job);
                if (Files.deleteIfExists(path)) {
                    deleted++;
                }
            } catch (Exception ex) {
                failures.add("export " + job.getId() + ": " + ex.getMessage());
            }
        }
        result.put("deletedExportArchives", deleted);
    }

    private long countRemainingBusinessRows(String orgId) {
        long total = 0L;
        for (String table : PURGE_DELETE_TABLES) {
            total += countRows(table, orgId);
        }
        return total;
    }

    private long countRows(String table, String orgId) {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE org_id = ?", Long.class, orgId);
        return value == null ? 0L : value;
    }

    private String buildTenantCreateAuditDetail(UserAccountEntity account, boolean reusedExistingAccount, String provisionNote) {
        String note = trimToNull(provisionNote);
        StringBuilder builder = new StringBuilder(reusedExistingAccount
                ? "Provisioned tenant with reused existing owner account"
                : "Provisioned tenant with new owner account");
        builder.append(" (account=").append(account.getId()).append(")");
        if (note != null) {
            builder.append(" note=").append(note);
        }
        return builder.toString();
    }

    private String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private String requireMobile(String mobile) {
        String normalized = trimToNull(mobile);
        if (normalized == null || !normalized.matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("手机号必须是 11 位大陆手机号");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private OrgEntity requireOrg(String orgId) {
        if (orgId == null || orgId.isBlank()) {
            throw new IllegalArgumentException("Tenant not found");
        }
        return orgRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
    }

    private OrganizationExportJobEntity requireExportJob(String orgId, Long jobId) {
        return exportJobRepository.findByIdAndOrgId(jobId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Export job not found"));
    }

    private Path safeExportPath(OrganizationExportJobEntity job) {
        if (job.getFilePath() == null || job.getFilePath().isBlank()) {
            throw new IllegalArgumentException("Export archive is missing");
        }
        Path path = Path.of(job.getFilePath()).toAbsolutePath().normalize();
        if (!path.startsWith(exportRoot)) {
            throw new IllegalArgumentException("Export archive path is invalid");
        }
        return path;
    }

    private String serializeManifest(Map<String, Object> manifest) {
        try {
            return objectMapper.writeValueAsString(manifest);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to serialize manifest");
        }
    }

    private Map<String, Object> parseManifest(String manifestJson) {
        if (manifestJson == null || manifestJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(manifestJson, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of("parseError", "manifest_json is invalid");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> manifestValue(Map<String, Object> manifest, String key) {
        Object raw = manifest.get(key);
        return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private Long longValue(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Instant parseInstant(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(field + " must be an ISO-8601 instant");
        }
    }

    private String iso(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String defaultWorkerId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            host = "local";
        }
        return host + "-" + UUID.randomUUID();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte item : bytes) {
                out.append(String.format("%02x", item));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalArgumentException("SHA-256 is not available");
        }
    }

    private record DomainGroup(String domain, String label, List<String> tables) {
    }

    private record PurgeExecutionResult(String status, Map<String, Object> result, String errorMessage) {
    }

    public record RetentionUpdateCommand(
            String graceUntil,
            String suspendUntil,
            String exportDeadline,
            String purgeAfter,
            Boolean legalHold,
            String policySource,
            String legalHoldReason,
            String legalHoldApprovedBy,
            String legalHoldApprovedAt,
            String legalHoldReviewAt
    ) {
    }

    public record PurgeJobCreateCommand(Boolean dryRun, String reason, Long sourceDryRunJobId, String confirmationText) {
    }

    public record PurgeJobRetryCommand(String confirmationText, String reason) {
    }

    public record TenantProvisionCommand(
            String tenantName,
            String ownerMobile,
            String ownerDisplayName,
            String ownerEmail,
            String initialPassword,
            String provisionNote
    ) {
    }

    public record TenantLifecycleView(
            String orgId,
            String name,
            String status,
            long memberCount,
            RetentionPolicyView retention,
            PurgeJobView latestJob
    ) {
    }

    public record TenantRetentionDetailView(
            TenantLifecycleView tenant,
            RetentionPolicyView retention,
            List<PurgeJobView> jobs,
            List<ExportJobView> exportJobs
    ) {
    }

    public record TenantProvisionView(
            String orgId,
            String orgName,
            String status,
            String ownerMemberId,
            String ownerAccountId,
            boolean reusedExistingAccount
    ) {
    }

    public record RetentionPolicyView(
            String orgId,
            String graceUntil,
            String suspendUntil,
            String exportDeadline,
            String purgeAfter,
            boolean legalHold,
            String policySource,
            String legalHoldReason,
            String legalHoldApprovedBy,
            String legalHoldApprovedAt,
            String legalHoldReviewAt,
            String createdAt,
            String updatedAt
    ) {
    }

    public record PurgeJobView(
            Long id,
            String orgId,
            boolean dryRun,
            String status,
            String phase,
            String requestedBy,
            String reason,
            String startedAt,
            String finishedAt,
            String errorMessage,
            Long totalRows,
            Long unsupportedCount,
            Map<String, Object> manifest,
            Map<String, Object> result,
            Long sourceDryRunJobId,
            String manifestHash,
            String workerId,
            String lockExpiresAt,
            int attemptCount,
            String deadLetterAt,
            String createdAt
    ) {
    }

    public record ExportJobView(
            Long id,
            String orgId,
            String status,
            String requestedBy,
            String reason,
            String filePath,
            Map<String, Object> manifest,
            String errorMessage,
            String startedAt,
            String finishedAt,
            String createdAt,
            String updatedAt
    ) {
    }

    public record ExportArtifact(String filename, byte[] bytes) {
    }
}
