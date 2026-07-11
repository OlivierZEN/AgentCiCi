package com.codehouse.ciciassistant.cloudcc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.integration.service.CloudccAccessTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
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

    @Test
    void refreshesAndRetriesWhenCloudccReturnsLoginFailureWithHttp200() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/openApi/common", exchange -> {
            String token = exchange.getRequestHeaders().getFirst("accessToken");
            String json = "old-token".equals(token)
                    ? "{\"result\":false,\"returnCode\":\"LOGIN_FAILED\",\"returnInfo\":\"登录失败，请再次尝试重新登录\"}"
                    : "{\"result\":true,\"data\":[{\"id\":\"001\",\"name\":\"客户\"}],\"pageNUM\":1,\"pageCount\":1,\"totalCount\":1}";
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            CloudccAccessTokenService tokenService = mock(CloudccAccessTokenService.class);
            when(tokenService.getSessionContext("org", "user")).thenReturn(
                    Optional.of(new CloudccAccessTokenService.CloudccSessionContext("old-token", baseUrl, "")),
                    Optional.of(new CloudccAccessTokenService.CloudccSessionContext("new-token", baseUrl, "")));
            CloudccOpenApiService service = new CloudccOpenApiService(tokenService, new ObjectMapper());

            CloudccOpenApiService.PageRecords result = service.pageQueryRecords(
                    "org", "user", "Account", "id,name", "", 1, 20);

            assertThat(result.records()).hasSize(1);
            assertThat(result.records().get(0)).containsEntry("id", "001");
            verify(tokenService).invalidateSessionContext("org", "user", "old-token");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void recognizesCloudccBusinessAuthenticationFailures() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        assertThat(CloudccOpenApiService.isAuthenticationFailure(mapper.readTree("""
                {"result":false,"returnInfo":"登录失败，请再次尝试重新登录","_httpStatus":200}
                """))).isTrue();
        assertThat(CloudccOpenApiService.isAuthenticationFailure(mapper.readTree("""
                {"result":false,"returnInfo":"字段不存在","_httpStatus":200}
                """))).isFalse();
    }
}
