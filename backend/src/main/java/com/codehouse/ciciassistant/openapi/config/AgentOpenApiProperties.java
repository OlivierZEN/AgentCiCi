package com.codehouse.ciciassistant.openapi.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.agent-open-api")
public class AgentOpenApiProperties {

    private boolean enabled;
    private String keyPepper = "";
    private int defaultRateLimitPerMinute = 60;
    private int defaultDailyQuota = 1000;
    private int defaultMaxPromptChars = 8000;
    private int defaultMaxResponseChars = 12000;
    private long defaultTimeoutMs = 120000L;
    private List<String> corsAllowedOrigins = new ArrayList<>();
    private List<String> corsAllowedOriginPatterns = new ArrayList<>();
    private long corsMaxAgeSeconds = 3600L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKeyPepper() {
        return keyPepper;
    }

    public void setKeyPepper(String keyPepper) {
        this.keyPepper = keyPepper == null ? "" : keyPepper;
    }

    public int getDefaultRateLimitPerMinute() {
        return defaultRateLimitPerMinute;
    }

    public void setDefaultRateLimitPerMinute(int defaultRateLimitPerMinute) {
        this.defaultRateLimitPerMinute = defaultRateLimitPerMinute;
    }

    public int getDefaultDailyQuota() {
        return defaultDailyQuota;
    }

    public void setDefaultDailyQuota(int defaultDailyQuota) {
        this.defaultDailyQuota = defaultDailyQuota;
    }

    public int getDefaultMaxPromptChars() {
        return defaultMaxPromptChars;
    }

    public void setDefaultMaxPromptChars(int defaultMaxPromptChars) {
        this.defaultMaxPromptChars = defaultMaxPromptChars;
    }

    public int getDefaultMaxResponseChars() {
        return defaultMaxResponseChars;
    }

    public void setDefaultMaxResponseChars(int defaultMaxResponseChars) {
        this.defaultMaxResponseChars = defaultMaxResponseChars;
    }

    public long getDefaultTimeoutMs() {
        return defaultTimeoutMs;
    }

    public void setDefaultTimeoutMs(long defaultTimeoutMs) {
        this.defaultTimeoutMs = defaultTimeoutMs;
    }

    public List<String> getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) {
        this.corsAllowedOrigins = normalizeList(corsAllowedOrigins);
    }

    public List<String> getCorsAllowedOriginPatterns() {
        return corsAllowedOriginPatterns;
    }

    public void setCorsAllowedOriginPatterns(List<String> corsAllowedOriginPatterns) {
        this.corsAllowedOriginPatterns = normalizeList(corsAllowedOriginPatterns);
    }

    public long getCorsMaxAgeSeconds() {
        return corsMaxAgeSeconds;
    }

    public void setCorsMaxAgeSeconds(long corsMaxAgeSeconds) {
        this.corsMaxAgeSeconds = corsMaxAgeSeconds;
    }

    private List<String> normalizeList(List<String> raw) {
        if (raw == null) {
            return new ArrayList<>();
        }
        List<String> normalized = new ArrayList<>();
        for (String value : raw) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.trim());
            }
        }
        return normalized;
    }
}
