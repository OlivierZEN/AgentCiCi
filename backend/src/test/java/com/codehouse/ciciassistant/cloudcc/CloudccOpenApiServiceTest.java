package com.codehouse.ciciassistant.cloudcc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.codehouse.ciciassistant.integration.service.CloudccAccessTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CloudccOpenApiServiceTest {

    @Test
    void extractsNestedIdsFromCloudccWriteResponse() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        CloudccOpenApiService service = new CloudccOpenApiService(
                mock(CloudccAccessTokenService.class), objectMapper);

        assertThat(service.extractIds(objectMapper.readTree("""
                {"ids":[{"errors":[],"id":"bfa2026FD4EE386fHde1","success":true}]}
                """))).containsExactly("bfa2026FD4EE386fHde1");
    }
}
