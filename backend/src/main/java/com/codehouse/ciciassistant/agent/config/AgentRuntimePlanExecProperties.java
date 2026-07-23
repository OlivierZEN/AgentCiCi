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
    private List<String> allowedOrgIds = new ArrayList<>();
    private List<String> allowedAgentIds = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<String> getAllowedOrgIds() { return List.copyOf(allowedOrgIds); }
    public void setAllowedOrgIds(List<String> allowedOrgIds) {
        this.allowedOrgIds = allowedOrgIds == null ? new ArrayList<>() : new ArrayList<>(allowedOrgIds);
    }
    public List<String> getAllowedAgentIds() { return List.copyOf(allowedAgentIds); }
    public void setAllowedAgentIds(List<String> allowedAgentIds) {
        this.allowedAgentIds = allowedAgentIds == null ? new ArrayList<>() : new ArrayList<>(allowedAgentIds);
    }

    public boolean isEnabledFor(String orgId, String agentId) {
        return enabled && isAllowlisted(allowedOrgIds, orgId) && isAllowlisted(allowedAgentIds, agentId);
    }

    private static boolean isAllowlisted(List<String> allowedValues, String candidate) {
        if (candidate == null || candidate.isBlank()) return false;
        String expected = candidate.trim();
        return allowedValues.stream().filter(value -> value != null && !value.isBlank())
                .map(String::trim).anyMatch(expected::equals);
    }
}
