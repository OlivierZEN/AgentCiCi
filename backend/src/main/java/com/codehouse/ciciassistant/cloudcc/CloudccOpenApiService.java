package com.codehouse.ciciassistant.cloudcc;

import com.codehouse.ciciassistant.integration.service.CloudccAccessTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * CloudCC OpenAPI 直接调用封装 —— 不经过 MCP 协议层，
 * 作为内部 Java Service 供 ToolOrchestrator 直接调用。
 *
 * 相比 MCP Server 方案，这个方案：
 * - 零额外服务部署（同进程调用）
 * - 鉴权复用 CloudccAccessTokenService
 * - 模型看到的工具名就是 cloudcc_pageQuery
 */
@Service
public class CloudccOpenApiService {

    private static final Logger log = LoggerFactory.getLogger(CloudccOpenApiService.class);

    private final CloudccAccessTokenService tokenService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public CloudccOpenApiService(CloudccAccessTokenService tokenService, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 分页查询 CloudCC 对象数据。
     *
     * @param orgId         组织 ID
     * @param userId        用户 ID
     * @param objectApiName 对象 API 名，如 Customer__c
     * @param fields        查询字段，逗号分隔，如 "id,name,phone"
     * @param expressions   查询条件，如 "name like '%张%'"
     * @param pageNum       页码（从 1 开始）
     * @param pageSize      每页条数
     */
    public String pageQuery(
            String orgId,
            String userId,
            String objectApiName,
            String fields,
            String expressions,
            Integer pageNum,
            Integer pageSize) {

        // 获取 CloudCC 访问令牌
        var ctx = tokenService.getSessionContext(orgId, userId);
        if (ctx.isEmpty()) {
            return error("无法获取 CloudCC 访问令牌，请确认已在「集成应用」中绑定 CloudCC 账号。");
        }

        String baseUrl = ensureBaseUrl(ctx.get().baseUrl());
        String accessToken = ctx.get().accessToken();

        // 构建请求体
        ObjectNode body = objectMapper.createObjectNode();
        body.put("serviceName", "pageQuery");
        body.put("objectApiName", objectApiName);
        // 如果未指定字段，默认查询 id,name（CloudCC 不指定 fields 时可能返回空对象）
        String actualFields = (fields != null && !fields.isBlank()) ? fields : "id,name";
        body.put("fields", actualFields);
        if (expressions != null && !expressions.isBlank()) {
            body.put("expressions", expressions);
        }
        // 注意：CloudCC API 参数名为 pageNUM（大写 NUM）
        body.put("pageNUM", pageNum != null ? pageNum : 1);
        body.put("pageSize", pageSize != null ? pageSize : 20);

        String url = baseUrl + "/openApi/common";
        log.info("CloudCC pageQuery: url={}, object={}, fields={}, pageNUM={}, pageSize={}",
                url, objectApiName, actualFields, body.get("pageNUM"), body.get("pageSize"));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("accessToken", accessToken)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            // 401 自动刷新令牌重试一次
            if (response.statusCode() == 401) {
                log.warn("CloudCC 401，尝试刷新令牌后重试");
                tokenService.invalidateSessionContext(orgId, userId);
                var fresh = tokenService.getSessionContext(orgId, userId);
                if (fresh.isEmpty()) {
                    return error("CloudCC 令牌刷新失败，请重新绑定账号。");
                }
                request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("accessToken", fresh.get().accessToken())
                        .timeout(Duration.ofSeconds(30))
                        .POST(HttpRequest.BodyPublishers.ofString(
                                objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                        .build();
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                responseBody = response.body();
            }

            // 解析并格式化返回结果
            return formatPageQueryResponse(objectApiName, responseBody);

        } catch (Exception e) {
            log.error("CloudCC pageQuery 调用失败: {}", e.getMessage(), e);
            return error("调用 CloudCC API 失败：" + e.getMessage());
        }
    }

    /**
     * 将 CloudCC 原始返回格式化为模型友好的文本。
     */
    private String formatPageQueryResponse(String objectName, String rawJson) throws Exception {
        JsonNode root = objectMapper.readTree(rawJson);
        boolean result = root.path("result").asBoolean(false);
        String returnInfo = root.path("returnInfo").asText("");
        String returnCode = root.path("returnCode").asText("");

        if (!result) {
            return String.format("❌ 查询失败\n编码: %s\n信息: %s", returnCode, returnInfo);
        }

        JsonNode dataNode = root.path("data");
        if (dataNode.isMissingNode() || dataNode.isNull()) {
            return String.format("✅ 查询成功，但无数据返回\n对象: %s", objectName);
        }

        // CloudCC 的 data 字段可能是 JSON 数组，也可能是 JSON 字符串
        JsonNode records;
        if (dataNode.isTextual()) {
            String dataStr = dataNode.asText().trim();
            if (dataStr.isEmpty()) {
                return String.format("✅ 查询成功\n对象: %s\n结果: 无匹配记录", objectName);
            }
            records = objectMapper.readTree(dataStr);
        } else if (dataNode.isArray()) {
            records = dataNode;
        } else if (dataNode.isObject()) {
            // 如果是对象，尝试取其内部数组或直接返回
            if (dataNode.has("records") && dataNode.get("records").isArray()) {
                records = dataNode.get("records");
            } else if (dataNode.has("data") && dataNode.get("data").isArray()) {
                records = dataNode.get("data");
            } else {
                records = dataNode; // fallback
            }
        } else {
            return String.format("✅ 查询成功\n对象: %s\n结果: 未知数据格式", objectName);
        }

        if (!records.isArray() || records.isEmpty()) {
            return String.format("✅ 查询成功\n对象: %s\n结果: 无匹配记录", objectName);
        }

        int total = records.size();
        int pageNum = root.path("pageNUM").asInt(root.path("pageNum").asInt(1));
        int pageCount = root.path("pageCount").asInt(1);
        int totalCount = root.path("totalCount").asInt(total);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("✅ 查询成功 | 对象: %s | 第%d/%d页 | 共%d条\n", objectName, pageNum, pageCount, totalCount));
        sb.append("─".repeat(60)).append("\n");

        // 取前 20 条展示（避免 token 爆炸）
        int displayLimit = Math.min(total, 20);
        for (int i = 0; i < displayLimit; i++) {
            JsonNode record = records.get(i);
            if (!record.isObject() || record.isEmpty()) {
                sb.append(String.format("[%d] (空记录)\n", i + 1));
                continue;
            }
            sb.append(String.format("[%d] ", i + 1));
            record.fields().forEachRemaining(entry -> {
                String val = entry.getValue().isTextual() ? entry.getValue().asText() : entry.getValue().toString();
                sb.append(entry.getKey()).append(": ").append(val).append(" | ");
            });
            // 去掉末尾的 " | "
            if (sb.length() > 2) {
                sb.setLength(sb.length() - 3);
            }
            sb.append("\n");
        }

        if (total > displayLimit) {
            sb.append(String.format("\n… 还有 %d 条记录未显示，可调整 pageNum 继续翻页\n", total - displayLimit));
        }

        return sb.toString();
    }

    private static String ensureBaseUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("CloudCC base_url 未配置");
        }
        String u = raw.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://" + u.replaceFirst("^/+", "");
        }
        return u;
    }

    private static String error(String msg) {
        return "❌ " + msg;
    }

    // ── MCP Tool Metadata（用于注册到工具列表） ──

    public static ObjectNode toolSchema(ObjectMapper mapper) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode props = mapper.createObjectNode();

        ObjectNode objectField = mapper.createObjectNode();
        objectField.put("type", "string");
        objectField.put("description", "CloudCC 对象 API 名称，如 Customer__c、Opportunity__c");
        props.set("objectApiName", objectField);

        ObjectNode fieldsField = mapper.createObjectNode();
        fieldsField.put("type", "string");
        fieldsField.put("description", "要查询的字段，逗号分隔，如 id,name,phone。不填则返回所有字段");
        props.set("fields", fieldsField);

        ObjectNode exprField = mapper.createObjectNode();
        exprField.put("type", "string");
        exprField.put("description", "查询条件表达式，如 \"name like '%张%'\" 或 \"status = 'active'\"");
        props.set("expressions", exprField);

        ObjectNode pageNumField = mapper.createObjectNode();
        pageNumField.put("type", "integer");
        pageNumField.put("description", "页码，从 1 开始");
        props.set("pageNum", pageNumField);

        ObjectNode pageSizeField = mapper.createObjectNode();
        pageSizeField.put("type", "integer");
        pageSizeField.put("description", "每页条数，默认 20");
        props.set("pageSize", pageSizeField);

        schema.set("properties", props);
        schema.set("required", mapper.createArrayNode().add("objectApiName"));
        return schema;
    }

    public static String toolName() {
        return "cloudcc_pageQuery";
    }

    public static String toolDescription() {
        return "分页查询 CloudCC CRM 中的对象数据。可用于查询客户、商机、联系人等业务对象。"
                + "支持按字段过滤、分页，返回结构化结果。调用前需确保已绑定 CloudCC 账号。"
                + "⚠️ 注意：查询前请先调用 cloudcc_getStandardObjects 或 cloudcc_getCustomObjects 确认正确的对象 API 名称。";
    }

    // ═══════════════════════════════════════════════════════
    // Tool 2: cloudcc_getStandardObjects
    // ═══════════════════════════════════════════════════════

    public static ObjectNode toolSchemaGetStandardObjects(ObjectMapper mapper) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", mapper.createObjectNode());
        schema.set("required", mapper.createArrayNode());
        return schema;
    }

    public static String toolNameGetStandardObjects() {
        return "cloudcc_getStandardObjects";
    }

    public static String toolDescriptionGetStandardObjects() {
        return "获取 CloudCC 标准对象列表（客户、联系人、商机、产品等系统内置对象）。"
                + "返回每个对象的中文标签、API 名称和前缀。查询数据前必须先调用此工具确认正确的对象 API 名称。";
    }

    // ═══════════════════════════════════════════════════════
    // Tool 3: cloudcc_getCustomObjects
    // ═══════════════════════════════════════════════════════

    public static ObjectNode toolSchemaGetCustomObjects(ObjectMapper mapper) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", mapper.createObjectNode());
        schema.set("required", mapper.createArrayNode());
        return schema;
    }

    public static String toolNameGetCustomObjects() {
        return "cloudcc_getCustomObjects";
    }

    public static String toolDescriptionGetCustomObjects() {
        return "获取 CloudCC 自定义对象列表（组织自行创建的业务对象）。"
                + "返回每个对象的中文标签、API 名称和前缀。查询自定义对象数据前必须先调用此工具确认正确的对象 API 名称。";
    }

    // ═══════════════════════════════════════════════════════
    // Tool 4: cloudcc_getObjectFields
    // ═══════════════════════════════════════════════════════

    public static ObjectNode toolSchemaGetObjectFields(ObjectMapper mapper) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode props = mapper.createObjectNode();
        ObjectNode prefixField = mapper.createObjectNode();
        prefixField.put("type", "string");
        prefixField.put("description", "对象前缀（prefix），从对象列表接口获取。如 '001'、'006' 等。");
        props.set("objprefix", prefixField);

        schema.set("properties", props);
        schema.set("required", mapper.createArrayNode().add("objprefix"));
        return schema;
    }

    public static String toolNameGetObjectFields() {
        return "cloudcc_getObjectFields";
    }

    public static String toolDescriptionGetObjectFields() {
        return "获取指定对象的字段列表，包括标准字段和自定义字段。"
                + "返回每个字段的中文名、API 名称和类型。查询数据时可参考此列表选择需要返回的字段。";
    }

    // ═══════════════════════════════════════════════════════
    // Setup API Tools — 元数据查询
    // CloudCC Setup API 的网关路径与 OpenAPI 不同：
    //   OpenAPI: https://xxx.apis.cloudcc.cn/lightningapi/openApi/common
    //   Setup:   https://xxx.apis.cloudcc.cn/setup/api/...
    // 即把 base_url 中的 "lightningapi" 替换为 "setup"
    // ═══════════════════════════════════════════════════════

    /**
     * 获取标准对象列表（客户、联系人、商机、产品等系统内置对象）。
     */
    public String getStandardObjects(String orgId, String userId) {
        return callSetupApi(orgId, userId, "/api/customObject/standardObjList", Map.of(),
                resp -> formatStandardObjectList(resp));
    }

    /**
     * 获取自定义对象列表（组织自行创建的业务对象）。
     */
    public String getCustomObjects(String orgId, String userId) {
        return callSetupApi(orgId, userId, "/api/customObject/list", Map.of(),
                resp -> formatCustomObjectList(resp));
    }

    /**
     * 获取对象的字段列表（标准字段 + 自定义字段）。
     */
    public String getObjectFields(String orgId, String userId, String objprefix) {
        return callSetupApi(orgId, userId, "/api/fieldSetup/queryField",
                Map.of("prefix", objprefix), resp -> formatObjectFields(resp));
    }

    /**
     * 通用 Setup API 调用封装。
     */
    private String callSetupApi(String orgId, String userId, String apiPath,
                                Map<String, String> bodyParams,
                                java.util.function.Function<JsonNode, String> formatter) {
        var ctx = tokenService.getSessionContext(orgId, userId);
        if (ctx.isEmpty()) {
            return error("无法获取 CloudCC 访问令牌，请确认已在「集成应用」中绑定 CloudCC 账号。");
        }

        String baseUrl = ensureBaseUrl(ctx.get().baseUrl());
        String accessToken = ctx.get().accessToken();

        // Setup API 网关路径：把 lightningapi 替换为 setup
        String setupUrl = baseUrl.replaceFirst("lightningapi$", "setup");
        // 兼容不以 lightningapi 结尾的情况
        if (!setupUrl.contains("/setup")) {
            int lastSlash = baseUrl.lastIndexOf("/");
            if (lastSlash > 0) {
                setupUrl = baseUrl.substring(0, lastSlash) + "/setup";
            }
        }

        String fullUrl = setupUrl + apiPath;
        log.info("CloudCC Setup API: url={}, params={}", fullUrl, bodyParams);

        try {
            ObjectNode body = objectMapper.createObjectNode();
            bodyParams.forEach(body::put);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .header("Content-Type", "application/json")
                    .header("accessToken", accessToken)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // 401 自动重试
            if (response.statusCode() == 401) {
                log.warn("CloudCC Setup 401，刷新令牌后重试");
                tokenService.invalidateSessionContext(orgId, userId);
                var fresh = tokenService.getSessionContext(orgId, userId);
                if (fresh.isEmpty()) {
                    return error("CloudCC 令牌刷新失败。");
                }
                request = HttpRequest.newBuilder()
                        .uri(URI.create(fullUrl))
                        .header("Content-Type", "application/json")
                        .header("accessToken", fresh.get().accessToken())
                        .timeout(Duration.ofSeconds(30))
                        .POST(HttpRequest.BodyPublishers.ofString(
                                objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                        .build();
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!root.path("result").asBoolean(false)) {
                return error("Setup API 调用失败: " + root.path("returnInfo").asText("未知错误"));
            }
            return formatter.apply(root);

        } catch (Exception e) {
            log.error("CloudCC Setup API 调用失败: {}", e.getMessage(), e);
            return error("调用失败: " + e.getMessage());
        }
    }

    private String formatStandardObjectList(JsonNode root) {
        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            return "✅ 标准对象列表为空";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("✅ 标准对象共 %d 个\n", data.size()));
        sb.append("─".repeat(60)).append("\n");
        for (JsonNode item : data) {
            String label = item.path("objname").asText("");
            String apiName = item.path("label").asText("");
            String prefix = item.path("objprefix").asText("");
            sb.append(String.format("  %-20s  API: %-25s  前缀: %s\n", label, apiName, prefix));
        }
        return sb.toString();
    }

    private String formatCustomObjectList(JsonNode root) {
        JsonNode data = root.path("data");
        JsonNode objList = data.path("objList");
        if (!objList.isArray() || objList.isEmpty()) {
            return "✅ 自定义对象列表为空";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("✅ 自定义对象共 %d 个\n", objList.size()));
        sb.append("─".repeat(60)).append("\n");
        for (JsonNode item : objList) {
            String label = item.path("objLabel").asText("");
            String apiName = item.path("schemetable_name").asText("");
            String prefix = item.path("prefix").asText("");
            sb.append(String.format("  %-20s  API: %-25s  前缀: %s\n", label, apiName, prefix));
        }
        return sb.toString();
    }

    private String formatObjectFields(JsonNode root) {
        JsonNode data = root.path("data");
        JsonNode obj = data.path("obj");
        JsonNode stdFields = data.path("stdFields");
        JsonNode cusFields = data.path("cusFields");

        String objLabel = obj.path("objLabel").asText("");
        String objApiName = obj.path("schemetableName").asText("");

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("✅ 对象: %s (API: %s)\n", objLabel, objApiName));

        int stdCount = stdFields.isArray() ? stdFields.size() : 0;
        int cusCount = cusFields.isArray() ? cusFields.size() : 0;
        sb.append(String.format("  标准字段 %d 个 | 自定义字段 %d 个\n", stdCount, cusCount));
        sb.append("─".repeat(60)).append("\n");

        if (stdFields.isArray() && !stdFields.isEmpty()) {
            sb.append("【标准字段】\n");
            for (JsonNode f : stdFields) {
                String name = f.path("labelName").asText("");
                String apiName = f.path("schemefieldName").asText("");
                String type = f.path("schemefieldType").asText("");
                sb.append(String.format("  %-18s  API: %-25s  类型: %s\n", name, apiName, type));
            }
        }

        if (cusFields.isArray() && !cusFields.isEmpty()) {
            sb.append("【自定义字段】\n");
            for (JsonNode f : cusFields) {
                String name = f.path("labelName").asText("");
                String apiName = f.path("schemefieldName").asText("");
                String type = f.path("schemefieldType").asText("");
                sb.append(String.format("  %-18s  API: %-25s  类型: %s\n", name, apiName, type));
            }
        }

        return sb.toString();
    }
}
