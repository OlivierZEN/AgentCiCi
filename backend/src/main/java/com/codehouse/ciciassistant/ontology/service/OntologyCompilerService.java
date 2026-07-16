package com.codehouse.ciciassistant.ontology.service;

import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class OntologyCompilerService {

    private static final Comparator<String> NULL_SAFE_TEXT =
            Comparator.nullsFirst(String::compareTo);

    private final ObjectMapper objectMapper;

    public OntologyCompilerService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public CompiledContracts compile(OntologyDocument document, int version) {
        if (document == null) {
            throw new IllegalArgumentException("Ontology document is required");
        }
        if (version < 1) {
            throw new IllegalArgumentException("Ontology version must be positive");
        }

        OntologyDocument canonicalDocument = canonicalize(document);
        String snapshotJson = serialize(canonicalDocument);
        String jsonSchema = compileJsonSchema(canonicalDocument, version);
        String graphqlSdl = compileGraphqlSdl(canonicalDocument);
        String queryContractJson = compileQueryContract(canonicalDocument, version);
        String contentHash = sha256(snapshotJson + "\nversion=" + version);
        return new CompiledContracts(
                contentHash,
                snapshotJson,
                jsonSchema,
                graphqlSdl,
                queryContractJson);
    }

    private OntologyDocument canonicalize(OntologyDocument document) {
        List<OntologyDocument.Concept> concepts = safe(document.concepts()).stream()
                .filter(java.util.Objects::nonNull)
                .map(this::canonicalizeConcept)
                .sorted(Comparator.comparing(OntologyDocument.Concept::key, NULL_SAFE_TEXT))
                .toList();
        List<OntologyDocument.Relation> relations = safe(document.relations()).stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(OntologyDocument.Relation::key, NULL_SAFE_TEXT))
                .toList();
        List<OntologyDocument.Metric> metrics = safe(document.metrics()).stream()
                .filter(java.util.Objects::nonNull)
                .map(this::canonicalizeMetric)
                .sorted(Comparator.comparing(OntologyDocument.Metric::key, NULL_SAFE_TEXT))
                .toList();
        List<OntologyDocument.Action> actions = safe(document.actions()).stream()
                .filter(java.util.Objects::nonNull)
                .map(this::canonicalizeAction)
                .sorted(Comparator.comparing(OntologyDocument.Action::key, NULL_SAFE_TEXT))
                .toList();
        List<OntologyDocument.DataSource> dataSources = safe(document.dataSources()).stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator
                        .comparing(OntologyDocument.DataSource::key, NULL_SAFE_TEXT)
                        .thenComparing(OntologyDocument.DataSource::id,
                                Comparator.nullsFirst(Long::compareTo)))
                .toList();
        List<OntologyDocument.Mapping> mappings = safe(document.mappings()).stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator
                        .comparing(OntologyDocument.Mapping::targetType, NULL_SAFE_TEXT)
                        .thenComparing(OntologyDocument.Mapping::targetKey, NULL_SAFE_TEXT)
                        .thenComparing(OntologyDocument.Mapping::dataSourceId,
                                Comparator.nullsFirst(Long::compareTo))
                        .thenComparing(OntologyDocument.Mapping::physicalObjectKey, NULL_SAFE_TEXT)
                        .thenComparing(OntologyDocument.Mapping::physicalFieldKey, NULL_SAFE_TEXT))
                .toList();
        return new OntologyDocument(
                document.key(),
                document.name(),
                document.description(),
                concepts,
                relations,
                metrics,
                actions,
                dataSources,
                mappings);
    }

    private OntologyDocument.Concept canonicalizeConcept(OntologyDocument.Concept concept) {
        List<OntologyDocument.Property> properties = safe(concept.properties()).stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(OntologyDocument.Property::key, NULL_SAFE_TEXT))
                .toList();
        return new OntologyDocument.Concept(
                concept.key(),
                concept.name(),
                concept.pluralName(),
                concept.description(),
                concept.conceptType(),
                concept.displayPropertyKey(),
                concept.positionX(),
                concept.positionY(),
                concept.queryable(),
                concept.enabled(),
                properties);
    }

    private OntologyDocument.Metric canonicalizeMetric(OntologyDocument.Metric metric) {
        List<String> groupBy = safe(metric.groupByPropertyKeys()).stream()
                .sorted(NULL_SAFE_TEXT)
                .toList();
        List<OntologyDocument.QueryFilter> filters = safe(metric.filters()).stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator
                        .comparing(OntologyDocument.QueryFilter::property, NULL_SAFE_TEXT)
                        .thenComparing(filter -> filter.operator() == null
                                ? "" : filter.operator().name())
                        .thenComparing(filter -> serialize(filter.value())))
                .toList();
        return new OntologyDocument.Metric(
                metric.key(),
                metric.name(),
                metric.conceptKey(),
                metric.aggregation(),
                metric.measurePropertyKey(),
                groupBy,
                metric.timePropertyKey(),
                filters);
    }

    private OntologyDocument.Action canonicalizeAction(OntologyDocument.Action action) {
        List<OntologyDocument.ActionParameter> parameters = safe(action.parameters()).stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(OntologyDocument.ActionParameter::key, NULL_SAFE_TEXT))
                .toList();
        return new OntologyDocument.Action(
                action.key(),
                action.name(),
                action.conceptKey(),
                action.description(),
                parameters);
    }

    private String compileJsonSchema(OntologyDocument document, int version) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.put("$id", "urn:agentcici:ontology:" + document.key() + ":v" + version);
        schema.put("title", document.name());
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        Map<String, Object> rootProperties = new LinkedHashMap<>();
        Map<String, Object> definitions = new LinkedHashMap<>();
        for (OntologyDocument.Concept concept : document.concepts()) {
            String definitionName = graphqlTypeName(concept.key());
            rootProperties.put(concept.key(), Map.of(
                    "type", "array",
                    "items", Map.of("$ref", "#/$defs/" + definitionName)));
            definitions.put(definitionName, conceptSchema(concept));
        }
        schema.put("properties", rootProperties);
        schema.put("$defs", definitions);
        return serialize(schema);
    }

    private Map<String, Object> conceptSchema(OntologyDocument.Concept concept) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("title", concept.name());
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (OntologyDocument.Property property : safe(concept.properties())) {
            properties.put(property.key(), propertySchema(property));
            if (property.required()) {
                required.add(property.key());
            }
        }
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    private Map<String, Object> propertySchema(OntologyDocument.Property property) {
        Map<String, Object> scalar = new LinkedHashMap<>();
        switch (property.dataType()) {
            case INTEGER -> scalar.put("type", "integer");
            case DECIMAL -> scalar.put("type", "number");
            case BOOLEAN -> scalar.put("type", "boolean");
            case DATE -> {
                scalar.put("type", "string");
                scalar.put("format", "date");
            }
            case DATETIME -> {
                scalar.put("type", "string");
                scalar.put("format", "date-time");
            }
            case ENUM -> {
                scalar.put("type", "string");
                scalar.put("enum", safe(property.enumValues()));
            }
            default -> scalar.put("type", "string");
        }
        if (!property.multiple()) {
            return scalar;
        }
        Map<String, Object> array = new LinkedHashMap<>();
        array.put("type", "array");
        array.put("items", scalar);
        return array;
    }

    private String compileGraphqlSdl(OntologyDocument document) {
        StringBuilder sdl = new StringBuilder();
        Map<String, List<OntologyDocument.Relation>> outgoingRelations = new LinkedHashMap<>();
        for (OntologyDocument.Relation relation : safe(document.relations())) {
            if (relation.enabled() && relation.queryable()) {
                outgoingRelations.computeIfAbsent(relation.sourceConceptKey(), ignored -> new ArrayList<>())
                        .add(relation);
            }
        }

        for (OntologyDocument.Concept concept : safe(document.concepts())) {
            sdl.append("type ").append(graphqlTypeName(concept.key())).append(" {\n");
            for (OntologyDocument.Property property : safe(concept.properties())) {
                if (property.queryable() && !property.sensitive()) {
                    sdl.append("  ")
                            .append(graphqlFieldName(property.key()))
                            .append(": ")
                            .append(graphqlPropertyType(property))
                            .append("\n");
                }
            }
            for (OntologyDocument.Relation relation : outgoingRelations.getOrDefault(
                    concept.key(), List.of())) {
                sdl.append("  ")
                        .append(graphqlFieldName(relation.key()))
                        .append(": ")
                        .append(graphqlRelationType(relation))
                        .append("\n");
            }
            sdl.append("}\n\n");
        }

        sdl.append("type Query {\n");
        for (OntologyDocument.Concept concept : safe(document.concepts())) {
            if (concept.enabled() && concept.queryable()) {
                String fieldName = graphqlFieldName(concept.key());
                String typeName = graphqlTypeName(concept.key());
                sdl.append("  ").append(fieldName).append("(id: ID!): ")
                        .append(typeName).append("\n");
                sdl.append("  ").append(fieldName).append("List(limit: Int = 50): [")
                        .append(typeName).append("!]!\n");
            }
        }
        for (OntologyDocument.Metric metric : safe(document.metrics())) {
            sdl.append("  ").append(graphqlFieldName(metric.key()))
                    .append(": Float\n");
        }
        sdl.append("}\n");
        return sdl.toString();
    }

    private String compileQueryContract(OntologyDocument document, int version) {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("contract", "READ_ONLY_SEMANTIC_QUERY");
        contract.put("documentKey", document.key());
        contract.put("version", version);

        List<Map<String, Object>> concepts = new ArrayList<>();
        for (OntologyDocument.Concept concept : safe(document.concepts())) {
            if (!concept.enabled() || !concept.queryable()) {
                continue;
            }
            Map<String, Object> conceptContract = new LinkedHashMap<>();
            conceptContract.put("key", concept.key());
            conceptContract.put("type", graphqlTypeName(concept.key()));
            conceptContract.put("displayPropertyKey", concept.displayPropertyKey());
            List<Map<String, Object>> fields = new ArrayList<>();
            for (OntologyDocument.Property property : safe(concept.properties())) {
                if (property.queryable() && !property.sensitive()) {
                    Map<String, Object> field = new LinkedHashMap<>();
                    field.put("key", property.key());
                    field.put("dataType", property.dataType().name());
                    field.put("multiple", property.multiple());
                    field.put("operators", allowedOperators(property.dataType()));
                    fields.add(field);
                }
            }
            conceptContract.put("fields", fields);
            concepts.add(conceptContract);
        }
        contract.put("concepts", concepts);

        List<Map<String, Object>> relations = new ArrayList<>();
        for (OntologyDocument.Relation relation : safe(document.relations())) {
            if (relation.enabled() && relation.queryable()) {
                Map<String, Object> relationContract = new LinkedHashMap<>();
                relationContract.put("key", relation.key());
                relationContract.put("sourceConceptKey", relation.sourceConceptKey());
                relationContract.put("targetConceptKey", relation.targetConceptKey());
                relationContract.put("cardinality", relation.cardinality().name());
                relations.add(relationContract);
            }
        }
        contract.put("relations", relations);

        List<Map<String, Object>> metrics = new ArrayList<>();
        for (OntologyDocument.Metric metric : safe(document.metrics())) {
            Map<String, Object> metricContract = new LinkedHashMap<>();
            metricContract.put("key", metric.key());
            metricContract.put("conceptKey", metric.conceptKey());
            metricContract.put("aggregation", metric.aggregation().name());
            metricContract.put("measurePropertyKey", metric.measurePropertyKey());
            metricContract.put("groupByPropertyKeys", safe(metric.groupByPropertyKeys()));
            metricContract.put("timePropertyKey", metric.timePropertyKey());
            metricContract.put("filters", safe(metric.filters()));
            metrics.add(metricContract);
        }
        contract.put("metrics", metrics);
        contract.put("writeOperations", List.of());
        return serialize(contract);
    }

    private List<String> allowedOperators(OntologyDocument.DataType dataType) {
        if (dataType == null) {
            return List.of();
        }
        return switch (dataType) {
            case TEXT, LONG_TEXT -> List.of("EQ", "NE", "IN", "CONTAINS", "IS_NULL");
            case INTEGER, DECIMAL, DATE, DATETIME ->
                    List.of("EQ", "NE", "IN", "GT", "GTE", "LT", "LTE", "BETWEEN", "IS_NULL");
            case BOOLEAN, ENUM, REFERENCE -> List.of("EQ", "NE", "IN", "IS_NULL");
        };
    }

    private String graphqlPropertyType(OntologyDocument.Property property) {
        String scalar = switch (property.dataType()) {
            case INTEGER -> "Int";
            case DECIMAL -> "Float";
            case BOOLEAN -> "Boolean";
            default -> "String";
        };
        String type = property.multiple() ? "[" + scalar + "!]" : scalar;
        return property.required() ? type + "!" : type;
    }

    private String graphqlRelationType(OntologyDocument.Relation relation) {
        String target = graphqlTypeName(relation.targetConceptKey());
        return switch (relation.cardinality()) {
            case ONE_TO_MANY, MANY_TO_MANY -> "[" + target + "!]!";
            case ONE_TO_ONE, MANY_TO_ONE -> target;
        };
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

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize ontology contract", exception);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    public record CompiledContracts(
            String contentHash,
            String snapshotJson,
            String jsonSchema,
            String graphqlSdl,
            String queryContractJson) {
    }
}
