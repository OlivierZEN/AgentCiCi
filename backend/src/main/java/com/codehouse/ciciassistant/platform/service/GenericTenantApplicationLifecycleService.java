package com.codehouse.ciciassistant.platform.service;

import com.codehouse.ciciassistant.common.error.ConflictException;
import com.codehouse.ciciassistant.common.error.ResourceNotFoundException;
import com.codehouse.ciciassistant.semattice.SematticeProvisioningService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable standard lifecycle executor for newly registered internal applications. */
@Service
public class GenericTenantApplicationLifecycleService {

    private static final Set<String> OPERATIONS = Set.of(
            "ACTIVATE", "RECONCILE", "SUSPEND", "RESUME", "UPGRADE");
    private static final Set<String> SPECIALIZED_APPLICATIONS = Set.of(
            "agentcici", "semattice", "devautopilot");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final InternalApplicationRegistryService registry;
    private final InternalApplicationProviderConnectionService providerConnections;
    private final SematticeProvisioningService sematticeProvisioning;
    private final PlatformAuditService audit;
    private final HttpClient httpClient;

    public GenericTenantApplicationLifecycleService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            InternalApplicationRegistryService registry,
            InternalApplicationProviderConnectionService providerConnections,
            SematticeProvisioningService sematticeProvisioning,
            PlatformAuditService audit) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.providerConnections = providerConnections;
        this.sematticeProvisioning = sematticeProvisioning;
        this.audit = audit;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public OperationView execute(String companyId,
                                 String appCode,
                                 OperationCommand command,
                                 String actorId,
                                 String actorRole) {
        String normalizedCompanyId = requireCompany(companyId);
        String normalizedAppCode = normalizeAppCode(appCode);
        if (SPECIALIZED_APPLICATIONS.contains(normalizedAppCode)) {
            throw new ConflictException("This application keeps its specialized lifecycle adapter");
        }
        String operationType = enumValue(command.operationType(), OPERATIONS);
        String idempotencyKey = requireText(command.idempotencyKey(), "idempotencyKey", 128);
        OperationView existing = findByIdempotency(
                normalizedCompanyId, normalizedAppCode, operationType, idempotencyKey);
        if (existing != null) {
            return existing;
        }

        InternalApplicationRegistryService.ApplicationDetailView detail = registry.get(normalizedAppCode);
        if (!InternalApplicationRegistryService.STATUS_PUBLISHED.equals(detail.application().catalogStatus())) {
            throw new ConflictException("Application catalog is not published");
        }
        String targetVersion = detail.application().defaultVersion();
        if (targetVersion == null) {
            throw new ConflictException("Application has no published default version");
        }
        InternalApplicationRegistryService.VersionView version = detail.versions().stream()
                .filter(item -> targetVersion.equals(item.version()) && "PUBLISHED".equals(item.status()))
                .findFirst()
                .orElseThrow(() -> new ConflictException("Application default version is not published"));
        if (version.providerBindingKey() == null || version.providerBindingKey().isBlank()) {
            throw new ConflictException("Application version has no provider connection");
        }
        verifyDependencies(normalizedCompanyId, version);
        InternalApplicationProviderConnectionService.ActiveConnection connection =
                providerConnections.resolveActive(normalizedAppCode, version.providerBindingKey());
        String actionPath = actionPath(connection.revision(), operationType);
        if (actionPath == null) {
            throw new ConflictException("Provider connection does not support " + operationType);
        }
        verifyCurrentState(normalizedCompanyId, normalizedAppCode, operationType);
        List<StepDefinition> steps = operationSteps(version, operationType);

        String operationId = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operationId", operationId);
        payload.put("idempotencyKey", idempotencyKey);
        payload.put("operationType", operationType);
        payload.put("companyId", normalizedCompanyId);
        payload.put("appCode", normalizedAppCode);
        payload.put("applicationVersion", targetVersion);
        payload.put("contractVersion", connection.revision().contractVersion());
        payload.put("dependencies", version.dependencies().stream().map(dependency -> Map.of(
                "appCode", dependency.appCode(),
                "versionConstraint", dependency.versionConstraint(),
                "dependencyType", dependency.dependencyType())).toList());
        String body = writeJson(payload);
        String requestDigest = sha256(body);
        try {
            jdbc.update("""
                    INSERT INTO tenant_application_operation(
                        id,company_id,app_code,target_version,operation_type,operation_status,
                        idempotency_key,connection_revision_id,request_digest,created_by)
                    VALUES (?,?,?,?,?,'PENDING',?,?,?,?)
                    """, operationId, normalizedCompanyId, normalizedAppCode, targetVersion,
                    operationType, idempotencyKey, connection.revision().id(), requestDigest, actorId);
        } catch (DataIntegrityViolationException exception) {
            OperationView raced = findByIdempotency(
                    normalizedCompanyId, normalizedAppCode, operationType, idempotencyKey);
            if (raced != null) return raced;
            throw new ConflictException("Tenant application operation conflicts with existing state");
        }
        for (StepDefinition step : steps) {
            jdbc.update("""
                    INSERT INTO tenant_application_operation_step(
                        id,operation_id,step_code,step_type,step_status)
                    VALUES (?,?,?,?,'PENDING')
                    """, UUID.randomUUID().toString(), operationId, step.code(), step.type());
        }
        jdbc.update("""
                UPDATE tenant_application_operation
                SET operation_status='RUNNING',started_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
                WHERE id=?
                """, operationId);

        try {
            String lastResponse = "";
            for (StepDefinition step : steps) {
                Map<String, Object> stepPayload = new LinkedHashMap<>(payload);
                stepPayload.put("stepCode", step.code());
                stepPayload.put("capability", step.capability());
                String stepBody = writeJson(stepPayload);
                lastResponse = invoke(operationId, normalizedAppCode, connection,
                        operationType, actionPath, step, stepBody);
            }
            String responseDigest = sha256(lastResponse);
            applySuccessfulState(normalizedCompanyId, normalizedAppCode, targetVersion,
                    operationType, operationId, actorId);
            jdbc.update("""
                    UPDATE tenant_application_operation
                    SET operation_status='SUCCEEDED',response_digest=?,completed_at=CURRENT_TIMESTAMP,
                        updated_at=CURRENT_TIMESTAMP,error_code=NULL,error_summary=NULL
                    WHERE id=?
                    """, responseDigest, operationId);
            audit.log("platform", actorId, actorRole, "tenant_application.lifecycle_succeeded",
                    "tenant_application_operation", operationId,
                    "appCode=" + normalizedAppCode + ";operation=" + operationType
                            + ";connectionRevision=" + connection.revision().id());
        } catch (RuntimeException exception) {
            String errorCode = lifecycleError(exception);
            jdbc.update("""
                    UPDATE tenant_application_operation
                    SET operation_status='FAILED',error_code=?,error_summary=?,completed_at=CURRENT_TIMESTAMP,
                        updated_at=CURRENT_TIMESTAMP
                    WHERE id=?
                    """, errorCode, safeSummary(exception), operationId);
            audit.log("platform", actorId, actorRole, "tenant_application.lifecycle_failed",
                    "tenant_application_operation", operationId,
                    "appCode=" + normalizedAppCode + ";operation=" + operationType + ";error=" + errorCode);
        }
        return requireOperation(operationId);
    }

    @Transactional(readOnly = true)
    public List<OperationView> list(String companyId, String appCode) {
        String normalizedCompanyId = requireCompany(companyId);
        String normalizedAppCode = normalizeAppCode(appCode);
        return jdbc.query("""
                SELECT id,company_id,app_code,target_version,operation_type,operation_status,
                       idempotency_key,connection_revision_id,request_digest,response_digest,
                       error_code,error_summary,started_at,completed_at,created_at,updated_at
                FROM tenant_application_operation
                WHERE company_id=? AND app_code=? ORDER BY created_at DESC
                LIMIT 50
                """, (rs, rowNum) -> mapOperation(rs), normalizedCompanyId, normalizedAppCode);
    }

    @Transactional(readOnly = true)
    public RuntimeView runtime(String companyId, String appCode) {
        List<RuntimeView> activations = jdbc.query("""
                SELECT template_version,desired_state,actual_state,activation_stage,failed_stage,
                       last_error_code,attempt_count
                FROM tenant_application_activation WHERE company_id=? AND app_code=?
                """, (rs, rowNum) -> new RuntimeView(
                true, rs.getString("template_version"), rs.getString("desired_state"),
                rs.getString("actual_state"), "ACTIVE".equals(rs.getString("actual_state")) ? "READY" : "UNKNOWN",
                "ACTIVE".equals(rs.getString("actual_state")), rs.getString("activation_stage"),
                rs.getString("failed_stage"), rs.getString("last_error_code"), rs.getInt("attempt_count")),
                companyId, appCode);
        if (!activations.isEmpty()) {
            return activations.getFirst();
        }
        List<OperationView> operations = list(companyId, appCode);
        if (!operations.isEmpty()) {
            OperationView latest = operations.getFirst();
            if ("FAILED".equals(latest.status())) {
                return new RuntimeView(false, null, "NOT_ENABLED", "FAILED", "BLOCKED", false,
                        latest.operationType(), latest.operationType(), latest.errorCode(), 1);
            }
            if (Set.of("PENDING", "RUNNING").contains(latest.status())) {
                return new RuntimeView(false, latest.targetVersion(), "ACTIVE", "PROVISIONING", "UNKNOWN", false,
                        latest.operationType(), null, null, 1);
            }
        }
        return RuntimeView.notEnabled();
    }

    public boolean connectionSupported(String appCode, String bindingKey) {
        return providerConnections.supportsLifecycle(appCode, bindingKey);
    }

    String invoke(String operationId,
                  String appCode,
                  InternalApplicationProviderConnectionService.ActiveConnection connection,
                  String operationType,
                  String path,
                  StepDefinition step,
                  String body) {
        String stepCode = step.code();
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= connection.revision().maxAttempts(); attempt++) {
            jdbc.update("""
                    UPDATE tenant_application_operation_step
                    SET step_status='RUNNING',attempt_count=?,started_at=COALESCE(started_at,CURRENT_TIMESTAMP),
                        updated_at=CURRENT_TIMESTAMP,error_code=NULL
                    WHERE operation_id=? AND step_code=?
                    """, attempt, operationId, stepCode);
            try {
                URI uri = providerConnections.resolvedUri(connection.revision().baseUrl(), path);
                providerConnections.validateResolvedAddress(uri, connection.connection().networkScope());
                HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofMillis(connection.revision().timeoutMs()))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("Idempotency-Key", operationId + ":" + stepCode)
                        .header("X-Correlation-Id", operationId)
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
                providerConnections.applyAuth(request, connection.revision(), appCode, "POST", path, body);
                HttpResponse<java.io.InputStream> response = httpClient.send(request.build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    response.body().close();
                    throw new ProviderLifecycleException("PROVIDER_HTTP_" + response.statusCode(),
                            "Provider lifecycle request returned HTTP " + response.statusCode());
                }
                byte[] responseBytes;
                try (java.io.InputStream stream = response.body()) {
                    responseBytes = stream.readNBytes(1_048_577);
                }
                if (responseBytes.length > 1_048_576) {
                    throw new ProviderLifecycleException("PROVIDER_RESPONSE_TOO_LARGE",
                            "Provider response exceeds the one-megabyte limit");
                }
                String responseBody = new String(responseBytes, StandardCharsets.UTF_8);
                validateResponse(responseBody, operationType);
                String digest = sha256(responseBody);
                jdbc.update("""
                        UPDATE tenant_application_operation_step
                        SET step_status='SUCCEEDED',response_digest=?,completed_at=CURRENT_TIMESTAMP,
                            updated_at=CURRENT_TIMESTAMP,error_code=NULL
                        WHERE operation_id=? AND step_code=?
                        """, digest, operationId, stepCode);
                return responseBody;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                lastFailure = new ProviderLifecycleException("PROVIDER_INTERRUPTED", "Provider lifecycle request interrupted");
                break;
            } catch (ProviderLifecycleException exception) {
                lastFailure = exception;
            } catch (Exception exception) {
                lastFailure = new ProviderLifecycleException(providerFailureCode(exception),
                        "Provider lifecycle request failed");
            }
        }
        String errorCode = lifecycleError(lastFailure);
        jdbc.update("""
                UPDATE tenant_application_operation_step
                SET step_status='FAILED',error_code=?,completed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
                WHERE operation_id=? AND step_code=?
                """, errorCode, operationId, stepCode);
        throw lastFailure == null
                ? new ProviderLifecycleException("PROVIDER_FAILED", "Provider lifecycle request failed")
                : lastFailure;
    }

    private void validateResponse(String body, String operationType) {
        if (body == null || body.isBlank()) {
            throw new ProviderLifecycleException("PROVIDER_RESPONSE_INVALID",
                    "Provider response must contain a lifecycle status");
        }
        try {
            JsonNode response = objectMapper.readTree(body);
            String status = response.path("status").asText("").toUpperCase(Locale.ROOT);
            if (status.isBlank() || !Set.of("SUCCEEDED", "ACTIVE", "SUSPENDED").contains(status)) {
                throw new ProviderLifecycleException("PROVIDER_REJECTED", "Provider rejected " + operationType);
            }
        } catch (ProviderLifecycleException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ProviderLifecycleException("PROVIDER_RESPONSE_INVALID", "Provider response is not valid JSON");
        }
    }

    private List<StepDefinition> operationSteps(
            InternalApplicationRegistryService.VersionView version, String operationType) {
        if (!"ACTIVATE".equals(operationType)) {
            return List.of(new StepDefinition("provider-" + operationType.toLowerCase(Locale.ROOT),
                    "PROVIDER_CALLBACK", "tenant." + operationType.toLowerCase(Locale.ROOT)));
        }
        List<StepDefinition> steps = new ArrayList<>();
        version.manifest().path("steps").forEach(step -> {
            String type = step.path("type").asText();
            if (!"PROVIDER_CALLBACK".equals(type)) {
                throw new ConflictException("Generic applications currently support Provider callback steps only");
            }
            steps.add(new StepDefinition(step.path("code").asText(), type, step.path("capability").asText()));
        });
        if (steps.isEmpty()) {
            throw new ConflictException("Application version has no Provider callback step");
        }
        return List.copyOf(steps);
    }

    private void verifyDependencies(String companyId,
                                    InternalApplicationRegistryService.VersionView version) {
        for (InternalApplicationRegistryService.DependencyView dependency : version.dependencies()) {
            if ("OPTIONAL".equals(dependency.dependencyType())) continue;
            boolean active;
            if ("agentcici".equals(dependency.appCode())) {
                String status = jdbc.queryForObject("SELECT status FROM company WHERE id=?", String.class, companyId);
                active = "ACTIVE".equals(status);
            } else if ("semattice".equals(dependency.appCode())) {
                active = "PROVISIONED".equals(sematticeProvisioning.getProvisioningStatus(companyId).state());
            } else {
                Long count = jdbc.queryForObject("""
                        SELECT count(*) FROM tenant_application_activation
                        WHERE company_id=? AND app_code=? AND actual_state='ACTIVE'
                        """, Long.class, companyId, dependency.appCode());
                active = count != null && count > 0;
            }
            if (!active) {
                throw new ConflictException("Required application dependency is not active: " + dependency.appCode());
            }
        }
    }

    private void verifyCurrentState(String companyId, String appCode, String operationType) {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM tenant_application_activation WHERE company_id=? AND app_code=?
                """, Long.class, companyId, appCode);
        boolean exists = count != null && count > 0;
        if (!"ACTIVATE".equals(operationType) && !exists) {
            throw new ConflictException("Tenant application is not activated");
        }
        if ("ACTIVATE".equals(operationType) && exists) {
            throw new ConflictException("Tenant application is already activated");
        }
    }

    @Transactional
    protected void applySuccessfulState(String companyId,
                                        String appCode,
                                        String version,
                                        String operationType,
                                        String operationId,
                                        String actorId) {
        switch (operationType) {
            case "ACTIVATE" -> jdbc.update("""
                    INSERT INTO tenant_application_activation(
                        id,company_id,app_code,template_version,idempotency_key,desired_state,actual_state,
                        created_by_member_id,activation_stage,attempt_count,last_attempt_at)
                    VALUES (?,?,?,?,?,'ACTIVE','ACTIVE',NULL,'ACTIVE',1,CURRENT_TIMESTAMP)
                    """, UUID.randomUUID().toString(), companyId, appCode, version,
                    "generic:" + operationId);
            case "SUSPEND" -> jdbc.update("""
                    UPDATE tenant_application_activation
                    SET desired_state='SUSPENDED',actual_state='SUSPENDED',updated_at=CURRENT_TIMESTAMP,
                        last_error_code=NULL WHERE company_id=? AND app_code=?
                    """, companyId, appCode);
            case "RESUME", "RECONCILE", "UPGRADE" -> jdbc.update("""
                    UPDATE tenant_application_activation
                    SET desired_state='ACTIVE',actual_state='ACTIVE',template_version=?,
                        activation_stage='ACTIVE',updated_at=CURRENT_TIMESTAMP,last_error_code=NULL
                    WHERE company_id=? AND app_code=?
                    """, version, companyId, appCode);
            default -> throw new IllegalArgumentException("Unsupported operation type");
        }
    }

    private String actionPath(InternalApplicationProviderConnectionService.RevisionView revision,
                              String operationType) {
        return switch (operationType) {
            case "ACTIVATE" -> revision.activatePath();
            case "RECONCILE" -> revision.reconcilePath();
            case "SUSPEND" -> revision.suspendPath();
            case "RESUME" -> revision.resumePath();
            case "UPGRADE" -> revision.upgradePath();
            default -> null;
        };
    }

    private OperationView findByIdempotency(String companyId, String appCode,
                                            String operationType, String idempotencyKey) {
        List<OperationView> values = jdbc.query("""
                SELECT id,company_id,app_code,target_version,operation_type,operation_status,
                       idempotency_key,connection_revision_id,request_digest,response_digest,
                       error_code,error_summary,started_at,completed_at,created_at,updated_at
                FROM tenant_application_operation
                WHERE company_id=? AND app_code=? AND operation_type=? AND idempotency_key=?
                """, (rs, rowNum) -> mapOperation(rs), companyId, appCode, operationType, idempotencyKey);
        return values.isEmpty() ? null : values.getFirst();
    }

    private OperationView requireOperation(String operationId) {
        List<OperationView> values = jdbc.query("""
                SELECT id,company_id,app_code,target_version,operation_type,operation_status,
                       idempotency_key,connection_revision_id,request_digest,response_digest,
                       error_code,error_summary,started_at,completed_at,created_at,updated_at
                FROM tenant_application_operation WHERE id=?
                """, (rs, rowNum) -> mapOperation(rs), operationId);
        if (values.isEmpty()) throw new ResourceNotFoundException("Tenant application operation not found");
        return values.getFirst();
    }

    private OperationView mapOperation(java.sql.ResultSet rs) throws java.sql.SQLException {
        String id = rs.getString("id");
        return new OperationView(
                id, rs.getString("company_id"), rs.getString("app_code"), rs.getString("target_version"),
                rs.getString("operation_type"), rs.getString("operation_status"),
                rs.getString("idempotency_key"), rs.getString("connection_revision_id"),
                rs.getString("request_digest"), rs.getString("response_digest"),
                rs.getString("error_code"), rs.getString("error_summary"),
                steps(id), instant(rs.getTimestamp("started_at")), instant(rs.getTimestamp("completed_at")),
                instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")));
    }

    private List<StepView> steps(String operationId) {
        return jdbc.query("""
                SELECT step_code,step_type,step_status,attempt_count,response_digest,error_code,
                       started_at,completed_at
                FROM tenant_application_operation_step
                WHERE operation_id=? ORDER BY created_at,step_code
                """, (rs, rowNum) -> new StepView(
                rs.getString("step_code"), rs.getString("step_type"), rs.getString("step_status"),
                rs.getInt("attempt_count"), rs.getString("response_digest"), rs.getString("error_code"),
                instant(rs.getTimestamp("started_at")), instant(rs.getTimestamp("completed_at"))), operationId);
    }

    private String requireCompany(String companyId) {
        String normalized = requireText(companyId, "companyId", 64);
        Long count = jdbc.queryForObject("SELECT count(*) FROM company WHERE id=?", Long.class, normalized);
        if (count == null || count == 0) throw new ResourceNotFoundException("tenant not found");
        return normalized;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize provider lifecycle request", exception);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate digest", exception);
        }
    }

    private static String lifecycleError(RuntimeException exception) {
        return exception instanceof ProviderLifecycleException provider ? provider.code() : "PROVIDER_LIFECYCLE_FAILED";
    }

    private static String providerFailureCode(Exception exception) {
        if (exception instanceof java.net.http.HttpTimeoutException) return "PROVIDER_TIMEOUT";
        if (exception instanceof java.net.ConnectException) return "PROVIDER_UNREACHABLE";
        if (exception instanceof java.net.UnknownHostException) return "PROVIDER_DNS_FAILED";
        if (exception instanceof ConflictException) return "PROVIDER_SECRET_UNAVAILABLE";
        if (exception instanceof IllegalArgumentException) return "PROVIDER_ADDRESS_REJECTED";
        return "PROVIDER_FAILED";
    }

    private static String safeSummary(RuntimeException exception) {
        if (exception instanceof ProviderLifecycleException provider) return provider.getMessage();
        if (exception instanceof ConflictException) return exception.getMessage();
        return "Provider lifecycle operation failed";
    }

    private static String normalizeAppCode(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[a-z][a-z0-9-]{1,63}$")) throw new IllegalArgumentException("Invalid appCode");
        return normalized;
    }

    private static String enumValue(String value, Set<String> allowed) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new IllegalArgumentException("Invalid operationType");
        return normalized;
    }

    private static String requireText(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " is required and must be at most " + maxLength + " characters");
        }
        return normalized;
    }

    private static Instant instant(java.sql.Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    record StepDefinition(String code, String type, String capability) {
    }

    private static final class ProviderLifecycleException extends RuntimeException {
        private final String code;

        private ProviderLifecycleException(String code, String message) {
            super(message);
            this.code = code;
        }

        private String code() {
            return code;
        }
    }

    public record OperationCommand(String operationType, String idempotencyKey) {
    }

    public record OperationView(
            String id,
            String companyId,
            String appCode,
            String targetVersion,
            String operationType,
            String status,
            String idempotencyKey,
            String connectionRevisionId,
            String requestDigest,
            String responseDigest,
            String errorCode,
            String errorSummary,
            List<StepView> steps,
            Instant startedAt,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record StepView(
            String code,
            String type,
            String status,
            int attemptCount,
            String responseDigest,
            String errorCode,
            Instant startedAt,
            Instant completedAt) {
    }

    public record RuntimeView(
            boolean enabled,
            String installedVersion,
            String desiredState,
            String actualState,
            String healthState,
            boolean initializationReady,
            String activationStage,
            String failedStage,
            String lastErrorCode,
            int attemptCount) {

        public static RuntimeView notEnabled() {
            return new RuntimeView(false, null, "NOT_ENABLED", "NOT_ENABLED", "UNKNOWN",
                    false, "NOT_ENABLED", null, null, 0);
        }
    }
}
