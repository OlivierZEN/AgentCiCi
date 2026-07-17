package com.codehouse.ciciassistant.ontology.service;

import com.codehouse.ciciassistant.common.error.ResourceNotFoundException;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

@Service
public class OntologyReferencePackageService {

    private static final String PACKAGE_PATTERN =
            "classpath*:ontology/reference-packages/*.json";

    private final ObjectMapper strictMapper;

    public OntologyReferencePackageService(ObjectMapper objectMapper) {
        this.strictMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }

    public List<ReferencePackageSummary> list() {
        return loadAll().values().stream()
                .map(value -> new ReferencePackageSummary(
                        value.id(),
                        value.title(),
                        value.description(),
                        new WorkspaceIdentity(
                                value.document().key(),
                                value.document().name(),
                                value.document().description()),
                        value.fingerprint(),
                        value.document().concepts().size(),
                        value.document().dataSources().size()))
                .sorted(java.util.Comparator.comparing(ReferencePackageSummary::id))
                .toList();
    }

    public ReferencePackage load(String id) {
        if (id == null || id.isBlank()) {
            throw new ResourceNotFoundException("ONTOLOGY_REFERENCE_PACKAGE_NOT_FOUND");
        }
        ReferencePackage value = loadAll().get(id.trim());
        if (value == null) {
            throw new ResourceNotFoundException("ONTOLOGY_REFERENCE_PACKAGE_NOT_FOUND");
        }
        return value;
    }

    private Map<String, ReferencePackage> loadAll() {
        Map<String, ReferencePackage> packages = new LinkedHashMap<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(PACKAGE_PATTERN);
            for (Resource resource : resources) {
                ReferencePackage value;
                try (InputStream stream = resource.getInputStream()) {
                    byte[] bytes = stream.readAllBytes();
                    ReferencePackageDefinition definition = strictMapper.readValue(
                            bytes, ReferencePackageDefinition.class);
                    value = new ReferencePackage(
                            definition.id(),
                            definition.title(),
                            definition.description(),
                            definition.document(),
                            sha256(bytes));
                }
                validate(value, resource.getFilename());
                if (packages.putIfAbsent(value.id(), value) != null) {
                    throw new IllegalStateException("ONTOLOGY_REFERENCE_PACKAGE_DUPLICATE");
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("ONTOLOGY_REFERENCE_PACKAGE_INVALID", exception);
        }
        return Map.copyOf(packages);
    }

    private void validate(ReferencePackage value, String filename) {
        if (value == null
                || !hasText(value.id())
                || !hasText(value.title())
                || value.fingerprint() == null
                || !value.fingerprint().matches("[0-9a-f]{64}")
                || value.document() == null
                || !Objects.equals(value.id(), value.document().key())
                || filename == null
                || !filename.equals(value.id() + ".json")) {
            throw new IllegalStateException("ONTOLOGY_REFERENCE_PACKAGE_INVALID");
        }
        List<OntologyDocument.DataSource> sources = safe(value.document().dataSources());
        Set<Long> sourceIds = new LinkedHashSet<>();
        for (OntologyDocument.DataSource source : sources) {
            if (source == null
                    || source.id() == null
                    || source.id() >= 0
                    || !sourceIds.add(source.id())) {
                throw new IllegalStateException("ONTOLOGY_REFERENCE_PACKAGE_INVALID");
            }
        }
        for (OntologyDocument.Mapping mapping : safe(value.document().mappings())) {
            if (mapping == null
                    || !sourceIds.contains(mapping.dataSourceId())
                    || !"REFERENCE".equals(mapping.source())
                    || !"PENDING".equals(mapping.validationStatus())) {
                throw new IllegalStateException("ONTOLOGY_REFERENCE_PACKAGE_INVALID");
            }
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("ONTOLOGY_REFERENCE_PACKAGE_FINGERPRINT_UNAVAILABLE", exception);
        }
    }

    private record ReferencePackageDefinition(
            String id,
            String title,
            String description,
            OntologyDocument document) {
    }

    public record ReferencePackage(
            String id,
            String title,
            String description,
            OntologyDocument document,
            String fingerprint) {
    }

    public record ReferencePackageSummary(
            String id,
            String title,
            String description,
            WorkspaceIdentity workspaceIdentity,
            String fingerprint,
            int conceptCount,
            int dataSourceCount) {
    }

    public record WorkspaceIdentity(
            String key,
            String name,
            String description) {
    }
}
