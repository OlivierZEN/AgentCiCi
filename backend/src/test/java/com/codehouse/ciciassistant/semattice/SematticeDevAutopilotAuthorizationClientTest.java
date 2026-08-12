package com.codehouse.ciciassistant.semattice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SematticeDevAutopilotAuthorizationClientTest {
    @Test
    void signsAndAppliesOnlyLogicalAssignments() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SematticeProvisioningClient signer = mock(SematticeProvisioningClient.class);
        when(signer.signature(eq("agentcici"), eq("POST"),
                eq("/internal/v1/devautopilot-authorization-templates"), anyString(), anyString(), anyString()))
                .thenReturn("signed");
        server.expect(requestTo("https://semattice.example.test/internal/v1/devautopilot-authorization-templates"))
                .andExpect(header("X-Internal-Service", "agentcici"))
                .andExpect(header("X-Internal-Signature", "signed"))
                .andExpect(content().json("""
                        {"company_id":"company-a","template_version":"devautopilot.authorization.v1",
                         "activation_id":"activation-a","idempotency_key":"key-a","assignments":[
                           {"principal_id":"human-a","logical_role":"application_admin"},
                           {"principal_id":"service-pm","logical_role":"product_manager"}]}
                        """, true))
                .andRespond(withSuccess("""
                        {"status":"succeeded","result":{"company_id":"company-a","tenant_id":"tenant-a",
                         "template_version":"devautopilot.authorization.v1",
                         "authorization_digest":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                         "role_count":4,"permission_set_count":4,"object_count":7,"assignment_count":2,
                         "verified":true,"state":"applied"}}
                        """, MediaType.APPLICATION_JSON));
        SematticeDevAutopilotAuthorizationClient client = new SematticeDevAutopilotAuthorizationClient(
                builder, new ObjectMapper(), signer, "https://semattice.example.test/");

        var result = client.apply("company-a", "activation-a", "key-a", List.of(
                new SematticeDevAutopilotAuthorizationClient.Assignment("human-a", "application_admin"),
                new SematticeDevAutopilotAuthorizationClient.Assignment("service-pm", "product_manager")));

        assertThat(result.verified()).isTrue();
        assertThat(result.roleCount()).isEqualTo(4);
        assertThat(result.assignmentCount()).isEqualTo(2);
        verify(signer).signature(eq("agentcici"), eq("POST"),
                eq("/internal/v1/devautopilot-authorization-templates"), anyString(), anyString(), anyString());
        server.verify();
    }
}
