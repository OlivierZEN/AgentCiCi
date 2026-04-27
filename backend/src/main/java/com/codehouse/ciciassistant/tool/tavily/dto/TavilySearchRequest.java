package com.codehouse.ciciassistant.tool.tavily.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Body payload for {@code POST https://api.tavily.com/search}. Null / empty fields are omitted
 * so Tavily can apply its own defaults.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TavilySearchRequest(
        @JsonProperty("api_key") String apiKey,
        @JsonProperty("query") String query,
        @JsonProperty("search_depth") String searchDepth,
        @JsonProperty("max_results") Integer maxResults,
        @JsonProperty("topic") String topic,
        @JsonProperty("time_range") String timeRange,
        @JsonProperty("start_date") String startDate,
        @JsonProperty("end_date") String endDate,
        @JsonProperty("include_domains") List<String> includeDomains,
        @JsonProperty("exclude_domains") List<String> excludeDomains,
        @JsonProperty("country") String country,
        @JsonProperty("include_answer") String includeAnswer,
        @JsonProperty("include_raw_content") String includeRawContent
) {
}
