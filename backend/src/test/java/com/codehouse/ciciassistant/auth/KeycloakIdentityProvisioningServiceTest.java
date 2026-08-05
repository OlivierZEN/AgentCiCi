package com.codehouse.ciciassistant.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.domain.AccountExternalIdentityEntity;
import com.codehouse.ciciassistant.auth.domain.AccountExternalIdentityRepository;
import com.codehouse.ciciassistant.auth.domain.UserAccountEntity;
import com.codehouse.ciciassistant.auth.domain.UserAccountRepository;
import com.codehouse.ciciassistant.auth.service.KeycloakIdentityProvisioningService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class KeycloakIdentityProvisioningServiceTest {

    @Test
    void allowsMachineProvisioningWithoutHumanInvitationRedirectUri() {
        KeycloakIdentityProvisioningService service = service(false, true, "");

        assertThat(service.isEnabled()).isFalse();
        assertThat(service.isMachineProvisioningEnabled()).isTrue();
    }

    @Test
    void requiresInvitationRedirectOnlyWhenHumanProvisioningIsEnabled() {
        assertThatThrownBy(() -> service(true, false, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("redirect URI");
    }

    @Test
    void rejectsAnOidcCallbackAsAnInvitationLandingPage() {
        assertThatThrownBy(() -> service(true, false, "https://x.agentcici.com/auth/oidc/callback"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must target /app");
    }

    @Test
    void failsClosedWhenMachineProvisioningIsDisabled() {
        KeycloakIdentityProvisioningService service = service(false, false, "");

        assertThatThrownBy(() -> service.createServiceClient("agentcici-test", "semattice-api"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("尚未启用");
    }

    @Test
    void recreatesMissingRemoteUserAndRebindsTheExistingLocalIdentity() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        UserAccountEntity account = account("account-1", "U2026AB12CD34", "invitee@example.com");
        AccountExternalIdentityEntity staleIdentity = new AccountExternalIdentityEntity(
                account, "http://placeholder/realms/agentcici", "deleted-subject");
        AccountExternalIdentityRepository identities = Mockito.mock(AccountExternalIdentityRepository.class);
        UserAccountRepository accounts = Mockito.mock(UserAccountRepository.class);
        when(identities.findByAccount_Id(account.getId())).thenReturn(java.util.Optional.of(staleIdentity));
        when(accounts.findById(account.getId())).thenReturn(java.util.Optional.of(account));

        AtomicInteger activationEmails = new AtomicInteger();
        HttpServer server = server(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/protocol/openid-connect/token")) {
                respond(exchange, 200, "{\"access_token\":\"provisioner-token\"}");
            } else if (path.endsWith("/users/deleted-subject")) {
                respond(exchange, 404, "");
            } else if (path.endsWith("/users") && "GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, "[]");
            } else if (path.endsWith("/users") && "POST".equals(exchange.getRequestMethod())) {
                JsonNode body = mapper.readTree(exchange.getRequestBody().readAllBytes());
                assertThat(body.path("username").asText()).isEqualTo("U2026AB12CD34");
                assertThat(body.path("attributes").path("agentcici_account_id").get(0).asText()).isEqualTo("account-1");
                exchange.getResponseHeaders().add("Location", "/admin/realms/agentcici/users/new-subject");
                respond(exchange, 201, "");
            } else if (path.endsWith("/users/new-subject/execute-actions-email")) {
                assertThat(exchange.getRequestURI().getRawQuery())
                        .contains("redirect_uri=https%3A%2F%2Fx.agentcici.com%2Fapp")
                        .contains("lifespan=86400");
                activationEmails.incrementAndGet();
                respond(exchange, 204, "");
            } else {
                respond(exchange, 404, "");
            }
        });
        try {
            KeycloakIdentityProvisioningService.ProvisionResult result = provisioningService(
                    identities, accounts, issuer(server), true).ensureHumanIdentity(account);

            assertThat(result).isEqualTo(new KeycloakIdentityProvisioningService.ProvisionResult(true, true, "new-subject"));
            assertThat(staleIdentity.getSubject()).isEqualTo("new-subject");
            assertThat(activationEmails.get()).isEqualTo(1);
            verify(identities).saveAndFlush(staleIdentity);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void recoversOnlyAnExactlyOwnedPendingKeycloakUserAndResendsActivation() throws Exception {
        UserAccountEntity account = account("account-2", "U2026EF56GH78", "pending@example.com");
        AccountExternalIdentityRepository identities = Mockito.mock(AccountExternalIdentityRepository.class);
        UserAccountRepository accounts = Mockito.mock(UserAccountRepository.class);
        when(identities.findByAccount_Id(account.getId())).thenReturn(java.util.Optional.empty());
        when(accounts.findById(account.getId())).thenReturn(java.util.Optional.of(account));

        AtomicInteger creates = new AtomicInteger();
        AtomicInteger activationEmails = new AtomicInteger();
        HttpServer server = server(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/protocol/openid-connect/token")) {
                respond(exchange, 200, "{\"access_token\":\"provisioner-token\"}");
            } else if (path.endsWith("/users") && "GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, "[{\"id\":\"pending-subject\"}]");
            } else if (path.endsWith("/users/pending-subject")) {
                respond(exchange, 200, """
                        {"id":"pending-subject","username":"u2026ef56gh78","enabled":true,
                         "email":"pending@example.com","emailVerified":false,
                         "requiredActions":["VERIFY_EMAIL","UPDATE_PASSWORD"],
                         "attributes":{"agentcici_public_id":["U2026EF56GH78"],"agentcici_account_id":["account-2"]}}
                        """);
            } else if (path.endsWith("/users") && "POST".equals(exchange.getRequestMethod())) {
                creates.incrementAndGet();
                respond(exchange, 500, "");
            } else if (path.endsWith("/users/pending-subject/execute-actions-email")) {
                assertThat(exchange.getRequestURI().getRawQuery())
                        .contains("redirect_uri=https%3A%2F%2Fx.agentcici.com%2Fapp")
                        .contains("lifespan=86400");
                activationEmails.incrementAndGet();
                respond(exchange, 204, "");
            } else {
                respond(exchange, 404, "");
            }
        });
        try {
            KeycloakIdentityProvisioningService.ProvisionResult result = provisioningService(
                    identities, accounts, issuer(server), true).ensureHumanIdentity(account);

            assertThat(result).isEqualTo(new KeycloakIdentityProvisioningService.ProvisionResult(false, true, "pending-subject"));
            assertThat(creates.get()).isZero();
            assertThat(activationEmails.get()).isEqualTo(1);
            verify(identities).saveAndFlush(any(AccountExternalIdentityEntity.class));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsAUsernameMatchWhoseOwnershipAttributesDoNotProveTheAccount() throws Exception {
        UserAccountEntity account = account("account-3", "U2026IJ90KL12", "conflict@example.com");
        AccountExternalIdentityRepository identities = Mockito.mock(AccountExternalIdentityRepository.class);
        UserAccountRepository accounts = Mockito.mock(UserAccountRepository.class);
        when(identities.findByAccount_Id(account.getId())).thenReturn(java.util.Optional.empty());
        when(accounts.findById(account.getId())).thenReturn(java.util.Optional.of(account));

        HttpServer server = server(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/protocol/openid-connect/token")) {
                respond(exchange, 200, "{\"access_token\":\"provisioner-token\"}");
            } else if (path.endsWith("/users") && "GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, "[{\"id\":\"conflicting-subject\"}]");
            } else if (path.endsWith("/users/conflicting-subject")) {
                respond(exchange, 200, """
                        {"id":"conflicting-subject","username":"u2026ij90kl12","enabled":true,
                         "email":"conflict@example.com","emailVerified":true,"requiredActions":[],
                         "attributes":{"agentcici_public_id":["U2026IJ90KL12"],"agentcici_account_id":["another-account"]}}
                        """);
            } else {
                respond(exchange, 404, "");
            }
        });
        try {
            assertThatThrownBy(() -> provisioningService(identities, accounts, issuer(server), true)
                    .ensureHumanIdentity(account))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Keycloak 用户创建或邀请发送失败")
                    .hasCauseInstanceOf(IllegalStateException.class);
            verify(identities, never()).saveAndFlush(any());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void keepsAnActivatedBoundUserActiveWithoutSendingAnotherPasswordSetupEmail() throws Exception {
        UserAccountEntity account = account("account-4", "U2026MN34OP56", "active@example.com");
        AccountExternalIdentityEntity identity = new AccountExternalIdentityEntity(
                account, "http://placeholder/realms/agentcici", "active-subject");
        AccountExternalIdentityRepository identities = Mockito.mock(AccountExternalIdentityRepository.class);
        UserAccountRepository accounts = Mockito.mock(UserAccountRepository.class);
        when(identities.findByAccount_Id(account.getId())).thenReturn(java.util.Optional.of(identity));
        when(accounts.findById(account.getId())).thenReturn(java.util.Optional.of(account));

        HttpServer server = server(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/protocol/openid-connect/token")) {
                respond(exchange, 200, "{\"access_token\":\"provisioner-token\"}");
            } else if (path.endsWith("/users/active-subject")) {
                respond(exchange, 200, """
                        {"id":"active-subject","username":"u2026mn34op56","enabled":true,
                         "email":"active@example.com","emailVerified":true,"requiredActions":[]}
                        """);
            } else {
                respond(exchange, 404, "");
            }
        });
        try {
            assertThat(provisioningService(identities, accounts, issuer(server), true).ensureHumanIdentity(account))
                    .isEqualTo(new KeycloakIdentityProvisioningService.ProvisionResult(true, false, "active-subject"));
            verify(identities, never()).saveAndFlush(any());
        } finally {
            server.stop(0);
        }
    }

    private KeycloakIdentityProvisioningService service(boolean humanEnabled, boolean machineEnabled, String redirectUri) {
        return new KeycloakIdentityProvisioningService(
                Mockito.mock(AccountExternalIdentityRepository.class),
                Mockito.mock(UserAccountRepository.class),
                new ObjectMapper(), humanEnabled, machineEnabled,
                "https://sso.agentcici.com/realms/agentcici", "agentcici-provisioner", "test-secret", redirectUri);
    }

    private KeycloakIdentityProvisioningService provisioningService(
            AccountExternalIdentityRepository identities,
            UserAccountRepository accounts,
            String issuer,
            boolean humanEnabled) {
        return new KeycloakIdentityProvisioningService(
                identities, accounts, new ObjectMapper(), humanEnabled, false,
                issuer, "agentcici-provisioner", "test-secret", "https://x.agentcici.com/app");
    }

    private static UserAccountEntity account(String id, String publicId, String email) throws Exception {
        UserAccountEntity account = new UserAccountEntity("13900000001");
        setField(account, "id", id);
        setField(account, "publicId", publicId);
        account.setEmail(email);
        return account;
    }

    private static void setField(Object target, String field, String value) throws Exception {
        java.lang.reflect.Field declared = target.getClass().getDeclaredField(field);
        declared.setAccessible(true);
        declared.set(target, value);
    }

    private static HttpServer server(ExchangeHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    private static String issuer(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/realms/agentcici";
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
