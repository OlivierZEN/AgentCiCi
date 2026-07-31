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
    private static final Pattern PROJECT_NAMED_DRAFT = Pattern.compile(
            "(?:创建|新建)\\s*(?:一个)?\\s*(?:研发)?项目\\s*(?:名称)?\\s*(?:(?:叫|为|是)\\s*(?:[：:])?|[：:])\\s*[“\\\"]?(.+?)[”\\\"]?\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PROJECT_DRAFT = Pattern.compile(
            "(?:创建|新建)\\s*(?:一个)?\\s*[“\\\"]?(.+?)[”\\\"]?\\s*(?:的)?(?:研发)?项目", Pattern.CASE_INSENSITIVE);

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

    /** Returns a deterministic draft for a write intent, without any remote side effect. */
    public static Optional<String> draftResponse(String question) {
        String value = question == null ? "" : question.trim();
        if (value.isBlank() || confirmedIntent(value).isPresent()) {
            return Optional.empty();
        }
        Matcher project = PROJECT_NAMED_DRAFT.matcher(value);
        boolean projectMatched = project.find();
        if (!projectMatched) {
            project = PROJECT_DRAFT.matcher(value);
            projectMatched = project.find();
        }
        if (projectMatched) {
            String name = normalizeText(project.group(1));
            if (!name.isBlank()) {
                return Optional.of("我可以直接在 Semattice 创建研发项目，但会先保留一次明确确认。\n\n"
                        + "拟创建项目：" + name + "\n初始状态：规划中｜健康度：待评估｜进度：0%｜版本：v0.1.0\n\n"
                        + "确认无误后，请回复：`确认创建项目：" + name + "`。确认后我会返回实际项目编号。");
            }
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if ((normalized.contains("创建") || normalized.contains("新建")) && normalized.contains("需求")) {
            return Optional.of("我可以创建需求记录。请先明确父项目和需求标题；确认时请回复：\n"
                    + "`确认创建需求：项目=DAS-项目编号；标题=需求标题`\n\n"
                    + "我会先核验项目唯一存在，再写入 Semattice 并返回需求编号。");
        }
        if ((normalized.contains("创建") || normalized.contains("新建")) && normalized.contains("任务")) {
            return Optional.of("我可以创建研发任务。请先明确父需求和任务标题；确认时请回复：\n"
                    + "`确认创建任务：需求=REQ-需求编号；标题=任务标题`\n\n"
                    + "我会先核验需求唯一存在，再写入 Semattice 并返回任务编号。");
        }
        return Optional.empty();
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
