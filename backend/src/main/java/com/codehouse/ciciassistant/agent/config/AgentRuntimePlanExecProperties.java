package com.codehouse.ciciassistant.agent.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Server-owned canary gate. Clients can never select Plan-Exec through request data. */
@Component
@ConfigurationProperties(prefix = "app.agent-runtime.plan-exec")
public class AgentRuntimePlanExecProperties {

    private boolean enabled;
    private List<String> allowedAgentIds = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<String> getAllowedAgentIds() { return List.copyOf(allowedAgentIds); }
    public void setAllowedAgentIds(List<String> allowedAgentIds) {
        this.allowedAgentIds = allowedAgentIds == null ? new ArrayList<>() : new ArrayList<>(allowedAgentIds);
    }

    public boolean isEnabledFor(String agentId) {
        if (!enabled || agentId == null || agentId.isBlank()) return false;
        String expected = agentId.trim();
        return allowedAgentIds.stream().filter(value -> value != null && !value.isBlank())
                .map(String::trim).anyMatch(expected::equals);
    }
}
