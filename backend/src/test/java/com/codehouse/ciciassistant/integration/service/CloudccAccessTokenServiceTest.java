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
import org.junit.jupiter.api.Test;

class CloudccAccessTokenServiceTest {

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
}
