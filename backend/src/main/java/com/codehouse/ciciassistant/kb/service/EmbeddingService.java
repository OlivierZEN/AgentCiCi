package com.codehouse.ciciassistant.kb.service;

import com.codehouse.ciciassistant.model.service.ModelProviderService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class EmbeddingService {

    private final int dimension;
    private final String defaultProvider;
    private final String defaultModel;
    private final ModelProviderService modelProviderService;

    public EmbeddingService(@Value("${app.kb.embedding.dimension:1024}") int dimension,
                            @Value("${app.kb.embedding.provider:local}") String defaultProvider,
                            @Value("${app.kb.embedding.model:local-hash}") String defaultModel,
                            ModelProviderService modelProviderService) {
        this.dimension = Math.max(4, dimension);
        this.defaultProvider = defaultProvider == null || defaultProvider.isBlank() ? "local" : defaultProvider.trim();
        this.defaultModel = defaultModel == null || defaultModel.isBlank() ? "local-hash" : defaultModel.trim();
        this.modelProviderService = modelProviderService;
    }

    public List<Float> embed(String text) {
        return localHashEmbedding(text, dimension);
    }

    public List<Float> embed(String orgId, String providerCode, String modelName, Integer requestedDimension, String text) {
        String provider = normalize(providerCode, defaultProvider);
        String model = normalize(modelName, defaultModel);
        int targetDimension = Math.max(4, requestedDimension == null ? dimension : requestedDimension);
        if ("local".equals(provider) || "local-hash".equals(model)) {
            return localHashEmbedding(text, targetDimension);
        }
        if (ModelProviderService.PROVIDER_OLLAMA.equals(provider)) {
            return embedWithOllama(orgId, model, text);
        }
        return embedWithOpenAiCompatible(orgId, provider, model, targetDimension, text);
    }

    private List<Float> embedWithOpenAiCompatible(String orgId,
                                                  String providerCode,
                                                  String modelName,
                                                  int targetDimension,
                                                  String text) {
        Map<String, String> credentials = modelProviderService.credentialsForProvider(orgId, providerCode);
        if (!Boolean.parseBoolean(credentials.getOrDefault("enabled", "false"))) {
            throw new IllegalStateException("Embedding provider is disabled: " + providerCode);
        }
        String apiKey = credentials.getOrDefault("apiKey", "");
        if (apiKey.isBlank()) {
            throw new IllegalStateException("Embedding provider API Key is empty: " + providerCode);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("input", text == null ? "" : text);
        body.put("dimensions", targetDimension);
        JsonNode root = RestClient.builder()
                .baseUrl(trimSlash(credentials.get("apiBaseUrl")))
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build()
                .post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        JsonNode vector = root == null ? null : root.path("data").path(0).path("embedding");
        return parseEmbeddingVector(vector, targetDimension, providerCode + "/" + modelName);
    }

    private List<Float> embedWithOllama(String orgId, String modelName, String text) {
        Map<String, String> credentials = modelProviderService.credentialsForProvider(orgId, ModelProviderService.PROVIDER_OLLAMA);
        if (!Boolean.parseBoolean(credentials.getOrDefault("enabled", "false"))) {
            throw new IllegalStateException("Embedding provider is disabled: " + ModelProviderService.PROVIDER_OLLAMA);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("input", text == null ? "" : text);
        JsonNode root = RestClient.builder()
                .baseUrl(trimSlash(credentials.get("apiBaseUrl")))
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
        return parseEmbeddingVector(vector, null, "ollama/" + modelName);
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

    private List<Float> localHashEmbedding(String text, int targetDimension) {
        String input = text == null ? "" : text;
        float[] values = new float[Math.max(4, targetDimension)];
        for (int i = 0; i < input.length(); i++) {
            int slot = i % values.length;
            values[slot] += ((input.charAt(i) % 31) / 31.0f);
        }
        ArrayList<Float> out = new ArrayList<>(values.length);
        for (float v : values) {
            out.add(v);
        }
        return out;
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String trimSlash(String input) {
        String s = input == null ? "" : input.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
