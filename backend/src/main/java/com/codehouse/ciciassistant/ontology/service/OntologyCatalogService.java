package com.codehouse.ciciassistant.ontology.service;

import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.common.error.DataSourceUnavailableException;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.AdapterContext;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.DataSourceConfig;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.MappingValidation;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalField;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalObject;
import com.codehouse.ciciassistant.ontology.service.OntologyCatalogTransactionService.MappingCommit;
import com.codehouse.ciciassistant.ontology.service.OntologyCatalogTransactionService.MappingBatchCommit;
import com.codehouse.ciciassistant.ontology.service.OntologyCatalogTransactionService.MappingKey;
import com.codehouse.ciciassistant.ontology.service.OntologyCatalogTransactionService.MappingPreparation;
import com.codehouse.ciciassistant.ontology.service.OntologyCatalogTransactionService.SourcePreparation;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class OntologyCatalogService {

    private static final int MAX_CATALOG_ITEMS = 5_000;
    private static final int MAX_METADATA_BYTES = 64 * 1024;
    private static final long MAX_CATALOG_RESPONSE_BYTES = 4L * 1024 * 1024;

    private final OntologyCatalogTransactionService transactions;
    private final List<OntologyDataSourceAdapter> adapters;
    private final ObjectMapper objectMapper;

    public OntologyCatalogService(
            OntologyCatalogTransactionService transactions,
            List<OntologyDataSourceAdapter> adapters,
            ObjectMapper objectMapper) {
        this.transactions = transactions;
        this.adapters = List.copyOf(adapters);
        this.objectMapper = objectMapper;
    }

    public CatalogMutation<PhysicalObject> discoverObjects(
            String orgId,
            String userId,
            Long workspaceId,
            Long dataSourceId,
            Long expectedRevision) {
        requireCurrentContext(orgId, userId);
        SourcePreparation prepared = transactions.prepareSource(
                orgId, workspaceId, dataSourceId, expectedRevision, null);
        OntologyDataSourceAdapter adapter = requireAdapter(prepared.source());
        List<PhysicalObject> discovered = adapterProtocol(() -> {
            List<PhysicalObject> values = List.copyOf(adapter.discoverObjects(
                    new AdapterContext(orgId, userId), prepared.source()));
            validateObjects(values);
            return values;
        });
        long revision = transactions.commitObjects(prepared, userId, discovered);
        return new CatalogMutation<>(discovered, revision);
    }

    public CatalogMutation<PhysicalField> discoverFields(
            String orgId,
            String userId,
            Long workspaceId,
            Long dataSourceId,
            String objectKey,
            Long expectedRevision) {
        requireCurrentContext(orgId, userId);
        SourcePreparation prepared = transactions.prepareSource(
                orgId, workspaceId, dataSourceId, expectedRevision, objectKey);
        OntologyDataSourceAdapter adapter = requireAdapter(prepared.source());
        List<PhysicalField> discovered = adapterProtocol(() -> {
            List<PhysicalField> values = List.copyOf(adapter.discoverFields(
                    new AdapterContext(orgId, userId), prepared.source(), objectKey));
            validateFields(objectKey, values);
            return values;
        });
        long revision = transactions.commitFields(prepared, userId, discovered);
        return new CatalogMutation<>(discovered, revision);
    }

    public MappingCommit validateMapping(
            String orgId,
            String userId,
            Long workspaceId,
            Long expectedRevision,
            MappingKey key) {
        requireCurrentContext(orgId, userId);
        MappingPreparation prepared = transactions.prepareMapping(
                orgId, workspaceId, expectedRevision, key);
        OntologyDataSourceAdapter adapter = requireAdapter(prepared.source());
        MappingValidation safeValidation = validatePreparedMapping(
                adapter, new AdapterContext(orgId, userId), prepared);
        return transactions.commitMappingValidation(prepared, userId, safeValidation);
    }

    public MappingBatchCommit validateMappings(
            String orgId,
            String userId,
            Long workspaceId,
            Long expectedRevision,
            List<MappingKey> keys) {
        requireCurrentContext(orgId, userId);
        if (keys == null || keys.isEmpty() || keys.size() > 500
                || new LinkedHashSet<>(keys).size() != keys.size()) {
            throw new IllegalArgumentException("MAPPING_IDENTITY_REQUIRED");
        }
        List<MappingPreparation> preparedMappings = keys.stream()
                .map(key -> transactions.prepareMapping(
                        orgId, workspaceId, expectedRevision, key))
                .toList();
        AdapterContext context = new AdapterContext(orgId, userId);
        List<MappingValidation> validations = preparedMappings.stream()
                .map(prepared -> validatePreparedMapping(
                        requireAdapter(prepared.source()), context, prepared))
                .toList();
        return transactions.commitMappingValidations(
                preparedMappings, userId, validations);
    }

    private MappingValidation validatePreparedMapping(
            OntologyDataSourceAdapter adapter,
            AdapterContext context,
            MappingPreparation prepared) {
        MappingValidation validation = adapterCall(() -> adapter.validateMapping(
                context, prepared.source(), prepared.mapping()));
        if (!validation.valid() && isUnavailable(validation.code())) {
            throw new DataSourceUnavailableException();
        }
        return validation.valid()
                ? MappingValidation.success()
                : MappingValidation.invalid(
                        safeValidationCode(validation.code()),
                        safeValidationMessage(validation.code()));
    }

    private OntologyDataSourceAdapter requireAdapter(DataSourceConfig source) {
        List<OntologyDataSourceAdapter> matching = adapters.stream()
                .filter(adapter -> supports(adapter, source))
                .limit(2)
                .toList();
        if (matching.size() != 1) {
            throw new DataSourceUnavailableException();
        }
        return matching.getFirst();
    }

    private boolean supports(OntologyDataSourceAdapter adapter, DataSourceConfig source) {
        try {
            return adapter.supports(source);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private <T> T adapterCall(Supplier<T> call) {
        try {
            T result = call.get();
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

    private <T> T adapterProtocol(Supplier<T> call) {
        return adapterCall(call);
    }

    private boolean isUnavailable(String code) {
        return "DATA_SOURCE_UNAVAILABLE".equals(code)
                || "CONNECTOR_MAPPING_VALIDATION_FAILED".equals(code);
    }

    private String safeValidationCode(String code) {
        return code != null && code.matches("[A-Z][A-Z0-9_]{2,63}")
                ? code
                : "MAPPING_INVALID";
    }

    private String safeValidationMessage(String code) {
        return switch (safeValidationCode(code)) {
            case "PHYSICAL_OBJECT_NOT_FOUND" -> "Mapped object was not discovered";
            case "PHYSICAL_FIELD_REQUIRED" -> "Mapped field is required";
            case "PHYSICAL_FIELD_NOT_FOUND" -> "Mapped field was not discovered";
            default -> "Mapping validation failed";
        };
    }

    private void validateObjects(List<PhysicalObject> values) {
        if (values.size() > MAX_CATALOG_ITEMS) {
            throw new IllegalArgumentException("CATALOG_ITEM_LIMIT_EXCEEDED");
        }
        requireUniqueKeys(values.stream().map(PhysicalObject::key).toList());
        for (PhysicalObject value : values) {
            if (value == null) {
                throw new IllegalArgumentException("CATALOG_OBJECT_INVALID");
            }
            requireCatalogText(value.key(), 256, "CATALOG_OBJECT_KEY_REQUIRED");
            requireCatalogText(value.name(), 160, "CATALOG_OBJECT_NAME_REQUIRED");
            requireMetadata(value.metadataJson());
        }
        requireAggregateBudget(values);
    }

    private void validateFields(String objectKey, List<PhysicalField> values) {
        if (values.size() > MAX_CATALOG_ITEMS) {
            throw new IllegalArgumentException("CATALOG_ITEM_LIMIT_EXCEEDED");
        }
        requireUniqueKeys(values.stream().map(PhysicalField::key).toList());
        for (PhysicalField value : values) {
            if (value == null || !Objects.equals(value.objectKey(), objectKey)) {
                throw new IllegalArgumentException("CATALOG_FIELD_INVALID");
            }
            requireCatalogText(value.key(), 256, "CATALOG_FIELD_KEY_REQUIRED");
            requireCatalogText(value.name(), 160, "CATALOG_FIELD_NAME_REQUIRED");
            requireCatalogText(value.dataType(), 64, "CATALOG_FIELD_TYPE_REQUIRED");
            requireMetadata(value.metadataJson());
        }
        requireAggregateBudget(values);
    }

    private void requireAggregateBudget(List<?> values) {
        BoundedCountingOutputStream output =
                new BoundedCountingOutputStream(MAX_CATALOG_RESPONSE_BYTES);
        try (JsonGenerator generator = objectMapper.getFactory().createGenerator(output)) {
            generator.writeStartArray();
            for (Object value : values) {
                objectMapper.writeValue(generator, value);
            }
            generator.writeEndArray();
        } catch (IOException exception) {
            if (hasCatalogBudgetCause(exception)) {
                throw new IllegalArgumentException(
                        "CATALOG_RESPONSE_LIMIT_EXCEEDED", exception);
            }
            throw new IllegalArgumentException("CATALOG_RESPONSE_INVALID", exception);
        }
    }

    private boolean hasCatalogBudgetCause(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof CatalogBudgetExceededException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void requireUniqueKeys(List<String> keys) {
        if (new LinkedHashSet<>(keys).size() != keys.size()) {
            throw new IllegalArgumentException("CATALOG_DUPLICATE_KEY");
        }
    }

    private void requireCatalogText(String value, int maxLength, String errorCode) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(errorCode);
        }
    }

    private void requireMetadata(String metadataJson) {
        if (metadataJson != null
                && metadataJson.getBytes(StandardCharsets.UTF_8).length > MAX_METADATA_BYTES) {
            throw new IllegalArgumentException("CATALOG_METADATA_LIMIT_EXCEEDED");
        }
    }

    private void requireCurrentContext(String orgId, String userId) {
        if (userId == null
                || userId.isBlank()
                || !Objects.equals(TenantContext.requireOrgId(), orgId)
                || TenantContext.getUserId().filter(userId::equals).isEmpty()) {
            throw new ForbiddenException("ONTOLOGY_CATALOG_CONTEXT_MISMATCH");
        }
    }

    public record CatalogMutation<T>(List<T> items, long revision) {
        public CatalogMutation {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    private static final class BoundedCountingOutputStream extends OutputStream {

        private final long maxBytes;
        private long bytesWritten;

        private BoundedCountingOutputStream(long maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public void write(int value) throws IOException {
            reserve(1);
        }

        @Override
        public void write(byte[] values, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, values.length);
            reserve(length);
        }

        private void reserve(int length) throws CatalogBudgetExceededException {
            if (length > maxBytes - bytesWritten) {
                throw new CatalogBudgetExceededException();
            }
            bytesWritten += length;
        }
    }

    private static final class CatalogBudgetExceededException extends IOException {
    }
}
