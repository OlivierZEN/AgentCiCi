package com.codehouse.ciciassistant.kb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            @Value("${app.kb.embedding.dimension:16}") int dimension) {
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
            if (!collectionExists()) {
                createCollection();
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
        payload.put("org_id", command.orgId());
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
        must.add(matchField("org_id", query.orgId()));
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
    public VectorDeleteResult deleteByVectorIds(String orgId, List<String> vectorIds) {
        if (vectorIds == null || vectorIds.isEmpty()) {
            return VectorDeleteResult.success(0, 0);
        }
        try {
            postJson("/collections/" + collection + "/points/delete?wait=true", Map.of("points", vectorIds));
            return VectorDeleteResult.success(vectorIds.size(), vectorIds.size());
        } catch (Exception ex) {
            log.warn("Qdrant delete by vector ids failed for org {}: {}", orgId, ex.getMessage());
            return VectorDeleteResult.failure(vectorIds.size(), ex.getMessage());
        }
    }

    @Override
    public VectorDeleteResult deleteByDocument(String orgId, String knowledgeBaseId, Long documentId) {
        if (documentId == null) {
            return VectorDeleteResult.success(0, 0);
        }
        List<Map<String, Object>> must = new ArrayList<>();
        must.add(matchField("org_id", orgId));
        must.add(matchField("knowledge_base_id", knowledgeBaseId));
        must.add(matchField("document_id", documentId));
        return deleteByFilter(orgId, Map.of("must", must), "document " + documentId);
    }

    @Override
    public VectorDeleteResult deleteByKnowledgeBase(String orgId, String knowledgeBaseId) {
        List<Map<String, Object>> must = new ArrayList<>();
        must.add(matchField("org_id", orgId));
        must.add(matchField("knowledge_base_id", knowledgeBaseId));
        return deleteByFilter(orgId, Map.of("must", must), "knowledge base " + knowledgeBaseId);
    }

    private VectorDeleteResult deleteByFilter(String orgId, Map<String, Object> filter, String scope) {
        try {
            postJson("/collections/" + collection + "/points/delete?wait=true", Map.of("filter", filter));
            return VectorDeleteResult.success(1, 1);
        } catch (Exception ex) {
            log.warn("Qdrant delete by {} failed for org {}: {}", scope, orgId, ex.getMessage());
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

    private boolean collectionExists() {
        try {
            restClient.get()
                    .uri("/collections/{name}", collection)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return false;
            }
            log.debug("Qdrant collection probe: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.debug("Qdrant collection probe failed: {}", e.getMessage());
            return false;
        }
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
