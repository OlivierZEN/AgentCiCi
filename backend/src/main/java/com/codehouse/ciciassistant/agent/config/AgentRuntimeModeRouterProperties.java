package com.codehouse.ciciassistant.agent.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Server-owned gate and bounded budgets for the P3 deterministic mode router. */
@Component
@ConfigurationProperties(prefix = "app.agent-runtime.mode-router")
public class AgentRuntimeModeRouterProperties {

    private boolean enabled;
    private List<String> allowedAgentIds = new ArrayList<>();
    private int maxReactToolRounds = 3;
    private int maxSteps = 6;
    private int maxReplans = 1;
    private int maxReflectRounds = 1;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<String> getAllowedAgentIds() { return List.copyOf(allowedAgentIds); }
    public void setAllowedAgentIds(List<String> allowedAgentIds) {
        this.allowedAgentIds = allowedAgentIds == null ? new ArrayList<>() : new ArrayList<>(allowedAgentIds);
    }
    public int getMaxReactToolRounds() { return clamp(maxReactToolRounds, 1, 12); }
    public void setMaxReactToolRounds(int maxReactToolRounds) { this.maxReactToolRounds = maxReactToolRounds; }
    public int getMaxSteps() { return clamp(maxSteps, 1, 12); }
    public void setMaxSteps(int maxSteps) { this.maxSteps = maxSteps; }
    public int getMaxReplans() { return clamp(maxReplans, 0, 2); }
    public void setMaxReplans(int maxReplans) { this.maxReplans = maxReplans; }
    public int getMaxReflectRounds() { return clamp(maxReflectRounds, 0, 2); }
    public void setMaxReflectRounds(int maxReflectRounds) { this.maxReflectRounds = maxReflectRounds; }

    public boolean isEnabledFor(String agentId) {
        if (!enabled || agentId == null || agentId.isBlank()) return false;
        String expected = agentId.trim();
        return allowedAgentIds.stream().filter(value -> value != null && !value.isBlank())
                .map(String::trim).anyMatch(expected::equals);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
