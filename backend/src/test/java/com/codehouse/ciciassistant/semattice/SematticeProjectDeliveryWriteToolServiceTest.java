package com.codehouse.ciciassistant.semattice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SematticeProjectDeliveryWriteToolServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void returnsDraftUntilExactProjectConfirmationThenCreatesWithDelegatedToken() throws Exception {
        assertThat(SematticeProjectDeliveryWriteToolService.draftResponse("现在创建一个棕榈地的研发项目"))
                .hasValueSatisfying(value -> assertThat(value).contains("确认创建项目：棕榈地"));
        assertThat(SematticeProjectDeliveryWriteToolService.confirmedIntent("确认创建项目：棕榈地"))
                .hasValueSatisfying(intent -> assertThat(intent.operation()).isEqualTo("create_project"));

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UserRepository users = mock(UserRepository.class);
        OfficialAccessTokenService tokens = mock(OfficialAccessTokenService.class);
        when(users.findByIdAndCompany_Id("member-1", "org-1")).thenReturn(Optional.of(mock(UserEntity.class)));
        when(tokens.issueForSemattice(any(UserEntity.class))).thenReturn(new OfficialAccessTokenService.IssuedToken(
                "delegated-oact", Instant.now().plusSeconds(300), "tenant-1", "org-1",
                List.of("runtime.record.read", "runtime.record.create")));
        server.expect(requestTo("https://semattice.example.test/v1/capabilities/runtime.record.create/invoke"))
                .andExpect(header("Authorization", "Bearer delegated-oact"))
                .andRespond(withSuccess("{\"status\":\"succeeded\",\"result\":{\"record_id\":\"019fb381-622b-73b9-b8c8-b97181509008\"}}", MediaType.APPLICATION_JSON));
        SematticeProjectDeliveryWriteToolService service = new SematticeProjectDeliveryWriteToolService(
                builder, objectMapper, users, tokens, "https://semattice.example.test");

        JsonNode result = objectMapper.readTree(service.dispatch("org-1", "member-1",
                "{\"operation\":\"create_project\",\"name\":\"棕榈地\"}"));

        assertThat(result.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(result.path("source").asText()).isEqualTo("SEMATTICE_LIVE");
        assertThat(result.path("code").asText()).startsWith("DAS-");
        assertThat(result.path("name").asText()).isEqualTo("棕榈地");
        server.verify();
    }

    @Test
    void extractsCompleteProjectNameWhenUserSaysProjectNameIs() {
        assertThat(SematticeProjectDeliveryWriteToolService.draftResponse(
                "现在创建一个研发项目名称叫：AgentCiCi企业级智能体平台"))
                .hasValueSatisfying(value -> {
                    assertThat(value).contains("拟创建项目：AgentCiCi企业级智能体平台");
                    assertThat(value).contains("确认创建项目：AgentCiCi企业级智能体平台");
                    assertThat(value).doesNotContain("确认创建项目：研发");
                });
    }

    @Test
    void rejectsCallerSuppliedTenantBeforeAnyRemoteCall() throws Exception {
        SematticeProjectDeliveryWriteToolService service = new SematticeProjectDeliveryWriteToolService(
                RestClient.builder(), objectMapper, mock(UserRepository.class), mock(OfficialAccessTokenService.class),
                "https://semattice.example.test");

        JsonNode result = objectMapper.readTree(service.dispatch("org-1", "member-1",
                "{\"operation\":\"create_project\",\"name\":\"棕榈地\",\"tenant_id\":\"other\"}"));

        assertThat(result.path("status").asText()).isEqualTo("FAILED");
        assertThat(result.path("code").asText()).isEqualTo("INVALID_ARGUMENTS");
    }
}
