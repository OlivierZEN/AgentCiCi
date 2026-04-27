package com.codehouse.ciciassistant.tool.tavily;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Non-credential defaults for Tavily tools.
 *
 * <p><b>API Key is intentionally NOT part of this properties class.</b>
 * API keys must be stored per tenant in {@code integration_app(code="tavily")}
 * (encrypted via {@link com.codehouse.ciciassistant.common.crypto.SecretCipherService}).
 * There is no environment-variable or yaml fallback — if a tenant has not configured
 * a key, the tool returns a structured {@code TAVILY_NOT_CONFIGURED} error.
 */
@ConfigurationProperties(prefix = "cici.tool.tavily")
public record TavilyProperties(
        String apiBase,
        int defaultMaxResults,
        String defaultSearchDepth,
        String defaultTopic,
        String defaultIncludeAnswer,
        String defaultExtractFormat,
        int maxExtractChars,
        int perSessionLimit,
        Duration timeout
) {

    public TavilyProperties {
        if (apiBase == null || apiBase.isBlank()) {
            apiBase = "https://api.tavily.com";
        }
        if (defaultMaxResults <= 0) {
            defaultMaxResults = 5;
        }
        if (defaultSearchDepth == null || defaultSearchDepth.isBlank()) {
            defaultSearchDepth = "basic";
        }
        if (defaultTopic == null || defaultTopic.isBlank()) {
            defaultTopic = "general";
        }
        // null means omit include_answer from the request — Tavily then uses its own default (false).
        // "none" is not a valid Tavily value; treat it as null.
        if (defaultIncludeAnswer != null && defaultIncludeAnswer.isBlank()) {
            defaultIncludeAnswer = null;
        }
        if ("none".equalsIgnoreCase(defaultIncludeAnswer)) {
            defaultIncludeAnswer = null;
        }
        if (defaultExtractFormat == null || defaultExtractFormat.isBlank()) {
            defaultExtractFormat = "markdown";
        }
        if (maxExtractChars <= 0) {
            maxExtractChars = 20_000;
        }
        if (perSessionLimit <= 0) {
            perSessionLimit = 10;
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            timeout = Duration.ofSeconds(10);
        }
    }
}
