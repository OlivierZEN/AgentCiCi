package com.codehouse.ciciassistant.mcp.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.codehouse.ciciassistant.common.crypto.SecretCipherService;
import com.codehouse.ciciassistant.mcp.domain.McpServerEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class McpAuthenticationServiceTest {

    @Test
    void exchangesEncryptedClientSecretAndCachesShortLivedBearerToken() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String[] receivedBody = new String[1];
        HttpServer keycloak = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        keycloak.createContext("/token", exchange -> {
            calls.incrementAndGet();
            receivedBody[0] = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] response = "{\"access_token\":\"signed-access-token\",\"expires_in\":300}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("content-type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        keycloak.start();
        try {
            SecretCipherService cipher = new SecretCipherService("");
            SecretCipherService.EncryptedSecret encrypted = cipher.encryptUtf8("not-returned-secret");
            McpServerEntity server = new McpServerEntity("company-1", "DevAutopilot", "", "streamableHttp",
                    "https://provider.example.test/mcp", "{}", 10);
            ReflectionTestUtils.setField(server, "id", 41L);
            server.setKeycloakAuthentication("http://127.0.0.1:" + keycloak.getAddress().getPort() + "/token",
                    "agentcici-mcp-runtime", encrypted.cipherBase64(), encrypted.ivBase64(),
                    "devautopilot-mcp", "openid devautopilot:mcp");
            McpAuthenticationService service = new McpAuthenticationService(cipher, new ObjectMapper());

            assertThat(service.headers(server)).containsEntry("Authorization", "Bearer signed-access-token");
            assertThat(service.headers(server)).containsEntry("Authorization", "Bearer signed-access-token");
            assertThat(calls).hasValue(1);
            assertThat(receivedBody[0]).contains("grant_type=client_credentials", "client_id=agentcici-mcp-runtime",
                    "client_secret=not-returned-secret", "audience=devautopilot-mcp", "scope=openid+devautopilot%3Amcp");
        } finally {
            keycloak.stop(0);
        }
    }
}
