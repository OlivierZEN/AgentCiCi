package com.codehouse.ciciassistant.platform.service;

import com.codehouse.ciciassistant.common.error.ConflictException;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Platform-owned allowlist for Keycloak clients that may call HUMAN ecosystem APIs. */
@Service
public class EcosystemApplicationTrustService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUSPENDED = "SUSPENDED";
    public static final String ORGANIZATION_READ_SCOPE = "organization.read";
    public static final String ORGANIZATION_CONTEXT_SCOPE = "organization.context";

    private static final Pattern APP_CODE = Pattern.compile("^[a-z][a-z0-9-]{1,63}$");
    private static final Pattern CLIENT_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{1,127}$");
    private static final Pattern SCOPE = Pattern.compile("^[a-z][a-z0-9._:-]{1,127}$");

    private final JdbcTemplate jdbc;
    private final PlatformAuditService audit;

    public EcosystemApplicationTrustService(JdbcTemplate jdbc, PlatformAuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<TrustedApplicationView> list() {
        return jdbc.query("""
                SELECT app_code, display_name, keycloak_client_id, allowed_scopes, status,
                       created_by, created_at, updated_at
                FROM ecosystem_trusted_application
                ORDER BY app_code
                """, this::mapRow);
    }

    @Transactional
    public TrustedApplicationView upsert(TrustedApplicationCommand command, String actorId, String actorRole) {
        String appCode = normalizeAppCode(command.appCode());
        String displayName = requireText(command.displayName(), "displayName", 128);
        String clientId = normalizeClientId(command.keycloakClientId());
        Set<String> scopes = normalizeScopes(command.allowedScopes());
        String status = normalizeStatus(command.status());
        String scopeValue = String.join(" ", scopes);
        List<String> conflictingApps = jdbc.queryForList("""
                SELECT app_code FROM ecosystem_trusted_application
                WHERE keycloak_client_id=? AND app_code<>?
                """, String.class, clientId, appCode);
        if (!conflictingApps.isEmpty()) {
            throw new ConflictException("Keycloak client is already registered by another application");
        }
        try {
            jdbc.update("""
                    INSERT INTO ecosystem_trusted_application(
                        app_code, display_name, keycloak_client_id, allowed_scopes, status, created_by)
                    VALUES (?,?,?,?,?,?)
                    ON CONFLICT (app_code) DO UPDATE SET
                        display_name=EXCLUDED.display_name,
                        keycloak_client_id=EXCLUDED.keycloak_client_id,
                        allowed_scopes=EXCLUDED.allowed_scopes,
                        status=EXCLUDED.status,
                        updated_at=CURRENT_TIMESTAMP
                    """, appCode, displayName, clientId, scopeValue, status, requireText(actorId, "actorId", 64));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Trusted application registration conflicts with an existing client");
        }
        audit.log("platform", actorId, actorRole, "ecosystem.application.upserted",
                "ecosystem_application", appCode,
                "clientId=" + clientId + "; status=" + status + "; scopes=" + scopeValue);
        return requireByAppCode(appCode);
    }

    @Transactional
    public TrustedApplicationView changeStatus(String appCode, String status, String actorId, String actorRole) {
        String normalizedAppCode = normalizeAppCode(appCode);
        String normalizedStatus = normalizeStatus(status);
        int updated = jdbc.update("""
                UPDATE ecosystem_trusted_application
                SET status=?, updated_at=CURRENT_TIMESTAMP
                WHERE app_code=?
                """, normalizedStatus, normalizedAppCode);
        if (updated == 0) {
            throw new IllegalArgumentException("Trusted application not found");
        }
        audit.log("platform", actorId, actorRole, "ecosystem.application.status_changed",
                "ecosystem_application", normalizedAppCode, "status=" + normalizedStatus);
        return requireByAppCode(normalizedAppCode);
    }

    @Transactional(readOnly = true)
    public TrustedApplicationView requireActiveClient(String keycloakClientId, String requiredScope) {
        String clientId = normalizeClientId(keycloakClientId);
        List<TrustedApplicationView> rows = jdbc.query("""
                SELECT app_code, display_name, keycloak_client_id, allowed_scopes, status,
                       created_by, created_at, updated_at
                FROM ecosystem_trusted_application
                WHERE keycloak_client_id=?
                """, this::mapRow, clientId);
        if (rows.isEmpty()) {
            throw new ForbiddenException("Keycloak client is not registered for ecosystem API access");
        }
        TrustedApplicationView application = rows.get(0);
        if (!STATUS_ACTIVE.equals(application.status())) {
            throw new ForbiddenException("Ecosystem application is suspended");
        }
        String scope = normalizeScope(requiredScope);
        if (!application.allowedScopes().contains(scope)) {
            throw new ForbiddenException("Ecosystem application is not allowed to use scope " + scope);
        }
        return application;
    }

    private TrustedApplicationView requireByAppCode(String appCode) {
        List<TrustedApplicationView> rows = jdbc.query("""
                SELECT app_code, display_name, keycloak_client_id, allowed_scopes, status,
                       created_by, created_at, updated_at
                FROM ecosystem_trusted_application
                WHERE app_code=?
                """, this::mapRow, appCode);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Trusted application not found");
        }
        return rows.get(0);
    }

    private TrustedApplicationView mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TrustedApplicationView(
                rs.getString("app_code"),
                rs.getString("display_name"),
                rs.getString("keycloak_client_id"),
                parseScopes(rs.getString("allowed_scopes")),
                rs.getString("status"),
                rs.getString("created_by"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")));
    }

    private static Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static Set<String> normalizeScopes(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("allowedScopes is required");
        }
        LinkedHashSet<String> scopes = new LinkedHashSet<>();
        values.forEach(value -> scopes.add(normalizeScope(value)));
        return Set.copyOf(scopes);
    }

    private static Set<String> parseScopes(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Set.copyOf(Arrays.stream(value.trim().split("\\s+"))
                .filter(item -> !item.isBlank())
                .toList());
    }

    private static String normalizeScope(String value) {
        String scope = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!SCOPE.matcher(scope).matches()) {
            throw new IllegalArgumentException("Invalid ecosystem scope");
        }
        return scope;
    }

    private static String normalizeAppCode(String value) {
        String appCode = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!APP_CODE.matcher(appCode).matches()) {
            throw new IllegalArgumentException("Invalid appCode");
        }
        return appCode;
    }

    private static String normalizeClientId(String value) {
        String clientId = value == null ? "" : value.trim();
        if (!CLIENT_ID.matcher(clientId).matches()) {
            throw new IllegalArgumentException("Invalid keycloakClientId");
        }
        return clientId;
    }

    private static String normalizeStatus(String value) {
        String status = value == null || value.isBlank() ? STATUS_ACTIVE : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of(STATUS_ACTIVE, STATUS_SUSPENDED).contains(status)) {
            throw new IllegalArgumentException("Invalid application status");
        }
        return status;
    }

    private static String requireText(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " is required and must not exceed " + maxLength + " characters");
        }
        return normalized;
    }

    public record TrustedApplicationCommand(
            String appCode,
            String displayName,
            String keycloakClientId,
            List<String> allowedScopes,
            String status) {
    }

    public record TrustedApplicationView(
            String appCode,
            String displayName,
            String keycloakClientId,
            Set<String> allowedScopes,
            String status,
            String createdBy,
            Instant createdAt,
            Instant updatedAt) {
    }
}
