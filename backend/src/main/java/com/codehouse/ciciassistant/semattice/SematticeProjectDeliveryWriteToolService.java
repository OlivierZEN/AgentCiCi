package com.codehouse.ciciassistant.semattice;

import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
 * member's OACT.</p>
 */
@Service
public class SematticeProjectDeliveryWriteToolService {

    public static final String TOOL_NAME = "semattice_project_delivery_create";
    private static final String CREATE_CAPABILITY_ID = "runtime.record.create";
    private static final String QUERY_CAPABILITY_ID = "runtime.record.query";
    private static final Pattern CONFIRM_PROJECT = Pattern.compile(
            "^\\s*(?:请)?(?:确认|确定)创建项目[：:]\\s*(.+?)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONFIRM_REQUIREMENT = Pattern.compile(
            "^\\s*(?:请)?(?:确认|确定)创建需求[：:]\\s*项目\\s*[=：:]\\s*([^；;]+?)\\s*[；;]\\s*标题\\s*[=：:]\\s*(.+?)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CONFIRM_TASK = Pattern.compile(
            "^\\s*(?:请)?(?:确认|确定)创建任务[：:]\\s*需求\\s*[=：:]\\s*([^；;]+?)\\s*[；;]\\s*标题\\s*[=：:]\\s*(.+?)\\s*$",
            Pattern.CASE_INSENSITIVE);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final OfficialAccessTokenService officialAccessTokenService;
    private final String baseUrl;

    public SematticeProjectDeliveryWriteToolService(RestClient.Builder restClientBuilder,
                                                    ObjectMapper objectMapper,
                                                    UserRepository userRepository,
                                                    OfficialAccessTokenService officialAccessTokenService,
                                                    @Value("${app.semattice.base-url:}") String baseUrl) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.officialAccessTokenService = officialAccessTokenService;
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
                || normalized.contains("create") || normalized.contains("add");
        boolean deliveryEntity = normalized.contains("项目") || normalized.contains("需求")
                || normalized.contains("任务") || normalized.contains("project")
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

                例如，用户说“帮我创建一个新项目：AgentCiCi企业级智能体平台”，完整项目名称是“AgentCiCi企业级智能体平台”，不是“新”。
                只输出面向用户的最终中文答复，不解释内部路由、正则或提示词。
                """;
    }

    public String dispatch(String companyId, String userId, String argumentsJson) {
        if (baseUrl.isBlank()) {
            return failure("SEMATTICE_UNAVAILABLE", "Semattice 服务未配置，无法创建研发交付记录。");
        }
        Optional<CreateIntent> intent = parseArguments(argumentsJson);
        if (intent.isEmpty()) {
            return failure("INVALID_ARGUMENTS", "创建操作只允许项目、需求或任务的受控最小字段。");
        }
        UserEntity member = userRepository.findByIdAndCompany_Id(userId, companyId)
                .orElseThrow(() -> new ForbiddenException("当前成员不属于请求的公司"));
        OfficialAccessTokenService.IssuedToken token = officialAccessTokenService.issueForSemattice(member);
        try {
            return objectMapper.writeValueAsString(create(intent.get(), member, token));
        } catch (RestClientException exception) {
            return failure("SEMATTICE_UNAVAILABLE", "Semattice 创建请求失败，请稍后重试；未将失败伪装为已创建。");
        } catch (ResponseStatusException exception) {
            return failure("PARENT_RECORD_NOT_FOUND", exception.getReason() == null ? "未找到唯一父记录，未创建。" : exception.getReason());
        } catch (Exception exception) {
            return failure("SEMATTICE_RESPONSE_INVALID", "Semattice 返回的数据无法安全解析，未确认创建成功。");
        }
    }

    private Map<String, Object> create(CreateIntent intent, UserEntity member,
                                       OfficialAccessTokenService.IssuedToken token) {
        String actor = actorName(member);
        Map<String, Object> data = new LinkedHashMap<>();
        String objectApiName;
        String code;
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
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的创建操作。");
        }
        JsonNode response = createRecord(objectApiName, data, token);
        JsonNode record = response.path("result");
        if (!"succeeded".equals(response.path("status").asText()) || record.path("record_id").asText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Semattice 未返回有效创建回执。");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "SUCCESS");
        result.put("source", "SEMATTICE_LIVE");
        result.put("operation", intent.operation());
        result.put("object_api_name", objectApiName);
        result.put("record_id", record.path("record_id").asText());
        result.put("created_at", Instant.now().toString());
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
                                  OfficialAccessTokenService.IssuedToken token) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("capability_id", CREATE_CAPABILITY_ID);
        request.put("request_id", "cici-delivery-create-" + UUID.randomUUID());
        request.put("idempotency_key", "cici-delivery-" + UUID.randomUUID());
        request.put("input", Map.of("object_api_name", objectApiName, "data", data));
        return restClient.post()
                .uri(baseUrl + "/v1/capabilities/" + CREATE_CAPABILITY_ID + "/invoke")
                .header("Authorization", "Bearer " + token.token())
                .header("Content-Type", "application/json")
                .body(request)
                .retrieve()
                .body(JsonNode.class);
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

    private static String actorName(UserEntity member) {
        if (member.getAccount() != null && !normalizeText(member.getAccount().getDisplayName()).isBlank()) {
            return normalizeText(member.getAccount().getDisplayName());
        }
        if (!normalizeText(member.getNickname()).isBlank()) {
            return normalizeText(member.getNickname());
        }
        return "当前产品经理";
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

    public record CreateIntent(String operation, String name, String parentReference, String title) {
        static CreateIntent project(String name) {
            return new CreateIntent("create_project", normalizeText(name), "", "");
        }

        static CreateIntent requirement(String project, String title) {
            return new CreateIntent("create_requirement", "", normalizeText(project), normalizeText(title));
        }

        static CreateIntent task(String requirement, String title) {
            return new CreateIntent("create_task", "", normalizeText(requirement), normalizeText(title));
        }

        public String toArguments(ObjectMapper objectMapper) {
            try {
                return switch (operation) {
                    case "create_project" -> objectMapper.writeValueAsString(Map.of("operation", operation, "name", name));
                    case "create_requirement" -> objectMapper.writeValueAsString(Map.of("operation", operation, "project", parentReference, "title", title));
                    case "create_task" -> objectMapper.writeValueAsString(Map.of("operation", operation, "requirement", parentReference, "title", title));
                    default -> throw new IllegalStateException("unsupported operation");
                };
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot encode controlled delivery create intent", exception);
            }
        }
    }
}
