package com.codehouse.ciciassistant.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.integration.domain.IntegrationAppEntity;
import com.codehouse.ciciassistant.integration.domain.IntegrationAppRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CloudccAccessTokenServiceTest {

    @Test
    void validatesOrdinaryUserOpenApiTokenWithoutSetupMetadataPermission() throws Exception {
        AtomicInteger openApiRequests = new AtomicInteger();
        List<String> requestBodies = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/lightningapi/openApi/common", exchange -> {
            openApiRequests.incrementAndGet();
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = """
                    {"result":false,"returnCode":"NO_OBJECT_PERMISSION","returnInfo":"无对象权限"}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            String orgId = "agent-org";
            IntegrationAppRepository appRepository = configuredAppRepository(
                    orgId,
                    "http://127.0.0.1:%d/lightningapi".formatted(server.getAddress().getPort()));
            CloudccAccessTokenService service = new CloudccAccessTokenService(
                    appRepository,
                    mock(UserRepository.class),
                    new ObjectMapper());

            Optional<CloudccAccessTokenService.ValidatedCloudccToken> validated = service
                    .validateRuntimeAccessToken(orgId, jwt("sales@example.com", "cloudcc-org"));

            assertThat(validated).get()
                    .satisfies(token -> {
                        assertThat(token.actorId()).isEqualTo("sales@example.com");
                        assertThat(token.cloudccOrgId()).isEqualTo("cloudcc-org");
                    });
            assertThat(openApiRequests).hasValue(1);
            assertThat(requestBodies).singleElement().asString()
                    .contains("\"serviceName\":\"pageQueryWithRoleRight\"")
                    .contains("\"objectApiName\":\"Account\"")
                    .contains("\"pageSize\":1");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsOpenApiResponseThatDoesNotProveAuthenticationOrAuthorization() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/lightningapi/openApi/common", exchange -> {
            byte[] body = """
                    {"result":false,"returnCode":"SYSTEM_BUSY","returnInfo":"Unexpected server error"}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            String orgId = "agent-org";
            CloudccAccessTokenService service = new CloudccAccessTokenService(
                    configuredAppRepository(
                            orgId,
                            "http://127.0.0.1:%d/lightningapi".formatted(server.getAddress().getPort())),
                    mock(UserRepository.class),
                    new ObjectMapper());

            assertThat(service.validateRuntimeAccessToken(
                    orgId,
                    jwt("forged@example.com", "cloudcc-org"))).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void coalescesConcurrentTokenRequestsForTheSameUser() throws Exception {
        AtomicInteger tokenRequests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/lightningapi/api/cauth/token", exchange -> {
            tokenRequests.incrementAndGet();
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "{\"result\":true,\"data\":{\"accessToken\":\"shared-token\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            String orgId = "agent-org";
            String userId = "member-1";
            String config = """
                    {"orgId":"cloudcc-org","clientId":"client","secretKey":"secret",
                     "orgapi_switch_address":"http://127.0.0.1:%d/lightningapi"}
                    """.formatted(server.getAddress().getPort());
            IntegrationAppRepository appRepository = mock(IntegrationAppRepository.class);
            when(appRepository.findByOrgIdAndAppCode(orgId, IntegrationAppService.APP_CODE_CLOUDCC_CRM))
                    .thenReturn(Optional.of(new IntegrationAppEntity(orgId, IntegrationAppService.APP_CODE_CLOUDCC_CRM,
                            "CloudCC", "", true, config)));
            UserEntity user = mock(UserEntity.class);
            when(user.getCcUsername()).thenReturn("member@example.com");
            when(user.getCcSafetymark()).thenReturn("safety");
            UserRepository userRepository = mock(UserRepository.class);
            when(userRepository.findByIdAndOrg_Id(userId, orgId)).thenReturn(Optional.of(user));
            CloudccAccessTokenService service = new CloudccAccessTokenService(appRepository, userRepository, new ObjectMapper());

            CountDownLatch start = new CountDownLatch(1);
            List<Future<Optional<CloudccAccessTokenService.CloudccSessionContext>>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return service.getSessionContext(orgId, userId);
                }));
            }
            start.countDown();

            for (Future<Optional<CloudccAccessTokenService.CloudccSessionContext>> future : futures) {
                assertThat(future.get()).get().extracting(CloudccAccessTokenService.CloudccSessionContext::accessToken)
                        .isEqualTo("shared-token");
            }
            assertThat(tokenRequests).hasValue(1);
        } finally {
            executor.shutdownNow();
            server.stop(0);
        }
    }

    private IntegrationAppRepository configuredAppRepository(String orgId, String gateway) {
        String config = """
                {"orgId":"cloudcc-org","clientId":"client","secretKey":"secret",
                 "orgapi_switch_address":"%s"}
                """.formatted(gateway);
        IntegrationAppRepository repository = mock(IntegrationAppRepository.class);
        when(repository.findByOrgIdAndAppCode(orgId, IntegrationAppService.APP_CODE_CLOUDCC_CRM))
                .thenReturn(Optional.of(new IntegrationAppEntity(orgId, IntegrationAppService.APP_CODE_CLOUDCC_CRM,
                        "CloudCC", "", true, config)));
        return repository;
    }

    private String jwt(String username, String orgId) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(("{\"username\":\"%s\",\"orgId\":\"%s\"}"
                .formatted(username, orgId)).getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".signature";
    }
}
