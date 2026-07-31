package com.codehouse.ciciassistant.semattice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SematticeProjectDeliveryToolServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void queriesAllPublishedDeliveryObjectsWithDelegatedOfficialAccessToken() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UserRepository users = mock(UserRepository.class);
        OfficialAccessTokenService tokens = mock(OfficialAccessTokenService.class);
        when(users.findByIdAndCompany_Id("member-1", "org-1")).thenReturn(Optional.of(mock(UserEntity.class)));
        when(tokens.issueForSemattice(any(UserEntity.class))).thenReturn(
                new OfficialAccessTokenService.IssuedToken("delegated-oact", Instant.now().plusSeconds(300), "tenant-1", "org-1", java.util.List.of("runtime.record.read")));
        for (String objectName : java.util.List.of("dev_project", "dev_requirement", "dev_task", "dev_worklog", "dev_change")) {
            server.expect(requestTo("https://semattice.example.test/v1/capabilities/runtime.record.query/invoke"))
                    .andExpect(header("Authorization", "Bearer delegated-oact"))
                    .andRespond(withSuccess("{\"status\":\"succeeded\",\"result\":{\"records\":[{\"record_id\":\"r-" + objectName + "\",\"data\":{\"status\":\"执行中\",\"name\":\"演示项目\",\"hours\":2.5}}]}}", MediaType.APPLICATION_JSON));
        }
        SematticeProjectDeliveryToolService service = new SematticeProjectDeliveryToolService(
                builder, objectMapper, users, tokens, "https://semattice.example.test");

        JsonNode result = objectMapper.readTree(service.dispatch("org-1", "member-1", "{\"focus\":\"overview\"}"));

        assertThat(result.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(result.path("source").asText()).isEqualTo("SEMATTICE_LIVE");
        assertThat(result.path("executing_project_count").asLong()).isEqualTo(1);
        assertThat(result.path("projects").get(0).path("name").asText()).isEqualTo("演示项目");
        server.verify();
    }

    @Test
    void rejectsTenantAndTokenArgumentsBeforeAnyRemoteCall() throws Exception {
        SematticeProjectDeliveryToolService service = new SematticeProjectDeliveryToolService(
                RestClient.builder(), objectMapper, mock(UserRepository.class), mock(OfficialAccessTokenService.class),
                "https://semattice.example.test");

        JsonNode result = objectMapper.readTree(service.dispatch("org-1", "member-1", "{\"tenant_id\":\"other\"}"));

        assertThat(result.path("status").asText()).isEqualTo("FAILED");
        assertThat(result.path("code").asText()).isEqualTo("INVALID_ARGUMENTS");
    }
}
