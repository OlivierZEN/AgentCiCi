package com.codehouse.ciciassistant.semattice;

import com.codehouse.ciciassistant.agent.service.AgentServicePrincipalExecutionService;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only bridge from the DEV Autopilot product-manager agent to the published Semattice
 * delivery model. Tenant and actor are always established by the OACT, never by tool arguments.
 */
@Service
public class SematticeProjectDeliveryToolService {

    public static final String TOOL_NAME = "semattice_project_delivery_query";
    private static final List<String> DELIVERY_OBJECTS = List.of(
            "dev_project", "dev_requirement", "dev_task", "dev_worklog", "dev_change", "dev_delivery_event");
    private static final String CAPABILITY_ID = "runtime.record.query";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AgentServicePrincipalExecutionService executionPrincipalService;
    private final String baseUrl;

    public SematticeProjectDeliveryToolService(RestClient.Builder restClientBuilder,
                                               ObjectMapper objectMapper,
                                               AgentServicePrincipalExecutionService executionPrincipalService,
                                               @Value("${app.semattice.base-url:}") String baseUrl) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.executionPrincipalService = executionPrincipalService;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    public static String toolDescription() {
        return "使用当前 Agent 显式绑定的 SERVICE Principal 读取同公司的 Semattice 研发交付数据（项目、需求、任务、工时、变更和交付事件）。"
                + "涉及项目状态、进度、工时、需求、任务或变更事实时必须先调用；"
                + "只读，不接受租户、成员或令牌参数，不能创建或修改记录。";
    }

    public static JsonNode toolSchema(ObjectMapper objectMapper) {
        return objectMapper.valueToTree(Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "focus", Map.of(
                                "type", "string",
                                "enum", List.of("overview", "projects", "requirements", "tasks", "worklogs", "changes", "events", "pending_reviews"),
                                "description", "可选的回答关注点；省略时返回完整交付概览。")),
                "required", List.of()));
    }

    public String dispatch(String companyId, String userId, String agentId, String argumentsJson) {
        if (baseUrl.isBlank()) {
            return failure("SEMATTICE_UNAVAILABLE", "Semattice 服务未配置，无法读取研发交付数据。");
        }
        if (!validArguments(argumentsJson)) {
            return failure("INVALID_ARGUMENTS", "只允许可选的 focus 参数，且不能指定租户、成员或令牌。");
        }
        AgentServicePrincipalExecutionService.ExecutionAuthorization authorization =
                executionPrincipalService.authorizeSemattice(
                        companyId,
                        userId,
                        agentId,
                        List.of("runtime.record.read"),
                        "semattice_project_delivery_query");
        OfficialAccessTokenService.IssuedToken token = authorization.token();
        try {
            Map<String, List<Map<String, Object>>> recordsByObject = new LinkedHashMap<>();
            for (String objectName : DELIVERY_OBJECTS) {
                recordsByObject.put(objectName, queryObject(objectName, token));
            }
            Map<String, Object> summary = buildSummary(recordsByObject);
            summary.put("execution_principal_type", "SERVICE");
            summary.put("execution_principal", authorization.servicePrincipalDisplayName());
            summary.put("delegation_policy", authorization.delegationPolicy());
            return objectMapper.writeValueAsString(summary);
        } catch (RestClientException exception) {
            return failure("SEMATTICE_UNAVAILABLE", "Semattice 实时检索失败，请稍后重试。");
        } catch (Exception exception) {
            return failure("SEMATTICE_RESPONSE_INVALID", "Semattice 返回的数据无法安全解析。");
        }
    }

    private List<Map<String, Object>> queryObject(String objectName, OfficialAccessTokenService.IssuedToken token) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("capability_id", CAPABILITY_ID);
        request.put("request_id", "cici-delivery-" + UUID.randomUUID());
        request.put("input", Map.of("object_api_name", objectName, "limit", 100));
        JsonNode response = restClient.post()
                .uri(baseUrl + "/v1/capabilities/" + CAPABILITY_ID + "/invoke")
                .header("Authorization", "Bearer " + token.token())
                .header("Content-Type", "application/json")
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        if (response == null || !"succeeded".equals(response.path("status").asText())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Semattice delivery query failed");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode record : response.path("result").path("records")) {
            JsonNode data = record.path("data");
            if (!data.isObject()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("record_id", record.path("record_id").asText());
            copyIfPresent(data, row, "code");
            copyIfPresent(data, row, "name");
            copyIfPresent(data, row, "title");
            copyIfPresent(data, row, "status");
            copyIfPresent(data, row, "owner");
            copyIfPresent(data, row, "priority");
            copyIfPresent(data, row, "progress");
            copyIfPresent(data, row, "health");
            copyIfPresent(data, row, "release");
            copyIfPresent(data, row, "estimate");
            copyIfPresent(data, row, "actual_hours");
            copyIfPresent(data, row, "hours");
            copyIfPresent(data, row, "member");
            copyIfPresent(data, row, "work_date");
            copyIfPresent(data, row, "summary");
            copyIfPresent(data, row, "impact");
            copyIfPresent(data, row, "project_id");
            copyIfPresent(data, row, "requirement_id");
            copyIfPresent(data, row, "task_id");
            copyIfPresent(data, row, "event_type");
            copyIfPresent(data, row, "detail");
            copyIfPresent(data, row, "evidence");
            copyIfPresent(data, row, "actor_principal_id");
            copyIfPresent(data, row, "agent_name");
            copyIfPresent(data, row, "agent_instance_id");
            copyIfPresent(data, row, "parent_event_id");
            copyIfPresent(data, row, "decision");
            copyIfPresent(data, row, "occurred_at");
            copyIfPresent(data, row, "correlation_id");
            result.add(row);
        }
        return result;
    }

    private Map<String, Object> buildSummary(Map<String, List<Map<String, Object>>> recordsByObject) {
        List<Map<String, Object>> projects = recordsByObject.getOrDefault("dev_project", List.of());
        List<Map<String, Object>> tasks = recordsByObject.getOrDefault("dev_task", List.of());
        List<Map<String, Object>> worklogs = recordsByObject.getOrDefault("dev_worklog", List.of());
        long executingProjects = projects.stream().filter(item -> isExecuting(String.valueOf(item.get("status")))).count();
        long activeTasks = tasks.stream().filter(item -> isExecuting(String.valueOf(item.get("status")))).count();
        double loggedHours = worklogs.stream()
                .map(item -> item.get("hours"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .mapToDouble(Number::doubleValue)
                .sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "SUCCESS");
        result.put("source", "SEMATTICE_LIVE");
        result.put("retrieved_at", Instant.now().toString());
        result.put("project_count", projects.size());
        result.put("executing_project_count", executingProjects);
        result.put("active_task_count", activeTasks);
        result.put("logged_hours", loggedHours);
        result.put("projects", projects);
        result.put("requirements", recordsByObject.getOrDefault("dev_requirement", List.of()));
        result.put("tasks", tasks);
        result.put("worklogs", worklogs);
        result.put("changes", recordsByObject.getOrDefault("dev_change", List.of()));
        List<Map<String, Object>> events = recordsByObject.getOrDefault("dev_delivery_event", List.of());
        result.put("events", events);
        Set<Object> decidedSubmissionIds = events.stream()
                .filter(item -> Set.of(
                                "design_approved", "design_changes_requested",
                                "completion_approved", "completion_changes_requested")
                        .contains(item.get("event_type")))
                .map(item -> item.get("parent_event_id"))
                .filter(value -> value != null && !String.valueOf(value).isBlank())
                .collect(java.util.stream.Collectors.toSet());
        result.put("pending_reviews", events.stream()
                .filter(item -> "pending".equals(item.get("status")))
                .filter(item -> Set.of("design_submitted", "completion_requested").contains(item.get("event_type")))
                .filter(item -> !decidedSubmissionIds.contains(item.get("record_id")))
                .toList());
        return result;
    }

    private boolean validArguments(String argumentsJson) {
        try {
            JsonNode root = argumentsJson == null || argumentsJson.isBlank()
                    ? objectMapper.createObjectNode() : objectMapper.readTree(argumentsJson);
            if (!root.isObject() || root.size() > 1 || (root.has("focus") && !root.path("focus").isTextual())) {
                return false;
            }
            return !root.has("tenant_id") && !root.has("company_id") && !root.has("user_id") && !root.has("token");
        } catch (Exception exception) {
            return false;
        }
    }

    private static void copyIfPresent(JsonNode source, Map<String, Object> target, String field) {
        JsonNode value = source.get(field);
        if (value != null && !value.isNull()) {
            if (value.isNumber()) {
                target.put(field, value.numberValue());
            } else if (value.isBoolean()) {
                target.put(field, value.booleanValue());
            } else {
                target.put(field, value.isValueNode() ? value.asText() : value);
            }
        }
    }

    private static boolean isExecuting(String status) {
        return status.contains("执行") || status.contains("进行") || status.contains("开发");
    }

    private String failure(String code, String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("status", "FAILED", "code", code, "message", message));
        } catch (Exception ignored) {
            return "{\"status\":\"FAILED\",\"code\":\"" + code + "\"}";
        }
    }
}
