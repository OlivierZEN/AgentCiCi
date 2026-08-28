package com.codehouse.ciciassistant.platform.service;

import com.codehouse.ciciassistant.common.error.ConflictException;
import com.codehouse.ciciassistant.common.error.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Platform-owned catalog of installable, versioned internal tenant applications. */
@Service
public class InternalApplicationRegistryService {

    public static final String MANIFEST_SCHEMA = "tenant-application/v2";
    private static final Set<String> SUPPORTED_MANIFEST_SCHEMAS = Set.of("tenant-application/v1", MANIFEST_SCHEMA);
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_SUSPENDED = "SUSPENDED";
    public static final String STATUS_RETIRED = "RETIRED";

    private static final Set<String> TENANT_MODES = Set.of(
            "PLATFORM_BASE", "SHARED_RUNTIME_TENANT_ISOLATED");
    private static final Set<String> LAUNCH_MODES = Set.of(
            "NONE", "PLATFORM_ROUTE", "SERVER_HANDOFF");
    private static final Set<String> ENGINES = Set.of("NONE", "SAGA_V1");
    private static final Set<String> STEP_TYPES = Set.of(
            "PLATFORM_CAPABILITY", "DEPENDENCY_CAPABILITY", "PROVIDER_CALLBACK");
    private static final Set<String> DEPENDENCY_TYPES = Set.of(
            "REQUIRED_ACTIVATION", "REQUIRED_RUNTIME", "OPTIONAL");
    private static final Set<String> ACTIVATION_POLICIES = Set.of(
            "REQUIRE_EXISTING", "AUTO_PROVISION_ALLOWED");
    private static final Pattern APP_CODE = Pattern.compile("^[a-z][a-z0-9-]{1,63}$");
    private static final Pattern IDENTIFIER = Pattern.compile("^[a-z][a-z0-9._-]{1,127}$");
    private static final Pattern SEMVER = Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$");
    private static final Pattern VERSION_CONSTRAINT = Pattern.compile("^(\\*|(?:>=|=)?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*))$");
    private static final Pattern DEPLOYMENT_ADDRESS = Pattern.compile(
            "(?i)(https?://|localhost|(?:\\d{1,3}\\.){3}\\d{1,3}|\\b[a-z0-9-]+(?:\\.[a-z0-9-]+)+(?:/|:\\d+|\\b))");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final PlatformAuditService audit;
    private final InternalApplicationProviderConnectionService providerConnections;

    public InternalApplicationRegistryService(JdbcTemplate jdbc,
                                              ObjectMapper objectMapper,
                                              PlatformAuditService audit,
                                              InternalApplicationProviderConnectionService providerConnections) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.audit = audit;
        this.providerConnections = providerConnections;
    }

    @Transactional(readOnly = true)
    public List<ApplicationSummaryView> list() {
        return jdbc.query("""
                SELECT app.app_code,app.display_name,app.summary,app.icon_key,app.owner_team,
                       app.tenant_mode,app.catalog_status,app.trusted_app_code,app.launch_mode,
                       app.launch_route_key,app.default_version,app.created_at,app.updated_at,
                       count(version.id) AS version_count
                FROM internal_application app
                LEFT JOIN internal_application_version version ON version.app_code=app.app_code
                GROUP BY app.app_code,app.display_name,app.summary,app.icon_key,app.owner_team,
                         app.tenant_mode,app.catalog_status,app.trusted_app_code,app.launch_mode,
                         app.launch_route_key,app.default_version,app.created_at,app.updated_at
                ORDER BY app.display_name,app.app_code
                """, (rs, rowNum) -> new ApplicationSummaryView(
                rs.getString("app_code"),
                rs.getString("display_name"),
                rs.getString("summary"),
                rs.getString("icon_key"),
                rs.getString("owner_team"),
                rs.getString("tenant_mode"),
                rs.getString("catalog_status"),
                rs.getString("trusted_app_code"),
                rs.getString("launch_mode"),
                rs.getString("launch_route_key"),
                rs.getString("default_version"),
                rs.getInt("version_count"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at"))));
    }

    @Transactional(readOnly = true)
    public ApplicationDetailView get(String appCode) {
        ApplicationSummaryView application = requireApplication(normalizeAppCode(appCode));
        List<VersionView> versions = jdbc.query("""
                SELECT id,app_code,version,manifest_schema_version,provider_binding_key,
                       initialization_engine,manifest_json::text,manifest_digest,version_status,
                       created_by,validated_by,validated_at,published_by,published_at,created_at,updated_at
                FROM internal_application_version
                WHERE app_code=?
                ORDER BY string_to_array(version,'.')::int[] DESC,created_at DESC
                """, (rs, rowNum) -> new VersionView(
                rs.getString("id"),
                rs.getString("app_code"),
                rs.getString("version"),
                rs.getString("manifest_schema_version"),
                rs.getString("provider_binding_key"),
                rs.getString("initialization_engine"),
                readJson(rs.getString("manifest_json")),
                rs.getString("manifest_digest"),
                rs.getString("version_status"),
                mcpProviders(rs.getString("id")),
                dependencies(rs.getString("id")),
                rs.getString("created_by"),
                rs.getString("validated_by"),
                instant(rs.getTimestamp("validated_at")),
                rs.getString("published_by"),
                instant(rs.getTimestamp("published_at")),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at"))), application.appCode());
        return new ApplicationDetailView(application, versions);
    }

    @Transactional
    public ApplicationDetailView create(ApplicationCommand command, String actorId, String actorRole) {
        String appCode = normalizeAppCode(command.appCode());
        NormalizedApplication normalized = normalizeApplication(command);
        try {
            jdbc.update("""
                    INSERT INTO internal_application(
                        app_code,display_name,summary,icon_key,owner_team,tenant_mode,catalog_status,
                        trusted_app_code,launch_mode,launch_route_key,created_by)
                    VALUES (?,?,?,?,?,?,'DRAFT',?,?,?,?)
                    """, appCode, normalized.displayName(), normalized.summary(), normalized.iconKey(),
                    normalized.ownerTeam(), normalized.tenantMode(), normalized.trustedAppCode(),
                    normalized.launchMode(), normalized.launchRouteKey(), requireText(actorId, "actorId", 64));
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Internal application already exists or conflicts with the catalog");
        }
        audit.log("platform", actorId, actorRole, "internal_application.created",
                "internal_application", appCode, "status=DRAFT");
        return get(appCode);
    }

    @Transactional
    public ApplicationDetailView update(String appCode,
                                        ApplicationCommand command,
                                        String actorId,
                                        String actorRole) {
        String normalizedCode = normalizeAppCode(appCode);
        if (command.appCode() != null && !command.appCode().isBlank()
                && !normalizedCode.equals(normalizeAppCode(command.appCode()))) {
            throw new IllegalArgumentException("appCode is immutable");
        }
        ApplicationSummaryView current = requireApplication(normalizedCode);
        if (STATUS_RETIRED.equals(current.catalogStatus())) {
            throw new ConflictException("Retired internal applications cannot be edited");
        }
        NormalizedApplication normalized = normalizeApplication(command);
        jdbc.update("""
                UPDATE internal_application
                SET display_name=?,summary=?,icon_key=?,owner_team=?,tenant_mode=?,trusted_app_code=?,
                    launch_mode=?,launch_route_key=?,updated_at=CURRENT_TIMESTAMP
                WHERE app_code=?
                """, normalized.displayName(), normalized.summary(), normalized.iconKey(), normalized.ownerTeam(),
                normalized.tenantMode(), normalized.trustedAppCode(), normalized.launchMode(),
                normalized.launchRouteKey(), normalizedCode);
        audit.log("platform", actorId, actorRole, "internal_application.updated",
                "internal_application", normalizedCode, "governance fields updated");
        return get(normalizedCode);
    }

    @Transactional
    public VersionView createVersion(String appCode,
                                     VersionCommand command,
                                     String actorId,
                                     String actorRole) {
        String normalizedCode = normalizeAppCode(appCode);
        ApplicationSummaryView application = requireApplication(normalizedCode);
        if (STATUS_RETIRED.equals(application.catalogStatus())) {
            throw new ConflictException("Retired internal applications cannot accept new versions");
        }
        NormalizedVersion normalized = normalizeVersion(command);
        ObjectNode manifest = buildManifest(normalized);
        String manifestJson = writeJson(manifest);
        String digest = sha256(manifestJson);
        String versionId = UUID.randomUUID().toString();
        try {
            jdbc.update("""
                    INSERT INTO internal_application_version(
                        id,app_code,version,manifest_schema_version,provider_binding_key,
                        initialization_engine,manifest_json,manifest_digest,version_status,created_by)
                    VALUES (?,?,?,?,?,?,CAST(? AS jsonb),?,'DRAFT',?)
                    """, versionId, normalizedCode, normalized.version(), MANIFEST_SCHEMA,
                    normalized.providerBindingKey(), normalized.initializationEngine(), manifestJson,
                    digest, requireText(actorId, "actorId", 64));
            for (DependencyCommand dependency : normalized.dependencies()) {
                jdbc.update("""
                        INSERT INTO internal_application_dependency(
                            id,application_version_id,dependency_app_code,version_constraint,
                            dependency_type,activation_policy)
                        VALUES (?,?,?,?,?,?)
                        """, UUID.randomUUID().toString(), versionId, dependency.appCode(),
                        dependency.versionConstraint(), dependency.dependencyType(), dependency.activationPolicy());
            }
            for (McpProviderCommand provider : normalized.mcpProviders()) {
                String providerId = UUID.randomUUID().toString();
                jdbc.update("""
                        INSERT INTO application_version_mcp_provider(
                            id,application_version_id,provider_key,transport_type,auth_type,audience,required_scope)
                        VALUES (?,?,?,?,?,?,?)
                        """, providerId, versionId, provider.providerKey(), provider.transportType(), provider.authType(),
                        provider.audience(), provider.requiredScope());
                for (McpToolCommand tool : provider.tools()) {
                    jdbc.update("""
                            INSERT INTO application_version_mcp_tool(
                                id,provider_id,tool_name,schema_digest,risk_level,confirmation_required)
                            VALUES (?,?,?,?,?,?)
                            """, UUID.randomUUID().toString(), providerId, tool.name(), tool.schemaDigest(),
                            tool.riskLevel(), tool.confirmationRequired());
                }
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Internal application version or dependency conflicts with the catalog");
        }
        audit.log("platform", actorId, actorRole, "internal_application.version_created",
                "internal_application_version", normalizedCode + "@" + normalized.version(),
                "manifestDigest=" + digest);
        return requireVersion(normalizedCode, normalized.version());
    }

    @Transactional
    public ValidationView validateVersion(String appCode,
                                          String version,
                                          String actorId,
                                          String actorRole) {
        String normalizedCode = normalizeAppCode(appCode);
        String normalizedVersion = normalizeSemver(version);
        VersionView target = requireVersion(normalizedCode, normalizedVersion);
        if (Set.of("PUBLISHED", "DEPRECATED", "REVOKED").contains(target.status())) {
            return new ValidationView(normalizedCode, normalizedVersion, true,
                    target.manifestDigest(), List.of(), "Version is immutable and already " + target.status().toLowerCase(Locale.ROOT));
        }
        List<String> checks = new ArrayList<>();
        validateManifest(target);
        checks.add("manifest");
        validateDependencies(target);
        checks.add("dependencies");
        validateAcyclic(normalizedCode, target.id());
        checks.add("dependency-graph");
        validateProviderConnection(target);
        checks.add("provider-connection");
        jdbc.update("""
                UPDATE internal_application_version
                SET version_status='VALIDATED',validated_by=?,validated_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND version_status IN ('DRAFT','VALIDATED')
                """, actorId, target.id());
        audit.log("platform", actorId, actorRole, "internal_application.version_validated",
                "internal_application_version", normalizedCode + "@" + normalizedVersion,
                "checks=" + String.join(",", checks) + "; manifestDigest=" + target.manifestDigest());
        return new ValidationView(normalizedCode, normalizedVersion, true,
                target.manifestDigest(), List.copyOf(checks), "Application version passed publication gates");
    }

    @Transactional
    public ApplicationDetailView publishVersion(String appCode,
                                                String version,
                                                String actorId,
                                                String actorRole) {
        String normalizedCode = normalizeAppCode(appCode);
        String normalizedVersion = normalizeSemver(version);
        validateVersion(normalizedCode, normalizedVersion, actorId, actorRole);
        VersionView target = requireVersion(normalizedCode, normalizedVersion);
        if (!"PUBLISHED".equals(target.status())) {
            jdbc.update("""
                    UPDATE internal_application_version
                    SET version_status='DEPRECATED',updated_at=CURRENT_TIMESTAMP
                    WHERE app_code=? AND version_status='PUBLISHED' AND id<>?
                    """, normalizedCode, target.id());
            int updated = jdbc.update("""
                    UPDATE internal_application_version
                    SET version_status='PUBLISHED',published_by=?,published_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
                    WHERE id=? AND version_status='VALIDATED'
                    """, actorId, target.id());
            if (updated != 1) {
                throw new ConflictException("Application version publication state changed; reload and retry");
            }
            jdbc.update("""
                    UPDATE internal_application
                    SET default_version=?,catalog_status='PUBLISHED',updated_at=CURRENT_TIMESTAMP
                    WHERE app_code=?
                    """, normalizedVersion, normalizedCode);
            audit.log("platform", actorId, actorRole, "internal_application.version_published",
                    "internal_application_version", normalizedCode + "@" + normalizedVersion,
                    "manifestDigest=" + target.manifestDigest());
        }
        return get(normalizedCode);
    }

    @Transactional
    public ApplicationDetailView changeStatus(String appCode,
                                              String status,
                                              String actorId,
                                              String actorRole) {
        String normalizedCode = normalizeAppCode(appCode);
        String normalizedStatus = normalizeEnum(status, "status",
                Set.of(STATUS_PUBLISHED, STATUS_SUSPENDED, STATUS_RETIRED));
        ApplicationSummaryView current = requireApplication(normalizedCode);
        if (STATUS_RETIRED.equals(current.catalogStatus())) {
            throw new ConflictException("Retired internal applications cannot change status");
        }
        if (STATUS_PUBLISHED.equals(normalizedStatus) && current.defaultVersion() == null) {
            throw new ConflictException("Application requires a published default version before activation");
        }
        jdbc.update("""
                UPDATE internal_application SET catalog_status=?,updated_at=CURRENT_TIMESTAMP WHERE app_code=?
                """, normalizedStatus, normalizedCode);
        audit.log("platform", actorId, actorRole, "internal_application.status_changed",
                "internal_application", normalizedCode, "status=" + normalizedStatus);
        return get(normalizedCode);
    }

    private ApplicationSummaryView requireApplication(String appCode) {
        return list().stream()
                .filter(item -> item.appCode().equals(appCode))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Internal application not found"));
    }

    private VersionView requireVersion(String appCode, String version) {
        return get(appCode).versions().stream()
                .filter(item -> item.version().equals(version))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Internal application version not found"));
    }

    private List<DependencyView> dependencies(String versionId) {
        return jdbc.query("""
                SELECT dependency_app_code,version_constraint,dependency_type,activation_policy
                FROM internal_application_dependency
                WHERE application_version_id=?
                ORDER BY dependency_type,dependency_app_code
                """, (rs, rowNum) -> new DependencyView(
                rs.getString("dependency_app_code"),
                rs.getString("version_constraint"),
                rs.getString("dependency_type"),
                rs.getString("activation_policy")), versionId);
    }

    private List<McpProviderView> mcpProviders(String versionId) {
        return jdbc.query("""
                SELECT id,provider_key,transport_type,auth_type,audience,required_scope
                FROM application_version_mcp_provider
                WHERE application_version_id=? ORDER BY provider_key
                """, (rs, rowNum) -> new McpProviderView(rs.getString("provider_key"),
                rs.getString("transport_type"), rs.getString("auth_type"), rs.getString("audience"),
                rs.getString("required_scope"), mcpTools(rs.getString("id"))), versionId);
    }

    private List<McpToolView> mcpTools(String providerId) {
        return jdbc.query("""
                SELECT tool_name,schema_digest,risk_level,confirmation_required
                FROM application_version_mcp_tool WHERE provider_id=? ORDER BY tool_name
                """, (rs, rowNum) -> new McpToolView(rs.getString("tool_name"), rs.getString("schema_digest"),
                rs.getString("risk_level"), rs.getBoolean("confirmation_required")), providerId);
    }

    private void validateManifest(VersionView version) {
        JsonNode manifest = version.manifest();
        if (!SUPPORTED_MANIFEST_SCHEMAS.contains(manifest.path("schemaVersion").asText())) {
            throw new IllegalArgumentException("Unsupported manifest schema version");
        }
        String engine = normalizeEnum(manifest.path("initializationEngine").asText(),
                "initializationEngine", ENGINES);
        JsonNode steps = manifest.path("steps");
        if (!steps.isArray() || steps.size() > 32) {
            throw new IllegalArgumentException("Manifest steps must be an array with at most 32 entries");
        }
        if ("NONE".equals(engine) && !steps.isEmpty()) {
            throw new IllegalArgumentException("NONE initialization engine cannot declare steps");
        }
        if ("SAGA_V1".equals(engine) && steps.isEmpty()) {
            throw new IllegalArgumentException("SAGA_V1 initialization engine requires at least one step");
        }
        Set<String> stepCodes = new HashSet<>();
        steps.forEach(step -> {
            String code = normalizeIdentifier(step.path("code").asText(), "step.code", false);
            normalizeEnum(step.path("type").asText(), "step.type", STEP_TYPES);
            normalizeIdentifier(step.path("capability").asText(), "step.capability", false);
            normalizeIdentifier(step.path("contractVersion").asText(), "step.contractVersion", false);
            if (!stepCodes.add(code)) {
                throw new IllegalArgumentException("Manifest step codes must be unique");
            }
        });
        if (MANIFEST_SCHEMA.equals(manifest.path("schemaVersion").asText())) {
            JsonNode providers = manifest.path("mcpProviders");
            if (!providers.isArray() || providers.size() > 16) {
                throw new IllegalArgumentException("mcpProviders must be an array with at most 16 entries");
            }
            Set<String> providerKeys = new HashSet<>();
            providers.forEach(provider -> {
                String key = normalizeIdentifier(provider.path("providerKey").asText(), "providerKey", false);
                if (!providerKeys.add(key)) throw new IllegalArgumentException("MCP provider keys must be unique");
                if (!"streamableHttp".equals(provider.path("transportType").asText())) {
                    throw new IllegalArgumentException("Only streamableHttp MCP transport is supported");
                }
                normalizeEnum(provider.path("authType").asText(), "authType", Set.of("NONE", "KEYCLOAK_CLIENT_CREDENTIALS"));
                if (!provider.path("tools").isArray() || provider.path("tools").isEmpty()) {
                    throw new IllegalArgumentException("Each MCP provider requires at least one tool");
                }
            });
        }
        rejectDeploymentAddress(version.manifest().toString(), "manifest");
    }

    private void validateDependencies(VersionView target) {
        for (DependencyView dependency : target.dependencies()) {
            if (target.appCode().equals(dependency.appCode())) {
                throw new IllegalArgumentException("Application version cannot depend on itself");
            }
            ApplicationSummaryView dependencyApplication = requireApplication(dependency.appCode());
            List<String> publishedVersions = jdbc.queryForList("""
                    SELECT version FROM internal_application_version
                    WHERE app_code=? AND version_status='PUBLISHED'
                    """, String.class, dependency.appCode());
            boolean satisfied = publishedVersions.stream()
                    .anyMatch(version -> matchesConstraint(version, dependency.versionConstraint()));
            if (!satisfied) {
                throw new ConflictException("Dependency " + dependency.appCode()
                        + " has no published version matching " + dependency.versionConstraint());
            }
            if (STATUS_RETIRED.equals(dependencyApplication.catalogStatus())) {
                throw new ConflictException("Dependency " + dependency.appCode() + " is retired");
            }
        }
    }

    private void validateProviderConnection(VersionView target) {
        List<String> providerContractVersions = new ArrayList<>();
        target.manifest().path("steps").forEach(step -> {
            if (!Set.of("agentcici", "semattice", "devautopilot").contains(target.appCode())
                    && !"PROVIDER_CALLBACK".equals(step.path("type").asText())) {
                throw new ConflictException("Generic applications currently support Provider callback steps only");
            }
            if ("PROVIDER_CALLBACK".equals(step.path("type").asText())) {
                providerContractVersions.add(step.path("contractVersion").asText());
            }
        });
        if (providerContractVersions.isEmpty()) {
            return;
        }
        if (target.providerBindingKey() == null || target.providerBindingKey().isBlank()) {
            throw new ConflictException("Provider callback steps require an active provider connection");
        }
        providerConnections.validateProviderContract(
                target.appCode(), target.providerBindingKey(), providerContractVersions);
    }

    private void validateAcyclic(String targetAppCode, String targetVersionId) {
        List<Edge> edges = jdbc.query("""
                SELECT version.app_code,dependency.dependency_app_code
                FROM internal_application_dependency dependency
                JOIN internal_application_version version ON version.id=dependency.application_version_id
                WHERE version.version_status='PUBLISHED' OR version.id=?
                """, (rs, rowNum) -> new Edge(rs.getString(1), rs.getString(2)), targetVersionId);
        Map<String, Set<String>> graph = new HashMap<>();
        edges.forEach(edge -> graph.computeIfAbsent(edge.from(), ignored -> new LinkedHashSet<>()).add(edge.to()));
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> path = new ArrayDeque<>();
        if (hasCycle(targetAppCode, graph, visiting, visited, path)) {
            throw new ConflictException("Application dependency graph contains a cycle: " + String.join(" -> ", path));
        }
    }

    private static boolean hasCycle(String node,
                                    Map<String, Set<String>> graph,
                                    Set<String> visiting,
                                    Set<String> visited,
                                    ArrayDeque<String> path) {
        if (visiting.contains(node)) {
            path.addLast(node);
            return true;
        }
        if (visited.contains(node)) {
            return false;
        }
        visiting.add(node);
        path.addLast(node);
        for (String next : graph.getOrDefault(node, Set.of())) {
            if (hasCycle(next, graph, visiting, visited, path)) {
                return true;
            }
        }
        path.removeLast();
        visiting.remove(node);
        visited.add(node);
        return false;
    }

    private NormalizedApplication normalizeApplication(ApplicationCommand command) {
        String displayName = requireText(command.displayName(), "displayName", 128);
        String summary = requireText(command.summary(), "summary", 500);
        String iconKey = normalizeIdentifier(command.iconKey(), "iconKey", false);
        String ownerTeam = requireText(command.ownerTeam(), "ownerTeam", 128);
        String tenantMode = normalizeEnum(command.tenantMode(), "tenantMode", TENANT_MODES);
        String trustedAppCode = command.trustedAppCode() == null || command.trustedAppCode().isBlank()
                ? null : normalizeAppCode(command.trustedAppCode());
        String launchMode = normalizeEnum(command.launchMode(), "launchMode", LAUNCH_MODES);
        String launchRouteKey = command.launchRouteKey() == null || command.launchRouteKey().isBlank()
                ? null : normalizeIdentifier(command.launchRouteKey(), "launchRouteKey", false);
        if ("NONE".equals(launchMode) != (launchRouteKey == null)) {
            throw new IllegalArgumentException("launchRouteKey is required exactly when launchMode is not NONE");
        }
        rejectDeploymentAddress(displayName, "displayName");
        rejectDeploymentAddress(summary, "summary");
        rejectDeploymentAddress(ownerTeam, "ownerTeam");
        return new NormalizedApplication(displayName, summary, iconKey, ownerTeam, tenantMode,
                trustedAppCode, launchMode, launchRouteKey);
    }

    private NormalizedVersion normalizeVersion(VersionCommand command) {
        String version = normalizeSemver(command.version());
        String providerBindingKey = command.providerBindingKey() == null || command.providerBindingKey().isBlank()
                ? null : normalizeIdentifier(command.providerBindingKey(), "providerBindingKey", false);
        String engine = normalizeEnum(command.initializationEngine(), "initializationEngine", ENGINES);
        List<StepCommand> rawSteps = command.steps() == null ? List.of() : command.steps();
        if (rawSteps.size() > 32) {
            throw new IllegalArgumentException("A manifest supports at most 32 initialization steps");
        }
        List<StepCommand> steps = new ArrayList<>();
        Set<String> stepCodes = new HashSet<>();
        for (StepCommand step : rawSteps) {
            String code = normalizeIdentifier(step.code(), "step.code", false);
            String type = normalizeEnum(step.type(), "step.type", STEP_TYPES);
            String capability = normalizeIdentifier(step.capability(), "step.capability", false);
            String contractVersion = normalizeIdentifier(step.contractVersion(), "step.contractVersion", false);
            if (!stepCodes.add(code)) {
                throw new IllegalArgumentException("Initialization step codes must be unique");
            }
            steps.add(new StepCommand(code, type, capability, contractVersion));
        }
        if ("NONE".equals(engine) && !steps.isEmpty()) {
            throw new IllegalArgumentException("NONE initialization engine cannot declare steps");
        }
        if ("SAGA_V1".equals(engine) && steps.isEmpty()) {
            throw new IllegalArgumentException("SAGA_V1 initialization engine requires at least one step");
        }
        if ("SAGA_V1".equals(engine) && providerBindingKey == null
                && steps.stream().anyMatch(step -> "PROVIDER_CALLBACK".equals(step.type()))) {
            throw new IllegalArgumentException("Provider callback steps require providerBindingKey");
        }
        List<DependencyCommand> dependencies = normalizeDependencies(command.dependencies());
        List<McpProviderCommand> mcpProviders = normalizeMcpProviders(command.mcpProviders());
        return new NormalizedVersion(version, providerBindingKey, engine, List.copyOf(steps), dependencies, mcpProviders);
    }

    private List<McpProviderCommand> normalizeMcpProviders(List<McpProviderCommand> values) {
        if (values == null || values.isEmpty()) return List.of();
        if (values.size() > 16) throw new IllegalArgumentException("A version supports at most 16 MCP providers");
        Map<String, McpProviderCommand> normalized = new LinkedHashMap<>();
        for (McpProviderCommand provider : values) {
            String key = normalizeIdentifier(provider.providerKey(), "providerKey", false);
            if (!"streamableHttp".equals(provider.transportType())) {
                throw new IllegalArgumentException("Only streamableHttp MCP transport is supported");
            }
            String authType = normalizeEnum(provider.authType(), "authType", Set.of("NONE", "KEYCLOAK_CLIENT_CREDENTIALS"));
            String audience = provider.audience() == null || provider.audience().isBlank()
                    ? null : requireText(provider.audience(), "audience", 256);
            String requiredScope = provider.requiredScope() == null || provider.requiredScope().isBlank()
                    ? null : requireText(provider.requiredScope(), "requiredScope", 256);
            if ("KEYCLOAK_CLIENT_CREDENTIALS".equals(authType) && (audience == null || requiredScope == null)) {
                throw new IllegalArgumentException("Keycloak MCP providers require audience and requiredScope");
            }
            if (provider.tools() == null || provider.tools().isEmpty() || provider.tools().size() > 128) {
                throw new IllegalArgumentException("Each MCP provider requires 1 to 128 tools");
            }
            Map<String, McpToolCommand> tools = new LinkedHashMap<>();
            for (McpToolCommand tool : provider.tools()) {
                String name = normalizeIdentifier(tool.name(), "tool.name", false);
                String digest = tool.schemaDigest() == null || tool.schemaDigest().isBlank()
                        ? null : tool.schemaDigest().trim().toLowerCase(Locale.ROOT);
                if (digest != null && !digest.matches("^[0-9a-f]{64}$")) {
                    throw new IllegalArgumentException("Invalid tool schemaDigest");
                }
                String risk = normalizeEnum(tool.riskLevel(), "tool.riskLevel", Set.of("LOW", "MEDIUM", "HIGH"));
                if (tools.putIfAbsent(name, new McpToolCommand(name, digest, risk, tool.confirmationRequired())) != null) {
                    throw new IllegalArgumentException("MCP tool names must be unique per provider");
                }
            }
            McpProviderCommand item = new McpProviderCommand(key, "streamableHttp", authType,
                    audience, requiredScope, List.copyOf(tools.values()));
            if (normalized.putIfAbsent(key, item) != null) throw new IllegalArgumentException("MCP provider keys must be unique");
        }
        return List.copyOf(normalized.values());
    }

    private List<DependencyCommand> normalizeDependencies(List<DependencyCommand> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Map<String, DependencyCommand> normalized = new LinkedHashMap<>();
        for (DependencyCommand dependency : values) {
            String appCode = normalizeAppCode(dependency.appCode());
            String constraint = dependency.versionConstraint() == null
                    ? "*" : dependency.versionConstraint().trim();
            if (!VERSION_CONSTRAINT.matcher(constraint).matches()) {
                throw new IllegalArgumentException("Invalid dependency version constraint");
            }
            String type = normalizeEnum(dependency.dependencyType(), "dependencyType", DEPENDENCY_TYPES);
            String policy = normalizeEnum(dependency.activationPolicy(), "activationPolicy", ACTIVATION_POLICIES);
            if (normalized.putIfAbsent(appCode,
                    new DependencyCommand(appCode, constraint, type, policy)) != null) {
                throw new IllegalArgumentException("Application dependencies must be unique");
            }
        }
        return List.copyOf(normalized.values());
    }

    private ObjectNode buildManifest(NormalizedVersion version) {
        ObjectNode manifest = objectMapper.createObjectNode();
        manifest.put("schemaVersion", MANIFEST_SCHEMA);
        if (version.providerBindingKey() != null) {
            manifest.put("providerBindingKey", version.providerBindingKey());
        }
        manifest.put("initializationEngine", version.initializationEngine());
        ArrayNode steps = manifest.putArray("steps");
        version.steps().forEach(step -> {
            ObjectNode item = steps.addObject();
            item.put("code", step.code());
            item.put("type", step.type());
            item.put("capability", step.capability());
            item.put("contractVersion", step.contractVersion());
        });
        ArrayNode providers = manifest.putArray("mcpProviders");
        version.mcpProviders().forEach(provider -> {
            ObjectNode item = providers.addObject();
            item.put("providerKey", provider.providerKey());
            item.put("transportType", provider.transportType());
            item.put("authType", provider.authType());
            if (provider.audience() != null) item.put("audience", provider.audience());
            if (provider.requiredScope() != null) item.put("requiredScope", provider.requiredScope());
            ArrayNode tools = item.putArray("tools");
            provider.tools().forEach(tool -> {
                ObjectNode toolNode = tools.addObject();
                toolNode.put("name", tool.name());
                if (tool.schemaDigest() != null) toolNode.put("schemaDigest", tool.schemaDigest());
                toolNode.put("riskLevel", tool.riskLevel());
                toolNode.put("confirmationRequired", tool.confirmationRequired());
            });
        });
        return manifest;
    }

    static boolean matchesConstraint(String version, String constraint) {
        SemVer candidate = SemVer.parse(normalizeSemver(version));
        String normalizedConstraint = constraint == null ? "*" : constraint.trim();
        if ("*".equals(normalizedConstraint)) {
            return true;
        }
        if (normalizedConstraint.startsWith(">=")) {
            return candidate.compareTo(SemVer.parse(normalizedConstraint.substring(2))) >= 0;
        }
        String exact = normalizedConstraint.startsWith("=") ? normalizedConstraint.substring(1) : normalizedConstraint;
        return candidate.compareTo(SemVer.parse(exact)) == 0;
    }

    private static String normalizeAppCode(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!APP_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid appCode");
        }
        return normalized;
    }

    private static String normalizeSemver(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!SEMVER.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Version must use MAJOR.MINOR.PATCH semantics");
        }
        return normalized;
    }

    private static String normalizeIdentifier(String value, String field, boolean optional) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (optional && normalized.isBlank()) {
            return null;
        }
        if (!IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return normalized;
    }

    private static String normalizeEnum(String value, String field, Set<String> allowed) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return normalized;
    }

    private static String requireText(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " is required and must not exceed " + maxLength + " characters");
        }
        return normalized;
    }

    private static void rejectDeploymentAddress(String value, String field) {
        if (value != null && DEPLOYMENT_ADDRESS.matcher(value).find()) {
            throw new IllegalArgumentException(field + " must use a deployment-managed logical key instead of an address");
        }
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize internal application manifest", exception);
        }
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored internal application manifest is invalid", exception);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot digest internal application manifest", exception);
        }
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    public record ApplicationCommand(
            String appCode,
            String displayName,
            String summary,
            String iconKey,
            String ownerTeam,
            String tenantMode,
            String trustedAppCode,
            String launchMode,
            String launchRouteKey) {
    }

    public record VersionCommand(
            String version,
            String providerBindingKey,
            String initializationEngine,
            List<StepCommand> steps,
            List<DependencyCommand> dependencies,
            List<McpProviderCommand> mcpProviders) {
    }

    public record McpProviderCommand(String providerKey, String transportType, String authType,
                                     String audience, String requiredScope, List<McpToolCommand> tools) {}

    public record McpToolCommand(String name, String schemaDigest, String riskLevel,
                                 boolean confirmationRequired) {}

    public record StepCommand(String code, String type, String capability, String contractVersion) {
    }

    public record DependencyCommand(
            String appCode,
            String versionConstraint,
            String dependencyType,
            String activationPolicy) {
    }

    public record ApplicationSummaryView(
            String appCode,
            String displayName,
            String summary,
            String iconKey,
            String ownerTeam,
            String tenantMode,
            String catalogStatus,
            String trustedAppCode,
            String launchMode,
            String launchRouteKey,
            String defaultVersion,
            int versionCount,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record ApplicationDetailView(ApplicationSummaryView application, List<VersionView> versions) {
    }

    public record VersionView(
            String id,
            String appCode,
            String version,
            String manifestSchemaVersion,
            String providerBindingKey,
            String initializationEngine,
            JsonNode manifest,
            String manifestDigest,
            String status,
            List<McpProviderView> mcpProviders,
            List<DependencyView> dependencies,
            String createdBy,
            String validatedBy,
            Instant validatedAt,
            String publishedBy,
            Instant publishedAt,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record DependencyView(
            String appCode,
            String versionConstraint,
            String dependencyType,
            String activationPolicy) {
    }

    public record McpProviderView(String providerKey, String transportType, String authType,
                                  String audience, String requiredScope, List<McpToolView> tools) {}

    public record McpToolView(String name, String schemaDigest, String riskLevel,
                              boolean confirmationRequired) {}

    public record ValidationView(
            String appCode,
            String version,
            boolean valid,
            String manifestDigest,
            List<String> checks,
            String message) {
    }

    private record NormalizedApplication(
            String displayName,
            String summary,
            String iconKey,
            String ownerTeam,
            String tenantMode,
            String trustedAppCode,
            String launchMode,
            String launchRouteKey) {
    }

    private record NormalizedVersion(
            String version,
            String providerBindingKey,
            String initializationEngine,
            List<StepCommand> steps,
            List<DependencyCommand> dependencies,
            List<McpProviderCommand> mcpProviders) {
    }

    private record Edge(String from, String to) {
    }

    private record SemVer(int major, int minor, int patch) implements Comparable<SemVer> {
        private static SemVer parse(String value) {
            Matcher matcher = SEMVER.matcher(value);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid semantic version");
            }
            return new SemVer(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)));
        }

        @Override
        public int compareTo(SemVer other) {
            int majorComparison = Integer.compare(major, other.major);
            if (majorComparison != 0) return majorComparison;
            int minorComparison = Integer.compare(minor, other.minor);
            if (minorComparison != 0) return minorComparison;
            return Integer.compare(patch, other.patch);
        }
    }
}
