package com.codehouse.ciciassistant.ontology.service;

import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class OntologyCompilerService {

    private static final Comparator<String> NULL_SAFE_TEXT =
            Comparator.nullsFirst(String::compareTo);
    private static final Pattern GRAPHQL_DEFINITION = Pattern.compile(
            "^(type|input|enum)\\s+([_A-Za-z][_0-9A-Za-z]*)\\s*\\{$");
    private static final Pattern GRAPHQL_NAME = Pattern.compile("^[_A-Za-z][_0-9A-Za-z]*$");
    private static final Set<String> RESERVED_GRAPHQL_DEFINITIONS = Set.of(
            "String", "Int", "Float", "Boolean", "ID", "Mutation", "Subscription");

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
        validateCompiledContracts(
                canonicalDocument, version, jsonSchema, graphqlSdl, queryContractJson);
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
        Map<String, List<OntologyDocument.Relation>> outgoingRelations =
                outgoingQueryableRelations(document);
        for (OntologyDocument.Concept concept : document.concepts()) {
            String definitionName = graphqlTypeName(concept.key());
            rootProperties.put(concept.key(), Map.of(
                    "type", "array",
                    "items", Map.of("$ref", "#/$defs/" + definitionName)));
            definitions.put(
                    definitionName,
                    conceptSchema(concept, outgoingRelations.getOrDefault(concept.key(), List.of())));
        }
        schema.put("properties", rootProperties);
        schema.put("$defs", definitions);
        return serialize(schema);
    }

    private Map<String, Object> conceptSchema(
            OntologyDocument.Concept concept,
            List<OntologyDocument.Relation> relations) {
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
        for (OntologyDocument.Relation relation : relations) {
            properties.put(relation.key(), relationSchema(relation));
        }
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    private Map<String, Object> relationSchema(OntologyDocument.Relation relation) {
        Map<String, Object> reference = Map.of(
                "$ref", "#/$defs/" + graphqlTypeName(relation.targetConceptKey()));
        if (relation.cardinality() == OntologyDocument.Cardinality.ONE_TO_ONE
                || relation.cardinality() == OntologyDocument.Cardinality.MANY_TO_ONE) {
            return reference;
        }
        Map<String, Object> array = new LinkedHashMap<>();
        array.put("type", "array");
        array.put("items", reference);
        return array;
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
        sdl.append("enum SemanticOperator {\n");
        for (OntologyDocument.Operator operator : OntologyDocument.Operator.values()) {
            sdl.append("  ").append(operator.name()).append("\n");
        }
        sdl.append("}\n\n");
        sdl.append("enum SortDirection {\n")
                .append("  ASC\n")
                .append("  DESC\n")
                .append("}\n\n");

        Map<String, List<OntologyDocument.Relation>> outgoingRelations =
                outgoingQueryableRelations(document);

        for (OntologyDocument.Concept concept : safe(document.concepts())) {
            String typeName = graphqlTypeName(concept.key());
            sdl.append("type ").append(typeName).append(" {\n");
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
            if (isEnabledAndQueryable(concept)) {
                sdl.append("input ").append(typeName).append("Filter {\n")
                        .append("  field: String!\n")
                        .append("  operator: SemanticOperator!\n")
                        .append("  value: String\n")
                        .append("}\n\n");
                sdl.append("input ").append(typeName).append("Order {\n")
                        .append("  field: String!\n")
                        .append("  direction: SortDirection!\n")
                        .append("}\n\n");
            }
        }

        sdl.append("type Query {\n");
        for (OntologyDocument.Concept concept : safe(document.concepts())) {
            if (concept.enabled() && concept.queryable()) {
                String fieldName = graphqlFieldName(concept.key());
                String typeName = graphqlTypeName(concept.key());
                sdl.append("  ").append(fieldName).append("(id: ID!): ")
                        .append(typeName).append("\n");
                sdl.append("  ").append(fieldName)
                        .append("List(filter: ").append(typeName)
                        .append("Filter, orderBy: ").append(typeName)
                        .append("Order, limit: Int = 50): [")
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
        Map<String, OntologyDocument.Concept> conceptsByKey = conceptsByKey(document);
        for (OntologyDocument.Relation relation : safe(document.relations())) {
            if (isCompilerQueryableRelation(relation, conceptsByKey)) {
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

    private Map<String, List<OntologyDocument.Relation>> outgoingQueryableRelations(
            OntologyDocument document) {
        Map<String, OntologyDocument.Concept> conceptsByKey = conceptsByKey(document);
        Map<String, List<OntologyDocument.Relation>> outgoing = new LinkedHashMap<>();
        for (OntologyDocument.Relation relation : safe(document.relations())) {
            if (isCompilerQueryableRelation(relation, conceptsByKey)) {
                outgoing.computeIfAbsent(
                        relation.sourceConceptKey(), ignored -> new ArrayList<>()).add(relation);
            }
        }
        return outgoing;
    }

    private Map<String, OntologyDocument.Concept> conceptsByKey(OntologyDocument document) {
        Map<String, OntologyDocument.Concept> concepts = new LinkedHashMap<>();
        for (OntologyDocument.Concept concept : safe(document.concepts())) {
            concepts.putIfAbsent(concept.key(), concept);
        }
        return concepts;
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

    private void validateCompiledContracts(
            OntologyDocument document,
            int version,
            String jsonSchema,
            String graphqlSdl,
            String queryContractJson) {
        JsonNode schema = parseContract(jsonSchema, "JSON Schema");
        JsonNode definitions = schema.path("$defs");
        if (!definitions.isObject() || definitions.isEmpty()) {
            invalidContract("JSON Schema requires non-empty $defs");
        }
        Set<String> definitionNames = new HashSet<>();
        definitions.fieldNames().forEachRemaining(definitionNames::add);
        validateSchemaReferences(schema, definitionNames);

        Map<String, OntologyDocument.Concept> conceptsByKey = conceptsByKey(document);
        Set<String> expectedRelations = new HashSet<>();
        for (OntologyDocument.Relation relation : safe(document.relations())) {
            if (!isCompilerQueryableRelation(relation, conceptsByKey)) {
                continue;
            }
            expectedRelations.add(relation.key());
            String sourceType = graphqlTypeName(relation.sourceConceptKey());
            JsonNode relationSchema = definitions
                    .path(sourceType)
                    .path("properties")
                    .path(relation.key());
            if (relationSchema.isMissingNode()) {
                invalidContract("JSON Schema relation is missing: " + relation.key());
            }
            String expectedRef = "#/$defs/" + graphqlTypeName(relation.targetConceptKey());
            JsonNode reference = isMany(relation)
                    ? relationSchema.path("items").path("$ref")
                    : relationSchema.path("$ref");
            if (!expectedRef.equals(reference.asText())) {
                invalidContract("JSON Schema relation reference is invalid: " + relation.key());
            }
        }

        GraphqlStructure graphql = validateGraphqlStructure(graphqlSdl);
        if (!"enum".equals(graphql.kinds().get("SemanticOperator"))
                || !"enum".equals(graphql.kinds().get("SortDirection"))
                || !"type".equals(graphql.kinds().get("Query"))) {
            invalidContract("GraphQL fixed definitions are missing or invalid");
        }
        for (String reserved : RESERVED_GRAPHQL_DEFINITIONS) {
            if (graphql.kinds().containsKey(reserved)) {
                invalidContract("GraphQL definition shadows a reserved type: " + reserved);
            }
        }
        for (OntologyDocument.Concept concept : safe(document.concepts())) {
            String typeName = graphqlTypeName(concept.key());
            if (!"type".equals(graphql.kinds().get(typeName))) {
                invalidContract("GraphQL concept type is missing: " + typeName);
            }
            if (!isEnabledAndQueryable(concept)) {
                continue;
            }
            if (!"input".equals(graphql.kinds().get(typeName + "Filter"))
                    || !"input".equals(graphql.kinds().get(typeName + "Order"))) {
                invalidContract("GraphQL filter/order inputs are missing: " + typeName);
            }
            String listSignature = graphqlFieldName(concept.key())
                    + "List(filter: " + typeName + "Filter, orderBy: " + typeName
                    + "Order, limit: Int = 50): [" + typeName + "!]!";
            if (!graphqlSdl.contains("  " + listSignature + "\n")) {
                invalidContract("GraphQL list query signature is invalid: " + typeName);
            }
        }

        JsonNode queryContract = parseContract(queryContractJson, "query contract");
        if (!queryContract.isObject()
                || version != queryContract.path("version").asInt()
                || !queryContract.path("concepts").isArray()
                || !queryContract.path("relations").isArray()
                || !queryContract.path("writeOperations").isArray()
                || !queryContract.path("writeOperations").isEmpty()) {
            invalidContract("Query contract fixed structure is invalid");
        }
        Set<String> actualRelations = new HashSet<>();
        queryContract.path("relations").forEach(
                relation -> actualRelations.add(relation.path("key").asText()));
        if (!actualRelations.equals(expectedRelations)) {
            invalidContract("Query contract relations do not match compiled relations");
        }
    }

    private GraphqlStructure validateGraphqlStructure(String graphqlSdl) {
        Map<String, String> kinds = new LinkedHashMap<>();
        Map<String, Set<String>> membersByDefinition = new LinkedHashMap<>();
        String currentKind = null;
        String currentName = null;
        Set<String> currentMembers = null;
        for (String rawLine : graphqlSdl.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (currentName == null) {
                Matcher matcher = GRAPHQL_DEFINITION.matcher(line);
                if (!matcher.matches()) {
                    invalidContract("Invalid GraphQL definition header: " + line);
                }
                currentKind = matcher.group(1);
                currentName = matcher.group(2);
                if (currentName.startsWith("__")
                        || kinds.putIfAbsent(currentName, currentKind) != null) {
                    invalidContract("Duplicate or reserved GraphQL definition: " + currentName);
                }
                currentMembers = new HashSet<>();
                membersByDefinition.put(currentName, currentMembers);
                continue;
            }
            if ("}".equals(line)) {
                if (currentMembers.isEmpty()) {
                    invalidContract("GraphQL definitions cannot be empty: " + currentName);
                }
                currentKind = null;
                currentName = null;
                currentMembers = null;
                continue;
            }
            String member = "enum".equals(currentKind)
                    ? line
                    : graphqlMemberName(line);
            if (!GRAPHQL_NAME.matcher(member).matches()
                    || member.startsWith("__")
                    || !currentMembers.add(member)) {
                invalidContract("Invalid or duplicate GraphQL member: " + member);
            }
        }
        if (currentName != null) {
            invalidContract("Unclosed GraphQL definition: " + currentName);
        }
        return new GraphqlStructure(kinds, membersByDefinition);
    }

    private String graphqlMemberName(String line) {
        int arguments = line.indexOf('(');
        int typeSeparator = line.indexOf(':');
        int end = arguments >= 0 && (typeSeparator < 0 || arguments < typeSeparator)
                ? arguments : typeSeparator;
        if (end <= 0) {
            invalidContract("Invalid GraphQL field: " + line);
        }
        return line.substring(0, end).trim();
    }

    private void validateSchemaReferences(JsonNode node, Set<String> definitionNames) {
        if (node.isObject()) {
            JsonNode reference = node.get("$ref");
            if (reference != null) {
                String prefix = "#/$defs/";
                String value = reference.asText();
                if (!value.startsWith(prefix)
                        || !definitionNames.contains(value.substring(prefix.length()))) {
                    invalidContract("JSON Schema contains an unresolved $ref: " + value);
                }
            }
            node.fields().forEachRemaining(
                    entry -> validateSchemaReferences(entry.getValue(), definitionNames));
        } else if (node.isArray()) {
            node.forEach(child -> validateSchemaReferences(child, definitionNames));
        }
    }

    private JsonNode parseContract(String json, String contractName) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "ONTOLOGY_CONTRACT_INVALID: unable to parse " + contractName, exception);
        }
    }

    private boolean isMany(OntologyDocument.Relation relation) {
        return relation.cardinality() == OntologyDocument.Cardinality.ONE_TO_MANY
                || relation.cardinality() == OntologyDocument.Cardinality.MANY_TO_MANY;
    }

    private void invalidContract(String message) {
        throw new IllegalStateException("ONTOLOGY_CONTRACT_INVALID: " + message);
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

    private record GraphqlStructure(
            Map<String, String> kinds,
            Map<String, Set<String>> members) {
    }
}
