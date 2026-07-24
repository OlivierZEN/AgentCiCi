package com.codehouse.ciciassistant.agent.service;

import com.codehouse.ciciassistant.agent.config.AgentRuntimeModeRouterProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * P3's deterministic and server-owned routing decision. It deliberately does not call a model or
 * inspect tool arguments, so request text can never authorize an operation or expand an allowlist.
 */
@Service
public class AgentRuntimeModeRouter {

    private static final Pattern DEPENDENCY_PATTERN = Pattern.compile(
            "(?is).*(先.{0,80}(再|然后|之后)|然后|之后|依赖|分步骤|步骤[一二三123]|first.{0,80}(then|after)|depend).*" );
    private static final Pattern MULTI_SOURCE_PATTERN = Pattern.compile(
            "(?is).*(多源|多数据源|多个.{0,12}(系统|来源)|订单.{0,48}工单|工单.{0,48}订单|订单.{0,48}客户|客户.{0,48}订单).*" );
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(?is).*(创建|新增|更新|修改|删除|发送|审批|提交|支付|转账|create|update|delete|send|approve|pay).*" );
    private static final Pattern STRUCTURED_OR_REVIEW_PATTERN = Pattern.compile(
            "(?is).*(报告|报表|结构化|审查|复核|review|audit|report).*" );
    private static final Set<String> WRITE_TOOL_MARKERS = Set.of(
            "create", "update", "delete", "send", "approve", "submit", "pay", "write");

    private final AgentRuntimeModeRouterProperties properties;
    private final AgentRuntimeOperationsMetrics operationsMetrics;

    public AgentRuntimeModeRouter(AgentRuntimeModeRouterProperties properties) {
        this(properties, AgentRuntimeOperationsMetrics.noop());
    }

    @Autowired
    public AgentRuntimeModeRouter(AgentRuntimeModeRouterProperties properties,
                                  AgentRuntimeOperationsMetrics operationsMetrics) {
        this.properties = properties;
        this.operationsMetrics = operationsMetrics;
    }

    public ModeDecision decide(RoutingInput input) {
        if (input == null) return recorded(legacy(ReasonCode.INVALID_INPUT));
        if (!properties.isEnabled()) return recorded(legacy(ReasonCode.ROUTER_DISABLED));
        if (!properties.isEnabledFor(input.companyId(), input.agentId())) return recorded(legacy(ReasonCode.SCOPE_NOT_ALLOWLISTED));
        if (input.pendingConfirmation()) return recorded(legacy(ReasonCode.PENDING_CONFIRMATION_CONTINUATION));

        String question = normalize(input.question());
        if (question.isBlank()) return recorded(legacy(ReasonCode.INVALID_INPUT));
        List<String> tools = input.allowedToolNames() == null ? List.of() : input.allowedToolNames();
        boolean sensitiveIntent = SENSITIVE_PATTERN.matcher(question).matches() || hasWriteLikeTool(tools);
        boolean reflectRequired = sensitiveIntent || STRUCTURED_OR_REVIEW_PATTERN.matcher(question).matches();
        RiskLevel riskLevel = sensitiveIntent ? RiskLevel.HIGH : reflectRequired ? RiskLevel.MEDIUM : RiskLevel.LOW;
        Budget budget = budget();

        if (DEPENDENCY_PATTERN.matcher(question).matches()) {
            return recorded(decision(Mode.PLAN_EXEC, ReasonCode.EXPLICIT_DEPENDENCY, riskLevel, budget, sensitiveIntent, reflectRequired));
        }
        if (MULTI_SOURCE_PATTERN.matcher(question).matches()) {
            return recorded(decision(Mode.PLAN_EXEC, ReasonCode.MULTI_SOURCE, riskLevel, budget, sensitiveIntent, reflectRequired));
        }
        if (tools.size() > budget.maxToolRounds()) {
            return recorded(decision(Mode.PLAN_EXEC, ReasonCode.REACT_BUDGET_EXCEEDED, riskLevel, budget, sensitiveIntent, reflectRequired));
        }
        if (tools.isEmpty() && !input.externalFactRequired()) {
            return recorded(decision(Mode.DIRECT, ReasonCode.NO_EXTERNAL_CONTEXT, riskLevel, budget, sensitiveIntent, reflectRequired));
        }
        return recorded(decision(Mode.REACT, input.externalFactRequired() ? ReasonCode.KNOWLEDGE_LOOKUP : ReasonCode.READONLY_TOOL_LOOKUP,
                riskLevel, budget, sensitiveIntent, reflectRequired));
    }

    public Map<String, Object> payload(ModeDecision decision) {
        ModeDecision safe = decision == null ? legacy(ReasonCode.INVALID_INPUT) : decision;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mode", safe.mode().name());
        payload.put("reasonCodes", safe.reasonCodes().stream().map(Enum::name).toList());
        payload.put("riskLevel", safe.riskLevel().name());
        payload.put("requiresConfirmation", safe.requiresConfirmation());
        payload.put("reflectRequired", safe.reflectRequired());
        payload.put("budget", Map.of(
                "maxToolRounds", safe.budget().maxToolRounds(),
                "maxSteps", safe.budget().maxSteps(),
                "maxReplans", safe.budget().maxReplans(),
                "maxReflectRounds", safe.budget().maxReflectRounds()));
        return payload;
    }

    /** P2's independent canary gate remains authoritative for durable execution. */
    public ModeDecision fallbackToReact(ModeDecision decision) {
        if (decision == null || !decision.usesPlanExec()) return decision == null ? legacy(ReasonCode.INVALID_INPUT) : decision;
        return new ModeDecision(Mode.REACT, List.of(ReasonCode.PLAN_EXEC_GATE_NOT_MET), decision.riskLevel(),
                decision.budget(), decision.requiresConfirmation(), decision.reflectRequired());
    }

    private ModeDecision legacy(ReasonCode reason) {
        return decision(Mode.LEGACY_REACT, reason, RiskLevel.LOW, budget(), false, false);
    }

    private ModeDecision recorded(ModeDecision decision) {
        operationsMetrics.recordMode(decision);
        return decision;
    }

    private ModeDecision decision(Mode mode, ReasonCode reason, RiskLevel riskLevel, Budget budget,
                                  boolean requiresConfirmation, boolean reflectRequired) {
        return new ModeDecision(mode, List.of(reason), riskLevel, budget, requiresConfirmation, reflectRequired);
    }

    private Budget budget() {
        return new Budget(properties.getMaxReactToolRounds(), properties.getMaxSteps(),
                properties.getMaxReplans(), properties.getMaxReflectRounds());
    }

    private static boolean hasWriteLikeTool(List<String> toolNames) {
        return toolNames.stream().filter(name -> name != null && !name.isBlank())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .anyMatch(name -> WRITE_TOOL_MARKERS.stream().anyMatch(name::contains));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public enum Mode { LEGACY_REACT, DIRECT, REACT, PLAN_EXEC }
    public enum RiskLevel { LOW, MEDIUM, HIGH }
    public enum ReasonCode {
        ROUTER_DISABLED, SCOPE_NOT_ALLOWLISTED, PENDING_CONFIRMATION_CONTINUATION, INVALID_INPUT,
        NO_EXTERNAL_CONTEXT, KNOWLEDGE_LOOKUP, READONLY_TOOL_LOOKUP, EXPLICIT_DEPENDENCY,
        MULTI_SOURCE, REACT_BUDGET_EXCEEDED, PLAN_EXEC_GATE_NOT_MET
    }
    public record Budget(int maxToolRounds, int maxSteps, int maxReplans, int maxReflectRounds) {}
    public record RoutingInput(String companyId, String agentId, String channel, String question, List<String> allowedToolNames,
                               boolean externalFactRequired, boolean pendingConfirmation) {}
    public record ModeDecision(Mode mode, List<ReasonCode> reasonCodes, RiskLevel riskLevel, Budget budget,
                               boolean requiresConfirmation, boolean reflectRequired) {
        public boolean usesPlanExec() { return mode == Mode.PLAN_EXEC; }
        public boolean suppressesTools() { return mode == Mode.DIRECT || mode == Mode.PLAN_EXEC; }
    }
}
