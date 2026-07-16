package com.codehouse.ciciassistant.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void preservesLegacyNullDataWhileOmittingOnlyNewNullFields() throws Exception {
        JsonNode failure = objectMapper.readTree(
                objectMapper.writeValueAsString(ApiResponse.fail("ONTOLOGY_NOT_FOUND")));

        assertThat(failure.has("data")).isTrue();
        assertThat(failure.path("data").isNull()).isTrue();
        assertThat(failure.path("code").asText()).isEqualTo("ONTOLOGY_NOT_FOUND");
        assertThat(failure.has("details")).isFalse();
    }
}
