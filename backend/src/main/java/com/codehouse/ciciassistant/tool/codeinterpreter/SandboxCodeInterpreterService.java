package com.codehouse.ciciassistant.tool.codeinterpreter;

import com.codehouse.ciciassistant.integration.service.IntegrationAppService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Governed built-in tool backed by Alibaba Cloud Model Studio's managed Python sandbox. */
@Service
public class SandboxCodeInterpreterService {

    public static final String TOOL_NAME = "sandbox_code_interpreter";
    private static final Logger log = LoggerFactory.getLogger(SandboxCodeInterpreterService.class);
    private static final String DEFAULT_MODEL = "qwen3.5-plus";

    private final SandboxCodeInterpreterClient client;
    private final IntegrationAppService integrationAppService;
    private final ObjectMapper objectMapper;

    public SandboxCodeInterpreterService(SandboxCodeInterpreterClient client,
                                         IntegrationAppService integrationAppService,
                                         ObjectMapper objectMapper) {
        this.client = client;
        this.integrationAppService = integrationAppService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> toolDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("task", Map.of(
                "type", "string",
                "description", "需要代码解释器完成的精确计算、数据分析、文本数据转换或 Python 代码验证任务。"));
        properties.put("context", Map.of(
                "type", "string",
                "description", "可选的纯文本数据或上下文。不要传入密码、Token 或其他秘密。"));
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", TOOL_NAME,
                        "description", "在阿里云受管 Python 沙箱中完成精确计算、数据分析和代码验证。沙箱结果可能增加模型 Token 消耗。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", properties,
                                "required", List.of("task"),
                                "additionalProperties", false)));
    }

    public String dispatch(String companyId, String userId, String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson == null ? "{}" : argumentsJson);
            String task = args.path("task").asText("").trim();
            String context = args.path("context").asText("").trim();
            if (task.isBlank()) {
                return error("CODE_INTERPRETER_BAD_REQUEST", "缺少必需参数 task");
            }
            Optional<ResolvedConfig> resolved = resolveConfig(null, false);
            if (resolved.isEmpty()) {
                return error("CODE_INTERPRETER_NOT_CONFIGURED", "运营平台尚未配置并启用代码解释器");
            }
            ResolvedConfig config = resolved.get();
            String input = context.isBlank() ? task : task + "\n\n输入数据：\n" + context;
            if (input.length() > config.maxInputChars()) {
                return error("CODE_INTERPRETER_INPUT_TOO_LARGE",
                        "输入超过平台限制（" + config.maxInputChars() + " 字符）");
            }
            SandboxCodeInterpreterClient.CallResult result = client.execute(
                    config.apiBaseUrl(), config.apiKey(), config.model(), input, config.timeoutMs());
            if (!result.ok()) {
                log.warn("code interpreter failed org={} user={} code={} status={} latencyMs={}",
                        companyId, userId, result.code(), result.httpStatus(), result.latencyMs());
                return toJson(Map.of(
                        "success", false,
                        "code", result.code(),
                        "message", result.message(),
                        "latencyMs", result.latencyMs()));
            }
            log.info("code interpreter ok org={} user={} calls={} tokens={} latencyMs={}",
                    companyId, userId, result.callCount(), result.totalTokens(), result.latencyMs());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("answer", result.answer());
            payload.put("executions", result.executions());
            payload.put("codeInterpreterCalls", result.callCount());
            payload.put("usage", Map.of(
                    "inputTokens", result.inputTokens(),
                    "outputTokens", result.outputTokens(),
                    "totalTokens", result.totalTokens()));
            payload.put("model", config.model());
            payload.put("latencyMs", result.latencyMs());
            return toJson(payload);
        } catch (IllegalArgumentException exception) {
            return error("CODE_INTERPRETER_CONFIG_INVALID", exception.getMessage());
        } catch (Exception exception) {
            return error("CODE_INTERPRETER_BAD_REQUEST", "代码解释器参数不是有效 JSON");
        }
    }

    public Map<String, Object> testConnection(String overrideApiKey,
                                              String overrideApiBaseUrl,
                                              String overrideModel) {
        Optional<ResolvedConfig> resolved;
        try {
            resolved = resolveConfig(new ConfigOverride(overrideApiKey, overrideApiBaseUrl, overrideModel), true);
        } catch (IllegalArgumentException exception) {
            return Map.of("ok", false, "code", "CODE_INTERPRETER_CONFIG_INVALID",
                    "message", exception.getMessage());
        }
        if (resolved.isEmpty()) {
            return Map.of("ok", false, "code", "CODE_INTERPRETER_NOT_CONFIGURED",
                    "message", "请先填写 API Key 并保存或直接使用当前草稿检测");
        }
        ResolvedConfig config = resolved.get();
        SandboxCodeInterpreterClient.CallResult result = client.execute(
                config.apiBaseUrl(), config.apiKey(), config.model(), "请使用代码解释器计算 12 的 3 次方，只返回结果。", config.timeoutMs());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", result.ok());
        out.put("latencyMs", result.latencyMs());
        out.put("model", config.model());
        out.put("codeInterpreterCalls", result.callCount());
        if (!result.ok()) {
            out.put("code", result.code());
            out.put("message", result.message());
        }
        return out;
    }

    public void validateConfigurationDraft(Map<String, Object> config) {
        Map<String, Object> safeConfig = config == null ? Map.of() : config;
        String apiBaseUrl = firstNonBlank(safeConfig.get("apiBaseUrl"));
        if (apiBaseUrl.isBlank()) {
            throw new IllegalArgumentException("请填写百炼业务空间对应地域的 API Host");
        }
        validateApiBaseUrl(apiBaseUrl);
        String model = firstNonBlank(safeConfig.get("model"), DEFAULT_MODEL);
        validateModel(model);
        parseBoundedInt(safeConfig.get("timeoutMs"), 120_000, 10_000, 180_000, "请求超时");
        parseBoundedInt(safeConfig.get("maxInputChars"), 12_000, 1_000, 50_000, "最大输入字符数");
    }

    private Optional<ResolvedConfig> resolveConfig(ConfigOverride override, boolean includeDisabled) {
        Optional<Map<String, Object>> rawOptional = includeDisabled
                ? integrationAppService.findStoredRawConfig("platform-runtime", IntegrationAppService.APP_CODE_CODE_INTERPRETER)
                : integrationAppService.findRawConfig("platform-runtime", IntegrationAppService.APP_CODE_CODE_INTERPRETER);
        Map<String, Object> raw = rawOptional.orElse(Map.of());
        String storedKey = integrationAppService.decryptCodeInterpreterApiKey(raw).orElse("");
        String overrideKey = override == null ? "" : safe(override.apiKey());
        if (IntegrationAppService.CODE_INTERPRETER_SECRET_MASK.equals(overrideKey)) overrideKey = "";
        String apiKey = overrideKey.isBlank() ? storedKey : overrideKey;
        if (apiKey.isBlank()) return Optional.empty();
        String apiBaseUrl = firstNonBlank(override == null ? null : override.apiBaseUrl(), raw.get("apiBaseUrl"));
        if (apiBaseUrl.isBlank()) {
            throw new IllegalArgumentException("请填写百炼业务空间对应地域的 API Host");
        }
        String model = firstNonBlank(override == null ? null : override.model(), raw.get("model"), DEFAULT_MODEL);
        validateApiBaseUrl(apiBaseUrl);
        validateModel(model);
        int timeoutMs = parseBoundedInt(raw.get("timeoutMs"), 120_000, 10_000, 180_000, "请求超时");
        int maxInputChars = parseBoundedInt(raw.get("maxInputChars"), 12_000, 1_000, 50_000, "最大输入字符数");
        return Optional.of(new ResolvedConfig(apiKey, apiBaseUrl, model, timeoutMs, maxInputChars));
    }

    static void validateApiBaseUrl(String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("代码解释器 API Host 无效");
        }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                || !host.endsWith(".maas.aliyuncs.com")
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("代码解释器 API Host 必须是百炼业务空间的 HTTPS maas.aliyuncs.com 地址");
        }
    }

    private void validateModel(String model) {
        if (!model.matches("[A-Za-z0-9._:/-]{2,128}")) {
            throw new IllegalArgumentException("代码解释器模型标识无效");
        }
    }

    private String firstNonBlank(Object... values) {
        for (Object value : values) {
            String safe = safe(value);
            if (!safe.isBlank()) return safe;
        }
        return "";
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int parseBoundedInt(Object value, int fallback, int min, int max, String label) {
        String raw = safe(value);
        if (raw.isBlank()) return fallback;
        int parsed;
        try {
            parsed = Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + "必须是整数");
        }
        if (parsed < min || parsed > max) {
            throw new IllegalArgumentException(label + "必须在 " + min + "–" + max + " 之间");
        }
        return parsed;
    }

    private String error(String code, String message) {
        return toJson(Map.of("success", false, "code", code, "message", message));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{\"success\":false,\"code\":\"CODE_INTERPRETER_SERIALIZATION_ERROR\"}";
        }
    }

    private record ResolvedConfig(String apiKey, String apiBaseUrl, String model, int timeoutMs, int maxInputChars) { }
    private record ConfigOverride(String apiKey, String apiBaseUrl, String model) { }
}
