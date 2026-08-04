package com.codehouse.ciciassistant.aitable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.codehouse.ciciassistant.auth.service.AuthService;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiTableDataServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void readsPublishedCatalogWithCurrentUsersOfficialAccessToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Fixture fixture = fixture(builder);
        server.expect(requestTo("https://semattice.example.test/v1/capabilities/metadata.version.get-current/invoke"))
                .andExpect(header("Authorization", "Bearer member-oact"))
                .andExpect(jsonPath("$.capability_id").value("metadata.version.get-current"))
                .andExpect(jsonPath("$.input").isMap())
                .andRespond(withSuccess(metadataResponse(), MediaType.APPLICATION_JSON));

        Map<String, Object> result = fixture.service().catalog("company-a", "member-a");

        assertThat(result).containsEntry("companyName", "鎏金账房演示租户");
        assertThat(String.valueOf(result.get("preferenceScope"))).hasSize(22);
        List<Map<String, Object>> objects = (List<Map<String, Object>>) result.get("objects");
        assertThat(objects).singleElement().satisfies(object -> {
            assertThat(object).containsEntry("apiName", "customer");
            assertThat(object).containsEntry("searchFieldApiName", "name");
        });
        server.verify();
    }

    @Test
    void validatesObjectMetadataThenUsesOnlyIndexedTextFieldForQuery() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Fixture fixture = fixture(builder);
        server.expect(requestTo("https://semattice.example.test/v1/capabilities/metadata.version.get-current/invoke"))
                .andExpect(header("Authorization", "Bearer member-oact"))
                .andRespond(withSuccess(metadataResponse(), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://semattice.example.test/v1/capabilities/runtime.record.query/invoke"))
                .andExpect(header("Authorization", "Bearer member-oact"))
                .andExpect(jsonPath("$.input.object_api_name").value("customer"))
                .andExpect(jsonPath("$.input.limit").value(100))
                .andExpect(jsonPath("$.input.filters[0].field").value("name"))
                .andExpect(jsonPath("$.input.filters[0].op").value("prefix"))
                .andExpect(jsonPath("$.input.filters[0].value").value("杭州"))
                .andRespond(withSuccess("""
                        {"status":"succeeded","result":{"records":[{
                          "record_id":"019fb381-622b-73b9-b8c8-b97181509008","revision":3,
                          "data":{"name":"杭州云序科技","owner":"林晓"}}],
                          "next_cursor":"019fb381-622b-73b9-b8c8-b97181509008"}}
                        """, MediaType.APPLICATION_JSON));

        Map<String, Object> result = fixture.service().records("company-a", "member-a", "CUSTOMER", 400, "", "杭州");

        assertThat(result).containsEntry("objectApiName", "customer");
        assertThat(result).containsEntry("nextCursor", "019fb381-622b-73b9-b8c8-b97181509008");
        List<Map<String, Object>> records = (List<Map<String, Object>>) result.get("records");
        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record).containsEntry("id", "019fb381-622b-73b9-b8c8-b97181509008");
            assertThat((Map<String, Object>) record.get("data")).containsEntry("name", "杭州云序科技");
        });
        server.verify();
    }

    private Fixture fixture(RestClient.Builder builder) {
        AuthService authService = mock(AuthService.class);
        OfficialAccessTokenService officialAccessTokens = mock(OfficialAccessTokenService.class);
        OfficialAccessTokenService.IssuedToken token = new OfficialAccessTokenService.IssuedToken(
                "member-oact", Instant.now().plusSeconds(300), "tenant-a", "company-a",
                List.of("metadata.read", "runtime.record.read"));
        when(authService.issueSematticeOfficialAccess(eq("company-a"), eq("member-a"), any(OfficialAccessTokenService.class)))
                .thenReturn(token);
        when(authService.currentUser("company-a", "member-a"))
                .thenReturn(Map.of("companyName", "鎏金账房演示租户"));
        return new Fixture(new AiTableDataService(builder, objectMapper, authService, officialAccessTokens,
                "https://semattice.example.test"));
    }

    private static String metadataResponse() {
        return """
                {"status":"succeeded","result":{"objects":[{
                  "object_id":"019fb381-622b-73b9-b8c8-b97181509001","api_name":"customer",
                  "label":"客户","description":"企业客户档案"}],"fields":[
                  {"object_id":"019fb381-622b-73b9-b8c8-b97181509001","api_name":"name","label":"客户名称","data_type":"text","indexed":true,"lifecycle_state":"active"},
                  {"object_id":"019fb381-622b-73b9-b8c8-b97181509001","api_name":"owner","label":"负责人","data_type":"text","indexed":false,"lifecycle_state":"active"}
                ]}}
                """;
    }

    private record Fixture(AiTableDataService service) { }
}
