package com.codehouse.ciciassistant.semattice;

import com.codehouse.ciciassistant.agent.service.AgentServicePrincipalExecutionService;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
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
    private static final Pattern CONFIRM_PENDING_INTAKE = Pattern.compile(
            "^\\s*(?:请)?(?:确认|确定)(?:提交|登记|创建|记录)?(?:研发事项|需求|缺陷|变更)?[。！!]?\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INTAKE_MARKER = Pattern.compile(
            "<!--\\s*DEV_AUTOPILOT_INTAKE_V1\\s*(\\{.*?})\\s*-->", Pattern.DOTALL);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AgentServicePrincipalExecutionService executionPrincipalService;
    private final DevAutopilotDeveloperAssignmentService developerAssignmentService;
    private final String baseUrl;

    public SematticeProjectDeliveryWriteToolService(RestClient.Builder restClientBuilder,
                                                    ObjectMapper objectMapper,
                                                    AgentServicePrincipalExecutionService executionPrincipalService,
                                                    DevAutopilotDeveloperAssignmentService developerAssignmentService,
                                                    @Value("${app.semattice.base-url:}") String baseUrl) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.executionPrincipalService = executionPrincipalService;
        this.developerAssignmentService = developerAssignmentService;
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

    /** Restores a user-friendly short confirmation from the latest validated intake draft. */
    public static Optional<CreateIntent> confirmedIntent(String question,
                                                          List<Map<String, Object>> messages,
                                                          String conversationId,
                                                          ObjectMapper objectMapper) {
        Optional<CreateIntent> exact = confirmedIntent(question);
        if (exact.isPresent()) {
            return exact;
        }
        if (!CONFIRM_PENDING_INTAKE.matcher(question == null ? "" : question.trim()).matches()) {
            return Optional.empty();
        }
        return pendingIntake(messages, objectMapper)
                .flatMap(intake -> intake.toCreateIntent(conversationId));
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
                || normalized.contains("希望") || normalized.contains("想要") || normalized.contains("建议")
                || normalized.contains("报错") || normalized.contains("无法") || normalized.contains("不能")
                || normalized.contains("不会") || normalized.contains("不生效") || normalized.contains("卡住")
                || normalized.contains("崩溃") || normalized.contains("闪退")
                || normalized.contains("不应该") || normalized.contains("应该") || normalized.contains("没有")
                || normalized.contains("异常") || normalized.contains("失败") || normalized.contains("错误")
                || normalized.contains("调整") || normalized.contains("修改") || normalized.contains("改成")
                || normalized.contains("优化") || normalized.contains("增加")
                || normalized.contains("create") || normalized.contains("add") || normalized.contains("submit")
                || normalized.contains("record");
        boolean deliveryEntity = normalized.contains("项目") || normalized.contains("需求")
                || normalized.contains("任务") || normalized.contains("缺陷") || normalized.contains("bug")
                || normalized.contains("变更") || normalized.contains("功能") || normalized.contains("页面")
                || normalized.contains("系统") || normalized.contains("产品") || normalized.contains("登录")
                || normalized.contains("退出") || normalized.contains("按钮") || normalized.contains("流程")
                || normalized.contains("能力") || normalized.contains("操作") || normalized.contains("使用")
                || normalized.contains("defect") || normalized.contains("project")
                || normalized.contains("requirement") || normalized.contains("change") || normalized.contains("task");
        return createLanguage && deliveryEntity;
    }

    /** Recognizes a field-only reply that completes a pending delivery draft in chat history. */
    public static boolean isDraftContinuation(String question) {
        String normalized = question == null ? "" : question.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || confirmedIntent(normalized).isPresent()) {
            return false;
        }
        return normalized.matches("^(?:父项目|项目编号|项目名称|项目|标题|描述|严重度|优先级|环境|复现步骤|预期结果|实际结果)\\s*[：:=].+")
                || normalized.matches("^(?:父项目|项目)(?:是|为|叫).+")
                || normalized.startsWith("补充：")
                || normalized.startsWith("补充:");
    }

    public static boolean hasPendingIntake(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        for (int index = messages.size() - 1; index >= 0; index--) {
            Map<String, Object> message = messages.get(index);
            String content = String.valueOf(message.getOrDefault("content", ""));
            if ("assistant".equals(String.valueOf(message.get("role")))
                    && isTerminalIntakeAnswer(content)) {
                return false;
            }
            Matcher marker = INTAKE_MARKER.matcher(content);
            JsonNode latest = null;
            while (marker.find()) {
                try {
                    JsonNode candidate = objectMapper.readTree(marker.group(1));
                    if (candidate.isObject()) {
                        latest = candidate;
                    }
                } catch (Exception ignored) {
                    // Malformed markers cannot keep a delivery intake pending.
                }
            }
            if (latest != null) {
                return !latest.path("cancelled").asBoolean(false);
            }
        }
        return visiblePendingIntake(messages, objectMapper).isPresent();
    }

    private static boolean isTerminalIntakeAnswer(String content) {
        String normalized = normalizeText(content);
        return normalized.contains("已在 Semattice 创建")
                || normalized.contains("本次受理已取消")
                || normalized.contains("研发事项已取消");
    }

    /** Model-only contract for understanding an unconfirmed delivery create request. */
    public static String modelDraftPrompt() {
        return """
                你正在处理 DEV Autopilot 研发交付产品经理的一轮“研发事项受理”对话。

                你面对的是普通产品使用者。先基于完整用户消息和会话上下文主动识别事项类型，再完成专业整理；不能要求用户自己选择对象类型或填写研发专业问卷。

                强制边界：
                1. 本轮只生成草案或追问，不调用任何工具，不写入 Semattice，不得声称已经创建成功。
                2. 主动分类：新增当前不存在的能力或业务结果是 requirement；已有能力偏离正常预期是 defect；调整已确认需求、范围、规则或交付方案是 change。
                3. `original_report` 必须逐字等于首次提出本事项的完整用户消息；`user_supplements` 每项也必须逐字等于后续真实用户消息，不得润色、摘要或补造用户原话。
                4. 产品经理负责推断专业字段。不得要求用户提供严重度、优先级、技术环境、完整复现步骤、预期结果、根因、测试方案或部署信息。缺陷字段不确定时写“待开发者验证”并放入 assumptions。
                5. 只有分类、父项目/父需求或用户真正期望无法判断时，才问一个业务语言的聚焦问题。不要一次列多个问题。
                6. 信息充分时展示分类依据、专业整理和待开发者验证项，请用户只回复“确认提交需求”“确认提交缺陷”“确认提交变更”或“确认提交”。
                7. 用户取消时明确回复已取消；不得保留可确认状态。

                如果是创建项目，严格按以下中文结构输出，其中占位符必须替换为你理解出的完整项目名称：
                我理解你要创建一个研发项目。
                拟创建项目：<完整项目名称>
                初始状态：规划中｜健康度：待评估｜进度：0%｜版本：v0.1.0
                确认无误后，请回复：`确认创建项目：<完整项目名称>`。确认后我会返回 Semattice 的实际项目编号。

                如果是创建任务，先识别父需求和完整任务标题；信息完整时给出草案，并以 `确认创建任务：需求=<父需求编号或标题>；标题=<完整任务标题>` 作为唯一确认文本。

                需求、缺陷或变更草案必须在回复最后附加一条 HTML 注释，页面不会向用户展示，但服务端会验证。不要使用 Markdown 代码块。JSON 必须是单个合法对象：
                <!-- DEV_AUTOPILOT_INTAKE_V1 {"classification":"requirement|defect|change","project":"父项目编号或名称","requirement":"变更的父需求编号或标题，其他类型为空","title":"专业标题","original_report":"首次用户消息逐字原文","pm_assessment":"产品经理分析与分类依据","priority":"P0|P1|P2|P3","severity":"critical|high|medium|low，非缺陷用空字符串","environment":"缺陷环境或待开发者验证","reproduction_steps":["缺陷复现线索"],"expected_result":"缺陷预期或空字符串","actual_result":"缺陷实际或空字符串","acceptance_criteria":["需求验收标准"],"impact_analysis":["变更影响"],"user_supplements":["后续用户消息逐字原文"],"assumptions":["待开发者验证的假设"],"clarification_question":"仍需用户回答的唯一问题，无则空字符串","ready_for_confirmation":true,"cancelled":false} -->

                类型特定要求：
                - requirement 必须有 project、title、pm_assessment、priority 和至少一条 acceptance_criteria。
                - defect 必须有 project、title、pm_assessment、priority、severity、environment、至少一条 reproduction_steps、expected_result 和 actual_result；无法确认的工程细节使用“待开发者验证”，不能向用户索要专业字段。
                - change 必须有 requirement、title、pm_assessment、priority 和至少一条 impact_analysis；project 可留空并由父需求解析。
                - 需要澄清时 `ready_for_confirmation=false` 且 `clarification_question` 非空；取消时 `cancelled=true` 且 `ready_for_confirmation=false`。

                例如，用户说“帮我创建一个新项目：AgentCiCi企业级智能体平台”，完整项目名称是“AgentCiCi企业级智能体平台”，不是“新”。
                只输出面向用户的最终中文答复和最后的不可见注释，不解释内部路由、正则或提示词。
                """;
    }

    public String dispatch(String companyId, String userId, String agentId, String argumentsJson) {
        if (baseUrl.isBlank()) {
            return failure("SEMATTICE_UNAVAILABLE", "Semattice 服务未配置，无法创建研发交付记录。");
        }
        Optional<CreateIntent> intent = parseArguments(argumentsJson);
        if (intent.isEmpty()) {
            return failure("INVALID_ARGUMENTS", "创建操作只允许项目、需求、任务、缺陷或变更的受控字段。");
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
            return objectMapper.writeValueAsString(create(companyId, intent.get(), authorization, token));
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

    private Map<String, Object> create(String companyId,
                                       CreateIntent intent,
                                       AgentServicePrincipalExecutionService.ExecutionAuthorization authorization,
                                       OfficialAccessTokenService.IssuedToken token) {
        String actor = normalizeText(authorization.servicePrincipalDisplayName());
        if (actor.isBlank()) {
            actor = "DEV Autopilot 产品经理";
        }
        Map<String, Object> data = new LinkedHashMap<>();
        String objectApiName;
        String code;
        String correlationId = correlationId(intent);
        Map<String, Object> parent = Map.of();
        Optional<DevAutopilotDeveloperAssignmentService.DeveloperAssignment> assignment = Optional.empty();
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
                data.put("priority", intent.priority().isBlank() ? "P1" : intent.priority());
                data.put("owner", actor);
                data.put("summary", intent.description().isBlank() ? "由研发交付产品经理创建" : intent.description());
                data.put("acceptance", intent.acceptanceCriteria());
                if (!intent.intake().isEmpty()) {
                    data.put("intake", confirmedIntake(intent, authorization, correlationId));
                }
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
            case "create_change" -> {
                objectApiName = "dev_change";
                parent = requireSingleRecord("dev_requirement", intent.parentReference(), token, "需求");
                code = "";
                data.put("project_id", parent.get("project_id"));
                data.put("requirement_id", parent.get("record_id"));
                data.put("summary", intent.title());
                data.put("impact", intent.impactAnalysis());
                data.put("status", "待评估");
                data.put("submitted_by", actor);
                data.put("intake", confirmedIntake(intent, authorization, correlationId));
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
                assignment = developerAssignmentService.select(companyId, parent.get("record_id") + ":" + intent.title());
                assignment.ifPresent(selected -> data.put("assignee_principal_id", selected.principalId()));
                data.put("environment", intent.environment());
                data.put("reproduction_steps", intent.reproductionSteps());
                data.put("expected_result", intent.expectedResult());
                data.put("actual_result", intent.actualResult());
                data.put("source", "chat");
                data.put("correlation_id", correlationId);
                String conversationId = normalizeText(String.valueOf(intent.intake().getOrDefault("conversation_id", "")));
                if (!conversationId.isBlank()) {
                    data.put("created_from_conversation_id", conversationId);
                }
                if (!intent.intake().isEmpty()) {
                    data.put("intake", confirmedIntake(intent, authorization, correlationId));
                }
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
        assignment.ifPresent(selected -> {
            result.put("assignee_principal_id", selected.principalId());
            result.put("assignee_display_name", selected.displayName());
        });
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

    private String correlationId(CreateIntent intent) {
        if (intent.intake().isEmpty()) {
            return "cici-delivery-" + UUID.randomUUID();
        }
        try {
            byte[] stablePayload = objectMapper.writeValueAsString(intent.intake()).getBytes(StandardCharsets.UTF_8);
            return "cici-delivery-intake-" + UUID.nameUUIDFromBytes(stablePayload);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot derive delivery intake idempotency key", exception);
        }
    }

    private Map<String, Object> confirmedIntake(
            CreateIntent intent,
            AgentServicePrincipalExecutionService.ExecutionAuthorization authorization,
            String correlationId) {
        Map<String, Object> intake = new LinkedHashMap<>(intent.intake());
        intake.put("confirmed_by_principal_id", normalizeText(authorization.delegatedByPrincipalId()));
        intake.put("confirmed_at", Instant.now().toString());
        intake.put("correlation_id", correlationId);
        return intake;
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
            if ("create_requirement".equals(operation) && exactFields(root,
                    "operation", "project", "title", "summary", "priority", "acceptance_criteria", "intake")) {
                List<String> acceptance = textArray(root.path("acceptance_criteria"));
                Map<String, Object> intake = intakeMap(root.path("intake"));
                String priority = normalizeText(root.path("priority").asText()).toUpperCase(Locale.ROOT);
                if (!requiredText(root, "project", "title", "summary")
                        || acceptance.isEmpty() || !validIntake(intake, "requirement")
                        || !Set.of("P0", "P1", "P2", "P3").contains(priority)) {
                    return Optional.empty();
                }
                return Optional.of(CreateIntent.requirementIntake(
                        root.path("project").asText(), root.path("title").asText(), root.path("summary").asText(),
                        priority, acceptance, intake));
            }
            if ("create_task".equals(operation) && onlyFields(root, "operation", "requirement", "title")) {
                return Optional.of(CreateIntent.task(root.path("requirement").asText(), root.path("title").asText()));
            }
            if ("create_change".equals(operation) && exactFields(root,
                    "operation", "requirement", "title", "summary", "priority", "impact_analysis", "intake")) {
                List<String> impact = textArray(root.path("impact_analysis"));
                Map<String, Object> intake = intakeMap(root.path("intake"));
                String priority = normalizeText(root.path("priority").asText()).toUpperCase(Locale.ROOT);
                if (!requiredText(root, "requirement", "title", "summary")
                        || impact.isEmpty() || !validIntake(intake, "change")
                        || !Set.of("P0", "P1", "P2", "P3").contains(priority)) {
                    return Optional.empty();
                }
                return Optional.of(CreateIntent.change(
                        root.path("requirement").asText(), root.path("title").asText(), root.path("summary").asText(),
                        priority, impact, intake));
            }
            if ("create_defect".equals(operation) && onlyFields(root,
                    "operation", "project", "title", "description", "severity", "priority", "environment",
                    "reproduction_steps", "expected_result", "actual_result")) {
                return CreateIntent.defect(
                        root.path("project").asText(), root.path("title").asText(), root.path("description").asText(),
                        root.path("severity").asText(), root.path("priority").asText(), root.path("environment").asText(),
                        root.path("reproduction_steps").asText(), root.path("expected_result").asText(), root.path("actual_result").asText());
            }
            if ("create_defect".equals(operation) && exactFields(root,
                    "operation", "project", "title", "description", "severity", "priority", "environment",
                    "reproduction_steps", "expected_result", "actual_result", "intake")) {
                List<String> reproduction = textArray(root.path("reproduction_steps"));
                Map<String, Object> intake = intakeMap(root.path("intake"));
                if (!requiredText(root, "project", "title", "description", "severity", "priority", "environment",
                        "expected_result", "actual_result")
                        || reproduction.isEmpty() || !validIntake(intake, "defect")) {
                    return Optional.empty();
                }
                return CreateIntent.defectIntake(
                        root.path("project").asText(), root.path("title").asText(), root.path("description").asText(),
                        root.path("severity").asText(), root.path("priority").asText(), root.path("environment").asText(),
                        reproduction, root.path("expected_result").asText(), root.path("actual_result").asText(), intake);
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

    private static boolean exactFields(JsonNode root, String... fields) {
        if (root.size() != fields.length) {
            return false;
        }
        Set<String> expected = Set.of(fields);
        for (var names = root.fieldNames(); names.hasNext(); ) {
            if (!expected.contains(names.next())) {
                return false;
            }
        }
        for (String field : fields) {
            if (!root.has(field)) {
                return false;
            }
        }
        return root.path("operation").isTextual();
    }

    private Map<String, Object> intakeMap(JsonNode node) {
        if (!node.isObject()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, Map.class);
    }

    private static boolean requiredText(JsonNode root, String... fields) {
        for (String field : fields) {
            if (!root.path(field).isTextual() || normalizeText(root.path(field).asText()).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static boolean validIntake(Map<String, Object> intake, String classification) {
        return "DEV_AUTOPILOT_INTAKE_V1".equals(String.valueOf(intake.get("version")))
                && classification.equals(String.valueOf(intake.get("classification")))
                && !normalizeText(String.valueOf(intake.getOrDefault("original_report", ""))).isBlank()
                && !normalizeText(String.valueOf(intake.getOrDefault("pm_assessment", ""))).isBlank();
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

    private static Optional<JsonNode> latestIntakeNode(List<Map<String, Object>> messages, ObjectMapper objectMapper) {
        if (messages == null || messages.isEmpty()) {
            return Optional.empty();
        }
        for (int index = messages.size() - 1; index >= 0; index--) {
            Map<String, Object> message = messages.get(index);
            if (!"assistant".equals(String.valueOf(message.get("role")))) {
                continue;
            }
            Matcher marker = INTAKE_MARKER.matcher(String.valueOf(message.getOrDefault("content", "")));
            JsonNode latest = null;
            while (marker.find()) {
                try {
                    JsonNode candidate = objectMapper.readTree(marker.group(1));
                    if (candidate.isObject()) {
                        latest = candidate;
                    }
                } catch (Exception ignored) {
                    // A malformed model marker is not an executable draft.
                }
            }
            if (latest != null) {
                return Optional.of(latest);
            }
        }
        return Optional.empty();
    }

    private static Optional<IntakeDraft> pendingIntake(List<Map<String, Object>> messages, ObjectMapper objectMapper) {
        if (messages != null) {
            for (int index = messages.size() - 1; index >= 0; index--) {
                Map<String, Object> message = messages.get(index);
                if ("assistant".equals(String.valueOf(message.get("role")))
                        && isTerminalIntakeAnswer(String.valueOf(message.getOrDefault("content", "")))) {
                    return Optional.empty();
                }
            }
        }
        Optional<JsonNode> latest = latestIntakeNode(messages, objectMapper);
        List<String> userMessages = messages == null ? List.of() : messages.stream()
                .filter(message -> "user".equals(String.valueOf(message.get("role"))))
                .map(message -> String.valueOf(message.getOrDefault("content", "")))
                .toList();
        if (latest.isPresent()) {
            return IntakeDraft.parse(latest.get(), userMessages, objectMapper);
        }
        return visiblePendingIntake(messages, objectMapper);
    }

    /**
     * Recovers a governed intake when a model produced the user-visible confirmation draft but
     * omitted the invisible JSON marker. The fallback never executes from a bare user message:
     * it requires a classified assistant draft that explicitly asks for confirmation, preserves
     * the verbatim user report, and fills uncertain engineering details as developer verification.
     */
    private static Optional<IntakeDraft> visiblePendingIntake(
            List<Map<String, Object>> messages, ObjectMapper objectMapper) {
        if (messages == null || messages.isEmpty()) {
            return Optional.empty();
        }
        String draft = "";
        for (int index = messages.size() - 1; index >= 0; index--) {
            Map<String, Object> message = messages.get(index);
            if (!"assistant".equals(String.valueOf(message.get("role")))) {
                continue;
            }
            String content = String.valueOf(message.getOrDefault("content", ""));
            if (isTerminalIntakeAnswer(content)) {
                return Optional.empty();
            }
            String compact = content.replaceAll("\\s+", "");
            boolean classifiedDraft = compact.contains("缺陷创建草案")
                    || compact.contains("缺陷内容摘要")
                    || compact.contains("需求创建草案")
                    || compact.contains("需求内容摘要")
                    || compact.contains("变更创建草案")
                    || compact.contains("变更内容摘要");
            boolean asksConfirmation = compact.contains("确认创建") || compact.contains("确认提交");
            if (classifiedDraft && asksConfirmation) {
                draft = content;
                break;
            }
        }
        if (draft.isBlank()) {
            return Optional.empty();
        }

        List<String> userMessages = messages.stream()
                .filter(message -> "user".equals(String.valueOf(message.get("role"))))
                .map(message -> String.valueOf(message.getOrDefault("content", "")))
                .toList();
        String original = userMessages.stream()
                .filter(message -> !CONFIRM_PENDING_INTAKE.matcher(message.trim()).matches())
                .filter(message -> !isDraftContinuation(message))
                .filter(SematticeProjectDeliveryWriteToolService::isDraftRequest)
                .findFirst()
                .orElse("");
        if (original.isBlank()) {
            return Optional.empty();
        }
        List<String> supplements = userMessages.stream()
                .dropWhile(message -> !message.equals(original))
                .skip(1)
                .filter(message -> !CONFIRM_PENDING_INTAKE.matcher(message.trim()).matches())
                .toList();

        String compact = draft.replace("**", "").replace("`", "");
        String classification = compact.contains("缺陷") ? "defect"
                : compact.contains("变更") ? "change" : compact.contains("需求") ? "requirement" : "";
        String title = firstMatch(compact,
                "(?m)(?:缺陷标题|需求标题|变更标题|标题)\\s*[：:]\\s*([^\\n|]+)");
        String project = firstMatch(compact,
                "(?m)(?:关联项目|父项目|项目)\\s*[：:]\\s*([^\\n|（(]+)");
        String requirement = firstMatch(compact,
                "(?m)(?:关联需求|父需求|需求)\\s*[：:]\\s*([^\\n|（(]+)");
        String priority = firstMatch(compact, "(?i)\\b(P[0-3])\\b").toUpperCase(Locale.ROOT);
        String severity = firstMatch(compact, "(?i)\\b(critical|high|medium|low)\\b").toLowerCase(Locale.ROOT);
        String environment = firstMatch(compact,
                "(?m)(?:环境|预计环境)\\s*[：:]\\s*([^\\n|]+)");
        if (title.isBlank() || classification.isBlank()) {
            return Optional.empty();
        }
        if (priority.isBlank()) {
            priority = "P2";
        }
        if ("defect".equals(classification) && severity.isBlank()) {
            severity = "medium";
        }
        if ("defect".equals(classification) && environment.isBlank()) {
            environment = "待开发者验证";
        }

        String assessment = switch (classification) {
            case "defect" -> "产品经理根据用户原始描述识别为已有能力偏离正常预期，分类为缺陷";
            case "change" -> "产品经理根据用户原始描述识别为对已确认范围或规则的调整，分类为变更";
            default -> "产品经理根据用户原始描述识别为新增能力或业务结果，分类为需求";
        };
        List<String> reproduction = "defect".equals(classification)
                ? List.of("由全栈开发者基于用户原始描述复现并验证") : List.of();
        String expected = "defect".equals(classification)
                ? "功能应符合用户正常使用预期，具体结果由全栈开发者验证" : "";
        String actual = "defect".equals(classification) ? original : "";
        List<String> acceptance = "requirement".equals(classification)
                ? List.of("由产品经理与全栈开发者基于用户原始描述细化并验证验收标准") : List.of();
        List<String> impact = "change".equals(classification)
                ? List.of("由产品经理与全栈开发者评估受影响范围并完成验证") : List.of();
        List<String> assumptions = List.of("工程细节由全栈开发者在源代码、开发环境和测试环境中验证");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", "DEV_AUTOPILOT_INTAKE_V1");
        payload.put("classification", classification);
        payload.put("project", project);
        payload.put("requirement", requirement);
        payload.put("title", title);
        payload.put("original_report", original);
        payload.put("pm_assessment", assessment);
        payload.put("priority", priority);
        payload.put("severity", severity);
        payload.put("environment", environment);
        payload.put("reproduction_steps", reproduction);
        payload.put("expected_result", expected);
        payload.put("actual_result", actual);
        payload.put("acceptance_criteria", acceptance);
        payload.put("impact_analysis", impact);
        payload.put("user_supplements", supplements);
        payload.put("assumptions", assumptions);
        payload.put("developer_verification_pending", true);

        return Optional.of(new IntakeDraft(classification, project, requirement, title, original,
                assessment, priority, severity, environment, reproduction, expected, actual,
                acceptance, impact, supplements, assumptions, payload));
    }

    private static String firstMatch(String value, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(value == null ? "" : value);
        return matcher.find() ? normalizeText(matcher.group(1)) : "";
    }

    private record IntakeDraft(String classification,
                               String project,
                               String requirement,
                               String title,
                               String originalReport,
                               String pmAssessment,
                               String priority,
                               String severity,
                               String environment,
                               List<String> reproductionSteps,
                               String expectedResult,
                               String actualResult,
                               List<String> acceptanceCriteria,
                               List<String> impactAnalysis,
                               List<String> userSupplements,
                               List<String> assumptions,
                               Map<String, Object> payload) {

        static Optional<IntakeDraft> parse(JsonNode root, List<String> userMessages, ObjectMapper objectMapper) {
            if (root.path("cancelled").asBoolean(false)
                    || !root.path("ready_for_confirmation").asBoolean(false)
                    || !normalizeText(root.path("clarification_question").asText()).isBlank()) {
                return Optional.empty();
            }
            String classification = normalizeText(root.path("classification").asText()).toLowerCase(Locale.ROOT);
            String project = normalizeText(root.path("project").asText());
            String requirement = normalizeText(root.path("requirement").asText());
            String title = normalizeText(root.path("title").asText());
            String reportedOriginal = root.path("original_report").asText();
            String assessment = normalizeText(root.path("pm_assessment").asText());
            String priority = normalizeText(root.path("priority").asText()).toUpperCase(Locale.ROOT);
            String severity = normalizeText(root.path("severity").asText()).toLowerCase(Locale.ROOT);
            String environment = normalizeText(root.path("environment").asText());
            List<String> reproduction = textArray(root.path("reproduction_steps"));
            String expected = normalizeText(root.path("expected_result").asText());
            String actual = normalizeText(root.path("actual_result").asText());
            List<String> acceptance = textArray(root.path("acceptance_criteria"));
            List<String> impact = textArray(root.path("impact_analysis"));
            List<String> reportedSupplements = rawTextArray(root.path("user_supplements"));
            List<String> assumptions = textArray(root.path("assumptions"));
            String originalReport = resolveVerbatimMessage(reportedOriginal, userMessages).orElse("");
            List<String> supplements = new ArrayList<>();
            for (String reportedSupplement : reportedSupplements) {
                Optional<String> verbatim = resolveVerbatimMessage(reportedSupplement, userMessages);
                if (verbatim.isEmpty()) {
                    return Optional.empty();
                }
                supplements.add(verbatim.get());
            }
            if (!Set.of("requirement", "defect", "change").contains(classification)
                    || title.isBlank() || assessment.isBlank()
                    || !Set.of("P0", "P1", "P2", "P3").contains(priority)
                    || originalReport.isBlank()) {
                return Optional.empty();
            }
            if ("requirement".equals(classification) && (project.isBlank() || acceptance.isEmpty())) {
                return Optional.empty();
            }
            if ("change".equals(classification) && (requirement.isBlank() || impact.isEmpty())) {
                return Optional.empty();
            }
            if ("defect".equals(classification)
                    && (project.isBlank() || !Set.of("critical", "high", "medium", "low").contains(severity)
                    || environment.isBlank() || reproduction.isEmpty() || expected.isBlank() || actual.isBlank())) {
                return Optional.empty();
            }
            Map<String, Object> payload = objectMapper.convertValue(root, Map.class);
            payload.put("original_report", originalReport);
            payload.put("user_supplements", List.copyOf(supplements));
            payload.put("version", "DEV_AUTOPILOT_INTAKE_V1");
            payload.put("developer_verification_pending", true);
            return Optional.of(new IntakeDraft(classification, project, requirement, title, originalReport,
                    assessment, priority, severity, environment, reproduction, expected, actual,
                    acceptance, impact, supplements, assumptions, payload));
        }

        Optional<CreateIntent> toCreateIntent(String conversationId) {
            Map<String, Object> intake = new LinkedHashMap<>(payload);
            intake.put("conversation_id", normalizeText(conversationId));
            return switch (classification) {
                case "requirement" -> Optional.of(CreateIntent.requirementIntake(
                        project, title, pmAssessment, priority, acceptanceCriteria, intake));
                case "change" -> Optional.of(CreateIntent.change(
                        requirement, title, pmAssessment, priority, impactAnalysis, intake));
                case "defect" -> CreateIntent.defectIntake(
                        project, title, originalReport, severity, priority, environment, reproductionSteps,
                        expectedResult, actualResult, intake);
                default -> Optional.empty();
            };
        }
    }

    private static List<String> textArray(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        node.forEach(item -> {
            String value = normalizeText(item.asText());
            if (!value.isBlank()) {
                result.add(value);
            }
        });
        return List.copyOf(result);
    }

    private static List<String> rawTextArray(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        node.forEach(item -> {
            String value = item.asText();
            if (!value.isBlank()) {
                result.add(value);
            }
        });
        return List.copyOf(result);
    }

    private static Optional<String> resolveVerbatimMessage(String reported, List<String> userMessages) {
        if (reported == null || reported.isBlank()) {
            return Optional.empty();
        }
        if (userMessages.contains(reported)) {
            return Optional.of(reported);
        }
        String comparable = reported.replaceAll("\\s+", "");
        return userMessages.stream()
                .filter(message -> message.replaceAll("\\s+", "").equals(comparable))
                .findFirst();
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
                               List<String> reproductionSteps, String expectedResult, String actualResult,
                               List<String> acceptanceCriteria, List<String> impactAnalysis,
                               Map<String, Object> intake) {
        static CreateIntent project(String name) {
            return new CreateIntent("create_project", normalizeText(name), "", "", "", "", "", "",
                    List.of(), "", "", List.of(), List.of(), Map.of());
        }

        static CreateIntent requirement(String project, String title) {
            return new CreateIntent("create_requirement", "", normalizeText(project), normalizeText(title), "", "", "", "",
                    List.of(), "", "", List.of(), List.of(), Map.of());
        }

        static CreateIntent requirementIntake(String project, String title, String assessment, String priority,
                                              List<String> acceptanceCriteria, Map<String, Object> intake) {
            return new CreateIntent("create_requirement", "", normalizeText(project), normalizeText(title),
                    normalizeText(assessment), "", normalizeText(priority).toUpperCase(Locale.ROOT), "",
                    List.of(), "", "", List.copyOf(acceptanceCriteria), List.of(), Map.copyOf(intake));
        }

        static CreateIntent task(String requirement, String title) {
            return new CreateIntent("create_task", "", normalizeText(requirement), normalizeText(title), "", "", "", "",
                    List.of(), "", "", List.of(), List.of(), Map.of());
        }

        static CreateIntent change(String requirement, String title, String assessment, String priority,
                                   List<String> impactAnalysis, Map<String, Object> intake) {
            return new CreateIntent("create_change", "", normalizeText(requirement), normalizeText(title),
                    normalizeText(assessment), "", normalizeText(priority).toUpperCase(Locale.ROOT), "",
                    List.of(), "", "", List.of(), List.copyOf(impactAnalysis), Map.copyOf(intake));
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
                    List.of(normalizeText(reproductionSteps)), normalizeText(expectedResult), normalizeText(actualResult),
                    List.of(), List.of(), Map.of()));
        }

        static Optional<CreateIntent> defectIntake(String project, String title, String originalReport, String severity,
                                                   String priority, String environment, List<String> reproductionSteps,
                                                   String expectedResult, String actualResult, Map<String, Object> intake) {
            String normalizedSeverity = normalizeText(severity).toLowerCase(Locale.ROOT);
            String normalizedPriority = normalizeText(priority).toUpperCase(Locale.ROOT);
            if (!Set.of("critical", "high", "medium", "low").contains(normalizedSeverity)
                    || !Set.of("P0", "P1", "P2", "P3").contains(normalizedPriority)
                    || reproductionSteps == null || reproductionSteps.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new CreateIntent("create_defect", "", normalizeText(project), normalizeText(title),
                    originalReport, normalizedSeverity, normalizedPriority, normalizeText(environment),
                    List.copyOf(reproductionSteps), normalizeText(expectedResult), normalizeText(actualResult),
                    List.of(), List.of(), Map.copyOf(intake)));
        }

        public String toArguments(ObjectMapper objectMapper) {
            try {
                return switch (operation) {
                    case "create_project" -> objectMapper.writeValueAsString(Map.of("operation", operation, "name", name));
                    case "create_requirement" -> intake.isEmpty()
                            ? objectMapper.writeValueAsString(Map.of("operation", operation, "project", parentReference, "title", title))
                            : objectMapper.writeValueAsString(Map.of(
                                    "operation", operation, "project", parentReference, "title", title,
                                    "summary", description, "priority", priority,
                                    "acceptance_criteria", acceptanceCriteria, "intake", intake));
                    case "create_task" -> objectMapper.writeValueAsString(Map.of("operation", operation, "requirement", parentReference, "title", title));
                    case "create_change" -> objectMapper.writeValueAsString(Map.of(
                            "operation", operation, "requirement", parentReference, "title", title,
                            "summary", description, "priority", priority, "impact_analysis", impactAnalysis, "intake", intake));
                    case "create_defect" -> intake.isEmpty()
                            ? objectMapper.writeValueAsString(Map.of(
                                    "operation", operation, "project", parentReference, "title", title, "description", description,
                                    "severity", severity, "priority", priority, "environment", environment,
                                    "reproduction_steps", reproductionSteps.getFirst(), "expected_result", expectedResult, "actual_result", actualResult))
                            : objectMapper.writeValueAsString(Map.ofEntries(
                                    Map.entry("operation", operation), Map.entry("project", parentReference),
                                    Map.entry("title", title), Map.entry("description", description),
                                    Map.entry("severity", severity), Map.entry("priority", priority),
                                    Map.entry("environment", environment), Map.entry("reproduction_steps", reproductionSteps),
                                    Map.entry("expected_result", expectedResult), Map.entry("actual_result", actualResult),
                                    Map.entry("intake", intake)));
                    default -> throw new IllegalStateException("unsupported operation");
                };
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot encode controlled delivery create intent", exception);
            }
        }
    }
}
