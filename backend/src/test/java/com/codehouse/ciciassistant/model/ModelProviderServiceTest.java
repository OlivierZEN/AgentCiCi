package com.codehouse.ciciassistant.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.config.PlatformAccountProperties;
import com.codehouse.ciciassistant.model.domain.CompanyModelConfigRepository;
import com.codehouse.ciciassistant.model.domain.ModelProviderConfigEntity;
import com.codehouse.ciciassistant.model.domain.ModelProviderConfigRepository;
import com.codehouse.ciciassistant.model.service.ModelProviderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class ModelProviderServiceTest {

    @Test
    void oneKeyTokenModelCatalogUsesStoredBearerKeyAndExplainsAuthorizationFailures() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> accept = new AtomicReference<>();
        AtomicReference<String> method = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> respondToModels(exchange, authorization, accept, method));
        server.start();
        try {
            String companyId = "platform-scope";
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            ModelProviderConfigEntity entity = new ModelProviderConfigEntity(
                    companyId,
                    ModelProviderService.PROVIDER_ONEKEYTOKEN,
                    "OneKeyToken",
                    true,
                    baseUrl,
                    "catalog-key",
                    "{}");
            ModelProviderConfigRepository providerRepository = mock(ModelProviderConfigRepository.class);
            when(providerRepository.findByCompanyIdAndProviderCode(
                    companyId, ModelProviderService.PROVIDER_ONEKEYTOKEN)).thenReturn(Optional.of(entity));
            ModelProviderService service = new ModelProviderService(
                    providerRepository,
                    mock(CompanyModelConfigRepository.class),
                    new PlatformAccountProperties(),
                    new ObjectMapper());

            Map<String, Object> result = service.fetchProviderModels(
                    companyId, ModelProviderService.PROVIDER_ONEKEYTOKEN);

            assertThat(authorization.get()).isEqualTo("Bearer catalog-key");
            assertThat(accept.get()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
            assertThat(method.get()).isEqualTo("GET");
            assertThat(result.get("catalogSource")).isEqualTo("remote");
            assertThat(result.get("remoteFetchSupported")).isEqualTo(true);
            assertThat(result.get("count")).isEqualTo(2);
            assertThat(result.get("models")).isEqualTo(List.of("model-chat-a", "model-chat-b"));

            entity.setApiKey("rotated-key");
            assertThatThrownBy(() -> service.fetchProviderModels(
                    companyId, ModelProviderService.PROVIDER_ONEKEYTOKEN))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("HTTP 401")
                    .hasMessageContaining("是否正确或已轮换")
                    .hasMessageNotContaining("rotated-key");

            entity.setApiKey("forbidden-key");
            assertThatThrownBy(() -> service.fetchProviderModels(
                    companyId, ModelProviderService.PROVIDER_ONEKEYTOKEN))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("HTTP 403")
                    .hasMessageContaining("scope 包含 model:invoke")
                    .hasMessageNotContaining("forbidden-key");
        } finally {
            server.stop(0);
        }
    }

    private void respondToModels(HttpExchange exchange,
                                 AtomicReference<String> authorization,
                                 AtomicReference<String> accept,
                                 AtomicReference<String> method) throws java.io.IOException {
        authorization.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        accept.set(exchange.getRequestHeaders().getFirst(HttpHeaders.ACCEPT));
        method.set(exchange.getRequestMethod());
        String key = authorization.get();
        int status;
        String body;
        if ("Bearer catalog-key".equals(key)) {
            status = 200;
            body = """
                    {"object":"list","data":[{"id":"model-chat-a","object":"model"},{"id":"model-chat-b","object":"model"}]}
                    """;
        } else if ("Bearer forbidden-key".equals(key)) {
            status = 403;
            body = """
                    {"error":{"code":"forbidden","message":"Missing scope"}}
                    """;
        } else {
            status = 401;
            body = """
                    {"error":{"code":"unauthorized","message":"Invalid API key"}}
                    """;
        }
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
