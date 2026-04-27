package com.codehouse.ciciassistant.model.service;

import com.codehouse.ciciassistant.model.domain.ModelProviderConfigEntity;
import com.codehouse.ciciassistant.model.domain.ModelProviderConfigRepository;
import com.codehouse.ciciassistant.model.domain.OrgModelConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelProviderService {

    public static final String PROVIDER_ALIYUN = "aliyun-bailian";
    public static final String PROVIDER_OLLAMA = "ollama-local";
    public static final String PROVIDER_ANTHROPIC = "anthropic";
    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_DEEPSEEK = "deepseek";

    private static final String FETCH_OPENAI_STYLE = "openai-compatible";
    private static final String FETCH_OLLAMA = "ollama";
    private static final String FETCH_ANTHROPIC = "anthropic";

    private final ModelProviderConfigRepository providerRepository;
    private final OrgModelConfigRepository modelConfigRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private final String aliyunDefaultBaseUrl;
    private final String aliyunDefaultApiKey;

    private static final Map<String, ProviderDef> PROVIDER_DEFS = Map.ofEntries(
            Map.entry(PROVIDER_ALIYUN,
            new ProviderDef(
                    PROVIDER_ALIYUN,
                    "阿里云百炼",
                    "https://dashscope.aliyuncs.com/compatible-mode/v1",
                    "https://help.aliyun.com/zh/model-studio/",
                    FETCH_OPENAI_STYLE,
                    List.of("deepseek-v3.1", "glm-5", "kimi-k2-250711", "minimax-m2", "qwen3.5-plus", "qwen3.5-flash", "qwen3-max")
            )),
            Map.entry(PROVIDER_OLLAMA,
            new ProviderDef(
                    PROVIDER_OLLAMA,
                    "本地 Ollama",
                    "http://127.0.0.1:11434",
                    "https://github.com/ollama/ollama",
                    FETCH_OLLAMA,
                    List.of("qwen2.5:7b", "llama3.1:8b", "deepseek-r1:8b")
            )),
            Map.entry(PROVIDER_ANTHROPIC,
            new ProviderDef(
                    PROVIDER_ANTHROPIC,
                    "Anthropic",
                    "https://api.anthropic.com",
                    "https://docs.anthropic.com/",
                    FETCH_ANTHROPIC,
                    List.of("claude-3-7-sonnet-latest", "claude-sonnet-4-5", "claude-opus-4-1")
            )),
            Map.entry(PROVIDER_OPENAI,
            new ProviderDef(
                    PROVIDER_OPENAI,
                    "OpenAI",
                    "https://api.openai.com/v1",
                    "https://platform.openai.com/docs/models",
                    FETCH_OPENAI_STYLE,
                    List.of("gpt-4.1", "gpt-4o", "gpt-4o-mini")
            )),
            Map.entry(PROVIDER_DEEPSEEK,
            new ProviderDef(
                    PROVIDER_DEEPSEEK,
                    "深度求索",
                    "https://api.deepseek.com/v1",
                    "https://platform.deepseek.com/api-docs/",
                    FETCH_OPENAI_STYLE,
                    List.of("deepseek-chat", "deepseek-reasoner")
            ))
    );

    public ModelProviderService(ModelProviderConfigRepository providerRepository,
                                OrgModelConfigRepository modelConfigRepository,
                                ObjectMapper objectMapper,
                                @Value("${app.model.aliyun.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String aliyunDefaultBaseUrl,
                                @Value("${app.model.aliyun.api-key:}") String aliyunDefaultApiKey) {
        this.providerRepository = providerRepository;
        this.modelConfigRepository = modelConfigRepository;
        this.objectMapper = objectMapper;
        this.aliyunDefaultBaseUrl = aliyunDefaultBaseUrl;
        this.aliyunDefaultApiKey = aliyunDefaultApiKey;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();
    }

    @Transactional
    public List<Map<String, Object>> listProviders(String orgId) {
        ensureBuiltinRows(orgId);
        return providerRepository.findByOrgIdOrderByIdAsc(orgId).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public Map<String, Object> updateProvider(String orgId,
                                              String providerCode,
                                              Boolean enabled,
                                              String apiBaseUrl,
                                              String apiKey) {
        ProviderDef def = requireDef(providerCode);
        ModelProviderConfigEntity entity = requireProviderEntity(orgId, providerCode);

        if (enabled != null) {
            entity.setEnabled(enabled);
        }
        if (apiBaseUrl != null && !apiBaseUrl.isBlank()) {
            entity.setApiBaseUrl(apiBaseUrl.trim());
        }
        if (apiKey != null) {
            entity.setApiKey(apiKey.trim());
        }

        entity.setProviderName(def.providerName());
        entity.touch();
        return toView(providerRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> providerModels(String orgId, String providerCode) {
        ProviderDef def = requireDef(providerCode);
        ModelProviderConfigEntity entity = requireProviderEntity(orgId, providerCode);

        List<Map<String, Object>> configured = modelConfigRepository.findByOrgIdAndProvider(orgId, providerCode).stream()
                .map(m -> Map.<String, Object>of(
                        "sceneCode", m.getSceneCode(),
                        "modelName", m.getModelName()
                ))
                .toList();

        List<String> selected = selectedModelsForProvider(entity, orgId, def);

        return Map.of(
                "providerCode", providerCode,
                "providerName", def.providerName(),
                "configuredModels", configured,
                "recommendedModels", def.defaultModels(),
                "selectedModels", selected,
                "apiBaseUrl", entity.getApiBaseUrl(),
                "enabled", entity.isEnabled()
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> agentBaseModels(String orgId) {
        ensureBuiltinRows(orgId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (ModelProviderConfigEntity entity : providerRepository.findByOrgIdOrderByIdAsc(orgId)) {
            if (!entity.isEnabled()) {
                continue;
            }
            ProviderDef def = requireDef(entity.getProviderCode());
            List<String> selected = selectedModelsForProvider(entity, orgId, def);
            for (String modelName : selected) {
                if (modelName == null || modelName.isBlank()) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("providerCode", entity.getProviderCode());
                row.put("providerName", def.providerName());
                row.put("modelName", modelName);
                row.put("displayLabel", modelName + " · " + def.providerName());
                out.add(row);
            }
        }
        return out;
    }

    @Transactional
    public Map<String, Object> updateSelectedModels(String orgId, String providerCode, List<String> selectedModels) {
        ProviderDef def = requireDef(providerCode);
        ModelProviderConfigEntity entity = requireProviderEntity(orgId, providerCode);
        Map<String, Object> config = new LinkedHashMap<>(readJsonToMap(entity.getConfigJson()));
        List<String> normalized = selectedModels == null ? List.of() : selectedModels.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        config.put("selectedModels", normalized);
        entity.setConfigJson(writeJson(config));
        entity.touch();
        providerRepository.save(entity);
        return Map.of(
                "providerCode", providerCode,
                "providerName", def.providerName(),
                "selectedModels", normalized
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> checkProvider(String orgId, String providerCode) {
        List<String> models = fetchRemoteModels(orgId, providerCode);
        return Map.of(
                "providerCode", providerCode,
                "ok", true,
                "modelCount", models.size(),
                "sampleModels", models.stream().limit(8).toList()
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> fetchProviderModels(String orgId, String providerCode) {
        List<ModelDetail> details = fetchRemoteModelDetails(orgId, providerCode);
        List<String> models = details.stream().map(ModelDetail::modelName).toList();
        return Map.of(
                "providerCode", providerCode,
                "count", models.size(),
                "models", models,
                "modelDetails", details
        );
    }

    @Transactional(readOnly = true)
    public Map<String, String> credentialsForProvider(String orgId, String providerCode) {
        ModelProviderConfigEntity e = requireProviderEntity(orgId, providerCode);
        return Map.of(
                "apiBaseUrl", e.getApiBaseUrl(),
                "apiKey", e.getApiKey(),
                "providerCode", e.getProviderCode(),
                "enabled", String.valueOf(e.isEnabled())
        );
    }

    private List<String> fetchRemoteModels(String orgId, String providerCode) {
        return fetchRemoteModelDetails(orgId, providerCode).stream()
                .map(ModelDetail::modelName)
                .toList();
    }

    private List<ModelDetail> fetchRemoteModelDetails(String orgId, String providerCode) {
        ProviderDef def = requireDef(providerCode);
        ModelProviderConfigEntity entity = requireProviderEntity(orgId, providerCode);

        if (!entity.isEnabled()) {
            throw new IllegalArgumentException("当前厂商已停用，请先启用后再检测/获取模型列表");
        }

        return switch (def.fetchKind()) {
            case FETCH_OPENAI_STYLE -> fetchOpenAiCompatibleModels(entity.getApiBaseUrl(), entity.getApiKey());
            case FETCH_OLLAMA -> fetchOllamaModels(entity.getApiBaseUrl());
            case FETCH_ANTHROPIC -> fetchAnthropicModels(entity.getApiBaseUrl(), entity.getApiKey());
            default -> throw new IllegalArgumentException("Unsupported provider fetch type: " + def.fetchKind());
        };
    }

    private List<ModelDetail> fetchOpenAiCompatibleModels(String baseUrl, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API Key 不能为空");
        }
        String endpoint = appendPath(baseUrl, "/models");
        HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(20))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .GET()
                .build();

        return parseDataModelDetails(send(req, "OpenAI-compatible /models 调用失败"));
    }

    private List<ModelDetail> fetchAnthropicModels(String baseUrl, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API Key 不能为空");
        }
        String endpoint = normalizeAnthropicModelsEndpoint(baseUrl);
        HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(20))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .GET()
                .build();

        return parseDataModelDetails(send(req, "Anthropic /v1/models 调用失败"));
    }

    private List<ModelDetail> fetchOllamaModels(String baseUrl) {
        String endpoint = appendPath(baseUrl, "/api/tags");
        HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        String body = send(req, "Ollama /api/tags 调用失败");
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode models = root.path("models");
            if (!models.isArray()) {
                return List.of();
            }
            Set<ModelDetail> out = new LinkedHashSet<>();
            for (JsonNode item : models) {
                String name = item.path("name").asText("").trim();
                if (!name.isEmpty()) {
                    out.add(new ModelDetail(name, inferCapabilitiesByName(name)));
                }
            }
            return new ArrayList<>(out);
        } catch (Exception e) {
            throw new IllegalArgumentException("解析 Ollama 模型列表失败: " + e.getMessage());
        }
    }

    private List<ModelDetail> parseDataModelDetails(String jsonBody) {
        try {
            JsonNode root = objectMapper.readTree(jsonBody);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return List.of();
            }
            Set<ModelDetail> out = new LinkedHashSet<>();
            for (JsonNode item : data) {
                String id = item.path("id").asText("").trim();
                if (id.isEmpty()) {
                    id = item.path("name").asText("").trim();
                }
                if (!id.isEmpty()) {
                    List<String> caps = extractCapabilities(item, id);
                    out.add(new ModelDetail(id, caps));
                }
            }
            return new ArrayList<>(out);
        } catch (Exception e) {
            throw new IllegalArgumentException("解析模型列表失败: " + e.getMessage());
        }
    }

    private List<String> extractCapabilities(JsonNode item, String modelName) {
        LinkedHashSet<String> caps = new LinkedHashSet<>();
        caps.add("text");

        JsonNode capsNode = item.path("capabilities");
        if (capsNode.isArray()) {
            for (JsonNode n : capsNode) {
                String normalized = normalizeCapabilityToken(n.asText(""));
                if (normalized != null) caps.add(normalized);
            }
        } else if (capsNode.isObject()) {
            capsNode.fields().forEachRemaining(entry -> {
                if (entry.getValue().asBoolean(false)) {
                    String normalized = normalizeCapabilityToken(entry.getKey());
                    if (normalized != null) caps.add(normalized);
                }
            });
        }

        JsonNode modalities = item.path("input_modalities");
        if (modalities.isArray()) {
            for (JsonNode n : modalities) {
                String normalized = normalizeCapabilityToken(n.asText(""));
                if (normalized != null) caps.add(normalized);
            }
        }

        JsonNode outputModalities = item.path("output_modalities");
        if (outputModalities.isArray()) {
            for (JsonNode n : outputModalities) {
                String normalized = normalizeCapabilityToken(n.asText(""));
                if (normalized != null) caps.add(normalized);
            }
        }

        if (item.path("supports_tools").asBoolean(false)
                || item.path("function_calling").asBoolean(false)
                || item.path("tool_use").asBoolean(false)) {
            caps.add("tool");
        }
        if (item.path("supports_search").asBoolean(false)
                || item.path("web_search").asBoolean(false)
                || item.path("internet").asBoolean(false)) {
            caps.add("search");
        }
        if (item.path("supports_reasoning").asBoolean(false)
                || item.path("reasoning").asBoolean(false)
                || item.path("thinking").asBoolean(false)) {
            caps.add("reasoning");
        }
        if (item.path("supports_vision").asBoolean(false)
                || item.path("vision").asBoolean(false)
                || item.path("multimodal").asBoolean(false)) {
            caps.add("vision");
        }

        caps.addAll(inferCapabilitiesByName(modelName));
        return new ArrayList<>(caps);
    }

    private List<String> inferCapabilitiesByName(String modelName) {
        String lower = modelName.toLowerCase();
        LinkedHashSet<String> inferred = new LinkedHashSet<>();
        inferred.add("text");
        inferred.add("tool");

        if (lower.contains("reason") || lower.contains("r1") || lower.contains("o1") || lower.contains("thinking")) {
            inferred.add("reasoning");
        }
        if (lower.contains("vision") || lower.contains("vl") || lower.contains("4o") || lower.contains("omni")) {
            inferred.add("vision");
        }
        if (lower.contains("search") || lower.contains("web")) {
            inferred.add("search");
        }
        return new ArrayList<>(inferred);
    }

    private String normalizeCapabilityToken(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim().toLowerCase();

        if (v.contains("tool") || v.contains("function")) return "tool";
        if (v.contains("search") || v.contains("web") || v.contains("internet")) return "search";
        if (v.contains("reason") || v.contains("thinking") || v.contains("logic")) return "reasoning";
        if (v.contains("vision") || v.contains("image") || v.contains("multimodal")
                || v.contains("video") || v.contains("audio")) return "vision";
        if (v.contains("text") || v.contains("chat")) return "text";
        return null;
    }

    private String send(HttpRequest req, String errorPrefix) {
        try {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IllegalArgumentException(errorPrefix + "，HTTP " + resp.statusCode() + "：" + clip(resp.body()));
            }
            return resp.body();
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception e) {
            throw new IllegalArgumentException(errorPrefix + "：" + e.getMessage());
        }
    }

    private String clip(String body) {
        if (body == null) return "";
        String text = body.replaceAll("\\s+", " ").trim();
        return text.length() <= 180 ? text : text.substring(0, 180) + "...";
    }

    private List<String> selectedModelsForProvider(ModelProviderConfigEntity entity, String orgId, ProviderDef def) {
        Map<String, Object> config = readJsonToMap(entity.getConfigJson());
        Object raw = config.get("selectedModels");
        List<String> fromConfig = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                String text = String.valueOf(item).trim();
                if (!text.isBlank() && !fromConfig.contains(text)) {
                    fromConfig.add(text);
                }
            }
        }
        if (!fromConfig.isEmpty()) {
            return fromConfig;
        }
        List<String> configured = modelConfigRepository.findByOrgIdAndProvider(orgId, entity.getProviderCode()).stream()
                .map(m -> m.getModelName() == null ? "" : m.getModelName().trim())
                .filter(v -> !v.isBlank())
                .distinct()
                .toList();
        if (!configured.isEmpty()) {
            return configured;
        }
        return def.defaultModels();
    }

    private String writeJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map == null ? Map.of() : map);
        } catch (Exception e) {
            throw new IllegalArgumentException("写入模型厂商配置失败");
        }
    }

    private void ensureBuiltinRows(String orgId) {
        for (ProviderDef def : sortedDefs()) {
            boolean exists = providerRepository.findByOrgIdAndProviderCode(orgId, def.providerCode()).isPresent();
            if (!exists) {
                String defaultKey = PROVIDER_ALIYUN.equals(def.providerCode()) ? nullableToBlank(aliyunDefaultApiKey) : "";
                String defaultUrl = PROVIDER_ALIYUN.equals(def.providerCode()) ? nullableToBlank(aliyunDefaultBaseUrl) : def.defaultBaseUrl();
                providerRepository.save(new ModelProviderConfigEntity(
                        orgId,
                        def.providerCode(),
                        def.providerName(),
                        true,
                        defaultUrl,
                        defaultKey,
                        "{}"
                ));
            }
        }
    }

    private List<ProviderDef> sortedDefs() {
        return List.of(
                PROVIDER_DEFS.get(PROVIDER_ALIYUN),
                PROVIDER_DEFS.get(PROVIDER_DEEPSEEK),
                PROVIDER_DEFS.get(PROVIDER_OLLAMA),
                PROVIDER_DEFS.get(PROVIDER_ANTHROPIC),
                PROVIDER_DEFS.get(PROVIDER_OPENAI)
        );
    }

    private Map<String, Object> toView(ModelProviderConfigEntity e) {
        ProviderDef def = requireDef(e.getProviderCode());
        Map<String, Object> config = readJsonToMap(e.getConfigJson());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", e.getId());
        out.put("providerCode", e.getProviderCode());
        out.put("providerName", e.getProviderName());
        out.put("enabled", e.isEnabled());
        out.put("apiBaseUrl", nullableToBlank(e.getApiBaseUrl()));
        out.put("apiKeyMasked", maskApiKey(e.getApiKey()));
        out.put("apiKeySet", e.getApiKey() != null && !e.getApiKey().isBlank());
        out.put("defaultBaseUrl", def.defaultBaseUrl());
        out.put("docUrl", def.docUrl());
        out.put("config", config);
        out.put("createdAt", e.getCreatedAt().toString());
        out.put("updatedAt", e.getUpdatedAt().toString());
        return out;
    }

    private ProviderDef requireDef(String providerCode) {
        ProviderDef def = PROVIDER_DEFS.get(providerCode);
        if (def == null) {
            throw new IllegalArgumentException("不支持的模型厂商: " + providerCode);
        }
        return def;
    }

    private ModelProviderConfigEntity requireProviderEntity(String orgId, String providerCode) {
        return providerRepository.findByOrgIdAndProviderCode(orgId, providerCode)
                .orElseThrow(() -> new IllegalArgumentException("模型厂商配置不存在: " + providerCode));
    }

    private String normalizeAnthropicModelsEndpoint(String baseUrl) {
        String trimmed = trimSlash(baseUrl);
        if (trimmed.endsWith("/v1")) {
            return trimmed + "/models";
        }
        return trimmed + "/v1/models";
    }

    private String appendPath(String baseUrl, String path) {
        return trimSlash(baseUrl) + path;
    }

    private String trimSlash(String input) {
        String s = nullableToBlank(input).trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private String nullableToBlank(String v) {
        return v == null ? "" : v;
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return "";
        String s = apiKey.trim();
        if (s.length() <= 8) return "********";
        return s.substring(0, 4) + "********" + s.substring(s.length() - 4);
    }

    private Map<String, Object> readJsonToMap(String json) {
        try {
            if (json == null || json.isBlank()) return Map.of();
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private record ProviderDef(
            String providerCode,
            String providerName,
            String defaultBaseUrl,
            String docUrl,
            String fetchKind,
            List<String> defaultModels
    ) {
    }

    private record ModelDetail(
            String modelName,
            List<String> capabilities
    ) {
    }
}
