package com.codehouse.ciciassistant.platform.service;

import com.codehouse.ciciassistant.common.error.ConflictException;
import com.codehouse.ciciassistant.common.error.ResourceNotFoundException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Platform deployment-topology registry for application lifecycle providers. */
@Service
public class InternalApplicationProviderConnectionService {

    private static final Pattern IDENTIFIER = Pattern.compile("^[a-z][a-z0-9._-]{1,127}$");
    private static final Pattern ENVIRONMENT_KEY = Pattern.compile("^[a-z][a-z0-9_-]{1,63}$");
    private static final Set<String> NETWORK_SCOPES = Set.of("PUBLIC_HTTPS", "PLATFORM_INTERNAL");
    private static final Set<String> AUTH_TYPES = Set.of("NONE", "BEARER_SECRET_REF", "HMAC_SHA256_SECRET_REF");
    private static final Set<String> BLOCKED_METADATA_HOSTS = Set.of(
            "169.254.169.254", "100.100.100.200", "metadata.google.internal");

    private final JdbcTemplate jdbc;
    private final PlatformAuditService audit;
    private final Environment environment;
    private final HttpClient httpClient;

    public InternalApplicationProviderConnectionService(JdbcTemplate jdbc,
                                                        PlatformAuditService audit,
                                                        Environment environment) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.environment = environment;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Transactional(readOnly = true)
    public List<ConnectionView> list(String appCode) {
        String normalizedAppCode = normalizeAppCode(appCode);
        requireApplication(normalizedAppCode);
        return jdbc.query("""
                SELECT binding_key,app_code,display_name,environment_key,network_scope,status,
                       active_revision_id,created_at,updated_at
                FROM internal_application_provider_connection
                WHERE app_code=?
                ORDER BY display_name,binding_key
                """, (rs, rowNum) -> new ConnectionView(
                rs.getString("binding_key"),
                rs.getString("app_code"),
                rs.getString("display_name"),
                rs.getString("environment_key"),
                rs.getString("network_scope"),
                rs.getString("status"),
                rs.getString("active_revision_id"),
                revisions(rs.getString("binding_key")),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at"))), normalizedAppCode);
    }

    @Transactional
    public ConnectionView createRevision(String appCode,
                                         ConnectionCommand command,
                                         String actorId,
                                         String actorRole) {
        String normalizedAppCode = normalizeAppCode(appCode);
        requireApplication(normalizedAppCode);
        NormalizedConnection normalized = normalize(command);
        List<String> owner = jdbc.queryForList("""
                SELECT app_code FROM internal_application_provider_connection WHERE binding_key=?
                """, String.class, normalized.bindingKey());
        if (!owner.isEmpty() && !normalizedAppCode.equals(owner.getFirst())) {
            throw new ConflictException("Provider binding key already belongs to another application");
        }
        if (owner.isEmpty()) {
            try {
                jdbc.update("""
                        INSERT INTO internal_application_provider_connection(
                            binding_key,app_code,display_name,environment_key,network_scope,status,created_by)
                        VALUES (?,?,?,?,?,'DRAFT',?)
                        """, normalized.bindingKey(), normalizedAppCode, normalized.displayName(),
                        normalized.environmentKey(), normalized.networkScope(), actorId);
            } catch (DataIntegrityViolationException exception) {
                throw new ConflictException("Provider connection conflicts with an existing connection");
            }
        } else {
            ConnectionView currentConnection = requireConnection(normalizedAppCode, normalized.bindingKey());
            validateStableMetadata(currentConnection, normalized.environmentKey(), normalized.networkScope());
            jdbc.update("""
                    UPDATE internal_application_provider_connection
                    SET display_name=?,updated_at=CURRENT_TIMESTAMP
                    WHERE binding_key=?
                    """, normalized.displayName(), normalized.bindingKey());
        }

        Integer current = jdbc.queryForObject("""
                SELECT COALESCE(MAX(revision_number),0)
                FROM internal_application_provider_connection_revision WHERE binding_key=?
                """, Integer.class, normalized.bindingKey());
        int revisionNumber = (current == null ? 0 : current) + 1;
        String revisionId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO internal_application_provider_connection_revision(
                    id,binding_key,revision_number,base_url,contract_version,auth_type,secret_ref,
                    health_path,activate_path,reconcile_path,suspend_path,resume_path,upgrade_path,
                    timeout_ms,max_attempts,created_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, revisionId, normalized.bindingKey(), revisionNumber, normalized.baseUrl(),
                normalized.contractVersion(), normalized.authType(), normalized.secretRef(),
                normalized.healthPath(), normalized.activatePath(), normalized.reconcilePath(),
                normalized.suspendPath(), normalized.resumePath(), normalized.upgradePath(),
                normalized.timeoutMs(), normalized.maxAttempts(), actorId);
        audit.log("platform", actorId, actorRole, "internal_application.provider_connection_revised",
                "internal_application_provider_connection", normalized.bindingKey(),
                "revision=" + revisionNumber + ";environment=" + normalized.environmentKey());
        return requireConnection(normalizedAppCode, normalized.bindingKey());
    }

    public ConnectionTestView test(String appCode,
                                   String bindingKey,
                                   String actorId,
                                   String actorRole) {
        String normalizedAppCode = normalizeAppCode(appCode);
        ConnectionView connection = requireConnection(normalizedAppCode, normalizeIdentifier(bindingKey, "bindingKey"));
        RevisionView revision = latestRevision(connection);
        long started = System.nanoTime();
        int status = 0;
        String errorCode = null;
        try {
            URI uri = resolvedUri(revision.baseUrl(), revision.healthPath());
            validateResolvedAddress(uri, connection.networkScope());
            HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(revision.timeoutMs()))
                    .GET()
                    .header("Accept", "application/json");
            applyAuth(request, revision, normalizedAppCode, "GET", revision.healthPath(), "");
            HttpResponse<Void> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.discarding());
            status = response.statusCode();
            if (status < 200 || status >= 300) {
                errorCode = "PROVIDER_HEALTH_HTTP_" + status;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            errorCode = "PROVIDER_HEALTH_INTERRUPTED";
        } catch (Exception exception) {
            errorCode = safeConnectionError(exception);
        }
        long latencyMs = Math.max(0, Duration.ofNanos(System.nanoTime() - started).toMillis());
        String testStatus = errorCode == null ? "PASSED" : "FAILED";
        jdbc.update("""
                UPDATE internal_application_provider_connection_revision
                SET test_status=?,last_tested_at=CURRENT_TIMESTAMP,last_test_http_status=?,
                    last_test_latency_ms=?,last_test_error_code=?
                WHERE id=?
                """, testStatus, status == 0 ? null : status, latencyMs, errorCode, revision.id());
        audit.log("platform", actorId, actorRole, "internal_application.provider_connection_tested",
                "internal_application_provider_connection", connection.bindingKey(),
                "revision=" + revision.revisionNumber() + ";status=" + testStatus);
        return new ConnectionTestView(connection.bindingKey(), revision.id(), revision.revisionNumber(),
                testStatus, status == 0 ? null : status, latencyMs, errorCode, Instant.now());
    }

    @Transactional
    public ConnectionView activate(String appCode,
                                   String bindingKey,
                                   String actorId,
                                   String actorRole) {
        String normalizedAppCode = normalizeAppCode(appCode);
        ConnectionView connection = requireConnection(normalizedAppCode, normalizeIdentifier(bindingKey, "bindingKey"));
        RevisionView revision = latestRevision(connection);
        if (!"PASSED".equals(revision.testStatus())) {
            throw new ConflictException("Provider connection revision must pass a connection test before activation");
        }
        jdbc.update("""
                UPDATE internal_application_provider_connection
                SET active_revision_id=?,status='ACTIVE',updated_at=CURRENT_TIMESTAMP
                WHERE binding_key=?
                """, revision.id(), connection.bindingKey());
        audit.log("platform", actorId, actorRole, "internal_application.provider_connection_activated",
                "internal_application_provider_connection", connection.bindingKey(),
                "revision=" + revision.revisionNumber());
        return requireConnection(normalizedAppCode, connection.bindingKey());
    }

    @Transactional
    public ConnectionView disable(String appCode,
                                  String bindingKey,
                                  String actorId,
                                  String actorRole) {
        String normalizedAppCode = normalizeAppCode(appCode);
        ConnectionView connection = requireConnection(normalizedAppCode, normalizeIdentifier(bindingKey, "bindingKey"));
        jdbc.update("""
                UPDATE internal_application_provider_connection
                SET status='DISABLED',updated_at=CURRENT_TIMESTAMP WHERE binding_key=?
                """, connection.bindingKey());
        audit.log("platform", actorId, actorRole, "internal_application.provider_connection_disabled",
                "internal_application_provider_connection", connection.bindingKey(), "status=DISABLED");
        return requireConnection(normalizedAppCode, connection.bindingKey());
    }

    @Transactional(readOnly = true)
    public ActiveConnection resolveActive(String appCode, String bindingKey) {
        ConnectionView connection = requireConnection(normalizeAppCode(appCode),
                normalizeIdentifier(bindingKey, "bindingKey"));
        if (!"ACTIVE".equals(connection.status()) || connection.activeRevisionId() == null) {
            throw new ConflictException("Provider connection is not active");
        }
        RevisionView revision = connection.revisions().stream()
                .filter(item -> item.id().equals(connection.activeRevisionId()))
                .findFirst()
                .orElseThrow(() -> new ConflictException("Provider connection active revision is missing"));
        if (!"PASSED".equals(revision.testStatus())) {
            throw new ConflictException("Provider connection active revision has not passed validation");
        }
        return new ActiveConnection(connection, revision);
    }

    @Transactional(readOnly = true)
    public boolean supportsLifecycle(String appCode, String bindingKey) {
        if (bindingKey == null || bindingKey.isBlank()) {
            return false;
        }
        try {
            return resolveActive(appCode, bindingKey).revision().activatePath() != null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public void validateProviderContract(String appCode, String bindingKey, List<String> contractVersions) {
        ActiveConnection active = resolveActive(appCode, bindingKey);
        if (contractVersions.stream().anyMatch(version -> !active.revision().contractVersion().equals(version))) {
            throw new ConflictException("Provider connection contract version does not match the application manifest");
        }
    }

    void validateCommand(ConnectionCommand command) {
        normalize(command);
    }

    void validateStableMetadata(ConnectionView currentConnection,
                                String environmentKey,
                                String networkScope) {
        if (!currentConnection.environmentKey().equals(environmentKey)
                || !currentConnection.networkScope().equals(networkScope)) {
            throw new ConflictException(
                    "Provider connection environment and network scope are immutable; create a new binding key");
        }
    }

    String resolveSecret(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) {
            throw new ConflictException("Provider connection secret reference is missing");
        }
        String value = environment.getProperty("app.platform.provider-secrets." + secretRef, "").trim();
        if (value.isBlank()) {
            throw new ConflictException("Provider connection secret reference is not configured in this environment");
        }
        return value;
    }

    void applyAuth(HttpRequest.Builder request,
                   RevisionView revision,
                   String appCode,
                   String method,
                   String path,
                   String body) {
        if ("NONE".equals(revision.authType())) {
            return;
        }
        String secret = resolveSecret(revision.secretRef());
        if ("BEARER_SECRET_REF".equals(revision.authType())) {
            request.header("Authorization", "Bearer " + secret);
            return;
        }
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        request.header("X-Internal-Service", "agentcici")
                .header("X-Internal-App", appCode)
                .header("X-Internal-Timestamp", timestamp)
                .header("X-Internal-Nonce", nonce)
                .header("X-Internal-Signature", hmac(secret, method, path, timestamp, nonce, body));
    }

    URI resolvedUri(String baseUrl, String path) {
        return URI.create(baseUrl.replaceAll("/+$", "") + path);
    }

    void validateResolvedAddress(URI uri, String networkScope) throws Exception {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (BLOCKED_METADATA_HOSTS.contains(host)) {
            throw new IllegalArgumentException("Cloud metadata addresses are not allowed");
        }
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address.isLinkLocalAddress() || address.isMulticastAddress()) {
                throw new IllegalArgumentException("Link-local and multicast addresses are not allowed");
            }
            if ("PUBLIC_HTTPS".equals(networkScope)
                    && (address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isSiteLocalAddress())) {
                throw new IllegalArgumentException("Public connections cannot resolve to private addresses");
            }
        }
    }

    private NormalizedConnection normalize(ConnectionCommand command) {
        String bindingKey = normalizeIdentifier(command.bindingKey(), "bindingKey");
        String displayName = requireText(command.displayName(), "displayName", 128);
        String environmentKey = command.environmentKey() == null ? "default"
                : command.environmentKey().trim().toLowerCase(Locale.ROOT);
        if (!ENVIRONMENT_KEY.matcher(environmentKey).matches()) {
            throw new IllegalArgumentException("Invalid environmentKey");
        }
        String networkScope = enumValue(command.networkScope(), "networkScope", NETWORK_SCOPES);
        String baseUrl = normalizeBaseUrl(command.baseUrl(), networkScope);
        String contractVersion = normalizeIdentifier(command.contractVersion(), "contractVersion");
        String authType = enumValue(command.authType(), "authType", AUTH_TYPES);
        String secretRef = command.secretRef() == null || command.secretRef().isBlank()
                ? null : normalizeIdentifier(command.secretRef(), "secretRef");
        if ("NONE".equals(authType) != (secretRef == null)) {
            throw new IllegalArgumentException("secretRef is required exactly when authType is not NONE");
        }
        String healthPath = normalizePath(command.healthPath(), "healthPath", true);
        String activatePath = normalizePath(command.activatePath(), "activatePath", false);
        String reconcilePath = normalizePath(command.reconcilePath(), "reconcilePath", false);
        String suspendPath = normalizePath(command.suspendPath(), "suspendPath", false);
        String resumePath = normalizePath(command.resumePath(), "resumePath", false);
        String upgradePath = normalizePath(command.upgradePath(), "upgradePath", false);
        if (activatePath == null) {
            throw new IllegalArgumentException("activatePath is required for a lifecycle connection");
        }
        int timeoutMs = command.timeoutMs() == null ? 10000 : command.timeoutMs();
        int maxAttempts = command.maxAttempts() == null ? 1 : command.maxAttempts();
        if (timeoutMs < 1000 || timeoutMs > 60000) {
            throw new IllegalArgumentException("timeoutMs must be between 1000 and 60000");
        }
        if (maxAttempts < 1 || maxAttempts > 5) {
            throw new IllegalArgumentException("maxAttempts must be between 1 and 5");
        }
        return new NormalizedConnection(bindingKey, displayName, environmentKey, networkScope,
                baseUrl, contractVersion, authType, secretRef, healthPath, activatePath,
                reconcilePath, suspendPath, resumePath, upgradePath, timeoutMs, maxAttempts);
    }

    private String normalizeBaseUrl(String value, String networkScope) {
        String raw = requireText(value, "baseUrl", 1024).replaceAll("/+$", "");
        URI uri;
        try {
            uri = URI.create(raw);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("baseUrl must be an absolute HTTP URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!("https".equals(scheme) || "http".equals(scheme)) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("baseUrl must contain only an HTTP(S) origin and optional base path");
        }
        if ("PUBLIC_HTTPS".equals(networkScope) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("PUBLIC_HTTPS connections require HTTPS");
        }
        if (BLOCKED_METADATA_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Cloud metadata addresses are not allowed");
        }
        return raw;
    }

    private String normalizePath(String value, String field, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw new IllegalArgumentException(field + " is required");
            return null;
        }
        String path = value.trim();
        if (!path.startsWith("/") || path.startsWith("//") || path.contains("..")
                || path.contains("://") || path.contains("?") || path.contains("#") || path.length() > 256) {
            throw new IllegalArgumentException(field + " must be a safe relative path");
        }
        return path;
    }

    private ConnectionView requireConnection(String appCode, String bindingKey) {
        return list(appCode).stream().filter(item -> item.bindingKey().equals(bindingKey)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Provider connection not found"));
    }

    private RevisionView latestRevision(ConnectionView connection) {
        return connection.revisions().stream().findFirst()
                .orElseThrow(() -> new ConflictException("Provider connection has no revision"));
    }

    private List<RevisionView> revisions(String bindingKey) {
        return jdbc.query("""
                SELECT id,binding_key,revision_number,base_url,contract_version,auth_type,secret_ref,
                       health_path,activate_path,reconcile_path,suspend_path,resume_path,upgrade_path,
                       timeout_ms,max_attempts,test_status,last_tested_at,last_test_http_status,
                       last_test_latency_ms,last_test_error_code,created_at
                FROM internal_application_provider_connection_revision
                WHERE binding_key=? ORDER BY revision_number DESC
                """, (rs, rowNum) -> new RevisionView(
                rs.getString("id"), rs.getString("binding_key"), rs.getInt("revision_number"),
                rs.getString("base_url"), rs.getString("contract_version"), rs.getString("auth_type"),
                rs.getString("secret_ref"), rs.getString("health_path"), rs.getString("activate_path"),
                rs.getString("reconcile_path"), rs.getString("suspend_path"), rs.getString("resume_path"),
                rs.getString("upgrade_path"), rs.getInt("timeout_ms"), rs.getInt("max_attempts"),
                rs.getString("test_status"), instant(rs.getTimestamp("last_tested_at")),
                nullableInteger(rs.getObject("last_test_http_status")),
                nullableLong(rs.getObject("last_test_latency_ms")), rs.getString("last_test_error_code"),
                instant(rs.getTimestamp("created_at"))), bindingKey);
    }

    private void requireApplication(String appCode) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM internal_application WHERE app_code=?",
                Long.class, appCode);
        if (count == null || count == 0) {
            throw new ResourceNotFoundException("Internal application not found");
        }
    }

    private static String hmac(String secret, String method, String path,
                               String timestamp, String nonce, String body) {
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(body.getBytes(StandardCharsets.UTF_8)));
            String canonical = String.join("\n", "agentcici", method, path, timestamp, nonce, digest);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign provider request", exception);
        }
    }

    private static String safeConnectionError(Exception exception) {
        if (exception instanceof java.net.http.HttpTimeoutException) return "PROVIDER_HEALTH_TIMEOUT";
        if (exception instanceof java.net.ConnectException) return "PROVIDER_HEALTH_UNREACHABLE";
        if (exception instanceof java.net.UnknownHostException) return "PROVIDER_HEALTH_DNS_FAILED";
        if (exception instanceof ConflictException) return "PROVIDER_HEALTH_SECRET_UNAVAILABLE";
        if (exception instanceof IllegalArgumentException) return "PROVIDER_HEALTH_ADDRESS_REJECTED";
        return "PROVIDER_HEALTH_FAILED";
    }

    private static String normalizeAppCode(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!Pattern.compile("^[a-z][a-z0-9-]{1,63}$").matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid appCode");
        }
        return normalized;
    }

    private static String normalizeIdentifier(String value, String field) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return normalized;
    }

    private static String enumValue(String value, String field, Set<String> allowed) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new IllegalArgumentException("Invalid " + field);
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

    private static Integer nullableInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static Long nullableLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    public record ConnectionCommand(
            String bindingKey,
            String displayName,
            String environmentKey,
            String networkScope,
            String baseUrl,
            String contractVersion,
            String authType,
            String secretRef,
            String healthPath,
            String activatePath,
            String reconcilePath,
            String suspendPath,
            String resumePath,
            String upgradePath,
            Integer timeoutMs,
            Integer maxAttempts) {
    }

    private record NormalizedConnection(
            String bindingKey,
            String displayName,
            String environmentKey,
            String networkScope,
            String baseUrl,
            String contractVersion,
            String authType,
            String secretRef,
            String healthPath,
            String activatePath,
            String reconcilePath,
            String suspendPath,
            String resumePath,
            String upgradePath,
            int timeoutMs,
            int maxAttempts) {
    }

    public record ConnectionView(
            String bindingKey,
            String appCode,
            String displayName,
            String environmentKey,
            String networkScope,
            String status,
            String activeRevisionId,
            List<RevisionView> revisions,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record RevisionView(
            String id,
            String bindingKey,
            int revisionNumber,
            String baseUrl,
            String contractVersion,
            String authType,
            String secretRef,
            String healthPath,
            String activatePath,
            String reconcilePath,
            String suspendPath,
            String resumePath,
            String upgradePath,
            int timeoutMs,
            int maxAttempts,
            String testStatus,
            Instant lastTestedAt,
            Integer lastTestHttpStatus,
            Long lastTestLatencyMs,
            String lastTestErrorCode,
            Instant createdAt) {
    }

    public record ConnectionTestView(
            String bindingKey,
            String revisionId,
            int revisionNumber,
            String status,
            Integer httpStatus,
            long latencyMs,
            String errorCode,
            Instant testedAt) {
    }

    public record ActiveConnection(ConnectionView connection, RevisionView revision) {
    }
}
