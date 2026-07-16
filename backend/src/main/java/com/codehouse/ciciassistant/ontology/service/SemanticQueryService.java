package com.codehouse.ciciassistant.ontology.service;

import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.AdapterContext;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.DataSourceConfig;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.Direction;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalFilter;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalOrder;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalQuery;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalResult;
import com.codehouse.ciciassistant.ontology.domain.OntologyQueryAuditEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyTenantPersistence;
import com.codehouse.ciciassistant.ontology.domain.OntologyVersionEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyVersionRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceRepository;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SemanticQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final OntologyWorkspaceRepository workspaces;
    private final OntologyVersionRepository versions;
    private final OntologyTenantPersistence persistence;
    private final List<OntologyDataSourceAdapter> adapters;
    private final ObjectMapper objectMapper;

    public SemanticQueryService(
            OntologyWorkspaceRepository workspaces,
            OntologyVersionRepository versions,
            OntologyTenantPersistence persistence,
            List<OntologyDataSourceAdapter> adapters,
            ObjectMapper objectMapper) {
        this.workspaces = workspaces;
        this.versions = versions;
        this.persistence = persistence;
        this.adapters = List.copyOf(adapters);
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public QueryPlan explain(String orgId, String userId, SemanticQuery query) {
        return resolve(orgId, userId, query).publicPlan();
    }

    public QueryResult execute(String orgId, String userId, SemanticQuery query) {
        long startedAt = System.nanoTime();
        ResolvedPlan resolved = resolve(orgId, userId, query);
        List<Map<String, Object>> rows;
        try {
            PhysicalResult physicalResult = resolved.adapter().executeRead(
                    new AdapterContext(orgId, userId),
                    resolved.source(),
                    resolved.physicalQuery());
            rows = normalizeRows(
                    physicalResult.rows(), resolved.selectedFields());
        } catch (RuntimeException exception) {
            try {
                saveAudit(
                        resolved,
                        userId,
                        "FAILED",
                        0,
                        elapsedMs(startedAt),
                        evidence(resolved),
                        safeErrorCode(exception));
            } catch (RuntimeException auditFailure) {
                exception.addSuppressed(auditFailure);
            }
            throw exception;
        }
        long elapsedMs = elapsedMs(startedAt);
        QueryEvidence evidence = evidence(resolved);
        saveAudit(
                resolved,
                userId,
                "SUCCEEDED",
                rows.size(),
                elapsedMs,
                evidence,
                null);
        return new QueryResult(rows, evidence, elapsedMs);
    }

    private ResolvedPlan resolve(String orgId, String userId, SemanticQuery requestedQuery) {
        SemanticQuery query = normalizeQuery(requestedQuery);
        requireCurrentContext(orgId, userId);

        OntologyWorkspaceEntity workspace = workspaces.findByOrgIdAndKey(orgId, query.ontologyKey())
                .orElseThrow(() -> new IllegalArgumentException("ONTOLOGY_WORKSPACE_NOT_FOUND"));
        OntologyVersionEntity version = versions.findByWorkspaceIdAndOrgIdAndVersionNo(
                        workspace.getId(), orgId, query.version())
                .orElseThrow(() -> new IllegalArgumentException(
                        "ONTOLOGY_VERSION_NOT_PUBLISHED"));
        OntologyDocument document = readSnapshot(version.getSnapshotJson());
        if (!Objects.equals(document.key(), query.ontologyKey())) {
            throw new IllegalStateException("ONTOLOGY_SNAPSHOT_KEY_MISMATCH");
        }

        OntologyDocument.Concept concept = safe(document.concepts()).stream()
                .filter(candidate -> candidate != null
                        && Objects.equals(candidate.key(), query.concept()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("QUERY_CONCEPT_UNKNOWN"));
        if (!concept.enabled() || !concept.queryable()) {
            throw new IllegalArgumentException("QUERY_CONCEPT_NOT_QUERYABLE");
        }

        List<OntologyDocument.Mapping> validMappings = safe(document.mappings()).stream()
                .filter(Objects::nonNull)
                .filter(mapping -> "VALID".equalsIgnoreCase(mapping.validationStatus()))
                .toList();
        OntologyDocument.Mapping conceptMapping = requireMapping(
                validMappings, "CONCEPT", concept.key(), "QUERY_CONCEPT_UNMAPPED");

        Map<String, ResolvedField> allFields = new LinkedHashMap<>();
        for (String field : query.select()) {
            allFields.putIfAbsent(field, resolveField(
                    document, concept, validMappings, field));
        }
        List<ResolvedFilter> filters = new ArrayList<>();
        for (Filter filter : query.filters()) {
            if (filter == null) {
                throw new IllegalArgumentException("QUERY_FILTER_REQUIRED");
            }
            ResolvedField field = allFields.computeIfAbsent(
                    filter.field(),
                    key -> resolveField(document, concept, validMappings, key));
            OntologyDocument.Operator operator = parseOperator(filter.operator());
            validateFilterValue(operator, filter.value());
            filters.add(new ResolvedFilter(field, operator, filter.value()));
        }
        List<ResolvedOrder> orders = new ArrayList<>();
        for (OrderBy order : query.orderBy()) {
            if (order == null) {
                throw new IllegalArgumentException("QUERY_ORDER_REQUIRED");
            }
            ResolvedField field = allFields.computeIfAbsent(
                    order.field(),
                    key -> resolveField(document, concept, validMappings, key));
            orders.add(new ResolvedOrder(field, parseDirection(order.direction())));
        }

        Set<Long> sourceIds = new LinkedHashSet<>();
        sourceIds.add(conceptMapping.dataSourceId());
        allFields.values().forEach(field -> {
            sourceIds.add(field.mapping().dataSourceId());
            if (field.relationDataSourceId() != null) {
                sourceIds.add(field.relationDataSourceId());
            }
        });
        if (sourceIds.size() != 1) {
            throw new IllegalArgumentException("CROSS_SOURCE_QUERY_NOT_SUPPORTED");
        }
        Long sourceId = sourceIds.iterator().next();
        OntologyDocument.DataSource sourceDocument = safe(document.dataSources()).stream()
                .filter(source -> source != null && Objects.equals(source.id(), sourceId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("QUERY_DATA_SOURCE_NOT_FOUND"));
        if (allFields.values().stream().anyMatch(field ->
                !Objects.equals(field.mapping().physicalObjectKey(),
                        conceptMapping.physicalObjectKey()))) {
            throw new IllegalArgumentException("CROSS_OBJECT_QUERY_NOT_SUPPORTED");
        }

        DataSourceConfig source = toConfig(workspace.getId(), sourceDocument);
        OntologyDataSourceAdapter adapter = adapters.stream()
                .filter(candidate -> candidate.supports(source))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("ONTOLOGY_ADAPTER_NOT_AVAILABLE"));

        List<ResolvedField> selectedFields = query.select().stream()
                .map(allFields::get)
                .toList();
        List<String> physicalFields = allFields.values().stream()
                .map(field -> field.mapping().physicalFieldKey())
                .distinct()
                .toList();
        PhysicalQuery physicalQuery = new PhysicalQuery(
                conceptMapping.physicalObjectKey(),
                physicalFields,
                filters.stream()
                        .map(filter -> new PhysicalFilter(
                                filter.field().mapping().physicalFieldKey(),
                                filter.operator(),
                                filter.value()))
                        .toList(),
                orders.stream()
                        .map(order -> new PhysicalOrder(
                                order.field().mapping().physicalFieldKey(),
                                order.direction()))
                        .toList(),
                query.limit());
        QueryPlan publicPlan = new QueryPlan(
                query.ontologyKey(),
                query.version(),
                query.concept(),
                source.type().name(),
                source.key(),
                conceptMapping.physicalObjectKey(),
                selectedFields.stream().map(field -> new FieldPlan(
                        field.logicalKey(), field.mapping().physicalFieldKey())).toList(),
                filters.stream().map(filter -> new FilterPlan(
                        filter.field().logicalKey(), filter.operator().name())).toList(),
                orders.stream().map(order -> new OrderPlan(
                        order.field().logicalKey(), order.direction().name())).toList(),
                query.limit());
        return new ResolvedPlan(
                query,
                workspace,
                version,
                source,
                adapter,
                conceptMapping,
                selectedFields,
                physicalQuery,
                publicPlan);
    }

    private SemanticQuery normalizeQuery(SemanticQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("SEMANTIC_QUERY_REQUIRED");
        }
        int limit = query.limit() == null ? DEFAULT_LIMIT : query.limit();
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("QUERY_BUDGET_EXCEEDED");
        }
        if (!hasText(query.ontologyKey())
                || query.version() == null
                || query.version() < 1
                || !hasText(query.concept())) {
            throw new IllegalArgumentException("SEMANTIC_QUERY_INVALID");
        }
        List<String> select = safe(query.select());
        if (select.isEmpty() || select.stream().anyMatch(field -> !hasText(field))) {
            throw new IllegalArgumentException("QUERY_FIELDS_REQUIRED");
        }
        List<Filter> filters = safe(query.filters());
        if (filters.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("QUERY_FILTER_REQUIRED");
        }
        List<OrderBy> orderBy = safe(query.orderBy());
        if (orderBy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("QUERY_ORDER_REQUIRED");
        }
        select.forEach(this::requireHopBudget);
        filters.stream()
                .map(Filter::field)
                .forEach(this::requireHopBudget);
        orderBy.stream()
                .map(OrderBy::field)
                .forEach(this::requireHopBudget);
        return new SemanticQuery(
                query.ontologyKey(),
                query.version(),
                query.concept(),
                List.copyOf(new LinkedHashSet<>(select)),
                List.copyOf(filters),
                List.copyOf(orderBy),
                limit);
    }

    private void requireHopBudget(String field) {
        if (!hasText(field)) {
            throw new IllegalArgumentException("QUERY_FIELD_REQUIRED");
        }
        if (field.split("\\.", -1).length > 2) {
            throw new IllegalArgumentException("RELATION_HOP_LIMIT_EXCEEDED");
        }
    }

    private ResolvedField resolveField(
            OntologyDocument document,
            OntologyDocument.Concept rootConcept,
            List<OntologyDocument.Mapping> mappings,
            String requestedField) {
        requireHopBudget(requestedField);
        String[] path = requestedField.split("\\.", -1);
        OntologyDocument.Concept concept = rootConcept;
        String propertyKey = requestedField;
        Long relationDataSourceId = null;
        if (path.length == 2) {
            OntologyDocument.Relation relation = safe(document.relations()).stream()
                    .filter(candidate -> candidate != null
                            && candidate.enabled()
                            && candidate.queryable()
                            && Objects.equals(candidate.sourceConceptKey(), rootConcept.key())
                            && Objects.equals(candidate.key(), path[0]))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("QUERY_RELATION_UNKNOWN"));
            relationDataSourceId = requireMapping(
                    mappings, "RELATION", relation.key(), "QUERY_RELATION_UNMAPPED")
                    .dataSourceId();
            concept = safe(document.concepts()).stream()
                    .filter(candidate -> candidate != null
                            && Objects.equals(candidate.key(), relation.targetConceptKey()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("QUERY_RELATION_TARGET_UNKNOWN"));
            propertyKey = path[1];
        }

        String resolvedPropertyKey = propertyKey;
        OntologyDocument.Property property = safe(concept.properties()).stream()
                .filter(candidate -> candidate != null
                        && Objects.equals(candidate.key(), resolvedPropertyKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("QUERY_FIELD_UNKNOWN"));
        if (property.sensitive()) {
            throw new IllegalArgumentException("QUERY_FIELD_SENSITIVE");
        }
        if (!property.queryable()) {
            throw new IllegalArgumentException("QUERY_FIELD_NOT_QUERYABLE");
        }
        OntologyDocument.Mapping mapping = requireMapping(
                mappings,
                "PROPERTY",
                concept.key() + "." + property.key(),
                "QUERY_FIELD_UNMAPPED");
        if (!hasText(mapping.physicalFieldKey())) {
            throw new IllegalArgumentException("QUERY_FIELD_UNMAPPED");
        }
        return new ResolvedField(requestedField, property, mapping, relationDataSourceId);
    }

    private OntologyDocument.Mapping requireMapping(
            List<OntologyDocument.Mapping> mappings,
            String targetType,
            String targetKey,
            String errorCode) {
        return mappings.stream()
                .filter(mapping -> targetType.equalsIgnoreCase(mapping.targetType()))
                .filter(mapping -> Objects.equals(targetKey, mapping.targetKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(errorCode));
    }

    private void validateFilterValue(OntologyDocument.Operator operator, Object value) {
        if ((operator == OntologyDocument.Operator.IN
                || operator == OntologyDocument.Operator.BETWEEN)
                && !(value instanceof List<?>)) {
            throw new IllegalArgumentException("QUERY_LIST_VALUE_REQUIRED");
        }
        if (operator == OntologyDocument.Operator.BETWEEN
                && ((List<?>) value).size() != 2) {
            throw new IllegalArgumentException("BETWEEN_REQUIRES_TWO_VALUES");
        }
        if (value instanceof Map<?, ?>) {
            throw new IllegalArgumentException("QUERY_FILTER_VALUE_NOT_ALLOWED");
        }
    }

    private OntologyDocument.Operator parseOperator(String value) {
        try {
            return OntologyDocument.Operator.valueOf(normalized(value));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("QUERY_OPERATOR_NOT_ALLOWED");
        }
    }

    private Direction parseDirection(String value) {
        try {
            return Direction.valueOf(normalized(value));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("QUERY_ORDER_DIRECTION_NOT_ALLOWED");
        }
    }

    private DataSourceConfig toConfig(
            Long workspaceId,
            OntologyDocument.DataSource source) {
        return new DataSourceConfig(
                source.id(),
                workspaceId,
                source.key(),
                source.name(),
                source.type(),
                adapterKey(source.configJson()),
                source.configJson(),
                null);
    }

    private String adapterKey(String configJson) {
        if (!hasText(configJson)) {
            return null;
        }
        try {
            JsonNode config = objectMapper.readTree(configJson);
            String adapterKey = config.path("adapterKey").asText("");
            return adapterKey.isBlank() ? null : adapterKey;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("DATA_SOURCE_CONFIG_INVALID", exception);
        }
    }

    private OntologyDocument readSnapshot(String snapshotJson) {
        try {
            return objectMapper.readValue(snapshotJson, OntologyDocument.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("ONTOLOGY_PUBLISHED_SNAPSHOT_INVALID", exception);
        }
    }

    private List<Map<String, Object>> normalizeRows(
            List<Map<String, Object>> physicalRows,
            List<ResolvedField> selectedFields) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> physicalRow : physicalRows) {
            Map<String, Object> logicalRow = new LinkedHashMap<>();
            for (ResolvedField field : selectedFields) {
                Object value = physicalRow.get(field.mapping().physicalFieldKey());
                logicalRow.put(field.logicalKey(), applyTransform(value, field.mapping().transform()));
            }
            result.add(Collections.unmodifiableMap(logicalRow));
        }
        return List.copyOf(result);
    }

    private Object applyTransform(Object value, String transform) {
        if (value == null || !hasText(transform) || "DIRECT".equalsIgnoreCase(transform)) {
            return value;
        }
        return switch (normalized(transform)) {
            case "TRIM" -> String.valueOf(value).trim();
            case "LOWERCASE" -> String.valueOf(value).toLowerCase(Locale.ROOT);
            case "UPPERCASE" -> String.valueOf(value).toUpperCase(Locale.ROOT);
            case "NUMBER" -> new BigDecimal(String.valueOf(value));
            case "DATE" -> LocalDate.parse(String.valueOf(value));
            case "DATETIME" -> OffsetDateTime.parse(String.valueOf(value));
            case "BOOLEAN_MAP" -> Boolean.valueOf(String.valueOf(value));
            case "ENUM_MAP", "REFERENCE" -> value;
            default -> throw new IllegalStateException("MAPPING_TRANSFORM_NOT_ALLOWED");
        };
    }

    private QueryEvidence evidence(ResolvedPlan resolved) {
        return new QueryEvidence(
                resolved.source().type().name(),
                resolved.source().key(),
                resolved.query().version(),
                resolved.selectedFields().stream()
                        .map(field -> new MappingEvidence(
                                field.logicalKey(),
                                field.mapping().physicalObjectKey(),
                                field.mapping().physicalFieldKey()))
                        .toList());
    }

    private void saveAudit(
            ResolvedPlan resolved,
            String userId,
            String status,
            int resultCount,
            long elapsedMs,
            QueryEvidence evidence,
            String errorCode) {
        OntologyQueryAuditEntity audit = new OntologyQueryAuditEntity(
                resolved.workspace().getOrgId(),
                resolved.workspace().getId(),
                resolved.version().getId(),
                resolved.source().id(),
                userId,
                resolved.query().concept(),
                writeJson(auditSummary(resolved.query())),
                resultCount,
                elapsedMs,
                status,
                writeJson(evidence),
                errorCode);
        persistence.saveForCurrentOrg(audit);
    }

    private Map<String, Object> auditSummary(SemanticQuery query) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("ontologyKey", query.ontologyKey());
        summary.put("version", query.version());
        summary.put("concept", query.concept());
        summary.put("select", query.select());
        summary.put("filters", query.filters().stream()
                .map(filter -> Map.of(
                        "field", filter.field(),
                        "operator", filter.operator(),
                        "value", "REDACTED"))
                .toList());
        summary.put("orderBy", query.orderBy());
        summary.put("limit", query.limit());
        return summary;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("ONTOLOGY_QUERY_AUDIT_SERIALIZATION_FAILED", exception);
        }
    }

    private void requireCurrentContext(String orgId, String userId) {
        if (!hasText(userId)
                || !Objects.equals(TenantContext.requireOrgId(), orgId)
                || TenantContext.getUserId().filter(userId::equals).isEmpty()) {
            throw new ForbiddenException("ONTOLOGY_QUERY_CONTEXT_MISMATCH");
        }
    }

    private String safeErrorCode(RuntimeException exception) {
        String message = exception.getMessage();
        if (message != null && message.matches("[A-Z0-9_]{3,64}")) {
            return message;
        }
        return "ADAPTER_READ_FAILED";
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    public record SemanticQuery(
            String ontologyKey,
            Integer version,
            String concept,
            List<String> select,
            List<Filter> filters,
            List<OrderBy> orderBy,
            Integer limit) {
    }

    public record Filter(String field, String operator, Object value) {
    }

    public record OrderBy(String field, String direction) {
    }

    public record QueryPlan(
            String ontologyKey,
            int ontologyVersion,
            String concept,
            String sourceType,
            String dataSourceKey,
            String physicalObjectKey,
            List<FieldPlan> fields,
            List<FilterPlan> filters,
            List<OrderPlan> orderBy,
            int limit) {
    }

    public record FieldPlan(String logicalField, String physicalField) {
    }

    public record FilterPlan(String logicalField, String operator) {
    }

    public record OrderPlan(String logicalField, String direction) {
    }

    public record MappingEvidence(
            String logicalField,
            String physicalObject,
            String physicalField) {
    }

    public record QueryEvidence(
            String sourceType,
            String dataSourceKey,
            int ontologyVersion,
            List<MappingEvidence> mappings) {
    }

    public record QueryResult(
            List<Map<String, Object>> rows,
            QueryEvidence evidence,
            long elapsedMs) {
    }

    private record ResolvedField(
            String logicalKey,
            OntologyDocument.Property property,
            OntologyDocument.Mapping mapping,
            Long relationDataSourceId) {
    }

    private record ResolvedFilter(
            ResolvedField field,
            OntologyDocument.Operator operator,
            Object value) {
    }

    private record ResolvedOrder(ResolvedField field, Direction direction) {
    }

    private record ResolvedPlan(
            SemanticQuery query,
            OntologyWorkspaceEntity workspace,
            OntologyVersionEntity version,
            DataSourceConfig source,
            OntologyDataSourceAdapter adapter,
            OntologyDocument.Mapping conceptMapping,
            List<ResolvedField> selectedFields,
            PhysicalQuery physicalQuery,
            QueryPlan publicPlan) {
    }
}
