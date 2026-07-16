package com.codehouse.ciciassistant.ontology.service;

import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class OntologyValidationService {

    private static final Pattern SAFE_KEY =
            Pattern.compile("^[a-z][a-z0-9]*(?:[-_][a-z0-9]+)*$");
    private static final Set<String> ALLOWED_TRANSFORMS = Set.of(
            "DIRECT",
            "TRIM",
            "LOWERCASE",
            "UPPERCASE",
            "NUMBER",
            "DATE",
            "DATETIME",
            "BOOLEAN_MAP",
            "ENUM_MAP",
            "REFERENCE");
    private static final Set<OntologyDocument.DataType> NUMERIC_TYPES = Set.of(
            OntologyDocument.DataType.INTEGER,
            OntologyDocument.DataType.DECIMAL);
    private static final Set<OntologyDocument.DataType> TIME_TYPES = Set.of(
            OntologyDocument.DataType.DATE,
            OntologyDocument.DataType.DATETIME);
    private static final Set<String> RESERVED_GRAPHQL_TYPES = Set.of(
            "String",
            "Int",
            "Float",
            "Boolean",
            "ID",
            "Query",
            "Mutation",
            "Subscription",
            "SemanticOperator",
            "SortDirection");

    public List<ValidationIssue> validate(OntologyDocument document, boolean forPublish) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (document == null) {
            add(issues, "DOCUMENT_REQUIRED", "$", "Ontology document is required");
            return List.copyOf(issues);
        }

        validateKey(document.key(), "$.key", issues);

        List<OntologyDocument.Concept> concepts = safe(document.concepts());
        List<OntologyDocument.Relation> relations = safe(document.relations());
        List<OntologyDocument.Metric> metrics = safe(document.metrics());
        List<OntologyDocument.Action> actions = safe(document.actions());
        List<OntologyDocument.DataSource> dataSources = safe(document.dataSources());
        List<OntologyDocument.Mapping> mappings = safe(document.mappings());

        detectDuplicates(
                concepts,
                OntologyDocument.Concept::key,
                "DUPLICATE_CONCEPT_KEY",
                "$.concepts",
                issues);
        detectDuplicates(
                relations,
                OntologyDocument.Relation::key,
                "DUPLICATE_RELATION_KEY",
                "$.relations",
                issues);
        detectDuplicates(
                metrics,
                OntologyDocument.Metric::key,
                "DUPLICATE_METRIC_KEY",
                "$.metrics",
                issues);
        detectDuplicates(
                actions,
                OntologyDocument.Action::key,
                "DUPLICATE_ACTION_KEY",
                "$.actions",
                issues);
        detectDuplicates(
                dataSources,
                OntologyDocument.DataSource::key,
                "DUPLICATE_DATA_SOURCE_KEY",
                "$.dataSources",
                issues);

        Map<String, OntologyDocument.Concept> conceptsByKey = firstByKey(
                concepts, OntologyDocument.Concept::key);
        Map<String, OntologyDocument.Relation> relationsByKey = firstByKey(
                relations, OntologyDocument.Relation::key);
        Map<String, OntologyDocument.Metric> metricsByKey = firstByKey(
                metrics, OntologyDocument.Metric::key);
        Map<String, OntologyDocument.Action> actionsByKey = firstByKey(
                actions, OntologyDocument.Action::key);

        validateConcepts(concepts, issues);
        validateRelations(relations, conceptsByKey, issues);
        validateMetrics(metrics, conceptsByKey, issues);
        validateActions(actions, conceptsByKey, issues);
        validateGraphqlContract(concepts, relations, metrics, issues);
        validateDataSources(dataSources, issues);
        validateMappings(
                mappings,
                dataSources,
                conceptsByKey,
                relationsByKey,
                metricsByKey,
                actionsByKey,
                issues);
        if (forPublish) {
            validatePublishMappings(concepts, relations, mappings, issues);
        }

        issues.sort(ValidationIssue.ORDERING);
        return List.copyOf(issues);
    }

    private void validateConcepts(
            List<OntologyDocument.Concept> concepts,
            List<ValidationIssue> issues) {
        for (int conceptIndex = 0; conceptIndex < concepts.size(); conceptIndex++) {
            OntologyDocument.Concept concept = concepts.get(conceptIndex);
            if (concept == null) {
                add(issues, "CONCEPT_REQUIRED", "$.concepts[" + conceptIndex + "]",
                        "Concept is required");
                continue;
            }
            String conceptPath = "$.concepts[" + conceptIndex + "]";
            validateKey(concept.key(), conceptPath + ".key", issues);
            List<OntologyDocument.Property> properties = safe(concept.properties());
            detectDuplicates(
                    properties,
                    OntologyDocument.Property::key,
                    "DUPLICATE_PROPERTY_KEY",
                    conceptPath + ".properties",
                    issues);
            Map<String, OntologyDocument.Property> propertiesByKey = firstByKey(
                    properties, OntologyDocument.Property::key);
            if (hasText(concept.displayPropertyKey())
                    && !propertiesByKey.containsKey(concept.displayPropertyKey())) {
                add(issues, "DISPLAY_PROPERTY_NOT_FOUND", conceptPath + ".displayPropertyKey",
                        "Display property must reference a property on the same concept");
            }
            for (int propertyIndex = 0; propertyIndex < properties.size(); propertyIndex++) {
                OntologyDocument.Property property = properties.get(propertyIndex);
                String propertyPath = conceptPath + ".properties[" + propertyIndex + "]";
                if (property == null) {
                    add(issues, "PROPERTY_REQUIRED", propertyPath, "Property is required");
                    continue;
                }
                validateKey(property.key(), propertyPath + ".key", issues);
                if (property.sensitive() && property.queryable()) {
                    add(issues, "SENSITIVE_PROPERTY_QUERYABLE", propertyPath + ".queryable",
                            "Sensitive properties cannot be queryable");
                }
                if (property.dataType() == OntologyDocument.DataType.ENUM) {
                    validateEnumValues(property.enumValues(), propertyPath + ".enumValues", issues);
                }
            }
        }
    }

    private void validateEnumValues(
            List<String> enumValues,
            String path,
            List<ValidationIssue> issues) {
        List<String> values = safe(enumValues);
        if (values.isEmpty()) {
            add(issues, "ENUM_VALUES_REQUIRED", path,
                    "Enum properties require at least one value");
            return;
        }
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < values.size(); index++) {
            String value = values.get(index);
            if (!hasText(value)) {
                add(issues, "ENUM_VALUE_BLANK", path + "[" + index + "]",
                        "Enum values cannot be blank");
            } else if (!seen.add(value.trim())) {
                add(issues, "DUPLICATE_ENUM_VALUE", path + "[" + index + "]",
                        "Enum values must be unique after trimming");
            }
        }
    }

    private void validateRelations(
            List<OntologyDocument.Relation> relations,
            Map<String, OntologyDocument.Concept> conceptsByKey,
            List<ValidationIssue> issues) {
        for (int index = 0; index < relations.size(); index++) {
            OntologyDocument.Relation relation = relations.get(index);
            String path = "$.relations[" + index + "]";
            if (relation == null) {
                add(issues, "RELATION_REQUIRED", path, "Relation is required");
                continue;
            }
            validateKey(relation.key(), path + ".key", issues);
            if (!conceptsByKey.containsKey(relation.sourceConceptKey())) {
                add(issues, "RELATION_SOURCE_NOT_FOUND", path + ".sourceConceptKey",
                        "Relation source concept does not exist");
            } else if (relation.enabled() && relation.queryable()
                    && !isEnabledAndQueryable(conceptsByKey.get(relation.sourceConceptKey()))) {
                add(issues, "RELATION_SOURCE_NOT_QUERYABLE", path + ".sourceConceptKey",
                        "Queryable relation sources must be enabled and queryable");
            }
            if (!conceptsByKey.containsKey(relation.targetConceptKey())) {
                add(issues, "RELATION_TARGET_NOT_FOUND", path + ".targetConceptKey",
                        "Relation target concept does not exist");
            } else if (relation.enabled() && relation.queryable()
                    && !isEnabledAndQueryable(conceptsByKey.get(relation.targetConceptKey()))) {
                add(issues, "RELATION_TARGET_NOT_QUERYABLE", path + ".targetConceptKey",
                        "Queryable relation targets must be enabled and queryable");
            }
        }
    }

    private void validateMetrics(
            List<OntologyDocument.Metric> metrics,
            Map<String, OntologyDocument.Concept> conceptsByKey,
            List<ValidationIssue> issues) {
        for (int index = 0; index < metrics.size(); index++) {
            OntologyDocument.Metric metric = metrics.get(index);
            String path = "$.metrics[" + index + "]";
            if (metric == null) {
                add(issues, "METRIC_REQUIRED", path, "Metric is required");
                continue;
            }
            validateKey(metric.key(), path + ".key", issues);
            OntologyDocument.Concept concept = conceptsByKey.get(metric.conceptKey());
            if (concept == null) {
                add(issues, "METRIC_CONCEPT_NOT_FOUND", path + ".conceptKey",
                        "Metric concept does not exist");
                continue;
            }
            if (!concept.enabled() || !concept.queryable()) {
                add(issues, "METRIC_CONCEPT_NOT_QUERYABLE", path + ".conceptKey",
                        "Metric concepts must be enabled and queryable");
            }
            Map<String, OntologyDocument.Property> properties = firstByKey(
                    safe(concept.properties()), OntologyDocument.Property::key);
            OntologyDocument.Property measure = properties.get(metric.measurePropertyKey());
            if (metric.aggregation() != OntologyDocument.Aggregation.COUNT
                    && !hasText(metric.measurePropertyKey())) {
                add(issues, "METRIC_MEASURE_REQUIRED", path + ".measurePropertyKey",
                        "This aggregation requires a measure property");
            } else if (hasText(metric.measurePropertyKey()) && measure == null) {
                add(issues, "METRIC_MEASURE_NOT_FOUND", path + ".measurePropertyKey",
                        "Metric measure property does not exist");
            } else if (measure != null
                    && (metric.aggregation() == OntologyDocument.Aggregation.SUM
                    || metric.aggregation() == OntologyDocument.Aggregation.AVG)
                    && !NUMERIC_TYPES.contains(measure.dataType())) {
                add(issues, "METRIC_MEASURE_TYPE_INVALID", path + ".measurePropertyKey",
                        "SUM and AVG require a numeric measure property");
            }
            validateMetricPropertySafety(
                    measure, path + ".measurePropertyKey", issues);
            List<String> groupByKeys = safe(metric.groupByPropertyKeys());
            for (int groupIndex = 0; groupIndex < groupByKeys.size(); groupIndex++) {
                OntologyDocument.Property groupProperty = properties.get(groupByKeys.get(groupIndex));
                if (groupProperty == null) {
                    add(issues, "METRIC_GROUP_BY_NOT_FOUND",
                            path + ".groupByPropertyKeys[" + groupIndex + "]",
                            "Metric group-by property does not exist");
                }
                validateMetricPropertySafety(groupProperty,
                        path + ".groupByPropertyKeys[" + groupIndex + "]", issues);
            }
            if (hasText(metric.timePropertyKey())) {
                OntologyDocument.Property timeProperty = properties.get(metric.timePropertyKey());
                if (timeProperty == null || !TIME_TYPES.contains(timeProperty.dataType())) {
                    add(issues, "METRIC_TIME_PROPERTY_INVALID", path + ".timePropertyKey",
                            "Metric time property must exist and be DATE or DATETIME");
                }
                validateMetricPropertySafety(
                        timeProperty, path + ".timePropertyKey", issues);
            }
            List<OntologyDocument.QueryFilter> filters = safe(metric.filters());
            for (int filterIndex = 0; filterIndex < filters.size(); filterIndex++) {
                OntologyDocument.QueryFilter filter = filters.get(filterIndex);
                if (filter == null || !properties.containsKey(filter.property())) {
                    add(issues, "METRIC_FILTER_PROPERTY_NOT_FOUND",
                            path + ".filters[" + filterIndex + "].property",
                            "Metric filter property does not exist");
                } else {
                    validateMetricPropertySafety(
                            properties.get(filter.property()),
                            path + ".filters[" + filterIndex + "].property",
                            issues);
                }
            }
        }
    }

    private void validateMetricPropertySafety(
            OntologyDocument.Property property,
            String path,
            List<ValidationIssue> issues) {
        if (property != null && (property.sensitive() || !property.queryable())) {
            add(issues, "METRIC_PROPERTY_NOT_QUERYABLE", path,
                    "Metric properties must be non-sensitive and queryable");
        }
    }

    private void validateActions(
            List<OntologyDocument.Action> actions,
            Map<String, OntologyDocument.Concept> conceptsByKey,
            List<ValidationIssue> issues) {
        for (int index = 0; index < actions.size(); index++) {
            OntologyDocument.Action action = actions.get(index);
            String path = "$.actions[" + index + "]";
            if (action == null) {
                add(issues, "ACTION_REQUIRED", path, "Action is required");
                continue;
            }
            validateKey(action.key(), path + ".key", issues);
            if (!conceptsByKey.containsKey(action.conceptKey())) {
                add(issues, "ACTION_CONCEPT_NOT_FOUND", path + ".conceptKey",
                        "Action concept does not exist");
            }
            List<OntologyDocument.ActionParameter> parameters = safe(action.parameters());
            detectDuplicates(
                    parameters,
                    OntologyDocument.ActionParameter::key,
                    "DUPLICATE_ACTION_PARAMETER_KEY",
                    path + ".parameters",
                    issues);
            for (int parameterIndex = 0; parameterIndex < parameters.size(); parameterIndex++) {
                OntologyDocument.ActionParameter parameter = parameters.get(parameterIndex);
                if (parameter == null) {
                    add(issues, "ACTION_PARAMETER_REQUIRED",
                            path + ".parameters[" + parameterIndex + "]",
                            "Action parameter is required");
                } else {
                    validateKey(parameter.key(),
                            path + ".parameters[" + parameterIndex + "].key", issues);
                }
            }
        }
    }

    private void validateDataSources(
            List<OntologyDocument.DataSource> dataSources,
            List<ValidationIssue> issues) {
        Set<Long> ids = new HashSet<>();
        for (int index = 0; index < dataSources.size(); index++) {
            OntologyDocument.DataSource source = dataSources.get(index);
            String path = "$.dataSources[" + index + "]";
            if (source == null) {
                add(issues, "DATA_SOURCE_REQUIRED", path, "Data source is required");
                continue;
            }
            validateKey(source.key(), path + ".key", issues);
            if (source.id() != null && !ids.add(source.id())) {
                add(issues, "DUPLICATE_DATA_SOURCE_ID", path + ".id",
                        "Data source id must be unique in the document");
            }
        }
    }

    private void validateGraphqlContract(
            List<OntologyDocument.Concept> concepts,
            List<OntologyDocument.Relation> relations,
            List<OntologyDocument.Metric> metrics,
            List<ValidationIssue> issues) {
        Map<String, String> typeOwners = new HashMap<>();
        for (String reservedType : RESERVED_GRAPHQL_TYPES) {
            typeOwners.put(reservedType, "$reserved");
        }
        Map<String, OntologyDocument.Concept> conceptsByKey = firstByKey(
                concepts, OntologyDocument.Concept::key);
        Map<String, List<OntologyDocument.Relation>> outgoingRelations = new HashMap<>();
        for (int relationIndex = 0; relationIndex < relations.size(); relationIndex++) {
            OntologyDocument.Relation relation = relations.get(relationIndex);
            if (relation != null) {
                rejectGraphqlReservedPrefix(
                        relation.key(), "$.relations[" + relationIndex + "].key", issues);
            }
            if (isCompilerQueryableRelation(relation, conceptsByKey)) {
                outgoingRelations.computeIfAbsent(
                        relation.sourceConceptKey(), ignored -> new ArrayList<>()).add(relation);
            }
        }

        Set<String> queryFields = new HashSet<>();
        for (int conceptIndex = 0; conceptIndex < concepts.size(); conceptIndex++) {
            OntologyDocument.Concept concept = concepts.get(conceptIndex);
            if (concept == null) {
                continue;
            }
            String conceptPath = "$.concepts[" + conceptIndex + "]";
            rejectGraphqlReservedPrefix(concept.key(), conceptPath + ".key", issues);
            String typeName = graphqlTypeName(concept.key());
            registerGraphqlType(
                    typeOwners, typeName, concept.key(), conceptPath + ".key", issues);
            if (isEnabledAndQueryable(concept)) {
                registerGraphqlType(
                        typeOwners,
                        typeName + "Filter",
                        concept.key() + "#filter",
                        conceptPath + ".key",
                        issues);
                registerGraphqlType(
                        typeOwners,
                        typeName + "Order",
                        concept.key() + "#order",
                        conceptPath + ".key",
                        issues);
            }

            Set<String> objectFields = new HashSet<>();
            for (int propertyIndex = 0;
                    propertyIndex < safe(concept.properties()).size();
                    propertyIndex++) {
                OntologyDocument.Property property = safe(concept.properties()).get(propertyIndex);
                if (property != null) {
                    rejectGraphqlReservedPrefix(
                            property.key(),
                            conceptPath + ".properties[" + propertyIndex + "].key",
                            issues);
                }
                if (property != null && property.queryable() && !property.sensitive()) {
                    addGraphqlName(
                            objectFields,
                            graphqlFieldName(property.key()),
                            conceptPath + ".properties[" + propertyIndex + "].key",
                            issues);
                }
            }
            List<OntologyDocument.Relation> outgoing = outgoingRelations.getOrDefault(
                    concept.key(), List.of());
            for (OntologyDocument.Relation relation : outgoing) {
                addGraphqlName(
                        objectFields,
                        graphqlFieldName(relation.key()),
                        conceptPath + ".relations[" + relation.key() + "]",
                        issues);
            }
            if (objectFields.isEmpty()) {
                add(issues, "GRAPHQL_OBJECT_EMPTY", conceptPath,
                        "Compiled GraphQL object types require at least one queryable field");
            }

            if (concept.enabled() && concept.queryable()) {
                String fieldName = graphqlFieldName(concept.key());
                addGraphqlName(queryFields, fieldName, conceptPath + ".key", issues);
                addGraphqlName(queryFields, fieldName + "List", conceptPath + ".key", issues);
            }
        }
        for (int metricIndex = 0; metricIndex < metrics.size(); metricIndex++) {
            OntologyDocument.Metric metric = metrics.get(metricIndex);
            if (metric != null) {
                rejectGraphqlReservedPrefix(
                        metric.key(), "$.metrics[" + metricIndex + "].key", issues);
                addGraphqlName(
                        queryFields,
                        graphqlFieldName(metric.key()),
                        "$.metrics[" + metricIndex + "].key",
                        issues);
            }
        }
        if (queryFields.isEmpty()) {
            add(issues, "GRAPHQL_QUERY_EMPTY", "$",
                    "Compiled GraphQL Query requires at least one field");
        }
    }

    private void registerGraphqlType(
            Map<String, String> typeOwners,
            String typeName,
            String owner,
            String path,
            List<ValidationIssue> issues) {
        String previousOwner = typeOwners.putIfAbsent(typeName, owner);
        if (previousOwner != null && !Objects.equals(previousOwner, owner)) {
            add(issues, "GRAPHQL_NAME_COLLISION", path,
                    "Ontology keys compile to a reserved or duplicate GraphQL type: " + typeName);
        }
    }

    private void rejectGraphqlReservedPrefix(
            String rawName,
            String path,
            List<ValidationIssue> issues) {
        if (rawName != null && rawName.startsWith("__")) {
            add(issues, "GRAPHQL_NAME_COLLISION", path,
                    "GraphQL names beginning with __ are reserved for introspection");
        }
    }

    private void addGraphqlName(
            Set<String> names,
            String name,
            String path,
            List<ValidationIssue> issues) {
        if (!names.add(name)) {
            add(issues, "GRAPHQL_NAME_COLLISION", path,
                    "Multiple ontology keys compile to the same GraphQL name: " + name);
        }
    }

    private String graphqlTypeName(String key) {
        String fieldName = graphqlFieldName(key);
        if (fieldName.isEmpty()) {
            return "OntologyType";
        }
        return Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }

    private String graphqlFieldName(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String[] parts = key.toLowerCase(Locale.ROOT).split("[-_]+");
        StringBuilder result = new StringBuilder(parts[0]);
        for (int index = 1; index < parts.length; index++) {
            if (!parts[index].isEmpty()) {
                result.append(Character.toUpperCase(parts[index].charAt(0)))
                        .append(parts[index].substring(1));
            }
        }
        return result.toString();
    }

    private void validateMappings(
            List<OntologyDocument.Mapping> mappings,
            List<OntologyDocument.DataSource> dataSources,
            Map<String, OntologyDocument.Concept> conceptsByKey,
            Map<String, OntologyDocument.Relation> relationsByKey,
            Map<String, OntologyDocument.Metric> metricsByKey,
            Map<String, OntologyDocument.Action> actionsByKey,
            List<ValidationIssue> issues) {
        Set<Long> sourceIds = new HashSet<>();
        for (OntologyDocument.DataSource source : dataSources) {
            if (source != null && source.id() != null) {
                sourceIds.add(source.id());
            }
        }
        Set<String> uniqueMappings = new HashSet<>();
        for (int index = 0; index < mappings.size(); index++) {
            OntologyDocument.Mapping mapping = mappings.get(index);
            String path = "$.mappings[" + index + "]";
            if (mapping == null) {
                add(issues, "MAPPING_REQUIRED", path, "Mapping is required");
                continue;
            }
            String targetType = normalized(mapping.targetType());
            String uniqueKey = targetType + "\u0000" + mapping.targetKey()
                    + "\u0000" + mapping.dataSourceId();
            if (!uniqueMappings.add(uniqueKey)) {
                add(issues, "DUPLICATE_MAPPING", path,
                        "Target and data source mapping must be unique");
            }
            if (!mappingTargetExists(
                    targetType,
                    mapping.targetKey(),
                    conceptsByKey,
                    relationsByKey,
                    metricsByKey,
                    actionsByKey)) {
                add(issues, "MAPPING_TARGET_NOT_FOUND", path + ".targetKey",
                        "Mapping target does not exist");
            }
            if (mapping.dataSourceId() == null || !sourceIds.contains(mapping.dataSourceId())) {
                add(issues, "MAPPING_DATA_SOURCE_NOT_FOUND", path + ".dataSourceId",
                        "Mapping data source does not exist in the document");
            }
            if (!hasText(mapping.physicalObjectKey())) {
                add(issues, "MAPPING_PHYSICAL_OBJECT_REQUIRED", path + ".physicalObjectKey",
                        "Mapping physical object is required");
            }
            if (("PROPERTY".equals(targetType) || "RELATION".equals(targetType))
                    && !hasText(mapping.physicalFieldKey())) {
                add(issues, "MAPPING_PHYSICAL_FIELD_REQUIRED", path + ".physicalFieldKey",
                        "Property and relation mappings require a physical field");
            }
            if ("RELATION".equals(targetType)
                    && !hasText(mapping.relationTargetFieldKey())) {
                add(issues, "MAPPING_RELATION_TARGET_FIELD_REQUIRED",
                        path + ".relationTargetFieldKey",
                        "Relation mappings require a target-side physical field");
            }
            if (hasText(mapping.transform())
                    && !ALLOWED_TRANSFORMS.contains(normalized(mapping.transform()))) {
                add(issues, "MAPPING_TRANSFORM_NOT_ALLOWED", path + ".transform",
                        "Mapping transform is not in the deterministic allowlist");
            }
            if (!Double.isFinite(mapping.confidence())
                    || mapping.confidence() < 0
                    || mapping.confidence() > 1) {
                add(issues, "MAPPING_CONFIDENCE_INVALID", path + ".confidence",
                        "Mapping confidence must be between 0 and 1");
            }
        }
    }

    private void validatePublishMappings(
            List<OntologyDocument.Concept> concepts,
            List<OntologyDocument.Relation> relations,
            List<OntologyDocument.Mapping> mappings,
            List<ValidationIssue> issues) {
        Set<String> validTargets = new HashSet<>();
        for (OntologyDocument.Mapping mapping : mappings) {
            if (mapping != null && "VALID".equals(normalized(mapping.validationStatus()))) {
                validTargets.add(normalized(mapping.targetType()) + ":" + mapping.targetKey());
            }
        }
        for (OntologyDocument.Concept concept : concepts) {
            if (concept == null || !concept.enabled() || !concept.queryable()) {
                continue;
            }
            requirePublishMapping(validTargets, "CONCEPT", concept.key(),
                    "$.concepts[" + concept.key() + "]", issues);
            for (OntologyDocument.Property property : safe(concept.properties())) {
                if (property != null && property.queryable() && !property.sensitive()) {
                    requirePublishMapping(validTargets, "PROPERTY",
                            concept.key() + "." + property.key(),
                            "$.concepts[" + concept.key() + "].properties[" + property.key() + "]",
                            issues);
                }
            }
        }
        for (OntologyDocument.Relation relation : relations) {
            if (relation != null && relation.enabled() && relation.queryable()) {
                requirePublishMapping(validTargets, "RELATION", relation.key(),
                        "$.relations[" + relation.key() + "]", issues);
            }
        }
    }

    private void requirePublishMapping(
            Set<String> validTargets,
            String targetType,
            String targetKey,
            String path,
            List<ValidationIssue> issues) {
        if (!validTargets.contains(targetType + ":" + targetKey)) {
            add(issues, "QUERYABLE_MAPPING_REQUIRED", path,
                    "Queryable targets require a VALID mapping before publish");
        }
    }

    private boolean mappingTargetExists(
            String targetType,
            String targetKey,
            Map<String, OntologyDocument.Concept> conceptsByKey,
            Map<String, OntologyDocument.Relation> relationsByKey,
            Map<String, OntologyDocument.Metric> metricsByKey,
            Map<String, OntologyDocument.Action> actionsByKey) {
        return switch (targetType) {
            case "CONCEPT" -> conceptsByKey.containsKey(targetKey);
            case "PROPERTY" -> propertyTargetExists(targetKey, conceptsByKey);
            case "RELATION" -> relationsByKey.containsKey(targetKey);
            case "METRIC" -> metricsByKey.containsKey(targetKey);
            case "ACTION" -> actionsByKey.containsKey(targetKey);
            default -> false;
        };
    }

    private boolean propertyTargetExists(
            String targetKey,
            Map<String, OntologyDocument.Concept> conceptsByKey) {
        if (!hasText(targetKey)) {
            return false;
        }
        int separator = targetKey.lastIndexOf('.');
        if (separator <= 0 || separator == targetKey.length() - 1) {
            return false;
        }
        OntologyDocument.Concept concept = conceptsByKey.get(targetKey.substring(0, separator));
        if (concept == null) {
            return false;
        }
        String propertyKey = targetKey.substring(separator + 1);
        return safe(concept.properties()).stream()
                .filter(java.util.Objects::nonNull)
                .anyMatch(property -> propertyKey.equals(property.key()));
    }

    private boolean isCompilerQueryableRelation(
            OntologyDocument.Relation relation,
            Map<String, OntologyDocument.Concept> conceptsByKey) {
        return relation != null
                && relation.enabled()
                && relation.queryable()
                && isEnabledAndQueryable(conceptsByKey.get(relation.sourceConceptKey()))
                && isEnabledAndQueryable(conceptsByKey.get(relation.targetConceptKey()));
    }

    private boolean isEnabledAndQueryable(OntologyDocument.Concept concept) {
        return concept != null && concept.enabled() && concept.queryable();
    }

    private void validateKey(String key, String path, List<ValidationIssue> issues) {
        if (!hasText(key) || !SAFE_KEY.matcher(key).matches()) {
            add(issues, "INVALID_KEY", path,
                    "Keys must use lower kebab-case or snake_case safe characters");
        }
    }

    private <T> void detectDuplicates(
            List<T> values,
            Function<T, String> keyExtractor,
            String code,
            String path,
            List<ValidationIssue> issues) {
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < values.size(); index++) {
            T value = values.get(index);
            if (value == null) {
                continue;
            }
            String key = keyExtractor.apply(value);
            if (hasText(key) && !seen.add(key)) {
                add(issues, code, path + "[" + index + "].key", "Duplicate key: " + key);
            }
        }
    }

    private <T> Map<String, T> firstByKey(
            List<T> values,
            Function<T, String> keyExtractor) {
        Map<String, T> result = new HashMap<>();
        for (T value : values) {
            if (value != null && keyExtractor.apply(value) != null) {
                result.putIfAbsent(keyExtractor.apply(value), value);
            }
        }
        return result;
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static void add(
            List<ValidationIssue> issues,
            String code,
            String path,
            String message) {
        issues.add(new ValidationIssue(code, Severity.ERROR, path, message));
    }

    public enum Severity {
        ERROR,
        WARNING
    }

    public record ValidationIssue(
            String code,
            Severity severity,
            String path,
            String message) {

        public static final Comparator<ValidationIssue> ORDERING = Comparator
                .comparing(ValidationIssue::path, Comparator.nullsFirst(String::compareTo))
                .thenComparing(ValidationIssue::code, Comparator.nullsFirst(String::compareTo))
                .thenComparing(ValidationIssue::severity, Comparator.nullsFirst(Enum::compareTo))
                .thenComparing(ValidationIssue::message, Comparator.nullsFirst(String::compareTo));
    }
}
