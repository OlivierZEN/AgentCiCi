package com.codehouse.ciciassistant.agent.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Server-owned gate for P4 controlled reflection. */
@Component
@ConfigurationProperties(prefix = "app.agent-runtime.reflect")
public class AgentRuntimeReflectProperties {
    private boolean enabled;
    private List<String> allowedCompanyIds = new ArrayList<>();
    private List<String> allowedAgentIds = new ArrayList<>();
    private int maxRounds = 1;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<String> getAllowedCompanyIds() { return List.copyOf(allowedCompanyIds); }
    public void setAllowedCompanyIds(List<String> allowedCompanyIds) {
        this.allowedCompanyIds = allowedCompanyIds == null ? new ArrayList<>() : new ArrayList<>(allowedCompanyIds);
    }
    public List<String> getAllowedAgentIds() { return List.copyOf(allowedAgentIds); }
    public void setAllowedAgentIds(List<String> allowedAgentIds) {
        this.allowedAgentIds = allowedAgentIds == null ? new ArrayList<>() : new ArrayList<>(allowedAgentIds);
    }
    public boolean isEnabledFor(String companyId, String agentId) {
        return enabled && isCompanyAllowlisted(companyId) && isAgentAllowlisted(agentId);
    }
    public boolean isCompanyAllowlisted(String companyId) { return isAllowlisted(allowedCompanyIds, companyId); }
    public boolean isAgentAllowlisted(String agentId) { return isAllowlisted(allowedAgentIds, agentId); }
    private static boolean isAllowlisted(List<String> allowedValues, String candidate) {
        if (candidate == null || candidate.isBlank()) return false;
        String expected = candidate.trim();
        return allowedValues.stream().filter(value -> value != null && !value.isBlank())
                .map(String::trim).anyMatch(expected::equals);
    }
    public int getMaxRounds() { return Math.max(0, Math.min(2, maxRounds)); }
    public void setMaxRounds(int maxRounds) { this.maxRounds = maxRounds; }
}
