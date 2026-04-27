package com.codehouse.ciciassistant.tool.tavily.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Body payload for {@code POST https://api.tavily.com/extract}. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TavilyExtractRequest(
        @JsonProperty("api_key") String apiKey,
        @JsonProperty("urls") List<String> urls,
        @JsonProperty("format") String format,
        @JsonProperty("extract_depth") String extractDepth,
        @JsonProperty("include_images") Boolean includeImages
) {
}
