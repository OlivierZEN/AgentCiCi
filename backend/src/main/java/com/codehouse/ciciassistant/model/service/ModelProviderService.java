package com.codehouse.ciciassistant.model.service;

import com.codehouse.ciciassistant.auth.config.PlatformAccountProperties;
import com.codehouse.ciciassistant.model.domain.ModelProviderConfigEntity;
import com.codehouse.ciciassistant.model.domain.ModelProviderConfigRepository;
import com.codehouse.ciciassistant.model.domain.CompanyModelConfigEntity;
import com.codehouse.ciciassistant.model.domain.CompanyModelConfigRepository;
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
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelProviderService {

    public static final String PROVIDER_ALIYUN = "aliyun-bailian";
    public static final String PROVIDER_OLLAMA = "ollama-local";
    public static final String PROVIDER_LMSTUDIO = "lmstudio-local";
    public static final String PROVIDER_ANTHROPIC = "anthropic";
    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_DEEPSEEK = "deepseek";
    public static final String PROVIDER_ONEKEYTOKEN = "onekeytoken";
    public static final String ONEKEYTOKEN_AUTO_MODEL = "onekeytoken/auto";

    private static final String FETCH_OPENAI_STYLE = "openai-compatible";
    private static final String FETCH_OLLAMA = "ollama";
    private static final String FETCH_ANTHROPIC = "anthropic";
    private static final String FETCH_REMOTE_UNAVAILABLE = "remote-unavailable";
    private static final List<SceneRouteDef> SCENE_ROUTES = List.of(
            new SceneRouteDef("chat", "智能体对话", "员工工作台、渠道消息和 OpenAPI chat 默认模型。"),
            new SceneRouteDef("skill-authoring", "技能创作", "Skill 生成、技能包标准化和编排草稿模型。"),
            new SceneRouteDef("ontology-modeling", "本体建模", "业务语义建模、草稿提案与结构化本体生成模型。"),
            new SceneRouteDef("meeting-minutes", "AI 听记", "会议纪要、行动项和拜访记录生成模型。"),
            new SceneRouteDef("customer-insight", "客户洞察", "客户洞察摘要、一客一策和客户经营分析模型。")
    );

    private final ModelProviderConfigRepository providerRepository;
    private final CompanyModelConfigRepository modelConfigRepository;
    private final PlatformAccountProperties platformAccountProperties;
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
                    true,
                    List.of("deepseek-v3.1", "glm-5", "kimi-k2-250711", "minimax-m2", "qwen3.5-plus", "qwen3.5-flash", "qwen3-max")
            )),
            Map.entry(PROVIDER_OLLAMA,
            new ProviderDef(
                    PROVIDER_OLLAMA,
                    "本地 Ollama",
                    "http://127.0.0.1:11434",
                    "https://github.com/ollama/ollama",
                    FETCH_OLLAMA,
                    false,
                    List.of("qwen2.5:7b", "llama3.1:8b", "deepseek-r1:8b")
            )),
            Map.entry(PROVIDER_LMSTUDIO,
            new ProviderDef(
                    PROVIDER_LMSTUDIO,
                    "本地 LM Studio",
                    "http://127.0.0.1:1234/v1",
                    "https://lmstudio.ai/docs",
                    FETCH_OPENAI_STYLE,
                    false,
                    List.of("qwen3.5-35b-a3b", "llama-3.1-8b-instruct", "text-embedding-nomic-embed-text-v1.5")
            )),
            Map.entry(PROVIDER_ANTHROPIC,
            new ProviderDef(
                    PROVIDER_ANTHROPIC,
                    "Anthropic",
                    "https://api.anthropic.com",
                    "https://docs.anthropic.com/",
                    FETCH_ANTHROPIC,
                    true,
                    List.of("claude-3-7-sonnet-latest", "claude-sonnet-4-5", "claude-opus-4-1")
            )),
            Map.entry(PROVIDER_OPENAI,
            new ProviderDef(
                    PROVIDER_OPENAI,
                    "OpenAI",
                    "https://api.openai.com/v1",
                    "https://platform.openai.com/docs/models",
                    FETCH_OPENAI_STYLE,
                    true,
                    List.of("gpt-4.1", "gpt-4o", "gpt-4o-mini")
            )),
            Map.entry(PROVIDER_DEEPSEEK,
            new ProviderDef(
                    PROVIDER_DEEPSEEK,
                    "深度求索",
                    "https://api.deepseek.com/v1",
                    "https://platform.deepseek.com/api-docs/",
                    FETCH_OPENAI_STYLE,
                    true,
                    List.of("deepseek-chat", "deepseek-reasoner")
            )),
            Map.entry(PROVIDER_ONEKEYTOKEN,
            new ProviderDef(
                    PROVIDER_ONEKEYTOKEN,
                    "OneKeyToken",
                    "https://my.onekeytoken.com/v1",
                    "https://my.onekeytoken.com",
                    FETCH_REMOTE_UNAVAILABLE,
                    true,
                    List.of()
            ))
    );

    public ModelProviderService(ModelProviderConfigRepository providerRepository,
                                CompanyModelConfigRepository modelConfigRepository,
                                PlatformAccountProperties platformAccountProperties,
                                ObjectMapper objectMapper,
                                @Value("${app.model.aliyun.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String aliyunDefaultBaseUrl,
                                @Value("${app.model.aliyun.api-key:}") String aliyunDefaultApiKey) {
        this.providerRepository = providerRepository;
        this.modelConfigRepository = modelConfigRepository;
        this.platformAccountProperties = platformAccountProperties;
        this.objectMapper = objectMapper;
        this.aliyunDefaultBaseUrl = aliyunDefaultBaseUrl;
        this.aliyunDefaultApiKey = aliyunDefaultApiKey;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(12))
                .build();
    }

    @Transactional
    public List<Map<String, Object>> listProviders(String companyId) {
        ensureBuiltinRows(companyId);
        return providerRepository.findByCompanyIdOrderByIdAsc(companyId).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public List<Map<String, Object>> listPlatformProviders() {
        return listProviders(platformScopeId());
    }

    @Transactional
    public Map<String, Object> updateProvider(String companyId,
                                              String providerCode,
                                              Boolean enabled,
                                              String apiBaseUrl,
                                              String apiKey) {
        ProviderDef def = requireDef(providerCode);
        ModelProviderConfigEntity entity = requireProviderEntity(companyId, providerCode);

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
    public Map<String, Object> providerModels(String companyId, String providerCode) {
        ProviderDef def = requireDef(providerCode);
        ModelProviderConfigEntity entity = requireProviderEntity(companyId, providerCode);

        List<Map<String, Object>> configured = modelConfigRepository.findByCompanyIdAndProvider(companyId, providerCode).stream()
                .map(m -> Map.<String, Object>of(
                        "sceneCode", m.getSceneCode(),
                        "modelName", m.getModelName()
                ))
                .toList();

        List<String> selected = configuredModelsForProvider(entity, companyId);

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

    @Transactional
    public Map<String, Object> updatePlatformProvider(String providerCode,
                                                      Boolean enabled,
                                                      String apiBaseUrl,
                                                      String apiKey) {
        ensureBuiltinRows(platformScopeId());
        return updateProvider(platformScopeId(), providerCode, enabled, apiBaseUrl, apiKey);
    }

    @Transactional
    public Map<String, Object> platformProviderModels(String providerCode) {
        ensureBuiltinRows(platformScopeId());
        return providerModels(platformScopeId(), providerCode);
    }

    @Transactional
    public Map<String, Object> updatePlatformSelectedModels(String providerCode, List<String> selectedModels) {
        ensureBuiltinRows(platformScopeId());
        return updateSelectedModels(platformScopeId(), providerCode, selectedModels);
    }

    @Transactional
    public Map<String, Object> checkPlatformProvider(String providerCode) {
        ensureBuiltinRows(platformScopeId());
        return checkProvider(platformScopeId(), providerCode);
    }

    @Transactional
    public Map<String, Object> fetchPlatformProviderModels(String providerCode) {
        ensureBuiltinRows(platformScopeId());
        return fetchProviderModels(platformScopeId(), providerCode);
    }

    @Transactional
    public List<Map<String, Object>> agentBaseModels(String companyId) {
        String scopeId = platformScopeId();
        ensureBuiltinRows(scopeId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (ModelProviderConfigEntity entity : providerRepository.findByCompanyIdOrderByIdAsc(scopeId)) {
            if (!entity.isEnabled()) {
                continue;
            }
            ProviderDef def = requireDef(entity.getProviderCode());
            List<String> selected = configuredModelsForProvider(entity, scopeId);
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
    public Map<String, Object> platformModelRouteSettings() {
        List<Map<String, Object>> candidates = agentBaseModels(platformScopeId());
        return Map.of(
                "routes", SCENE_ROUTES.stream().map(scene -> routeView(scene, candidates)).toList(),
                "modelCandidates", candidates
        );
    }

    @Transactional
    public Map<String, Object> updatePlatformModelRoute(String sceneCode, String providerCode, String modelName) {
        SceneRouteDef scene = requireSceneRoute(sceneCode);
        ModelChoice choice = requirePlatformModelChoice(providerCode, modelName);
        String scopeId = platformScopeId();
        CompanyModelConfigEntity entity = modelConfigRepository.findByCompanyIdAndSceneCode(scopeId, scene.sceneCode())
                .orElse(new CompanyModelConfigEntity(scopeId, scene.sceneCode(), choice.providerCode(), choice.modelName()));
        entity.update(choice.providerCode(), choice.modelName());
        modelConfigRepository.save(entity);
        return routeView(scene, agentBaseModels(scopeId));
    }

    @Transactional
    public Map<String, Object> deletePlatformModelRoute(String sceneCode) {
        SceneRouteDef scene = requireSceneRoute(sceneCode);
        modelConfigRepository.deleteByCompanyIdAndSceneCode(platformScopeId(), scene.sceneCode());
        return routeView(scene, agentBaseModels(platformScopeId()));
    }

    @Transactional
    public Map<String, String> resolveRuntimeModelRoute(String companyId, String sceneCode, String preferredModelName) {
        List<ModelChoice> candidates = agentBaseModels(companyId).stream()
                .map(this::toModelChoice)
                .filter(choice -> choice != null)
                .toList();
        if (candidates.isEmpty()) {
            throw new IllegalStateException("暂无平台可用模型，请联系平台运营启用模型厂商。");
        }

        CompanyModelConfigEntity configured = modelConfigRepository
                .findByCompanyIdAndSceneCode(platformScopeId(), normalizeSceneCode(sceneCode))
                .orElse(null);
        ModelChoice sceneChoice = configured == null
                ? null
                : findCandidate(candidates, configured.getProvider(), configured.getModelName());
        if (sceneChoice != null) {
            return routePayload(sceneChoice, "platform_scene");
        }

        ModelChoice preferred = findPreferredCandidate(candidates, preferredModelName);
        if (preferred != null) {
            return routePayload(preferred, "agent_preferred");
        }

        return routePayload(candidates.getFirst(), "platform_default");
    }

    @Transactional
    public List<Map<String, Object>> embeddingModelOptions(String companyId) {
        String scopeId = platformScopeId();
        ensureBuiltinRows(scopeId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (ModelProviderConfigEntity entity : providerRepository.findByCompanyIdOrderByIdAsc(scopeId)) {
            if (!entity.isEnabled()) {
                continue;
            }
            ProviderDef def = requireDef(entity.getProviderCode());
            LinkedHashMap<String, EmbeddingModelDef> candidates = new LinkedHashMap<>();
            for (EmbeddingModelDef item : defaultEmbeddingModels(entity.getProviderCode())) {
                candidates.put(item.modelName(), item);
            }
            for (String modelName : configuredModelsForProvider(entity, scopeId)) {
                if (!isEmbeddingModelName(modelName)) {
                    continue;
                }
                candidates.putIfAbsent(modelName, inferEmbeddingModelDef(entity.getProviderCode(), modelName));
            }
            for (EmbeddingModelDef item : candidates.values()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("providerCode", entity.getProviderCode());
                row.put("providerName", def.providerName());
                row.put("modelName", item.modelName());
                row.put("displayLabel", item.modelName() + " · " + def.providerName());
                row.put("defaultDimension", item.defaultDimension());
                row.put("dimensionChoices", item.dimensionChoices());
                row.put("supportsCustomDimension", item.supportsCustomDimension());
                out.add(row);
            }
        }
        return out;
    }

    @Transactional
    public Map<String, Object> updateSelectedModels(String companyId, String providerCode, List<String> selectedModels) {
        ProviderDef def = requireDef(providerCode);
        ensureBuiltinRows(companyId);
        ModelProviderConfigEntity entity = requireProviderEntity(companyId, providerCode);
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
    public Map<String, Object> checkProvider(String companyId, String providerCode) {
        ProviderDef def = requireDef(providerCode);
        if (PROVIDER_ONEKEYTOKEN.equals(providerCode)) {
            return checkOneKeyTokenProvider(requireProviderEntity(companyId, providerCode), null, null, null);
        }
        List<String> models = fetchRemoteModels(companyId, providerCode);
        return Map.of(
                "providerCode", providerCode,
                "ok", true,
                "modelCount", models.size(),
                "sampleModels", models.stream().limit(8).toList(),
                "catalogSource", catalogSource(def),
                "remoteFetchSupported", supportsRemoteModelFetch(def)
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> checkPlatformProvider(String providerCode,
                                                      Boolean enabledOverride,
                                                      String apiBaseUrlOverride,
                                                      String apiKeyOverride) {
        ensureBuiltinRows(platformScopeId());
        if (!PROVIDER_ONEKEYTOKEN.equals(providerCode)) {
            return checkProvider(platformScopeId(), providerCode);
        }
        return checkOneKeyTokenProvider(
                requireProviderEntity(platformScopeId(), providerCode),
                enabledOverride,
                apiBaseUrlOverride,
                apiKeyOverride);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> fetchProviderModels(String companyId, String providerCode) {
        ProviderDef def = requireDef(providerCode);
        List<ModelDetail> details = fetchRemoteModelDetails(companyId, providerCode);
        List<String> models = details.stream().map(ModelDetail::modelName).toList();
        return Map.of(
                "providerCode", providerCode,
                "count", models.size(),
                "models", models,
                "modelDetails", details,
                "catalogSource", catalogSource(def),
                "remoteFetchSupported", supportsRemoteModelFetch(def)
        );
    }

    @Transactional
    public Map<String, String> credentialsForProvider(String companyId, String providerCode) {
        String scopeId = platformScopeId();
        ensureBuiltinRows(scopeId);
        ModelProviderConfigEntity e = requireProviderEntity(scopeId, providerCode);
        return Map.of(
                "apiBaseUrl", e.getApiBaseUrl(),
                "apiKey", e.getApiKey(),
                "providerCode", e.getProviderCode(),
                "enabled", String.valueOf(e.isEnabled()),
                "apiKeyRequired", String.valueOf(requireDef(e.getProviderCode()).apiKeyRequired())
        );
    }

    private List<String> fetchRemoteModels(String companyId, String providerCode) {
        return fetchRemoteModelDetails(companyId, providerCode).stream()
                .map(ModelDetail::modelName)
                .toList();
    }

    private List<ModelDetail> fetchRemoteModelDetails(String companyId, String providerCode) {
        ProviderDef def = requireDef(providerCode);
        ModelProviderConfigEntity entity = requireProviderEntity(companyId, providerCode);

        if (!entity.isEnabled()) {
            throw new IllegalArgumentException("当前厂商已停用，请先启用后再检测/获取模型列表");
        }

        return switch (def.fetchKind()) {
            case FETCH_OPENAI_STYLE -> fetchOpenAiCompatibleModels(entity.getApiBaseUrl(), entity.getApiKey(), def.apiKeyRequired());
            case FETCH_OLLAMA -> fetchOllamaModels(entity.getApiBaseUrl());
            case FETCH_ANTHROPIC -> fetchAnthropicModels(entity.getApiBaseUrl(), entity.getApiKey());
            case FETCH_REMOTE_UNAVAILABLE -> List.of();
            default -> throw new IllegalArgumentException("Unsupported provider fetch type: " + def.fetchKind());
        };
    }

    private Map<String, Object> checkOneKeyTokenProvider(ModelProviderConfigEntity entity,
                                                          Boolean enabledOverride,
                                                          String apiBaseUrlOverride,
                                                          String apiKeyOverride) {
        boolean enabled = enabledOverride == null ? entity.isEnabled() : enabledOverride;
        String apiBaseUrl = valueOrConfigured(apiBaseUrlOverride, entity.getApiBaseUrl());
        String apiKey = valueOrConfigured(apiKeyOverride, entity.getApiKey());
        if (!enabled) {
            throw new IllegalArgumentException("当前厂商已停用，请先启用后再检测。");
        }
        if (apiKey.isBlank()) {
            throw new IllegalArgumentException("OneKeyToken API Key 不能为空。");
        }
        if (apiBaseUrl.isBlank()) {
            throw new IllegalArgumentException("OneKeyToken API 地址不能为空。");
        }

        String requestId = "req_agentcici_onekeytoken_check_" + UUID.randomUUID().toString().replace("-", "");
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", ONEKEYTOKEN_AUTO_MODEL,
                    "messages", List.of(Map.of("role", "user", "content", "Reply with OK only.")),
                    "max_tokens", 8,
                    "temperature", 0,
                    "stream", false));
        } catch (Exception e) {
            throw new IllegalArgumentException("无法构造 OneKeyToken 检测请求。");
        }

        String responseBody = sendOneKeyTokenValidation(HttpRequest.newBuilder(URI.create(appendPath(apiBaseUrl, "/chat/completions")))
                .timeout(Duration.ofSeconds(30))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header("x-request-id", requestId)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build());
        try {
            JsonNode response = objectMapper.readTree(responseBody);
            if (!response.path("choices").isArray() || response.path("choices").isEmpty()) {
                throw new IllegalArgumentException("OneKeyToken 检测失败：网关未返回有效的 Chat Completions 响应。");
            }
            String routedModel = response.path("routing").path("model_used").asText("").trim();
            if (routedModel.isBlank()) {
                routedModel = response.path("model").asText(ONEKEYTOKEN_AUTO_MODEL).trim();
            }
            return Map.of(
                    "providerCode", PROVIDER_ONEKEYTOKEN,
                    "ok", true,
                    "checkMode", "live_chat_completions",
                    "validatedModel", ONEKEYTOKEN_AUTO_MODEL,
                    "resolvedModel", routedModel,
                    "modelCount", 0,
                    "sampleModels", List.of(),
                    "catalogSource", "unavailable",
                    "remoteFetchSupported", false);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("OneKeyToken 检测失败：网关响应无法识别。");
        }
    }

    private String catalogSource(ProviderDef def) {
        return supportsRemoteModelFetch(def) ? "remote" : "unavailable";
    }

    private boolean supportsRemoteModelFetch(ProviderDef def) {
        return !FETCH_REMOTE_UNAVAILABLE.equals(def.fetchKind());
    }

    private List<ModelDetail> fetchOpenAiCompatibleModels(String baseUrl, String apiKey, boolean apiKeyRequired) {
        if (apiKeyRequired && (apiKey == null || apiKey.isBlank())) {
            throw new IllegalArgumentException("API Key 不能为空");
        }
        String endpoint = appendPath(baseUrl, "/models");
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(60))
                .GET();
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }

        return parseDataModelDetails(send(builder.build(), "OpenAI-compatible /models 调用失败"));
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

    private String sendOneKeyTokenValidation(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                if (response.statusCode() == 401 || response.statusCode() == 403) {
                    throw new IllegalArgumentException("OneKeyToken 检测失败，HTTP " + response.statusCode()
                            + "：API Key 无效、已失效或没有模型调用权限。");
                }
                throw new IllegalArgumentException("OneKeyToken 检测失败，HTTP " + response.statusCode()
                        + "：请检查 API 地址、Key 权限与账户余额后重试。");
            }
            return response.body();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("OneKeyToken 检测失败：无法连接网关，请检查 API 地址后重试。");
        }
    }

    private String clip(String body) {
        if (body == null) return "";
        String text = body.replaceAll("\\s+", " ").trim();
        return text.length() <= 180 ? text : text.substring(0, 180) + "...";
    }

    private List<String> configuredModelsForProvider(ModelProviderConfigEntity entity, String companyId) {
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
        List<String> configured = modelConfigRepository.findByCompanyIdAndProvider(companyId, entity.getProviderCode()).stream()
                .map(m -> m.getModelName() == null ? "" : m.getModelName().trim())
                .filter(v -> !v.isBlank())
                .distinct()
                .toList();
        if (!configured.isEmpty()) {
            return configured;
        }
        return List.of();
    }

    private List<EmbeddingModelDef> defaultEmbeddingModels(String providerCode) {
        return switch (providerCode) {
            case PROVIDER_ALIYUN -> List.of(
                    new EmbeddingModelDef("text-embedding-v4", 1024, List.of(1024, 2048, 1536, 768, 512, 256, 128, 64), true),
                    new EmbeddingModelDef("text-embedding-v3", 1024, List.of(1024, 768, 512, 256, 128, 64), true)
            );
            case PROVIDER_OPENAI -> List.of(
                    new EmbeddingModelDef("text-embedding-3-small", 1536, List.of(1536, 1024, 512, 256), true),
                    new EmbeddingModelDef("text-embedding-3-large", 3072, List.of(3072, 1536, 1024, 512, 256), true)
            );
            case PROVIDER_OLLAMA -> List.of(
                    new EmbeddingModelDef("bge-m3", 1024, List.of(1024), false),
                    new EmbeddingModelDef("nomic-embed-text", 768, List.of(768), false),
                    new EmbeddingModelDef("mxbai-embed-large", 1024, List.of(1024), false)
            );
            default -> List.of();
        };
    }

    private boolean isEmbeddingModelName(String modelName) {
        String lower = modelName == null ? "" : modelName.toLowerCase();
        return lower.contains("embedding")
                || lower.contains("embed")
                || lower.contains("bge")
                || lower.contains("e5")
                || lower.contains("gte");
    }

    private EmbeddingModelDef inferEmbeddingModelDef(String providerCode, String modelName) {
        String lower = modelName == null ? "" : modelName.toLowerCase();
        if (PROVIDER_ALIYUN.equals(providerCode)) {
            if (lower.contains("v3")) {
                return new EmbeddingModelDef(modelName, 1024, List.of(1024, 768, 512, 256, 128, 64), true);
            }
            return new EmbeddingModelDef(modelName, 1024, List.of(1024, 2048, 1536, 768, 512, 256, 128, 64), true);
        }
        if (PROVIDER_OPENAI.equals(providerCode)) {
            if (lower.contains("large")) {
                return new EmbeddingModelDef(modelName, 3072, List.of(3072, 1536, 1024, 512, 256), true);
            }
            return new EmbeddingModelDef(modelName, 1536, List.of(1536, 1024, 512, 256), true);
        }
        if (PROVIDER_OLLAMA.equals(providerCode)) {
            if (lower.contains("nomic")) {
                return new EmbeddingModelDef(modelName, 768, List.of(768), false);
            }
            return new EmbeddingModelDef(modelName, 1024, List.of(1024), false);
        }
        return new EmbeddingModelDef(modelName, 1024, List.of(1024), false);
    }

    private String writeJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map == null ? Map.of() : map);
        } catch (Exception e) {
            throw new IllegalArgumentException("写入模型厂商配置失败");
        }
    }

    private void ensureBuiltinRows(String companyId) {
        for (ProviderDef def : sortedDefs()) {
            boolean exists = providerRepository.findByCompanyIdAndProviderCode(companyId, def.providerCode()).isPresent();
            if (!exists) {
                String defaultKey = PROVIDER_ALIYUN.equals(def.providerCode()) ? nullableToBlank(aliyunDefaultApiKey) : "";
                String defaultUrl = PROVIDER_ALIYUN.equals(def.providerCode()) ? nullableToBlank(aliyunDefaultBaseUrl) : def.defaultBaseUrl();
                providerRepository.save(new ModelProviderConfigEntity(
                        companyId,
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
                PROVIDER_DEFS.get(PROVIDER_LMSTUDIO),
                PROVIDER_DEFS.get(PROVIDER_ONEKEYTOKEN),
                PROVIDER_DEFS.get(PROVIDER_ANTHROPIC),
                PROVIDER_DEFS.get(PROVIDER_OPENAI)
        );
    }

    private String platformScopeId() {
        String configured = platformAccountProperties.getGovernanceCompanyId();
        return configured == null || configured.isBlank() ? "demo-org" : configured.trim();
    }

    private Map<String, Object> routeView(SceneRouteDef scene, List<Map<String, Object>> candidates) {
        List<ModelChoice> choices = candidates.stream().map(this::toModelChoice).filter(choice -> choice != null).toList();
        CompanyModelConfigEntity configured = modelConfigRepository
                .findByCompanyIdAndSceneCode(platformScopeId(), scene.sceneCode())
                .orElse(null);
        ModelChoice choice = configured == null
                ? null
                : findCandidate(choices, configured.getProvider(), configured.getModelName());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sceneCode", scene.sceneCode());
        out.put("displayName", scene.displayName());
        out.put("description", scene.description());
        out.put("providerCode", configured == null ? "" : configured.getProvider());
        out.put("modelName", configured == null ? "" : configured.getModelName());
        out.put("configured", configured != null);
        out.put("available", choice != null);
        out.put("providerName", choice == null ? "" : choice.providerName());
        return out;
    }

    private SceneRouteDef requireSceneRoute(String sceneCode) {
        String normalized = normalizeSceneCode(sceneCode);
        return SCENE_ROUTES.stream()
                .filter(scene -> scene.sceneCode().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的模型场景：" + sceneCode));
    }

    private String normalizeSceneCode(String sceneCode) {
        return sceneCode == null ? "" : sceneCode.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private ModelChoice requirePlatformModelChoice(String providerCode, String modelName) {
        ModelChoice choice = findCandidate(
                agentBaseModels(platformScopeId()).stream().map(this::toModelChoice).filter(item -> item != null).toList(),
                providerCode,
                modelName);
        if (choice == null) {
            throw new IllegalArgumentException("模型必须先加入平台已选模型目录。");
        }
        return choice;
    }

    private ModelChoice findPreferredCandidate(List<ModelChoice> candidates, String preferredModelName) {
        String preferred = nullableToBlank(preferredModelName).trim();
        if (preferred.isBlank()) {
            return null;
        }
        int sep = preferred.indexOf("::");
        if (sep > 0 && sep < preferred.length() - 2) {
            return findCandidate(candidates, preferred.substring(0, sep), preferred.substring(sep + 2));
        }
        for (ModelChoice candidate : candidates) {
            if (preferred.equalsIgnoreCase(candidate.modelName())) {
                return candidate;
            }
        }
        return null;
    }

    private ModelChoice findCandidate(List<ModelChoice> candidates, String providerCode, String modelName) {
        String provider = nullableToBlank(providerCode).trim();
        String model = nullableToBlank(modelName).trim();
        if (provider.isBlank() || model.isBlank()) {
            return null;
        }
        for (ModelChoice candidate : candidates) {
            if (provider.equalsIgnoreCase(candidate.providerCode())
                    && model.equalsIgnoreCase(candidate.modelName())) {
                return candidate;
            }
        }
        return null;
    }

    private ModelChoice toModelChoice(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        String providerCode = nullableToBlank(String.valueOf(row.getOrDefault("providerCode", ""))).trim();
        String providerName = nullableToBlank(String.valueOf(row.getOrDefault("providerName", ""))).trim();
        String modelName = nullableToBlank(String.valueOf(row.getOrDefault("modelName", ""))).trim();
        return providerCode.isBlank() || modelName.isBlank()
                ? null
                : new ModelChoice(providerCode, providerName, modelName);
    }

    private Map<String, String> routePayload(ModelChoice choice, String source) {
        return Map.of(
                "provider", choice.providerCode(),
                "modelName", choice.modelName(),
                "routeSource", source
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
        out.put("apiKeyRequired", def.apiKeyRequired());
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

    private ModelProviderConfigEntity requireProviderEntity(String companyId, String providerCode) {
        return providerRepository.findByCompanyIdAndProviderCode(companyId, providerCode)
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

    private String valueOrConfigured(String override, String configured) {
        return override == null || override.isBlank() ? nullableToBlank(configured).trim() : override.trim();
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
            boolean apiKeyRequired,
            List<String> defaultModels
    ) {
    }

    private record SceneRouteDef(String sceneCode, String displayName, String description) {
    }

    private record ModelChoice(String providerCode, String providerName, String modelName) {
    }

    private record ModelDetail(
            String modelName,
            List<String> capabilities
    ) {
    }

    private record EmbeddingModelDef(
            String modelName,
            int defaultDimension,
            List<Integer> dimensionChoices,
            boolean supportsCustomDimension
    ) {
    }
}
