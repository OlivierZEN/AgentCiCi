package com.codehouse.ciciassistant.ontology.adapter;

import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.codehouse.ciciassistant.ontology.semattice.SematticeCapabilityException;
import com.codehouse.ciciassistant.ontology.semattice.SematticeOntologyGateway;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Read-only phase-one adapter for the current published Semattice metadata and record runtime. */
@Component
public class SematticeOntologyAdapter implements OntologyDataSourceAdapter {

    public static final String ADAPTER_KEY = "semattice";
    private static final String METADATA_CAPABILITY = "metadata.version.get-current";
    private static final String QUERY_CAPABILITY = "runtime.record.query";
    private static final int MAX_LIMIT = 100;
    private static final int MAX_FILTERS = 8;
    private static final int MAX_FIELDS = 100;
    private static final Pattern IDENTIFIER = Pattern.compile("^[a-z][a-z0-9_]{0,95}$");

    private final SematticeOntologyGateway gateway;
    private final ObjectMapper objectMapper;

    public SematticeOntologyAdapter(
            SematticeOntologyGateway gateway,
            ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(DataSourceConfig source) {
        return source != null
                && source.type() == OntologyDocument.SourceType.CONNECTOR
                && ADAPTER_KEY.equalsIgnoreCase(trim(source.adapterKey()));
    }

    @Override
    public Set<String> publicConfigKeys() {
        return Set.of("adapterKey");
    }

    @Override
    public void validatePublicConfig(JsonNode config) {
        OntologyDataSourceAdapter.super.validatePublicConfig(config);
        if (!ADAPTER_KEY.equalsIgnoreCase(config.path("adapterKey").asText())
                || config.size() != 1) {
            throw new IllegalArgumentException("DATA_SOURCE_CONFIG_INVALID");
        }
    }

    @Override
    public List<PhysicalObject> discoverObjects(AdapterContext context, DataSourceConfig source) {
        requireSupported(source);
        JsonNode bundle = currentMetadata(context);
        String versionId = requiredText(bundle.path("version"), "metadata_version_id");
        long sequence = bundle.path("version").path("sequence").asLong();
        List<PhysicalObject> result = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        for (JsonNode object : requiredArray(bundle, "objects")) {
            String apiName = requiredIdentifier(object, "api_name");
            if (!keys.add(apiName)) {
                throw new IllegalStateException("SEMATTICE_METADATA_DUPLICATE_OBJECT");
            }
            result.add(new PhysicalObject(
                    apiName,
                    nonBlank(object.path("label").asText(), apiName),
                    "SEMATTICE",
                    metadata(Map.of(
                            "metadataVersionId", versionId,
                            "sequence", sequence,
                            "objectId", requiredText(object, "object_id"),
                            "semantic", jsonValue(object.path("semantic"))))));
        }
        return List.copyOf(result);
    }

    @Override
    public List<PhysicalField> discoverFields(
            AdapterContext context,
            DataSourceConfig source,
            String objectKey) {
        requireSupported(source);
        requireIdentifier(objectKey, "PHYSICAL_OBJECT_NOT_FOUND");
        JsonNode bundle = currentMetadata(context);
        JsonNode object = findObject(bundle, objectKey);
        String objectId = requiredText(object, "object_id");
        List<PhysicalField> result = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        for (JsonNode field : requiredArray(bundle, "fields")) {
            if (!objectId.equals(field.path("object_id").asText())
                    || "tombstone".equalsIgnoreCase(field.path("lifecycle_state").asText())) {
                continue;
            }
            String apiName = requiredIdentifier(field, "api_name");
            if (!keys.add(apiName)) {
                throw new IllegalStateException("SEMATTICE_METADATA_DUPLICATE_FIELD");
            }
            result.add(new PhysicalField(
                    objectKey,
                    apiName,
                    nonBlank(field.path("label").asText(), apiName),
                    requiredText(field, "data_type").toUpperCase(Locale.ROOT),
                    !field.path("required").asBoolean(false),
                    false,
                    metadata(Map.of(
                            "fieldId", requiredText(field, "field_id"),
                            "indexed", field.path("indexed").asBoolean(false),
                            "uniqueValue", field.path("unique_value").asBoolean(false),
                            "lifecycleState", field.path("lifecycle_state").asText("active"),
                            "semantic", jsonValue(field.path("semantic"))))));
        }
        return List.copyOf(result);
    }

    @Override
    public MappingValidation validateMapping(
            AdapterContext context,
            DataSourceConfig source,
            OntologyDocument.Mapping mapping) {
        if (mapping == null) {
            return MappingValidation.invalid("MAPPING_REQUIRED", "Mapping is required");
        }
        try {
            boolean objectExists = discoverObjects(context, source).stream()
                    .anyMatch(object -> Objects.equals(object.key(), mapping.physicalObjectKey()));
            if (!objectExists) {
                return MappingValidation.invalid(
                        "PHYSICAL_OBJECT_NOT_FOUND", "Mapped object was not discovered");
            }
            if (blank(mapping.physicalFieldKey())) {
                return "CONCEPT".equalsIgnoreCase(mapping.targetType())
                        ? MappingValidation.success()
                        : MappingValidation.invalid(
                                "PHYSICAL_FIELD_REQUIRED", "Mapped field is required");
            }
            boolean fieldExists = discoverFields(
                    context, source, mapping.physicalObjectKey()).stream()
                    .anyMatch(field -> Objects.equals(field.key(), mapping.physicalFieldKey()));
            return fieldExists
                    ? MappingValidation.success()
                    : MappingValidation.invalid(
                            "PHYSICAL_FIELD_NOT_FOUND", "Mapped field was not discovered");
        } catch (RuntimeException exception) {
            return MappingValidation.invalid(
                    "DATA_SOURCE_UNAVAILABLE", "Data source is unavailable");
        }
    }

    @Override
    public void validateQuery(DataSourceConfig source, PhysicalQuery query) {
        requireSupported(source);
        if (query == null || query.limit() < 1 || query.limit() > MAX_LIMIT) {
            throw new IllegalArgumentException("QUERY_BUDGET_EXCEEDED");
        }
        requireIdentifier(query.objectKey(), "PHYSICAL_OBJECT_NOT_ALLOWED");
        if (query.fields().isEmpty() || query.fields().size() > MAX_FIELDS) {
            throw new IllegalArgumentException("QUERY_FIELDS_INVALID");
        }
        query.fields().forEach(field -> requireIdentifier(field, "PHYSICAL_FIELD_NOT_ALLOWED"));
        if (!query.orderBy().isEmpty()) {
            throw new IllegalArgumentException("QUERY_ORDER_NOT_SUPPORTED");
        }
        if (query.filters().size() > MAX_FILTERS) {
            throw new IllegalArgumentException("QUERY_FILTER_LIMIT_EXCEEDED");
        }
        query.filters().forEach(filter -> {
            if (filter == null || filter.operator() == null) {
                throw new IllegalArgumentException("QUERY_OPERATOR_NOT_ALLOWED");
            }
            requireIdentifier(filter.field(), "PHYSICAL_FIELD_NOT_ALLOWED");
            operator(filter.operator());
        });
    }

    @Override
    public PhysicalResult executeRead(
            AdapterContext context,
            DataSourceConfig source,
            PhysicalQuery query) {
        validateQuery(source, query);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("object_api_name", query.objectKey());
        input.put("limit", query.limit());
        if (!query.filters().isEmpty()) {
            input.put("filters", query.filters().stream()
                    .map(filter -> Map.of(
                            "field", filter.field(),
                            "op", operator(filter.operator()),
                            "value", filter.value()))
                    .toList());
        }
        JsonNode response = gateway.invokeRead(
                context.companyId(), context.userId(), QUERY_CAPABILITY, input);
        Set<String> selected = new LinkedHashSet<>(query.fields());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode record : requiredArray(response, "records")) {
            JsonNode data = record.path("data");
            if (!data.isObject()) {
                throw new IllegalStateException("SEMATTICE_RECORD_INVALID");
            }
            Map<String, Object> row = new LinkedHashMap<>();
            selected.forEach(field -> {
                if (data.has(field)) {
                    row.put(field, jsonValue(data.get(field)));
                } else {
                    row.put(field, null);
                }
            });
            rows.add(row);
        }
        boolean more = !blank(response.path("next_cursor").asText());
        return new PhysicalResult(rows, rows.size() + (more ? 1 : 0), more);
    }

    private JsonNode currentMetadata(AdapterContext context) {
        try {
            JsonNode bundle = gateway.invokeRead(
                    context.companyId(), context.userId(), METADATA_CAPABILITY, Map.of());
            if (!bundle.path("version").isObject()) {
                throw new IllegalStateException("SEMATTICE_METADATA_INVALID");
            }
            requiredArray(bundle, "objects");
            requiredArray(bundle, "fields");
            requiredArray(bundle, "relations");
            return bundle;
        } catch (SematticeCapabilityException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SematticeCapabilityException(
                    "DATA_SOURCE_UNAVAILABLE", "Semattice metadata is unavailable", exception);
        }
    }

    private JsonNode findObject(JsonNode bundle, String objectKey) {
        for (JsonNode object : requiredArray(bundle, "objects")) {
            if (objectKey.equals(object.path("api_name").asText())) {
                return object;
            }
        }
        throw new IllegalArgumentException("PHYSICAL_OBJECT_NOT_FOUND");
    }

    private Iterable<JsonNode> requiredArray(JsonNode parent, String field) {
        JsonNode value = parent.path(field);
        if (!value.isArray()) {
            throw new IllegalStateException("SEMATTICE_METADATA_INVALID");
        }
        return value;
    }

    private String operator(OntologyDocument.Operator operator) {
        return switch (operator) {
            case EQ -> "eq";
            case GT -> "gt";
            case GTE -> "gte";
            case LT -> "lt";
            case LTE -> "lte";
            default -> throw new IllegalArgumentException("QUERY_OPERATOR_NOT_SUPPORTED");
        };
    }

    private String metadata(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("SEMATTICE_METADATA_INVALID", exception);
        }
    }

    private Object jsonValue(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        return objectMapper.convertValue(value, Object.class);
    }

    private void requireSupported(DataSourceConfig source) {
        if (!supports(source)) {
            throw new IllegalArgumentException("DATA_SOURCE_NOT_SUPPORTED");
        }
    }

    private String requiredIdentifier(JsonNode value, String field) {
        String result = requiredText(value, field);
        requireIdentifier(result, "SEMATTICE_METADATA_INVALID");
        return result;
    }

    private String requiredText(JsonNode value, String field) {
        String result = value.path(field).asText("");
        if (result.isBlank()) {
            throw new IllegalStateException("SEMATTICE_METADATA_INVALID");
        }
        return result;
    }

    private void requireIdentifier(String value, String code) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(code);
        }
    }

    private static String nonBlank(String value, String fallback) {
        return blank(value) ? fallback : value;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
