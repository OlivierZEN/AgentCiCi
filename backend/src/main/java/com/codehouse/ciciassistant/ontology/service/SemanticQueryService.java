package com.codehouse.ciciassistant.ontology.service;

import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.common.error.ResourceNotFoundException;
import com.codehouse.ciciassistant.common.error.DataSourceUnavailableException;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.AdapterContext;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.DataSourceConfig;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.Direction;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalFilter;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalOrder;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalQuery;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalResult;
import com.codehouse.ciciassistant.ontology.domain.OntologyQueryAuditEntity;
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
    private static final int MAX_SELECT_FIELDS = 50;
    private static final int MAX_FILTERS = 20;
    private static final int MAX_ORDER_BY = 5;
    private static final int MAX_IN_VALUES = 100;
    private static final int MAX_RELATION_SELECT_FIELDS = 20;
    private static final int MAX_RELATION_PLANS = 5;
    private static final int MAX_RELATION_ROWS = 200;

    private final OntologyWorkspaceRepository workspaces;
    private final OntologyVersionRepository versions;
    private final OntologyQueryAuditWriter auditWriter;
    private final List<OntologyDataSourceAdapter> adapters;
    private final ObjectMapper objectMapper;

    public SemanticQueryService(
            OntologyWorkspaceRepository workspaces,
            OntologyVersionRepository versions,
            OntologyQueryAuditWriter auditWriter,
            List<OntologyDataSourceAdapter> adapters,
            ObjectMapper objectMapper) {
        this.workspaces = workspaces;
        this.versions = versions;
        this.auditWriter = auditWriter;
        this.adapters = List.copyOf(adapters);
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public QueryPlan explain(String orgId, String userId, SemanticQuery query) {
        SemanticQuery normalized = normalizeQuery(query);
        requireCurrentContext(orgId, userId);
        return resolve(locateScope(orgId, normalized), normalized).publicPlan();
    }

    public QueryResult execute(String orgId, String userId, SemanticQuery query) {
        long startedAt = System.nanoTime();
        SemanticQuery normalized = normalizeQuery(query);
        requireCurrentContext(orgId, userId);
        QueryScope scope = locateScope(orgId, normalized);
        ResolvedPlan resolved = null;
        int totalCount = 0;
        boolean moreAvailable = false;
        List<Map<String, Object>> rows;
        try {
            resolved = resolve(scope, normalized);
            AdapterContext adapterContext = new AdapterContext(orgId, userId);
            PhysicalResult physicalResult = executeRead(
                    resolved.adapter(), adapterContext, resolved.source(), resolved.physicalQuery());
            totalCount = physicalResult.totalCount();
            moreAvailable = physicalResult.moreAvailable();
            Map<String, RelationRows> relationRows = executeRelations(
                    adapterContext, resolved, physicalResult.rows());
            rows = normalizeRows(
                    physicalResult.rows(), resolved.selectedFields(),
                    resolved.relationPlans(), relationRows);
        } catch (RuntimeException exception) {
            try {
                saveAudit(
                        scope,
                        normalized,
                        resolved == null ? null : resolved.source(),
                        userId,
                        "FAILED",
                        0,
                        elapsedMs(startedAt),
                        resolved == null
                                ? null
                                : evidence(resolved, totalCount, moreAvailable),
                        safeErrorCode(exception));
            } catch (RuntimeException auditFailure) {
                exception.addSuppressed(auditFailure);
            }
            throw exception;
        }
        long elapsedMs = elapsedMs(startedAt);
        QueryEvidence evidence = evidence(resolved, totalCount, moreAvailable);
        saveAudit(
                scope,
                normalized,
                resolved.source(),
                userId,
                "SUCCEEDED",
                rows.size(),
                elapsedMs,
                evidence,
                null);
        return new QueryResult(rows, evidence, elapsedMs);
    }

    private QueryScope locateScope(String orgId, SemanticQuery query) {
        OntologyWorkspaceEntity workspace = workspaces.findByOrgIdAndKey(orgId, query.ontologyKey())
                .orElseThrow(() -> new ResourceNotFoundException("ONTOLOGY_NOT_FOUND"));
        OntologyVersionEntity version = versions.findByWorkspaceIdAndOrgIdAndVersionNo(
                        workspace.getId(), orgId, query.version())
                .orElseThrow(() -> new ResourceNotFoundException("ONTOLOGY_NOT_FOUND"));
        return new QueryScope(workspace, version);
    }

    private ResolvedPlan resolve(QueryScope scope, SemanticQuery query) {
        OntologyWorkspaceEntity workspace = scope.workspace();
        OntologyVersionEntity version = scope.version();
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
            ResolvedField field = allFields.computeIfAbsent(
                    filter.field(),
                    key -> resolveField(document, concept, validMappings, key));
            filters.add(new ResolvedFilter(
                    field, parseOperator(filter.operator()), filter.value()));
        }
        List<ResolvedOrder> orders = new ArrayList<>();
        for (OrderBy order : query.orderBy()) {
            ResolvedField field = allFields.computeIfAbsent(
                    order.field(),
                    key -> resolveField(document, concept, validMappings, key));
            orders.add(new ResolvedOrder(field, parseDirection(order.direction())));
        }

        Set<Long> sourceIds = new LinkedHashSet<>();
        sourceIds.add(conceptMapping.dataSourceId());
        allFields.values().forEach(field -> {
            sourceIds.add(field.mapping().dataSourceId());
            if (field.relation() != null) {
                sourceIds.add(field.relation().mapping().dataSourceId());
                sourceIds.add(field.relation().targetConceptMapping().dataSourceId());
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

        for (ResolvedField field : allFields.values()) {
            if (field.relation() == null) {
                if (!Objects.equals(
                        field.mapping().physicalObjectKey(),
                        conceptMapping.physicalObjectKey())) {
                    throw new IllegalArgumentException("CROSS_OBJECT_QUERY_NOT_SUPPORTED");
                }
                continue;
            }
            ResolvedRelation relation = field.relation();
            if (!Objects.equals(
                    relation.mapping().physicalObjectKey(),
                    conceptMapping.physicalObjectKey())) {
                throw new IllegalArgumentException("RELATION_SOURCE_OBJECT_MISMATCH");
            }
            if (!hasText(relation.mapping().physicalFieldKey())
                    || !hasText(relation.mapping().relationTargetFieldKey())) {
                throw new IllegalArgumentException("QUERY_RELATION_UNMAPPED");
            }
            if (!Objects.equals(
                    field.mapping().physicalObjectKey(),
                    relation.targetConceptMapping().physicalObjectKey())) {
                throw new IllegalArgumentException("RELATION_TARGET_OBJECT_MISMATCH");
            }
        }

        DataSourceConfig source = toConfig(workspace.getId(), sourceDocument);
        List<OntologyDataSourceAdapter> matchingAdapters = adapters.stream()
                .filter(candidate -> supports(candidate, source))
                .limit(2)
                .toList();
        if (matchingAdapters.size() != 1) {
            throw new DataSourceUnavailableException();
        }
        OntologyDataSourceAdapter adapter = matchingAdapters.getFirst();

        List<ResolvedField> selectedFields = query.select().stream()
                .map(allFields::get)
                .toList();
        LinkedHashSet<String> rootPhysicalFields = new LinkedHashSet<>();
        allFields.values().stream()
                .filter(field -> field.relation() == null)
                .map(field -> field.mapping().physicalFieldKey())
                .forEach(rootPhysicalFields::add);

        Map<String, List<ResolvedField>> fieldsByRelation = new LinkedHashMap<>();
        selectedFields.stream()
                .filter(field -> field.relation() != null)
                .forEach(field -> fieldsByRelation.computeIfAbsent(
                        field.relation().relation().key(), ignored -> new ArrayList<>()).add(field));
        List<ResolvedRelationPlan> relationPlans = new ArrayList<>();
        for (List<ResolvedField> relationFields : fieldsByRelation.values()) {
            ResolvedRelation relation = relationFields.getFirst().relation();
            rootPhysicalFields.add(relation.mapping().physicalFieldKey());
            LinkedHashSet<String> targetFields = new LinkedHashSet<>();
            targetFields.add(relation.mapping().relationTargetFieldKey());
            relationFields.stream()
                    .map(field -> field.mapping().physicalFieldKey())
                    .forEach(targetFields::add);
            PhysicalQuery targetValidationQuery = new PhysicalQuery(
                    relation.targetConceptMapping().physicalObjectKey(),
                    List.copyOf(targetFields),
                    List.of(new PhysicalFilter(
                            relation.mapping().relationTargetFieldKey(),
                            OntologyDocument.Operator.IN,
                            List.of("__join_key__"))),
                    List.of(),
                    MAX_RELATION_ROWS);
            adapter.validateQuery(source, targetValidationQuery);
            relationPlans.add(new ResolvedRelationPlan(
                    relation,
                    List.copyOf(relationFields),
                    List.copyOf(targetFields)));
        }

        PhysicalQuery physicalQuery = new PhysicalQuery(
                conceptMapping.physicalObjectKey(),
                List.copyOf(rootPhysicalFields),
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
        adapter.validateQuery(source, physicalQuery);
        QueryPlan publicPlan = new QueryPlan(
                query.ontologyKey(),
                query.version(),
                query.concept(),
                source.type().name(),
                source.key(),
                conceptMapping.physicalObjectKey(),
                selectedFields.stream().map(field -> new FieldPlan(
                        field.logicalKey(), field.mapping().physicalFieldKey())).toList(),
                relationPlans.stream().map(plan -> new RelationPlan(
                        plan.relation().relation().key(),
                        plan.relation().relation().cardinality().name(),
                        conceptMapping.physicalObjectKey(),
                        plan.relation().mapping().physicalFieldKey(),
                        plan.relation().targetConceptMapping().physicalObjectKey(),
                        plan.relation().mapping().relationTargetFieldKey(),
                        plan.fields().stream().map(field -> new FieldPlan(
                                field.logicalKey(), field.mapping().physicalFieldKey())).toList()))
                        .toList(),
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
                filters,
                orders,
                physicalQuery,
                List.copyOf(relationPlans),
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
        if (select.size() > MAX_SELECT_FIELDS) {
            throw new IllegalArgumentException("QUERY_SELECT_LIMIT_EXCEEDED");
        }
        List<Filter> filters = safe(query.filters());
        if (filters.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("QUERY_FILTER_REQUIRED");
        }
        if (filters.size() > MAX_FILTERS) {
            throw new IllegalArgumentException("QUERY_FILTER_LIMIT_EXCEEDED");
        }
        List<OrderBy> orderBy = safe(query.orderBy());
        if (orderBy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("QUERY_ORDER_REQUIRED");
        }
        if (orderBy.size() > MAX_ORDER_BY) {
            throw new IllegalArgumentException("QUERY_ORDER_LIMIT_EXCEEDED");
        }
        filters.forEach(filter -> validateFilterValue(
                parseOperator(filter.operator()), filter.value()));
        select.forEach(this::requireHopBudget);
        List<String> relationSelects = select.stream()
                .filter(this::isRelationField)
                .toList();
        if (relationSelects.size() > MAX_RELATION_SELECT_FIELDS) {
            throw new IllegalArgumentException("QUERY_RELATION_SELECT_LIMIT_EXCEEDED");
        }
        long relationPlanCount = relationSelects.stream()
                .map(field -> field.substring(0, field.indexOf('.')))
                .distinct()
                .count();
        if (relationPlanCount > MAX_RELATION_PLANS) {
            throw new IllegalArgumentException("QUERY_RELATION_PLAN_LIMIT_EXCEEDED");
        }
        filters.stream()
                .map(Filter::field)
                .forEach(this::requireHopBudget);
        orderBy.stream()
                .map(OrderBy::field)
                .forEach(this::requireHopBudget);
        if (filters.stream().map(Filter::field).anyMatch(this::isRelationField)) {
            throw new IllegalArgumentException("RELATION_FILTER_NOT_SUPPORTED");
        }
        if (orderBy.stream().map(OrderBy::field).anyMatch(this::isRelationField)) {
            throw new IllegalArgumentException("RELATION_ORDER_NOT_SUPPORTED");
        }
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

    private boolean isRelationField(String field) {
        return field != null && field.indexOf('.') >= 0;
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
        ResolvedRelation resolvedRelation = null;
        if (path.length == 2) {
            OntologyDocument.Relation relation = safe(document.relations()).stream()
                    .filter(candidate -> candidate != null
                            && candidate.enabled()
                            && candidate.queryable()
                            && Objects.equals(candidate.sourceConceptKey(), rootConcept.key())
                            && Objects.equals(candidate.key(), path[0]))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("QUERY_RELATION_UNKNOWN"));
            OntologyDocument.Mapping relationMapping = requireMapping(
                    mappings, "RELATION", relation.key(), "QUERY_RELATION_UNMAPPED");
            concept = safe(document.concepts()).stream()
                    .filter(candidate -> candidate != null
                            && Objects.equals(candidate.key(), relation.targetConceptKey()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("QUERY_RELATION_TARGET_UNKNOWN"));
            OntologyDocument.Mapping targetConceptMapping = requireMapping(
                    mappings, "CONCEPT", concept.key(), "QUERY_RELATION_TARGET_UNMAPPED");
            resolvedRelation = new ResolvedRelation(
                    relation, relationMapping, concept, targetConceptMapping);
            propertyKey = path[1];
        }

        String resolvedPropertyKey = propertyKey;
        OntologyDocument.Property property = safe(concept.properties()).stream()
                .filter(candidate -> candidate != null
                        && Objects.equals(candidate.key(), resolvedPropertyKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("QUERY_FIELD_UNKNOWN"));
        if (property.sensitive()) {
            throw new ForbiddenException("SENSITIVE_PROPERTY_FORBIDDEN");
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
        return new ResolvedField(requestedField, property, mapping, resolvedRelation);
    }

    private OntologyDocument.Mapping requireMapping(
            List<OntologyDocument.Mapping> mappings,
            String targetType,
            String targetKey,
            String errorCode) {
        List<OntologyDocument.Mapping> candidates = mappings.stream()
                .filter(mapping -> targetType.equalsIgnoreCase(mapping.targetType()))
                .filter(mapping -> Objects.equals(targetKey, mapping.targetKey()))
                .toList();
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException(errorCode);
        }
        if (candidates.size() > 1) {
            throw new IllegalArgumentException("MAPPING_AMBIGUOUS");
        }
        return candidates.getFirst();
    }

    private void validateFilterValue(OntologyDocument.Operator operator, Object value) {
        if (operator == OntologyDocument.Operator.IN) {
            if (!(value instanceof List<?> values) || values.isEmpty()) {
                throw new IllegalArgumentException("QUERY_IN_VALUES_REQUIRED");
            }
            if (values.size() > MAX_IN_VALUES) {
                throw new IllegalArgumentException("QUERY_IN_VALUE_LIMIT_EXCEEDED");
            }
            if (values.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("QUERY_IN_VALUE_NULL");
            }
        }
        if (operator == OntologyDocument.Operator.BETWEEN
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
                source.sampleDataJson());
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

    private Map<String, RelationRows> executeRelations(
            AdapterContext context,
            ResolvedPlan resolved,
            List<Map<String, Object>> rootRows) {
        Map<String, RelationRows> result = new LinkedHashMap<>();
        for (ResolvedRelationPlan plan : resolved.relationPlans()) {
            String sourceJoinField = plan.relation().mapping().physicalFieldKey();
            Map<Object, Object> joinValues = new LinkedHashMap<>();
            for (Map<String, Object> rootRow : rootRows) {
                Object value = rootRow.get(sourceJoinField);
                if (value != null) {
                    Object normalizedKey = normalizeJoinKey(value);
                    if (plan.relation().relation().cardinality()
                                    == OntologyDocument.Cardinality.ONE_TO_ONE
                            && joinValues.containsKey(normalizedKey)) {
                        throw new IllegalStateException("RELATION_CARDINALITY_VIOLATION");
                    }
                    joinValues.putIfAbsent(normalizedKey, value);
                }
            }

            List<Map<String, Object>> targetRows = new ArrayList<>();
            List<Object> values = List.copyOf(joinValues.values());
            for (int start = 0; start < values.size(); start += MAX_IN_VALUES) {
                List<Object> batch = values.subList(
                        start, Math.min(start + MAX_IN_VALUES, values.size()));
                PhysicalQuery targetQuery = new PhysicalQuery(
                        plan.relation().targetConceptMapping().physicalObjectKey(),
                        plan.targetPhysicalFields(),
                        List.of(new PhysicalFilter(
                                plan.relation().mapping().relationTargetFieldKey(),
                                OntologyDocument.Operator.IN,
                                batch)),
                        List.of(),
                        MAX_RELATION_ROWS);
                resolved.adapter().validateQuery(resolved.source(), targetQuery);
                PhysicalResult targetResult = executeRead(
                        resolved.adapter(), context, resolved.source(), targetQuery);
                if (targetResult.moreAvailable()
                        || targetResult.totalCount() > targetResult.rows().size()
                        || targetResult.totalCount() > MAX_RELATION_ROWS) {
                    throw new IllegalStateException("RELATION_RESULT_LIMIT_EXCEEDED");
                }
                targetRows.addAll(targetResult.rows());
                if (targetRows.size() > MAX_RELATION_ROWS) {
                    throw new IllegalStateException("RELATION_RESULT_LIMIT_EXCEEDED");
                }
            }

            Map<Object, List<Map<String, Object>>> rowsByJoinKey = new LinkedHashMap<>();
            String targetJoinField = plan.relation().mapping().relationTargetFieldKey();
            for (Map<String, Object> targetRow : targetRows) {
                Object targetKey = targetRow.get(targetJoinField);
                if (targetKey == null) {
                    throw new IllegalStateException("RELATION_TARGET_JOIN_KEY_REQUIRED");
                }
                rowsByJoinKey.computeIfAbsent(
                        normalizeJoinKey(targetKey), ignored -> new ArrayList<>()).add(targetRow);
            }
            if (isSingleValued(plan.relation().relation().cardinality())
                    && rowsByJoinKey.values().stream().anyMatch(rows -> rows.size() > 1)) {
                throw new IllegalStateException("RELATION_CARDINALITY_VIOLATION");
            }
            Map<Object, List<Map<String, Object>>> immutableRows = new LinkedHashMap<>();
            rowsByJoinKey.forEach((key, value) -> immutableRows.put(key, List.copyOf(value)));
            result.put(
                    plan.relation().relation().key(),
                    new RelationRows(Collections.unmodifiableMap(immutableRows)));
        }
        return Collections.unmodifiableMap(result);
    }

    private List<Map<String, Object>> normalizeRows(
            List<Map<String, Object>> physicalRows,
            List<ResolvedField> selectedFields,
            List<ResolvedRelationPlan> relationPlans,
            Map<String, RelationRows> relationRows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> physicalRow : physicalRows) {
            Map<String, Object> logicalRow = new LinkedHashMap<>();
            for (ResolvedField field : selectedFields) {
                if (field.relation() != null) {
                    continue;
                }
                Object value = physicalRow.get(field.mapping().physicalFieldKey());
                logicalRow.put(field.logicalKey(), applyTransform(value, field.mapping().transform()));
            }
            for (ResolvedRelationPlan plan : relationPlans) {
                String relationKey = plan.relation().relation().key();
                Object sourceKey = physicalRow.get(
                        plan.relation().mapping().physicalFieldKey());
                List<Map<String, Object>> matched = sourceKey == null
                        ? List.of()
                        : relationRows.get(relationKey).rowsByJoinKey()
                                .getOrDefault(normalizeJoinKey(sourceKey), List.of());
                List<Map<String, Object>> nestedRows = matched.stream()
                        .map(row -> normalizeRelatedRow(row, plan.fields()))
                        .toList();
                Object nested = isSingleValued(plan.relation().relation().cardinality())
                        ? nestedRows.isEmpty() ? null : nestedRows.getFirst()
                        : nestedRows;
                logicalRow.put(relationKey, nested);
            }
            result.add(Collections.unmodifiableMap(logicalRow));
        }
        return List.copyOf(result);
    }

    private Map<String, Object> normalizeRelatedRow(
            Map<String, Object> physicalRow,
            List<ResolvedField> fields) {
        Map<String, Object> logicalRow = new LinkedHashMap<>();
        for (ResolvedField field : fields) {
            logicalRow.put(
                    field.property().key(),
                    applyTransform(
                            physicalRow.get(field.mapping().physicalFieldKey()),
                            field.mapping().transform()));
        }
        return Collections.unmodifiableMap(logicalRow);
    }

    private Object normalizeJoinKey(Object value) {
        if (value instanceof Number) {
            return new BigDecimal(String.valueOf(value)).stripTrailingZeros();
        }
        return value;
    }

    private boolean isSingleValued(OntologyDocument.Cardinality cardinality) {
        return cardinality == OntologyDocument.Cardinality.ONE_TO_ONE
                || cardinality == OntologyDocument.Cardinality.MANY_TO_ONE;
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

    private QueryEvidence evidence(
            ResolvedPlan resolved,
            int totalCount,
            boolean moreAvailable) {
        List<MappingEvidence> mappings = new ArrayList<>();
        mappings.add(new MappingEvidence(
                resolved.query().concept(),
                resolved.conceptMapping().physicalObjectKey(),
                null,
                "ROOT_OBJECT"));
        resolved.selectedFields().forEach(field -> mappings.add(new MappingEvidence(
                field.logicalKey(),
                field.mapping().physicalObjectKey(),
                field.mapping().physicalFieldKey(),
                "SELECT")));
        resolved.filters().forEach(filter -> mappings.add(new MappingEvidence(
                filter.field().logicalKey(),
                filter.field().mapping().physicalObjectKey(),
                filter.field().mapping().physicalFieldKey(),
                "FILTER")));
        resolved.orders().forEach(order -> mappings.add(new MappingEvidence(
                order.field().logicalKey(),
                order.field().mapping().physicalObjectKey(),
                order.field().mapping().physicalFieldKey(),
                "ORDER")));
        resolved.relationPlans().forEach(plan -> {
            ResolvedRelation relation = plan.relation();
            mappings.add(new MappingEvidence(
                    relation.relation().key(),
                    relation.mapping().physicalObjectKey(),
                    relation.mapping().physicalFieldKey(),
                    "JOIN_SOURCE"));
            mappings.add(new MappingEvidence(
                    relation.targetConcept().key(),
                    relation.targetConceptMapping().physicalObjectKey(),
                    null,
                    "TARGET_OBJECT"));
            mappings.add(new MappingEvidence(
                    relation.relation().key(),
                    relation.targetConceptMapping().physicalObjectKey(),
                    relation.mapping().relationTargetFieldKey(),
                    "JOIN_TARGET"));
        });
        return new QueryEvidence(
                resolved.source().type().name(),
                resolved.source().key(),
                resolved.query().version(),
                List.copyOf(mappings),
                totalCount,
                moreAvailable);
    }

    private void saveAudit(
            QueryScope scope,
            SemanticQuery query,
            DataSourceConfig source,
            String userId,
            String status,
            int resultCount,
            long elapsedMs,
            QueryEvidence evidence,
            String errorCode) {
        OntologyQueryAuditEntity audit = new OntologyQueryAuditEntity(
                scope.workspace().getOrgId(),
                scope.workspace().getId(),
                scope.version().getId(),
                source == null ? null : source.id(),
                userId,
                query.concept(),
                writeJson(auditSummary(query)),
                resultCount,
                elapsedMs,
                status,
                evidence == null ? null : writeJson(evidence),
                errorCode);
        auditWriter.write(audit);
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

    private boolean supports(OntologyDataSourceAdapter adapter, DataSourceConfig source) {
        try {
            return adapter.supports(source);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private PhysicalResult executeRead(
            OntologyDataSourceAdapter adapter,
            AdapterContext context,
            DataSourceConfig source,
            PhysicalQuery query) {
        try {
            PhysicalResult result = adapter.executeRead(context, source, query);
            if (result == null) {
                throw new DataSourceUnavailableException();
            }
            return result;
        } catch (DataSourceUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DataSourceUnavailableException(exception);
        }
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
            List<RelationPlan> relations,
            List<FilterPlan> filters,
            List<OrderPlan> orderBy,
            int limit) {
    }

    public record FieldPlan(String logicalField, String physicalField) {
    }

    public record RelationPlan(
            String logicalRelation,
            String cardinality,
            String sourceObject,
            String sourceJoinField,
            String targetObject,
            String targetJoinField,
            List<FieldPlan> fields) {
    }

    public record FilterPlan(String logicalField, String operator) {
    }

    public record OrderPlan(String logicalField, String direction) {
    }

    public record MappingEvidence(
            String logicalField,
            String physicalObject,
            String physicalField,
            String usage) {
    }

    public record QueryEvidence(
            String sourceType,
            String dataSourceKey,
            int ontologyVersion,
            List<MappingEvidence> mappings,
            int totalCount,
            boolean moreAvailable) {
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
            ResolvedRelation relation) {
    }

    private record ResolvedRelation(
            OntologyDocument.Relation relation,
            OntologyDocument.Mapping mapping,
            OntologyDocument.Concept targetConcept,
            OntologyDocument.Mapping targetConceptMapping) {
    }

    private record ResolvedRelationPlan(
            ResolvedRelation relation,
            List<ResolvedField> fields,
            List<String> targetPhysicalFields) {
    }

    private record RelationRows(
            Map<Object, List<Map<String, Object>>> rowsByJoinKey) {
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
            List<ResolvedFilter> filters,
            List<ResolvedOrder> orders,
            PhysicalQuery physicalQuery,
            List<ResolvedRelationPlan> relationPlans,
            QueryPlan publicPlan) {
    }

    private record QueryScope(
            OntologyWorkspaceEntity workspace,
            OntologyVersionEntity version) {
    }
}
