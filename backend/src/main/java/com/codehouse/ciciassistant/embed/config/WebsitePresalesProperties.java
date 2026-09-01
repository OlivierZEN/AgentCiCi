package com.codehouse.ciciassistant.embed.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.website-presales")
public class WebsitePresalesProperties {

    private boolean enabled = true;
    private int idleMinutes = 30;
    private String cloudccTicketEntryUrl = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getIdleMinutes() { return idleMinutes; }
    public void setIdleMinutes(int idleMinutes) { this.idleMinutes = Math.max(5, Math.min(idleMinutes, 1440)); }
    public String getCloudccTicketEntryUrl() { return cloudccTicketEntryUrl; }
    public void setCloudccTicketEntryUrl(String cloudccTicketEntryUrl) {
        this.cloudccTicketEntryUrl = cloudccTicketEntryUrl == null ? "" : cloudccTicketEntryUrl.trim();
    }
}
