package com.codehouse.ciciassistant.ontology.adapter;

import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Domain-neutral read adapter contract for ontology catalog discovery and execution.
 * Connector selection is based on the complete data-source configuration so multiple
 * peripheral adapters can share the core {@code CONNECTOR} source type.
 */
public interface OntologyDataSourceAdapter {

    boolean supports(DataSourceConfig source);

    /**
     * Public, non-secret root configuration keys accepted by this adapter.
     * Unknown keys are rejected before persistence.
     */
    default Set<String> publicConfigKeys() {
        return Set.of("adapterKey");
    }

    /**
     * Validates the complete public connector configuration after the core has
     * rejected unknown root fields and secret-like values.
     */
    default void validatePublicConfig(JsonNode config) {
        if (config == null
                || !config.isObject()
                || !config.path("adapterKey").isTextual()
                || config.path("adapterKey").asText().isBlank()) {
            throw new IllegalArgumentException("DATA_SOURCE_CONFIG_INVALID");
        }
    }

    List<PhysicalObject> discoverObjects(AdapterContext context, DataSourceConfig source);

    List<PhysicalField> discoverFields(
            AdapterContext context,
            DataSourceConfig source,
            String objectKey);

    MappingValidation validateMapping(
            AdapterContext context,
            DataSourceConfig source,
            OntologyDocument.Mapping mapping);

    /**
     * Validates an already governed physical query without performing external I/O.
     * Implementations may reject capabilities that cannot be executed faithfully.
     */
    default void validateQuery(DataSourceConfig source, PhysicalQuery query) {
        // Most adapters support the complete bounded query contract.
    }

    PhysicalResult executeRead(
            AdapterContext context,
            DataSourceConfig source,
            PhysicalQuery query);

    record AdapterContext(String orgId, String userId) {
        public AdapterContext {
            if (orgId == null || orgId.isBlank() || userId == null || userId.isBlank()) {
                throw new IllegalArgumentException("ADAPTER_CONTEXT_REQUIRED");
            }
        }
    }

    record DataSourceConfig(
            Long id,
            Long workspaceId,
            String key,
            String name,
            OntologyDocument.SourceType type,
            String adapterKey,
            String configJson,
            String sampleDataJson) {
    }

    record PhysicalObject(
            String key,
            String name,
            String objectType,
            String metadataJson) {
    }

    record PhysicalField(
            String objectKey,
            String key,
            String name,
            String dataType,
            boolean nullable,
            boolean multiple,
            String metadataJson) {
    }

    record MappingValidation(boolean valid, String code, String message) {
        public static MappingValidation success() {
            return new MappingValidation(true, "VALID", "Mapping is valid");
        }

        public static MappingValidation invalid(String code, String message) {
            return new MappingValidation(false, code, message);
        }
    }

    record PhysicalFilter(
            String field,
            OntologyDocument.Operator operator,
            Object value) {
    }

    record PhysicalOrder(String field, Direction direction) {
    }

    enum Direction {
        ASC,
        DESC
    }

    record PhysicalQuery(
            String objectKey,
            List<String> fields,
            List<PhysicalFilter> filters,
            List<PhysicalOrder> orderBy,
            int limit) {
        public PhysicalQuery {
            fields = fields == null ? List.of() : List.copyOf(fields);
            filters = filters == null ? List.of() : List.copyOf(filters);
            orderBy = orderBy == null ? List.of() : List.copyOf(orderBy);
        }
    }

    record PhysicalResult(
            List<Map<String, Object>> rows,
            int totalCount,
            boolean moreAvailable) {
        public PhysicalResult(List<Map<String, Object>> rows, int totalCount) {
            this(rows, totalCount, totalCount > (rows == null ? 0 : rows.size()));
        }

        public PhysicalResult {
            rows = rows == null
                    ? List.of()
                    : rows.stream()
                            .map(row -> Collections.unmodifiableMap(new LinkedHashMap<>(row)))
                            .toList();
            if (totalCount < rows.size()) {
                throw new IllegalArgumentException("PHYSICAL_RESULT_COUNT_INVALID");
            }
            moreAvailable = moreAvailable || totalCount > rows.size();
        }
    }
}
