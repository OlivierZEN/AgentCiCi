package com.codehouse.ciciassistant.tool.tavily;

import com.codehouse.ciciassistant.tool.tavily.dto.TavilyExtractRequest;
import com.codehouse.ciciassistant.tool.tavily.dto.TavilySearchRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Low-level HTTP client for the Tavily REST API.
 * Never resolves API keys by itself — every call is given an explicit {@code apiKey}
 * by {@link TavilyToolService} (which in turn reads it from {@code integration_app}).
 */
@Component
public class TavilyClient {

    private static final Logger log = LoggerFactory.getLogger(TavilyClient.class);

    private final TavilyProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public TavilyClient(TavilyProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public TavilyCallResult<Map<String, Object>> search(String apiKey, TavilySearchRequest body) {
        TavilySearchRequest payload = new TavilySearchRequest(
                apiKey,
                body.query(),
                body.searchDepth(),
                body.maxResults(),
                body.topic(),
                body.timeRange(),
                body.startDate(),
                body.endDate(),
                body.includeDomains(),
                body.excludeDomains(),
                body.country(),
                body.includeAnswer(),
                body.includeRawContent()
        );
        return exchange("/search", payload);
    }

    public TavilyCallResult<Map<String, Object>> extract(String apiKey, TavilyExtractRequest body) {
        TavilyExtractRequest payload = new TavilyExtractRequest(
                apiKey,
                body.urls(),
                body.format(),
                body.extractDepth(),
                body.includeImages()
        );
        return exchange("/extract", payload);
    }

    private TavilyCallResult<Map<String, Object>> exchange(String path, Object payload) {
        String url = properties.apiBase() + path;
        long start = System.currentTimeMillis();
        try {
            String json = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(properties.timeout())
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;
            int status = response.statusCode();
            String body = response.body();
            if (status < 200 || status >= 300) {
                log.warn("Tavily {} failed status={} latencyMs={}", path, status, latency);
                return TavilyCallResult.upstreamError(status, body, latency);
            }
            Map<String, Object> parsed = objectMapper.readValue(
                    body == null || body.isBlank() ? "{}" : body,
                    new TypeReference<Map<String, Object>>() {});
            return TavilyCallResult.ok(parsed, status, latency);
        } catch (java.net.http.HttpTimeoutException ex) {
            long latency = System.currentTimeMillis() - start;
            log.warn("Tavily {} timeout after {}ms", path, latency);
            return TavilyCallResult.timeout(latency);
        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - start;
            log.warn("Tavily {} transport error: {}", path, ex.getMessage());
            return TavilyCallResult.transportError(ex.getMessage(), latency);
        }
    }

    /** Immutable envelope for a Tavily call, either success or one of the failure modes. */
    public record TavilyCallResult<T>(
            boolean ok,
            T data,
            int httpStatus,
            long latencyMs,
            String errorCode,
            String errorMessage
    ) {
        static <T> TavilyCallResult<T> ok(T data, int status, long latency) {
            return new TavilyCallResult<>(true, data, status, latency, null, null);
        }

        static <T> TavilyCallResult<T> upstreamError(int status, String body, long latency) {
            String trimmed = body == null ? "" : body.length() > 512 ? body.substring(0, 512) : body;
            return new TavilyCallResult<>(false, null, status, latency,
                    "TAVILY_UPSTREAM_ERROR",
                    "Tavily API returned status " + status + (trimmed.isBlank() ? "" : ": " + trimmed));
        }

        static <T> TavilyCallResult<T> timeout(long latency) {
            return new TavilyCallResult<>(false, null, 0, latency,
                    "TAVILY_TIMEOUT", "Tavily API request timed out");
        }

        static <T> TavilyCallResult<T> transportError(String reason, long latency) {
            return new TavilyCallResult<>(false, null, 0, latency,
                    "TAVILY_TRANSPORT_ERROR",
                    reason == null ? "Tavily API transport error" : reason);
        }
    }
}
