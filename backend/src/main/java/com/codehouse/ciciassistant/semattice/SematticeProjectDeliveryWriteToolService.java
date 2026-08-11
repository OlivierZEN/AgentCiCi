package com.codehouse.ciciassistant.semattice;

import com.codehouse.ciciassistant.agent.service.AgentServicePrincipalExecutionService;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Controlled writer for the published DEV Autopilot delivery objects.
 *
 * <p>This tool is intentionally not exposed in the model function schema. Chat orchestration calls it
 * only after an exact user confirmation, and Semattice derives actor and tenant from the current
 * Agent's governed SERVICE OACT. The logged-in human only supplies delegation and confirmation context.</p>
 */
@Service
public class SematticeProjectDeliveryWriteToolService {

    public static final String TOOL_NAME = "semattice_project_delivery_create";
    private static final String CREATE_CAPABILITY_ID = "runtime.record.create";
    private static final String READ_CAPABILITY_ID = "runtime.record.get";
    private static final String QUERY_CAPABILITY_ID = "runtime.record.query";
    private static final Pattern CONFIRM_PROJECT = Pattern.compile(
            "^\\s*(?:请)?(?:确认|确定)创建项目[：:]\\s*(.+?)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONFIRM_REQUIREMENT = Pattern.compile(
            "^\\s*(?:请)?(?:确认|确定)创建需求[：:]\\s*项目\\s*[=：:]\\s*([^；;]+?)\\s*[；;]\\s*标题\\s*[=：:]\\s*(.+?)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CONFIRM_TASK = Pattern.compile(
            "^\\s*(?:请)?(?:确认|确定)创建任务[：:]\\s*需求\\s*[=：:]\\s*([^；;]+?)\\s*[；;]\\s*标题\\s*[=：:]\\s*(.+?)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CONFIRM_DEFECT = Pattern.compile(
            "^\\s*(?:请)?(?:确认|确定)(?:创建|提交|记录)缺陷[：:]\\s*(.+?)\\s*$",
            Pattern.CASE_INSENSITIVE);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AgentServicePrincipalExecutionService executionPrincipalService;
    private final String baseUrl;

    public SematticeProjectDeliveryWriteToolService(RestClient.Builder restClientBuilder,
                                                    ObjectMapper objectMapper,
                                                    AgentServicePrincipalExecutionService executionPrincipalService,
                                                    @Value("${app.semattice.base-url:}") String baseUrl) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.executionPrincipalService = executionPrincipalService;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    public static Optional<CreateIntent> confirmedIntent(String question) {
        String value = question == null ? "" : question.trim();
        Matcher project = CONFIRM_PROJECT.matcher(value);
        if (project.matches()) {
            return Optional.of(CreateIntent.project(project.group(1)));
        }
        Matcher requirement = CONFIRM_REQUIREMENT.matcher(value);
        if (requirement.matches()) {
            return Optional.of(CreateIntent.requirement(requirement.group(1), requirement.group(2)));
        }
        Matcher task = CONFIRM_TASK.matcher(value);
        if (task.matches()) {
            return Optional.of(CreateIntent.task(task.group(1), task.group(2)));
        }
        Matcher defect = CONFIRM_DEFECT.matcher(value);
        if (defect.matches()) {
            return parseDefectConfirmation(defect.group(1));
        }
        return Optional.empty();
    }

    /**
     * Broadly routes a possible create request to the model. This method deliberately does not
     * extract names, titles, or parent references; business semantics belong to the model turn.
     */
    public static boolean isDraftRequest(String question) {
        String value = question == null ? "" : question.trim();
        if (value.isBlank() || confirmedIntent(value).isPresent()) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        boolean createLanguage = normalized.contains("创建") || normalized.contains("新建")
                || normalized.contains("新增") || normalized.contains("建立")
                || normalized.contains("提交") || normalized.contains("记录") || normalized.contains("登记")
                || normalized.contains("create") || normalized.contains("add") || normalized.contains("submit")
                || normalized.contains("record");
        boolean deliveryEntity = normalized.contains("项目") || normalized.contains("需求")
                || normalized.contains("任务") || normalized.contains("缺陷") || normalized.contains("bug")
                || normalized.contains("defect") || normalized.contains("project")
                || normalized.contains("requirement") || normalized.contains("task");
        return createLanguage && deliveryEntity;
    }

    /** Model-only contract for understanding an unconfirmed delivery create request. */
    public static String modelDraftPrompt() {
        return """
                你正在处理 DEV Autopilot 研发交付产品经理的一轮“创建草案”对话。

                本轮必须先由你基于完整用户消息和会话上下文进行语义理解。服务端没有、也不会用正则替你抽取项目名、需求标题或任务标题。请先判断用户真正想创建的对象，再识别完整业务名称和父级信息。

                强制边界：
                1. 本轮只生成草案或追问，不调用任何工具，不写入 Semattice，不得声称已经创建成功。
                2. 不得把“新”“一个”“研发”“项目”“需求”“任务”等类别或修饰词误当成业务名称。
                3. 名称通常位于冒号、引号、“名称叫/为/是”之后，或由整句语义明确给出；必须保留大小写、中文、空格和产品专名的完整内容。
                4. 如果完整名称、标题或必需父级无法从上下文确定，只问一个聚焦问题，不臆造值，也不输出可执行的伪确认。
                5. 用户尚未发送精确确认指令，因此无论信息多完整，本轮都只能返回待确认草案。

                如果是创建项目，严格按以下中文结构输出，其中占位符必须替换为你理解出的完整项目名称：
                我理解你要创建一个研发项目。
                拟创建项目：<完整项目名称>
                初始状态：规划中｜健康度：待评估｜进度：0%｜版本：v0.1.0
                确认无误后，请回复：`确认创建项目：<完整项目名称>`。确认后我会返回 Semattice 的实际项目编号。

                如果是创建需求，先识别父项目和完整需求标题；信息完整时给出草案，并以 `确认创建需求：项目=<父项目编号或名称>；标题=<完整需求标题>` 作为唯一确认文本。

                如果是创建任务，先识别父需求和完整任务标题；信息完整时给出草案，并以 `确认创建任务：需求=<父需求编号或标题>；标题=<完整任务标题>` 作为唯一确认文本。

                如果是提交缺陷，必须先识别父项目、标题、描述、严重度（critical/high/medium/low）、优先级（P0/P1/P2/P3）、环境、复现步骤、预期结果和实际结果。信息完整时以如下唯一格式请求确认：
                `确认提交缺陷：项目=<父项目编号或名称>；标题=<标题>；描述=<描述>；严重度=<critical|high|medium|low>；优先级=<P0|P1|P2|P3>；环境=<环境>；复现步骤=<步骤>；预期结果=<预期>；实际结果=<实际>`
                缺少任一项时只追问缺失信息，不生成 Bug 编号、记录 ID 或对象位置。

                例如，用户说“帮我创建一个新项目：AgentCiCi企业级智能体平台”，完整项目名称是“AgentCiCi企业级智能体平台”，不是“新”。
                只输出面向用户的最终中文答复，不解释内部路由、正则或提示词。
                """;
    }

    public String dispatch(String companyId, String userId, String agentId, String argumentsJson) {
        if (baseUrl.isBlank()) {
            return failure("SEMATTICE_UNAVAILABLE", "Semattice 服务未配置，无法创建研发交付记录。");
        }
        Optional<CreateIntent> intent = parseArguments(argumentsJson);
        if (intent.isEmpty()) {
            return failure("INVALID_ARGUMENTS", "创建操作只允许项目、需求、任务或缺陷的受控字段。");
        }
        AgentServicePrincipalExecutionService.ExecutionAuthorization authorization =
                executionPrincipalService.authorizeSemattice(
                        companyId,
                        userId,
                        agentId,
                        List.of("runtime.record.read", "runtime.record.create"),
                        "semattice_project_delivery_create");
        OfficialAccessTokenService.IssuedToken token = authorization.token();
        try {
            return objectMapper.writeValueAsString(create(intent.get(), authorization, token));
        } catch (RestClientException exception) {
            return failure("SEMATTICE_UNAVAILABLE", "Semattice 创建请求失败，请稍后重试；未将失败伪装为已创建。");
        } catch (ResponseStatusException exception) {
            String reason = exception.getReason() == null ? "Semattice 未返回可验证结果，未确认创建成功。" : exception.getReason();
            if (reason.contains("未找到") || reason.contains("多个同名")) {
                return failure("PARENT_RECORD_NOT_FOUND", reason);
            }
            if (reason.contains("回读") || reason.contains("回执")) {
                return failure("SEMATTICE_WRITE_UNVERIFIED", reason);
            }
            return failure("SEMATTICE_UNAVAILABLE", reason);
        } catch (Exception exception) {
            return failure("SEMATTICE_RESPONSE_INVALID", "Semattice 返回的数据无法安全解析，未确认创建成功。");
        }
    }

    private Map<String, Object> create(CreateIntent intent,
                                       AgentServicePrincipalExecutionService.ExecutionAuthorization authorization,
                                       OfficialAccessTokenService.IssuedToken token) {
        String actor = normalizeText(authorization.servicePrincipalDisplayName());
        if (actor.isBlank()) {
            actor = "DEV Autopilot 产品经理";
        }
        Map<String, Object> data = new LinkedHashMap<>();
        String objectApiName;
        String code;
        String correlationId = "cici-delivery-" + UUID.randomUUID();
        Map<String, Object> parent = Map.of();
        switch (intent.operation()) {
            case "create_project" -> {
                objectApiName = "dev_project";
                code = newCode("DAS");
                data.put("code", code);
                data.put("name", intent.name());
                data.put("owner", actor);
                data.put("status", "规划中");
                data.put("health", "待评估");
                data.put("progress", 0);
                data.put("release", "v0.1.0");
                data.put("description", "由研发交付产品经理创建");
            }
            case "create_requirement" -> {
                objectApiName = "dev_requirement";
                parent = requireSingleRecord("dev_project", intent.parentReference(), token, "项目");
                code = newCode("REQ");
                data.put("code", code);
                data.put("project_id", parent.get("record_id"));
                data.put("title", intent.title());
                data.put("status", "待确认");
                data.put("priority", "P1");
                data.put("owner", actor);
                data.put("summary", "由研发交付产品经理创建");
                data.put("acceptance", List.of());
            }
            case "create_task" -> {
                objectApiName = "dev_task";
                parent = requireSingleRecord("dev_requirement", intent.parentReference(), token, "需求");
                code = "";
                data.put("project_id", parent.get("project_id"));
                data.put("requirement_id", parent.get("record_id"));
                data.put("title", intent.title());
                data.put("status", "待开始");
                data.put("owner", "研发待分配");
                data.put("estimate", 8);
                data.put("sequence", 1);
                data.put("actual_hours", 0);
            }
            case "create_defect" -> {
                objectApiName = "dev_defect";
                parent = requireSingleRecord("dev_project", intent.parentReference(), token, "项目");
                code = newCode("BUG");
                data.put("project_id", parent.get("record_id"));
                data.put("code", code);
                data.put("title", intent.title());
                data.put("description", intent.description());
                data.put("severity", intent.severity());
                data.put("priority", intent.priority());
                data.put("status", "new");
                data.put("reporter_principal_id", authorization.delegatedByPrincipalId());
                data.put("environment", intent.environment());
                data.put("reproduction_steps", List.of(intent.reproductionSteps()));
                data.put("expected_result", intent.expectedResult());
                data.put("actual_result", intent.actualResult());
                data.put("source", "chat");
                data.put("correlation_id", correlationId);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的创建操作。");
        }
        JsonNode response = createRecord(objectApiName, data, token, correlationId);
        JsonNode record = response.path("result");
        if (!"succeeded".equals(response.path("status").asText()) || record.path("record_id").asText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Semattice 未返回有效创建回执。");
        }
        JsonNode verified = readRecord(objectApiName, record.path("record_id").asText(), token, correlationId);
        JsonNode verifiedData = verified.path("data");
        if (!verifiedData.isObject() || !matchesWrittenData(verifiedData, data)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Semattice 写入后回读不一致，不能确认创建成功。");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "SUCCESS");
        result.put("source", "SEMATTICE_LIVE");
        result.put("operation", intent.operation());
        result.put("object_api_name", objectApiName);
        result.put("record_id", verified.path("record_id").asText());
        result.put("revision", verified.path("revision").asLong());
        result.put("correlation_id", correlationId);
        result.put("readback_verified", true);
        result.put("created_at", Instant.now().toString());
        result.put("execution_principal_type", "SERVICE");
        result.put("execution_principal", actor);
        result.put("delegation_policy", authorization.delegationPolicy());
        if (!code.isBlank()) {
            result.put("code", code);
        }
        if (intent.operation().equals("create_project")) {
            result.put("name", intent.name());
        } else {
            result.put("title", intent.title());
            result.put("parent_record_id", parent.get("record_id"));
        }
        return result;
    }

    private boolean matchesWrittenData(JsonNode verifiedData, Map<String, Object> writtenData) {
        JsonNode expected = objectMapper.valueToTree(writtenData);
        for (var fields = expected.fields(); fields.hasNext(); ) {
            var field = fields.next();
            if (Set.of("code", "correlation_id").contains(field.getKey())) {
                continue;
            }
            if (!field.getValue().equals(verifiedData.path(field.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> requireSingleRecord(String objectApiName, String reference,
                                                     OfficialAccessTokenService.IssuedToken token, String label) {
        List<Map<String, Object>> matches = queryRecords(objectApiName, token).stream()
                .filter(record -> matchesReference(record, reference))
                .toList();
        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未找到对应" + label + "，未创建记录。");
        }
        if (matches.size() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "找到多个同名" + label + "，请使用编号确认，未创建记录。");
        }
        return matches.get(0);
    }

    private List<Map<String, Object>> queryRecords(String objectApiName, OfficialAccessTokenService.IssuedToken token) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("capability_id", QUERY_CAPABILITY_ID);
        request.put("request_id", "cici-delivery-parent-" + UUID.randomUUID());
        request.put("input", Map.of("object_api_name", objectApiName, "limit", 100));
        JsonNode response = restClient.post()
                .uri(baseUrl + "/v1/capabilities/" + QUERY_CAPABILITY_ID + "/invoke")
                .header("Authorization", "Bearer " + token.token())
                .header("Content-Type", "application/json")
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        if (response == null || !"succeeded".equals(response.path("status").asText())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Semattice 父记录查询失败，未创建记录。");
        }
        return java.util.stream.StreamSupport.stream(response.path("result").path("records").spliterator(), false)
                .filter(item -> item.path("data").isObject())
                .map(item -> {
                    Map<String, Object> record = objectMapper.convertValue(item.path("data"), Map.class);
                    record.put("record_id", item.path("record_id").asText());
                    return record;
                })
                .toList();
    }

    private JsonNode createRecord(String objectApiName, Map<String, Object> data,
                                  OfficialAccessTokenService.IssuedToken token, String correlationId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("capability_id", CREATE_CAPABILITY_ID);
        request.put("request_id", correlationId);
        request.put("idempotency_key", correlationId);
        request.put("input", Map.of("object_api_name", objectApiName, "data", data));
        return restClient.post()
                .uri(baseUrl + "/v1/capabilities/" + CREATE_CAPABILITY_ID + "/invoke")
                .header("Authorization", "Bearer " + token.token())
                .header("Content-Type", "application/json")
                .body(request)
                .retrieve()
                .body(JsonNode.class);
    }

    private JsonNode readRecord(String objectApiName, String recordId,
                                OfficialAccessTokenService.IssuedToken token, String correlationId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("capability_id", READ_CAPABILITY_ID);
        request.put("request_id", correlationId + "-readback");
        request.put("input", Map.of("object_api_name", objectApiName, "record_id", recordId));
        JsonNode response = restClient.post()
                .uri(baseUrl + "/v1/capabilities/" + READ_CAPABILITY_ID + "/invoke")
                .header("Authorization", "Bearer " + token.token())
                .header("Content-Type", "application/json")
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        JsonNode record = response == null ? null : response.path("result");
        if (response == null || !"succeeded".equals(response.path("status").asText())
                || record == null || !recordId.equals(record.path("record_id").asText())
                || record.path("revision").asLong() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Semattice 写入后回读失败，不能确认创建成功。");
        }
        return record;
    }

    private Optional<CreateIntent> parseArguments(String argumentsJson) {
        try {
            JsonNode root = objectMapper.readTree(argumentsJson == null ? "" : argumentsJson);
            if (!root.isObject() || root.has("tenant_id") || root.has("company_id") || root.has("user_id") || root.has("token")) {
                return Optional.empty();
            }
            String operation = root.path("operation").asText();
            if ("create_project".equals(operation) && onlyFields(root, "operation", "name")) {
                return Optional.of(CreateIntent.project(root.path("name").asText()));
            }
            if ("create_requirement".equals(operation) && onlyFields(root, "operation", "project", "title")) {
                return Optional.of(CreateIntent.requirement(root.path("project").asText(), root.path("title").asText()));
            }
            if ("create_task".equals(operation) && onlyFields(root, "operation", "requirement", "title")) {
                return Optional.of(CreateIntent.task(root.path("requirement").asText(), root.path("title").asText()));
            }
            if ("create_defect".equals(operation) && onlyFields(root,
                    "operation", "project", "title", "description", "severity", "priority", "environment",
                    "reproduction_steps", "expected_result", "actual_result")) {
                return CreateIntent.defect(
                        root.path("project").asText(), root.path("title").asText(), root.path("description").asText(),
                        root.path("severity").asText(), root.path("priority").asText(), root.path("environment").asText(),
                        root.path("reproduction_steps").asText(), root.path("expected_result").asText(), root.path("actual_result").asText());
            }
        } catch (Exception ignored) {
            // Return the stable invalid-arguments result below.
        }
        return Optional.empty();
    }

    private static boolean onlyFields(JsonNode root, String... fields) {
        if (root.size() != fields.length) {
            return false;
        }
        for (String field : fields) {
            if (!root.path(field).isTextual() || normalizeText(root.path(field).asText()).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesReference(Map<String, Object> record, String reference) {
        String expected = normalizeText(reference);
        return expected.equalsIgnoreCase(normalizeText(String.valueOf(record.get("record_id"))))
                || expected.equalsIgnoreCase(normalizeText(String.valueOf(record.get("code"))))
                || expected.equalsIgnoreCase(normalizeText(String.valueOf(record.get("name"))))
                || expected.equalsIgnoreCase(normalizeText(String.valueOf(record.get("title"))));
    }

    private static String newCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static Optional<CreateIntent> parseDefectConfirmation(String body) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String segment : body.split("[；;]")) {
            String[] pair = segment.split("[=：:]", 2);
            if (pair.length != 2 || normalizeText(pair[0]).isBlank() || normalizeText(pair[1]).isBlank()) {
                return Optional.empty();
            }
            values.put(normalizeText(pair[0]), normalizeText(pair[1]));
        }
        if (!values.keySet().equals(Set.of("项目", "标题", "描述", "严重度", "优先级", "环境", "复现步骤", "预期结果", "实际结果"))) {
            return Optional.empty();
        }
        return CreateIntent.defect(values.get("项目"), values.get("标题"), values.get("描述"), values.get("严重度"),
                values.get("优先级"), values.get("环境"), values.get("复现步骤"), values.get("预期结果"), values.get("实际结果"));
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String failure(String code, String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("status", "FAILED", "code", code, "message", message));
        } catch (Exception ignored) {
            return "{\"status\":\"FAILED\",\"code\":\"" + code + "\"}";
        }
    }

    public record CreateIntent(String operation, String name, String parentReference, String title,
                               String description, String severity, String priority, String environment,
                               String reproductionSteps, String expectedResult, String actualResult) {
        static CreateIntent project(String name) {
            return new CreateIntent("create_project", normalizeText(name), "", "", "", "", "", "", "", "", "");
        }

        static CreateIntent requirement(String project, String title) {
            return new CreateIntent("create_requirement", "", normalizeText(project), normalizeText(title), "", "", "", "", "", "", "");
        }

        static CreateIntent task(String requirement, String title) {
            return new CreateIntent("create_task", "", normalizeText(requirement), normalizeText(title), "", "", "", "", "", "", "");
        }

        static Optional<CreateIntent> defect(String project, String title, String description, String severity,
                                             String priority, String environment, String reproductionSteps,
                                             String expectedResult, String actualResult) {
            String normalizedSeverity = normalizeText(severity).toLowerCase(Locale.ROOT);
            String normalizedPriority = normalizeText(priority).toUpperCase(Locale.ROOT);
            if (!Set.of("critical", "high", "medium", "low").contains(normalizedSeverity)
                    || !Set.of("P0", "P1", "P2", "P3").contains(normalizedPriority)) {
                return Optional.empty();
            }
            return Optional.of(new CreateIntent("create_defect", "", normalizeText(project), normalizeText(title),
                    normalizeText(description), normalizedSeverity, normalizedPriority, normalizeText(environment),
                    normalizeText(reproductionSteps), normalizeText(expectedResult), normalizeText(actualResult)));
        }

        public String toArguments(ObjectMapper objectMapper) {
            try {
                return switch (operation) {
                    case "create_project" -> objectMapper.writeValueAsString(Map.of("operation", operation, "name", name));
                    case "create_requirement" -> objectMapper.writeValueAsString(Map.of("operation", operation, "project", parentReference, "title", title));
                    case "create_task" -> objectMapper.writeValueAsString(Map.of("operation", operation, "requirement", parentReference, "title", title));
                    case "create_defect" -> objectMapper.writeValueAsString(Map.of(
                            "operation", operation, "project", parentReference, "title", title, "description", description,
                            "severity", severity, "priority", priority, "environment", environment,
                            "reproduction_steps", reproductionSteps, "expected_result", expectedResult, "actual_result", actualResult));
                    default -> throw new IllegalStateException("unsupported operation");
                };
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot encode controlled delivery create intent", exception);
            }
        }
    }
}
