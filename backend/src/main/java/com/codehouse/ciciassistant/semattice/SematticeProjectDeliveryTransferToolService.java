package com.codehouse.ciciassistant.semattice;

import com.codehouse.ciciassistant.agent.service.AgentServicePrincipalExecutionService;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
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
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/** Deterministic product-manager hand-off for queued DevAutopilot tasks. */
@Service
public class SematticeProjectDeliveryTransferToolService {
    public static final String TOOL_NAME = "semattice_project_delivery_transfer";
    private static final Pattern CONFIRM = Pattern.compile("^\\s*(?:请)?(?:确认|确定)将?(.+?)(?:的)?任务(?:都|全部)?转交给(.+?)\\s*$");
    private static final List<String> TRANSFERABLE_STATUSES = List.of("待开始", "已批准待执行");
    private final RestClient client;
    private final ObjectMapper mapper;
    private final AgentServicePrincipalExecutionService executions;
    private final DevAutopilotDeveloperAssignmentService developers;
    private final String baseUrl;

    public SematticeProjectDeliveryTransferToolService(RestClient.Builder builder, ObjectMapper mapper,
            AgentServicePrincipalExecutionService executions, DevAutopilotDeveloperAssignmentService developers,
            @Value("${app.semattice.base-url:}") String baseUrl) {
        this.client = builder.build(); this.mapper = mapper; this.executions = executions; this.developers = developers;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    public static Optional<TransferIntent> confirmedIntent(String text) {
        Matcher match = CONFIRM.matcher(normalizeInstruction(text));
        if (!match.matches()) return Optional.empty();
        String from = match.group(1).trim(), to = match.group(2).trim();
        return from.isBlank() || to.isBlank() || from.equals(to) ? Optional.empty() : Optional.of(new TransferIntent(from, to));
    }

    /** Chat input commonly includes a terminal sentence mark or a copied Markdown wrapper. */
    private static String normalizeInstruction(String text) {
        String normalized = text == null ? "" : text.trim();
        normalized = normalized.replaceAll("^[`\\\"“”‘’']+", "");
        normalized = normalized.replaceAll("[`\\\"“”‘’。！？!?]+$", "");
        return normalized.trim();
    }

    public String dispatch(String companyId, String userId, String agentId, String arguments) {
        if (baseUrl.isBlank()) return json(Map.of("status", "FAILED", "message", "Semattice 服务未配置，无法转派任务。"));
        try {
            JsonNode request = mapper.readTree(arguments);
            boolean execute = "execute".equals(request.path("mode").asText());
            TransferIntent intent = new TransferIntent(request.path("from").asText(), request.path("to").asText());
            return json(execute ? execute(companyId, userId, agentId, intent) : draft(companyId, userId, agentId, intent));
        } catch (Exception ex) { return json(Map.of("status", "FAILED", "message", failureMessage(ex))); }
    }

    private Map<String, Object> draft(String companyId, String userId, String agentId, TransferIntent intent) {
        Resolved resolved = resolve(companyId, userId, agentId, intent, List.of("runtime.record.read"));
        List<Task> tasks = queuedTasks(resolved.token(), resolved.source().principalId());
        Map<String, Object> out = result("DRAFT", resolved, tasks);
        out.put("message", tasks.isEmpty() ? "没有可转派的排队任务。" : "请确认后执行转派。");
        return out;
    }

    private Map<String, Object> execute(String companyId, String userId, String agentId, TransferIntent intent) {
        Resolved resolved = resolve(companyId, userId, agentId, intent, List.of("runtime.record.read", "runtime.record.transfer"));
        List<Task> tasks = queuedTasks(resolved.token(), resolved.source().principalId());
        if (tasks.isEmpty()) return result("NOOP", resolved, tasks);
        List<Map<String, Object>> transferred = new ArrayList<>();
        for (Task task : tasks) {
            Map<String, Object> input = Map.of("object_api_name", "dev_task", "record_id", task.id(), "expected_revision", task.revision(), "new_owner_principal_id", resolved.target().principalId());
            JsonNode response = invoke("runtime.record.transfer", input, resolved.token());
            JsonNode record = response.path("result");
            if (!"succeeded".equals(response.path("status").asText()) || !resolved.target().principalId().equals(record.path("owner_principal_id").asText()) || record.path("revision").asLong() <= task.revision()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "任务转派回读不完整，已停止后续转派。");
            }
            transferred.add(Map.of(
                    "title", task.title(),
                    "record_id", record.path("record_id").asText(),
                    "revision", record.path("revision").asLong()));
        }
        Map<String, Object> out = result("SUCCESS", resolved, tasks);
        out.put("source", "SEMATTICE_LIVE");
        out.put("object_api_name", "dev_task");
        out.put("transferred", transferred);
        out.put("readback_verified", true);
        return out;
    }

    private Resolved resolve(String companyId, String userId, String agentId, TransferIntent intent, List<String> scopes) {
        var source = developers.resolveTransferSourceByDisplayName(companyId, intent.from()).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "未找到可转出的开发者“" + intent.from() + "”。"));
        var target = developers.resolveActiveByDisplayName(companyId, intent.to()).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "未找到有效开发者“" + intent.to() + "”。"));
        if (source.principalId().equals(target.principalId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "转出与转入开发者不能相同。");
        var authorization = executions.authorizeSemattice(companyId, userId, agentId, scopes, TOOL_NAME);
        return new Resolved(source, target, authorization.token());
    }

    private List<Task> queuedTasks(OfficialAccessTokenService.IssuedToken token, String owner) {
        JsonNode response = invoke("runtime.record.query", Map.of("object_api_name", "dev_task", "limit", 100), token);
        if (!"succeeded".equals(response.path("status").asText())) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "任务查询失败，未转派。");
        List<Task> tasks = new ArrayList<>();
        for (JsonNode item : response.path("result").path("records")) {
            JsonNode data = item.path("data");
            if (owner.equals(item.path("owner_principal_id").asText()) && TRANSFERABLE_STATUSES.contains(data.path("status").asText())) {
                tasks.add(new Task(item.path("record_id").asText(), item.path("revision").asLong(), data.path("title").asText()));
            }
        }
        return tasks;
    }

    private JsonNode invoke(String capability, Map<String, Object> input, OfficialAccessTokenService.IssuedToken token) {
        return client.post().uri(baseUrl + "/v1/capabilities/" + capability + "/invoke")
                .header("Authorization", "Bearer " + token.token()).header("Content-Type", "application/json")
                .body(Map.of("capability_id", capability, "request_id", "cici-task-transfer-" + UUID.randomUUID(), "idempotency_key", "cici-task-transfer-" + UUID.randomUUID(), "input", input))
                .retrieve().body(JsonNode.class);
    }
    private Map<String, Object> result(String status, Resolved resolved, List<Task> tasks) {
        Map<String, Object> out = new LinkedHashMap<>(); out.put("status", status); out.put("from", resolved.source().displayName()); out.put("to", resolved.target().displayName()); out.put("tasks", tasks.stream().map(Task::title).toList()); return out;
    }
    private String json(Map<String, Object> value) { try { return mapper.writeValueAsString(value); } catch (Exception ex) { return "{\"status\":\"FAILED\"}"; } }
    public static String failureMessage(Exception ex) {
        if (ex instanceof ForbiddenException) {
            return "产品经理 SERVICE 缺少 `runtime.record.transfer` 授权；请由组织管理员同步交付授权后重试。未修改任务。";
        }
        if (ex instanceof ResponseStatusException response && response.getReason() != null && !response.getReason().isBlank()) {
            return response.getReason();
        }
        if (ex instanceof RestClientResponseException response) {
            return "Semattice 拒绝了转派请求（HTTP " + response.getStatusCode().value() + "），未修改任务。";
        }
        return "转派执行失败，未修改任务。请稍后重试。";
    }
    public record TransferIntent(String from, String to) { }
    private record Resolved(DevAutopilotDeveloperAssignmentService.DeveloperAssignment source, DevAutopilotDeveloperAssignmentService.DeveloperAssignment target, OfficialAccessTokenService.IssuedToken token) { }
    private record Task(String id, long revision, String title) { }
}
