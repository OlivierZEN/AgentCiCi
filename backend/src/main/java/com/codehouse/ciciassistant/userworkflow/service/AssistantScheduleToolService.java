package com.codehouse.ciciassistant.userworkflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Native, current-user-only tool for creating a real personal workflow schedule. */
@Service
public class AssistantScheduleToolService {

    public static final String TOOL_NAME = "workflow_schedule_create";

    private final ObjectProvider<UserWorkflowService> workflowServiceProvider;
    private final ObjectMapper objectMapper;

    public AssistantScheduleToolService(ObjectProvider<UserWorkflowService> workflowServiceProvider,
                                        ObjectMapper objectMapper) {
        this.workflowServiceProvider = workflowServiceProvider;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> toolDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("title", Map.of("type", "string", "description", "任务标题，最多 80 字。"));
        properties.put("cadence", Map.of("type", "string", "description", "明确执行周期，例如“每天 09:00”或“每周一 09:00”。"));
        properties.put("task", Map.of("type", "string", "description", "每次执行的任务内容，例如“搜索美国 K12 教育机构”。"));
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", TOOL_NAME,
                        "description", "仅当用户明确要求创建定时任务且已给出周期时，创建当前用户、当前智能体的个人工作流定时任务。缺少周期时不要调用，应先追问。",
                        "parameters", Map.of("type", "object", "properties", properties,
                                "required", List.of("cadence", "task"))));
    }

    public String dispatch(String companyId, String userId, String agentId, String argumentsJson) {
        if (agentId == null || agentId.isBlank()) {
            return "创建定时任务失败：缺少当前智能体上下文。";
        }
        try {
            JsonNode args = objectMapper.readTree(argumentsJson == null ? "{}" : argumentsJson);
            UserWorkflowService.AssistantScheduleResult result = workflowServiceProvider.getObject()
                    .createScheduledRoutine(companyId, userId, agentId,
                            args.path("title").asText(""),
                            args.path("cadence").asText(""),
                            args.path("task").asText(""));
            return objectMapper.writeValueAsString(Map.of(
                    "status", "CREATED",
                    "triggerId", result.triggerId(),
                    "routineKey", result.routineKey(),
                    "title", result.title(),
                    "versionNo", result.versionNo(),
                    "nextFireAt", result.nextFireAt() == null ? "" : result.nextFireAt().toString()));
        } catch (IllegalArgumentException ex) {
            return "创建定时任务失败：" + ex.getMessage();
        } catch (Exception ex) {
            return "创建定时任务失败：" + ex.getClass().getSimpleName();
        }
    }
}
