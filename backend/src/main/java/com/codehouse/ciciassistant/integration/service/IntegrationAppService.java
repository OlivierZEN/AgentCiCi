package com.codehouse.ciciassistant.integration.service;

import com.codehouse.ciciassistant.auth.config.PlatformAccountProperties;
import com.codehouse.ciciassistant.common.crypto.SecretCipherService;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.feishu.service.FeishuBotClientManager;
import com.codehouse.ciciassistant.integration.domain.IntegrationAppEntity;
import com.codehouse.ciciassistant.integration.domain.IntegrationAppRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationAppService {

    public static final String APP_CODE_CLOUDCC_CRM = "cloudcc_crm";
    public static final String APP_CODE_FEISHU_BOT = "feishu_bot";
    public static final String APP_CODE_TAVILY = "tavily";
    public static final String APP_CODE_IFLYTEK_ASR = "iflytek_asr";
    public static final String APP_CODE_CODE_INTERPRETER = "code_interpreter";
    public static final String APP_CODE_MANAGED_WEB_SEARCH = "managed_web_search";
    public static final String APP_CODE_MANAGED_WEB_EXTRACTOR = "managed_web_extractor";

    /** Displayed to the frontend when an encrypted apiKey exists. The frontend never receives the ciphertext. */
    public static final String API_KEY_MASK = "tvly-****";
    public static final String IFLYTEK_SECRET_MASK = "iflytek-****";
    public static final String CODE_INTERPRETER_SECRET_MASK = "bailian-****";
    public static final String MANAGED_WEB_SECRET_MASK = "bailian-****";
    public static final String CLOUDCC_SECRET_MASK = "cloudcc-****";
    public static final String PLATFORM_MANAGED_MESSAGE = "Tavily 搜索、讯飞实时转写、代码解释器、联网搜索和网页抓取由运营平台统一配置，组织后台不可修改。";

    private static final String DEFAULT_IFLYTEK_REALTIME_URL = "wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1";
    private static final Map<String, BuiltinAppDef> BUILTIN_APPS = builtinApps();
    private static final List<String> PLATFORM_MANAGED_APP_CODES = List.of(
            APP_CODE_TAVILY,
            APP_CODE_IFLYTEK_ASR,
            APP_CODE_CODE_INTERPRETER,
            APP_CODE_MANAGED_WEB_SEARCH,
            APP_CODE_MANAGED_WEB_EXTRACTOR);

    private final IntegrationAppRepository repository;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<FeishuBotClientManager> feishuBotClientManagerProvider;
    private final SecretCipherService secretCipherService;
    private final PlatformAccountProperties platformAccountProperties;

    public IntegrationAppService(IntegrationAppRepository repository,
                                 ObjectMapper objectMapper,
                                 ObjectProvider<FeishuBotClientManager> feishuBotClientManagerProvider,
                                 SecretCipherService secretCipherService,
                                 PlatformAccountProperties platformAccountProperties) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.feishuBotClientManagerProvider = feishuBotClientManagerProvider;
        this.secretCipherService = secretCipherService;
        this.platformAccountProperties = platformAccountProperties;
    }

    @Transactional
    public List<Map<String, Object>> list(String companyId) {
        ensureBuiltinRows(companyId);
        return repository.findByCompanyIdOrderByIdAsc(companyId).stream()
                .filter(entity -> !isPlatformManagedApp(entity.getAppCode()))
                .map(this::toView)
                .toList();
    }

    @Transactional
    public List<Map<String, Object>> listPlatformManaged() {
        String scopeId = platformScopeId();
        ensurePlatformManagedRows(scopeId);
        return repository.findByCompanyIdOrderByIdAsc(scopeId).stream()
                .filter(entity -> isPlatformManagedApp(entity.getAppCode()))
                .map(this::toView)
                .toList();
    }

    @Transactional
    public Map<String, Object> update(
            String companyId,
            String appCode,
            boolean enabled,
            String description,
            Map<String, Object> config) {
        if (isPlatformManagedApp(appCode)) {
            throw new ForbiddenException(PLATFORM_MANAGED_MESSAGE);
        }
        return updateInternal(companyId, appCode, enabled, description, config);
    }

    @Transactional
    public Map<String, Object> updatePlatformManaged(
            String appCode,
            boolean enabled,
            String description,
            Map<String, Object> config) {
        if (!isPlatformManagedApp(appCode)) {
            throw new IllegalArgumentException("非平台托管集成应用不可在运营端配置: " + appCode);
        }
        String scopeId = platformScopeId();
        ensurePlatformManagedRows(scopeId);
        return updateInternal(scopeId, appCode, enabled, description, config);
    }

    private Map<String, Object> updateInternal(
            String companyId,
            String appCode,
            boolean enabled,
            String description,
            Map<String, Object> config) {
        BuiltinAppDef def = BUILTIN_APPS.get(appCode);
        if (def == null) {
            throw new IllegalArgumentException("Unknown built-in integration app: " + appCode);
        }

        IntegrationAppEntity entity = repository.findByCompanyIdAndAppCode(companyId, appCode)
                .orElseGet(() -> repository.save(new IntegrationAppEntity(
                        companyId,
                        def.appCode(),
                        def.appName(),
                        def.description(),
                        def.defaultEnabled(),
                        "{}"
                )));

        Map<String, Object> existingConfig = readJsonToMap(entity.getConfigJson());
        Map<String, Object> sanitizedConfig = sanitizeConfig(def, config, existingConfig);
        entity.setEnabled(enabled);
        entity.setDescription(description == null || description.isBlank() ? def.description() : description.trim());
        entity.setConfigJson(writeJson(sanitizedConfig));
        entity.touch();

        IntegrationAppEntity saved = repository.save(entity);
        if (APP_CODE_FEISHU_BOT.equals(appCode)) {
            feishuBotClientManagerProvider.ifAvailable(manager -> manager.refreshOrg(companyId));
        }
        return toView(saved);
    }

    /**
     * Return the raw (as-stored) config JSON for the given app, or empty if the integration
     * row is missing or disabled. Intended for runtime services (e.g. TavilyToolService)
     * — <b>not</b> for returning to HTTP clients, because the map may include encrypted
     * secret envelopes.
     */
    public Optional<Map<String, Object>> findRawConfig(String companyId, String appCode) {
        return repository.findByCompanyIdAndAppCode(configScopeId(companyId, appCode), appCode)
                .filter(IntegrationAppEntity::isEnabled)
                .map(e -> readJsonToMap(e.getConfigJson()));
    }

    /** Runtime-internal read used by platform connection tests before an integration is enabled. */
    public Optional<Map<String, Object>> findStoredRawConfig(String companyId, String appCode) {
        return repository.findByCompanyIdAndAppCode(configScopeId(companyId, appCode), appCode)
                .map(e -> readJsonToMap(e.getConfigJson()));
    }

    public Optional<Boolean> isEnabled(String companyId, String appCode) {
        return repository.findByCompanyIdAndAppCode(configScopeId(companyId, appCode), appCode)
                .map(IntegrationAppEntity::isEnabled);
    }

    public boolean isPlatformManagedApp(String appCode) {
        return PLATFORM_MANAGED_APP_CODES.contains(appCode);
    }

    /**
     * Decrypt the Tavily apiKey from a raw config map. Returns empty when the key is missing,
     * blank, or equal to the frontend mask sentinel.
     */
    public Optional<String> decryptTavilyApiKey(Map<String, Object> rawConfig) {
        return decryptSecret(rawConfig, "apiKey", API_KEY_MASK);
    }

    public Optional<String> decryptIflytekAccessKeySecret(Map<String, Object> rawConfig) {
        return decryptSecret(rawConfig, "accessKeySecret", IFLYTEK_SECRET_MASK);
    }

    public Optional<String> decryptCodeInterpreterApiKey(Map<String, Object> rawConfig) {
        return decryptSecret(rawConfig, "apiKey", CODE_INTERPRETER_SECRET_MASK);
    }

    public Optional<String> decryptManagedWebApiKey(Map<String, Object> rawConfig) {
        return decryptSecret(rawConfig, "apiKey", MANAGED_WEB_SECRET_MASK);
    }

    private Optional<String> decryptSecret(Map<String, Object> rawConfig, String key, String mask) {
        if (rawConfig == null) return Optional.empty();
        Object value = rawConfig.get(key);
        if (value == null) return Optional.empty();
        if (value instanceof String s) {
            String trimmed = s.trim();
            if (trimmed.isEmpty() || mask.equals(trimmed)) {
                return Optional.empty();
            }
            return Optional.of(trimmed);
        }
        if (value instanceof Map<?, ?> map) {
            Object cipher = map.get("cipher");
            Object iv = map.get("iv");
            if (cipher instanceof String c && iv instanceof String i && !c.isBlank() && !i.isBlank()) {
                try {
                    String decrypted = secretCipherService.decryptUtf8(c, i);
                    if (decrypted != null && !decrypted.isBlank()) {
                        return Optional.of(decrypted);
                    }
                } catch (Exception ignored) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private void ensureBuiltinRows(String companyId) {
        for (BuiltinAppDef def : BUILTIN_APPS.values()) {
            if (isPlatformManagedApp(def.appCode())) {
                continue;
            }
            boolean exists = repository.findByCompanyIdAndAppCode(companyId, def.appCode()).isPresent();
            if (!exists) {
                repository.save(new IntegrationAppEntity(
                        companyId,
                        def.appCode(),
                        def.appName(),
                        def.description(),
                        def.defaultEnabled(),
                        "{}"
                ));
            }
        }
    }

    private void ensurePlatformManagedRows(String companyId) {
        for (String appCode : PLATFORM_MANAGED_APP_CODES) {
            BuiltinAppDef def = BUILTIN_APPS.get(appCode);
            boolean exists = repository.findByCompanyIdAndAppCode(companyId, def.appCode()).isPresent();
            if (!exists) {
                repository.save(new IntegrationAppEntity(
                        companyId,
                        def.appCode(),
                        def.appName(),
                        def.description(),
                        def.defaultEnabled(),
                        "{}"
                ));
            }
        }
    }

    private String configScopeId(String companyId, String appCode) {
        return isPlatformManagedApp(appCode) ? platformScopeId() : companyId;
    }

    private String platformScopeId() {
        String configured = platformAccountProperties.getGovernanceCompanyId();
        return configured == null || configured.isBlank()
                ? PlatformAccountProperties.LEGACY_DEFAULT_GOVERNANCE_COMPANY_ID
                : configured.trim();
    }

    private Map<String, Object> toView(IntegrationAppEntity e) {
        BuiltinAppDef def = BUILTIN_APPS.get(e.getAppCode());
        List<String> requiredKeys = def == null ? List.of() : def.configKeys();
        Map<String, Object> config = new LinkedHashMap<>(readJsonToMap(e.getConfigJson()));
        if (APP_CODE_CLOUDCC_CRM.equals(e.getAppCode())) {
            Object orgId = config.get("orgId");
            if ((orgId == null || String.valueOf(orgId).isBlank()) && config.containsKey("companyId")) {
                config.put("orgId", config.get("companyId"));
            }
            config.remove("companyId");
            maskSecrets(config, "secretKey", CLOUDCC_SECRET_MASK);
        }
        if (APP_CODE_TAVILY.equals(e.getAppCode())) {
            maskSecrets(config, "apiKey", API_KEY_MASK);
        } else if (APP_CODE_IFLYTEK_ASR.equals(e.getAppCode())) {
            config.clear();
        } else if (APP_CODE_CODE_INTERPRETER.equals(e.getAppCode())) {
            config.remove("apiKey");
            config.remove("apiBaseUrl");
            config.remove("model");
            config.putIfAbsent("timeoutMs", "120000");
            config.putIfAbsent("maxInputChars", "12000");
        } else if (isManagedWebApp(e.getAppCode())) {
            config.remove("apiKey");
            config.remove("apiBaseUrl");
            config.remove("model");
            config.putIfAbsent("timeoutMs", "120000");
            config.putIfAbsent("maxInputChars", "12000");
        }
        return Map.of(
                "id", e.getId(),
                "appCode", e.getAppCode(),
                "appName", e.getAppName(),
                "description", e.getDescription() == null ? "" : e.getDescription(),
                "enabled", e.isEnabled(),
                "config", config,
                "configKeys", requiredKeys,
                "createdAt", e.getCreatedAt().toString(),
                "updatedAt", e.getUpdatedAt().toString(),
                "builtin", true
        );
    }

    /** Replace the named secret field with the mask string; if absent or already masked, leaves untouched. */
    private void maskSecrets(Map<String, Object> config, String field, String mask) {
        Object value = config.get(field);
        if (value == null) {
            config.put(field, "");
            return;
        }
        if (value instanceof Map<?, ?> m && m.get("cipher") instanceof String c && !c.isBlank()) {
            config.put(field, mask);
            return;
        }
        if (value instanceof String s && !s.isBlank() && !mask.equals(s)) {
            // Legacy plaintext rows are masked on read too, to avoid surfacing raw keys to the UI.
            config.put(field, mask);
        }
    }

    private Map<String, Object> sanitizeConfig(BuiltinAppDef def,
                                               Map<String, Object> rawConfig,
                                               Map<String, Object> existingConfig) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> cfg = rawConfig == null ? Map.of() : rawConfig;
        Map<String, Object> existing = existingConfig == null ? Map.of() : existingConfig;
        for (String key : def.configKeys()) {
            Object v = cfg.getOrDefault(key, "");
            // Backward compat: old field name was `baseUrl`, new name is `orgapi_switch_address`.
            if ((v == null || String.valueOf(v).isBlank()) && "orgapi_switch_address".equals(key) && cfg.containsKey("baseUrl")) {
                v = cfg.get("baseUrl");
            }
            // CloudCC's external organization identifier is orgId. companyId was an
            // earlier, misleading config key and remains readable only for migration.
            if ((v == null || String.valueOf(v).isBlank()) && APP_CODE_CLOUDCC_CRM.equals(def.appCode())
                    && "orgId".equals(key) && cfg.containsKey("companyId")) {
                v = cfg.get("companyId");
            }
            if ((v == null || String.valueOf(v).isBlank()) && APP_CODE_FEISHU_BOT.equals(def.appCode())
                    && "defaultAgentCode".equals(key)) {
                v = "cici";
            }
            if ((v == null || String.valueOf(v).isBlank()) && APP_CODE_FEISHU_BOT.equals(def.appCode())
                    && "pairingCommandHint".equals(key)) {
                v = "配对";
            }
            if (APP_CODE_IFLYTEK_ASR.equals(def.appCode())) {
                if ((v == null || String.valueOf(v).isBlank()) && "realtimeUrl".equals(key)) {
                    v = DEFAULT_IFLYTEK_REALTIME_URL;
                }
                if ((v == null || String.valueOf(v).isBlank()) && "lang".equals(key)) {
                    v = "autodialect";
                }
                if ((v == null || String.valueOf(v).isBlank()) && "domain".equals(key)) {
                    v = "com";
                }
            }
            if (APP_CODE_CODE_INTERPRETER.equals(def.appCode())) {
                if ((v == null || String.valueOf(v).isBlank()) && "timeoutMs".equals(key)) {
                    v = "120000";
                }
                if ((v == null || String.valueOf(v).isBlank()) && "maxInputChars".equals(key)) {
                    v = "12000";
                }
            }
            if (isManagedWebApp(def.appCode())) {
                if ((v == null || String.valueOf(v).isBlank()) && "timeoutMs".equals(key)) {
                    v = "120000";
                }
                if ((v == null || String.valueOf(v).isBlank()) && "maxInputChars".equals(key)) {
                    v = "12000";
                }
            }

            // Tavily apiKey: encrypt new plaintext; preserve existing encrypted envelope when
            // the incoming value is blank or the frontend mask sentinel.
            if (APP_CODE_TAVILY.equals(def.appCode()) && "apiKey".equals(key)) {
                out.put(key, encryptOrPreserveSecret(existing.get(key), v, API_KEY_MASK));
                continue;
            }
            if (APP_CODE_IFLYTEK_ASR.equals(def.appCode()) && "accessKeySecret".equals(key)) {
                out.put(key, encryptOrPreserveSecret(existing.get(key), v, IFLYTEK_SECRET_MASK));
                continue;
            }
            if (APP_CODE_CLOUDCC_CRM.equals(def.appCode()) && "secretKey".equals(key)) {
                out.put(key, preservePlainSecret(existing.get(key), v, CLOUDCC_SECRET_MASK));
                continue;
            }

            out.put(key, v == null ? "" : String.valueOf(v).trim());
        }
        return out;
    }

    private Map<String, Object> readJsonToMap(String json) {
        try {
            if (json == null || json.isBlank()) return new HashMap<>();
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private String writeJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map == null ? Map.of() : map);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize integration config");
        }
    }

    private Object encryptOrPreserveSecret(Object existingValue, Object incomingValue, String mask) {
        String incoming = incomingValue == null ? "" : String.valueOf(incomingValue).trim();
        if (incoming.isEmpty() || mask.equals(incoming)) {
            return existingValue == null ? "" : existingValue;
        }
        SecretCipherService.EncryptedSecret encrypted = secretCipherService.encryptUtf8(incoming);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("cipher", encrypted.cipherBase64());
        envelope.put("iv", encrypted.ivBase64());
        return envelope;
    }

    private Object preservePlainSecret(Object existingValue, Object incomingValue, String mask) {
        String incoming = incomingValue == null ? "" : String.valueOf(incomingValue).trim();
        if (incoming.isEmpty() || mask.equals(incoming)) {
            return existingValue == null ? "" : existingValue;
        }
        return incoming;
    }

    private static Map<String, BuiltinAppDef> builtinApps() {
        Map<String, BuiltinAppDef> apps = new LinkedHashMap<>();
        apps.put(APP_CODE_CLOUDCC_CRM, new BuiltinAppDef(
                APP_CODE_CLOUDCC_CRM,
                "CloudCC CRM",
                "接入 CloudCC CRM 系统，获取并处理业务数据与业务功能",
                List.of("orgId", "orgapi_switch_address", "clientId", "secretKey"),
                true));
        apps.put(APP_CODE_FEISHU_BOT, new BuiltinAppDef(
                APP_CODE_FEISHU_BOT,
                "飞书机器人",
                "通过飞书官方长连接方式接收机器人单聊消息，并桥接到系统智能体。",
                List.of("appId", "appSecret", "defaultAgentCode", "pairingCommandHint"),
                true));
        apps.put(APP_CODE_TAVILY, new BuiltinAppDef(
                APP_CODE_TAVILY,
                "Tavily 搜索",
                "接入 Tavily Web 搜索与正文抽取能力，为内置 web-search 技能与 tavily_search / tavily_extract 工具供能。",
                List.of(
                        "apiKey",
                        "defaultSearchDepth",
                        "defaultMaxResults",
                        "defaultTopic",
                        "defaultIncludeAnswer",
                        "defaultExtractFormat",
                        "timeoutMs"
                ),
                true));
        apps.put(APP_CODE_IFLYTEK_ASR, new BuiltinAppDef(
                APP_CODE_IFLYTEK_ASR,
                "实时转写（已迁移）",
                "实时转写的厂商、模型和凭据统一在 voice-asr 场景模型路由中配置；此旧配置入口不再接受独立凭据。",
                List.of(),
                false));
        apps.put(APP_CODE_CODE_INTERPRETER, new BuiltinAppDef(
                APP_CODE_CODE_INTERPRETER,
                "代码解释器",
                "调用阿里云百炼受管 Python 沙箱完成精确计算、数据分析与代码验证，并生成可治理的内置工具。",
                List.of("timeoutMs", "maxInputChars"),
                false));
        apps.put(APP_CODE_MANAGED_WEB_SEARCH, new BuiltinAppDef(
                APP_CODE_MANAGED_WEB_SEARCH,
                "联网搜索（百炼）",
                "调用百炼联网搜索获取时效信息并生成可治理的内置工具；兼容 Responses API 不提供可验证来源列表。",
                List.of("timeoutMs", "maxInputChars"),
                false));
        apps.put(APP_CODE_MANAGED_WEB_EXTRACTOR, new BuiltinAppDef(
                APP_CODE_MANAGED_WEB_EXTRACTOR,
                "网页抓取（百炼）",
                "调用百炼网页抓取读取公开 URL 内容并生成可治理的内置工具；单次请求会同时使用搜索与抓取能力。",
                List.of("timeoutMs", "maxInputChars"),
                false));
        return Collections.unmodifiableMap(apps);
    }

    private static boolean isManagedWebApp(String appCode) {
        return APP_CODE_MANAGED_WEB_SEARCH.equals(appCode)
                || APP_CODE_MANAGED_WEB_EXTRACTOR.equals(appCode);
    }

    private record BuiltinAppDef(String appCode,
                                 String appName,
                                 String description,
                                 List<String> configKeys,
                                 boolean defaultEnabled) {
    }
}
