package com.codehouse.ciciassistant.kb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
@ConditionalOnProperty(name = "app.kb.vector-store", havingValue = "qdrant")
public class QdrantVectorStoreClient implements VectorStoreClient {

    private static final Logger log = LoggerFactory.getLogger(QdrantVectorStoreClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String collection;
    private final int dimension;

    public QdrantVectorStoreClient(
            ObjectMapper objectMapper,
            @Value("${app.kb.qdrant.base-url:http://localhost:6333}") String baseUrl,
            @Value("${app.kb.qdrant.collection:cici_kb_chunk}") String collection,
            @Value("${app.kb.qdrant.api-key:}") String apiKey,
            @Value("${app.kb.embedding.dimension:1024}") int dimension) {
        this.objectMapper = objectMapper;
        this.collection = collection;
        this.dimension = Math.max(4, dimension);
        String root = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        RestClient.Builder b = RestClient.builder().baseUrl(root);
        if (StringUtils.hasText(apiKey)) {
            b.defaultHeader("api-key", apiKey.trim());
        }
        this.restClient = b.build();
    }

    @jakarta.annotation.PostConstruct
    void ensureCollection() {
        try {
            Optional<JsonNode> metadata = collectionMetadata();
            if (metadata.isEmpty()) {
                createCollection();
            } else {
                validateCollectionDimension(metadata.get());
            }
        } catch (Exception ex) {
            log.warn("Qdrant collection bootstrap skipped or failed (operations may fail until fixed): {}", ex.getMessage());
        }
    }

    @Override
    public String upsert(VectorUpsertCommand command) {
        String id = UUID.randomUUID().toString();
        String text = command.content() == null ? "" : command.content();
        if (text.length() > 4000) {
            text = text.substring(0, 4000);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("company_id", command.companyId());
        payload.put("knowledge_base_id", command.knowledgeBaseId());
        if (command.documentId() != null) {
            payload.put("document_id", command.documentId());
        }
        if (command.chunkId() != null) {
            payload.put("chunk_id", command.chunkId());
        }
        if (command.chunkIndex() != null) {
            payload.put("chunk_index", command.chunkIndex());
        }
        if (StringUtils.hasText(command.contentHash())) {
            payload.put("content_hash", command.contentHash());
        }
        payload.put("content", text);
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("id", id);
        point.put("vector", command.embedding());
        point.put("payload", payload);
        Map<String, Object> body = Map.of("points", List.of(point));
        try {
            putJson("/collections/" + collection + "/points?wait=true", body);
        } catch (Exception ex) {
            throw new IllegalStateException("Qdrant upsert failed: " + ex.getMessage(), ex);
        }
        return id;
    }

    @Override
    public List<VectorSearchHit> search(VectorSearchQuery query) {
        if (query.knowledgeBaseIds() == null || query.knowledgeBaseIds().isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> must = new ArrayList<>();
        must.add(matchField("company_id", query.companyId()));
        must.add(matchKnowledgeBases(query.knowledgeBaseIds()));
        Map<String, Object> filter = Map.of("must", must);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vector", query.queryEmbedding());
        body.put("limit", Math.max(1, query.topK()));
        body.put("filter", filter);
        body.put("with_payload", true);
        try {
            JsonNode root = postJson("/collections/" + collection + "/points/search", body);
            JsonNode result = root.path("result");
            if (!result.isArray()) {
                return List.of();
            }
            List<VectorSearchHit> out = new ArrayList<>();
            for (JsonNode hit : result) {
                JsonNode payload = hit.path("payload");
                String content = payload.path("content").asText("");
                Long chunkId = nullableLong(payload, "chunk_id");
                Long documentId = nullableLong(payload, "document_id");
                Integer chunkIndex = nullableInteger(payload, "chunk_index");
                String kbId = payload.path("knowledge_base_id").asText("");
                if (!content.isBlank()) {
                    out.add(new VectorSearchHit(
                            hit.path("id").asText(""),
                            kbId,
                            documentId,
                            chunkId,
                            chunkIndex,
                            content,
                            hit.path("score").asDouble(0.0)));
                }
            }
            return out;
        } catch (Exception ex) {
            log.debug("Qdrant search failed: {}", ex.getMessage());
            return List.of();
        }
    }

    @Override
    public VectorDeleteResult deleteByVectorIds(String companyId, List<String> vectorIds) {
        if (vectorIds == null || vectorIds.isEmpty()) {
            return VectorDeleteResult.success(0, 0);
        }
        try {
            postJson("/collections/" + collection + "/points/delete?wait=true", Map.of("points", vectorIds));
            return VectorDeleteResult.success(vectorIds.size(), vectorIds.size());
        } catch (Exception ex) {
            log.warn("Qdrant delete by vector ids failed for org {}: {}", companyId, ex.getMessage());
            return VectorDeleteResult.failure(vectorIds.size(), ex.getMessage());
        }
    }

    @Override
    public VectorDeleteResult deleteByDocument(String companyId, String knowledgeBaseId, Long documentId) {
        if (documentId == null) {
            return VectorDeleteResult.success(0, 0);
        }
        List<Map<String, Object>> must = new ArrayList<>();
        must.add(matchField("company_id", companyId));
        must.add(matchField("knowledge_base_id", knowledgeBaseId));
        must.add(matchField("document_id", documentId));
        return deleteByFilter(companyId, Map.of("must", must), "document " + documentId);
    }

    @Override
    public VectorDeleteResult deleteByKnowledgeBase(String companyId, String knowledgeBaseId) {
        List<Map<String, Object>> must = new ArrayList<>();
        must.add(matchField("company_id", companyId));
        must.add(matchField("knowledge_base_id", knowledgeBaseId));
        return deleteByFilter(companyId, Map.of("must", must), "knowledge base " + knowledgeBaseId);
    }

    @Override
    public VectorStoreAuditResult auditOrgVectors(String companyId, List<String> registeredVectorIds) {
        List<String> registered = registeredVectorIds == null ? List.of() : registeredVectorIds;
        try {
            List<String> orphanIds = new ArrayList<>();
            int scanned = 0;
            int orphanCount = 0;
            Object offset = null;
            do {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("filter", Map.of("must", List.of(matchField("company_id", companyId))));
                body.put("limit", 256);
                body.put("with_payload", false);
                body.put("with_vector", false);
                if (offset != null) {
                    body.put("offset", offset);
                }
                JsonNode root = postJson("/collections/" + collection + "/points/scroll", body);
                JsonNode result = root.path("result");
                JsonNode points = result.path("points");
                if (!points.isArray()) {
                    break;
                }
                for (JsonNode point : points) {
                    scanned++;
                    String vectorId = point.path("id").asText("");
                    if (!vectorId.isBlank() && !registered.contains(vectorId)) {
                        orphanCount++;
                        if (orphanIds.size() < 50) {
                            orphanIds.add(vectorId);
                        }
                    }
                }
                JsonNode nextOffset = result.path("next_page_offset");
                offset = nextOffset.isMissingNode() || nextOffset.isNull()
                        ? null
                        : objectMapper.convertValue(nextOffset, Object.class);
            } while (offset != null);
            return VectorStoreAuditResult.success(scanned, registered.size(), orphanCount, orphanIds);
        } catch (Exception ex) {
            log.warn("Qdrant vector audit failed for org {}: {}", companyId, ex.getMessage());
            return VectorStoreAuditResult.failure(registered.size(), ex.getMessage());
        }
    }

    private VectorDeleteResult deleteByFilter(String companyId, Map<String, Object> filter, String scope) {
        try {
            postJson("/collections/" + collection + "/points/delete?wait=true", Map.of("filter", filter));
            return VectorDeleteResult.success(1, 1);
        } catch (Exception ex) {
            log.warn("Qdrant delete by {} failed for org {}: {}", scope, companyId, ex.getMessage());
            return VectorDeleteResult.failure(1, ex.getMessage());
        }
    }

    private static Map<String, Object> matchField(String key, Object value) {
        return Map.of("key", key, "match", Map.of("value", value == null ? "" : value));
    }

    private static Map<String, Object> matchKnowledgeBases(List<String> knowledgeBaseIds) {
        if (knowledgeBaseIds.size() == 1) {
            return matchField("knowledge_base_id", knowledgeBaseIds.get(0));
        }
        return Map.of("key", "knowledge_base_id", "match", Map.of("any", knowledgeBaseIds));
    }

    private static Long nullableLong(JsonNode payload, String field) {
        JsonNode value = payload.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.canConvertToLong()) {
            return value.asLong();
        }
        String text = value.asText("");
        if (text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer nullableInteger(JsonNode payload, String field) {
        JsonNode value = payload.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.canConvertToInt()) {
            return value.asInt();
        }
        String text = value.asText("");
        if (text.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Optional<JsonNode> collectionMetadata() {
        try {
            String response = restClient.get()
                    .uri("/collections/{name}", collection)
                    .retrieve()
                    .body(String.class);
            return Optional.of(objectMapper.readTree(response == null ? "{}" : response));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            log.debug("Qdrant collection probe: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.debug("Qdrant collection probe failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void validateCollectionDimension(JsonNode metadata) {
        Optional<Integer> actual = extractCollectionVectorSize(metadata);
        if (actual.isEmpty()) {
            log.warn("Qdrant collection {} exists but vector dimension could not be read; expected {}", collection, dimension);
            return;
        }
        if (actual.get() != dimension) {
            log.error("Qdrant collection {} vector dimension mismatch: expected {}, actual {}. "
                            + "KB indexing will fail until the collection is rebuilt or app.kb.embedding.dimension is corrected.",
                    collection, dimension, actual.get());
        }
    }

    static Optional<Integer> extractCollectionVectorSize(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return Optional.empty();
        }
        JsonNode vectors = root.path("result").path("config").path("params").path("vectors");
        if (vectors.path("size").canConvertToInt()) {
            return Optional.of(vectors.path("size").asInt());
        }
        if (vectors.isObject()) {
            Integer first = null;
            Iterator<JsonNode> items = vectors.elements();
            while (items.hasNext()) {
                JsonNode item = items.next();
                if (!item.path("size").canConvertToInt()) {
                    return Optional.empty();
                }
                int size = item.path("size").asInt();
                if (first == null) {
                    first = size;
                } else if (first != size) {
                    return Optional.empty();
                }
            }
            return first == null ? Optional.empty() : Optional.of(first);
        }
        return Optional.empty();
    }

    private void createCollection() throws Exception {
        Map<String, Object> vectors = Map.of("size", dimension, "distance", "Cosine");
        Map<String, Object> body = Map.of("vectors", vectors);
        String json = objectMapper.writeValueAsString(body);
        try {
            restClient.put()
                    .uri("/collections/{name}", collection)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                return;
            }
            String resp = e.getResponseBodyAsString();
            if (resp != null && resp.toLowerCase().contains("already")) {
                return;
            }
            throw e;
        }
    }

    private void putJson(String path, Object body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        restClient.put()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .retrieve()
                .toBodilessEntity();
    }

    private JsonNode postJson(String path, Object body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        String response = restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .retrieve()
                .body(String.class);
        return objectMapper.readTree(response == null ? "{}" : response);
    }
}
