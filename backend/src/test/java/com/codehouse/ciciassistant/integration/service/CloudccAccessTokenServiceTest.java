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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CloudccAccessTokenServiceTest {

    @Test
    void validatesCurrentCloudccSessionThroughCurrentUserEndpoint() throws Exception {
        AtomicInteger userInfoRequests = new AtomicInteger();
        List<String> accessTokenHeaders = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/lightningapi/api/user/getUserInfo", exchange -> {
            userInfoRequests.incrementAndGet();
            accessTokenHeaders.add(exchange.getRequestHeaders().getFirst("accessToken"));
            byte[] body = """
                    {"result":true,"data":{"userId":"cloudcc-user","loginName":"sales@example.com","companyId":"cloudcc-org"}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            String companyId = "agent-org";
            IntegrationAppRepository appRepository = configuredAppRepository(
                    companyId,
                    "http://127.0.0.1:%d/lightningapi".formatted(server.getAddress().getPort()));
            CloudccAccessTokenService service = new CloudccAccessTokenService(
                    appRepository,
                    mock(UserRepository.class),
                    new ObjectMapper());

            Optional<CloudccAccessTokenService.ValidatedCloudccToken> validated = service
                    .validateRuntimeAccessToken(companyId, "current-crm-session-token");

            assertThat(validated).get()
                    .satisfies(token -> {
                        assertThat(token.actorId()).isEqualTo("sales@example.com");
                        assertThat(token.cloudccCompanyId()).isEqualTo("cloudcc-org");
                    });
            assertThat(userInfoRequests).hasValue(1);
            assertThat(accessTokenHeaders).containsExactly("current-crm-session-token");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsCurrentUserResponseThatDoesNotProveAuthentication() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/lightningapi/api/user/getUserInfo", exchange -> {
            byte[] body = """
                    {"result":false,"returnCode":"10002","returnInfo":"Token认证过期，请重新登录"}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            String companyId = "agent-org";
            CloudccAccessTokenService service = new CloudccAccessTokenService(
                    configuredAppRepository(
                            companyId,
                            "http://127.0.0.1:%d/lightningapi".formatted(server.getAddress().getPort())),
                    mock(UserRepository.class),
                    new ObjectMapper());

            assertThat(service.validateRuntimeAccessToken(
                    companyId,
                    "expired-crm-session-token")).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void coalescesConcurrentTokenRequestsForTheSameUser() throws Exception {
        AtomicInteger tokenRequests = new AtomicInteger();
        AtomicReference<String> requestBody = new AtomicReference<>("");
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/lightningapi/api/cauth/token", exchange -> {
            tokenRequests.incrementAndGet();
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
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
            String companyId = "agent-org";
            String userId = "member-1";
            String config = """
                    {"orgId":"cloudcc-org","clientId":"client","secretKey":"secret",
                     "orgapi_switch_address":"http://127.0.0.1:%d/lightningapi"}
                    """.formatted(server.getAddress().getPort());
            IntegrationAppRepository appRepository = mock(IntegrationAppRepository.class);
            when(appRepository.findByCompanyIdAndAppCode(companyId, IntegrationAppService.APP_CODE_CLOUDCC_CRM))
                    .thenReturn(Optional.of(new IntegrationAppEntity(companyId, IntegrationAppService.APP_CODE_CLOUDCC_CRM,
                            "CloudCC", "", true, config)));
            UserEntity user = mock(UserEntity.class);
            when(user.getCcUsername()).thenReturn("member@example.com");
            when(user.getCcSafetymark()).thenReturn("safety");
            UserRepository userRepository = mock(UserRepository.class);
            when(userRepository.findByIdAndCompany_Id(userId, companyId)).thenReturn(Optional.of(user));
            CloudccAccessTokenService service = new CloudccAccessTokenService(appRepository, userRepository, new ObjectMapper());

            CountDownLatch start = new CountDownLatch(1);
            List<Future<Optional<CloudccAccessTokenService.CloudccSessionContext>>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return service.getSessionContext(companyId, userId);
                }));
            }
            start.countDown();

            for (Future<Optional<CloudccAccessTokenService.CloudccSessionContext>> future : futures) {
                assertThat(future.get()).get().extracting(CloudccAccessTokenService.CloudccSessionContext::accessToken)
                        .isEqualTo("shared-token");
            }
            assertThat(tokenRequests).hasValue(1);
            var payload = new ObjectMapper().readTree(requestBody.get());
            assertThat(payload.path("orgId").asText()).isEqualTo("cloudcc-org");
            assertThat(payload.has("companyId")).isFalse();
        } finally {
            executor.shutdownNow();
            server.stop(0);
        }
    }

    private IntegrationAppRepository configuredAppRepository(String companyId, String gateway) {
        String config = """
                {"orgId":"cloudcc-org","clientId":"client","secretKey":"secret",
                 "orgapi_switch_address":"%s"}
                """.formatted(gateway);
        IntegrationAppRepository repository = mock(IntegrationAppRepository.class);
        when(repository.findByCompanyIdAndAppCode(companyId, IntegrationAppService.APP_CODE_CLOUDCC_CRM))
                .thenReturn(Optional.of(new IntegrationAppEntity(companyId, IntegrationAppService.APP_CODE_CLOUDCC_CRM,
                        "CloudCC", "", true, config)));
        return repository;
    }

}
