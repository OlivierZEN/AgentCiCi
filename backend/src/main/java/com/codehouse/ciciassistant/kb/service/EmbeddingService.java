package com.codehouse.ciciassistant.kb.service;

import com.codehouse.ciciassistant.ai.service.ModelInvocationResolver;
import com.codehouse.ciciassistant.ai.service.ModelInvocationResolver.ResolvedModelInvocation;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class EmbeddingService {

    private final ModelInvocationResolver modelInvocationResolver;

    public EmbeddingService(ModelInvocationResolver modelInvocationResolver) {
        this.modelInvocationResolver = modelInvocationResolver;
    }

    /**
     * Embeddings are always resolved from the tenant's published knowledge-embedding scene route.
     * Callers may select vector dimensions for storage compatibility, but cannot select a provider,
     * model, endpoint, or credential.
     */
    public List<Float> embed(String companyId, Integer requestedDimension, String text) {
        return embedForScene(companyId, "knowledge-embedding", requestedDimension, text);
    }

    public List<Float> embedForScene(String companyId, String sceneCode, Integer requestedDimension, String text) {
        int targetDimension = Math.max(4, requestedDimension == null ? 1024 : requestedDimension);
        ResolvedModelInvocation invocation = modelInvocationResolver.resolve(companyId, sceneCode);
        if ("ollama".equals(invocation.providerCode())) {
            return embedWithOllama(invocation, text);
        }
        return embedWithOpenAiCompatible(invocation, targetDimension, text);
    }

    private List<Float> embedWithOpenAiCompatible(ResolvedModelInvocation invocation,
                                                  int targetDimension,
                                                  String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", invocation.modelName());
        body.put("input", text == null ? "" : text);
        body.put("dimensions", targetDimension);
        JsonNode root = RestClient.builder()
                .baseUrl(trimSlash(invocation.apiBaseUrl()))
                .defaultHeader("Authorization", "Bearer " + invocation.apiKey())
                .build()
                .post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        JsonNode vector = root == null ? null : root.path("data").path(0).path("embedding");
        return parseEmbeddingVector(vector, targetDimension, invocation.providerCode() + "/" + invocation.modelName());
    }

    private List<Float> embedWithOllama(ResolvedModelInvocation invocation, String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", invocation.modelName());
        body.put("input", text == null ? "" : text);
        JsonNode root = RestClient.builder()
                .baseUrl(trimSlash(invocation.apiBaseUrl()))
                .build()
                .post()
                .uri("/api/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        JsonNode vector = root == null ? null : root.path("embeddings").path(0);
        if (vector == null || !vector.isArray()) {
            vector = root == null ? null : root.path("embedding");
        }
        return parseEmbeddingVector(vector, null, invocation.providerCode() + "/" + invocation.modelName());
    }

    private List<Float> parseEmbeddingVector(JsonNode vector, Integer expectedDimension, String source) {
        if (vector == null || !vector.isArray() || vector.isEmpty()) {
            throw new IllegalStateException("Embedding response is empty: " + source);
        }
        ArrayList<Float> out = new ArrayList<>(vector.size());
        for (JsonNode item : vector) {
            out.add((float) item.asDouble());
        }
        if (expectedDimension != null && out.size() != expectedDimension) {
            throw new IllegalStateException("Embedding dimension mismatch for " + source
                    + ": expected " + expectedDimension + ", got " + out.size());
        }
        return out;
    }

    private String trimSlash(String input) {
        String s = input == null ? "" : input.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
