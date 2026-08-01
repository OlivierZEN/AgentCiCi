package com.codehouse.ciciassistant.auth.service;

import com.codehouse.ciciassistant.auth.domain.AccountExternalIdentityEntity;
import com.codehouse.ciciassistant.auth.domain.AccountExternalIdentityRepository;
import com.codehouse.ciciassistant.auth.domain.UserAccountEntity;
import com.codehouse.ciciassistant.auth.domain.UserAccountRepository;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Creates the Keycloak identity for a newly invited human account.  It is intentionally
 * server-to-server only: neither an administrator nor the browser sees the admin token,
 * Keycloak password, or a Keycloak client secret.
 */
@Service
public class KeycloakIdentityProvisioningService {

    private final AccountExternalIdentityRepository identityRepository;
    private final UserAccountRepository accountRepository;
    private final ObjectMapper objectMapper;
    private final boolean humanProvisioningEnabled;
    private final boolean machineProvisioningEnabled;
    private final String issuer;
    private final String adminClientId;
    private final String adminClientSecret;
    private final String invitationRedirectUri;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public KeycloakIdentityProvisioningService(AccountExternalIdentityRepository identityRepository,
                                               UserAccountRepository accountRepository,
                                               ObjectMapper objectMapper,
                                               @Value("${app.auth.oidc.provisioning.enabled:false}") boolean humanProvisioningEnabled,
                                               @Value("${app.auth.oidc.machine-provisioning.enabled:false}") boolean machineProvisioningEnabled,
                                               @Value("${app.auth.oidc.issuer:}") String issuer,
                                               @Value("${app.auth.oidc.provisioning.admin-client-id:}") String adminClientId,
                                               @Value("${app.auth.oidc.provisioning.admin-client-secret:}") String adminClientSecret,
                                               @Value("${app.auth.oidc.provisioning.invitation-redirect-uri:}") String invitationRedirectUri) {
        this.identityRepository = identityRepository;
        this.accountRepository = accountRepository;
        this.objectMapper = objectMapper;
        this.humanProvisioningEnabled = humanProvisioningEnabled;
        this.machineProvisioningEnabled = machineProvisioningEnabled;
        this.issuer = trimTrailingSlash(issuer);
        this.adminClientId = trim(adminClientId);
        this.adminClientSecret = trim(adminClientSecret);
        this.invitationRedirectUri = trim(invitationRedirectUri);
        if ((humanProvisioningEnabled || machineProvisioningEnabled)
                && (this.issuer.isBlank() || this.adminClientId.isBlank() || this.adminClientSecret.isBlank())) {
            throw new IllegalArgumentException("Keycloak provisioning configuration is incomplete");
        }
        if (humanProvisioningEnabled && this.invitationRedirectUri.isBlank()) {
            throw new IllegalArgumentException("Keycloak invitation provisioning redirect URI is incomplete");
        }
    }

    public ProvisionResult ensureHumanIdentity(UserAccountEntity account) {
        var existing = identityRepository.findByAccount_Id(account.getId());
        if (existing.isPresent()) {
            return new ProvisionResult(true, false, existing.get().getSubject());
        }
        if (!humanProvisioningEnabled) {
            // Compatibility mode is deliberately explicit and must not be used in production
            // once the Keycloak invitation client has been configured.
            return new ProvisionResult(false, false, "");
        }
        UserAccountEntity current = accountRepository.findById(account.getId())
                .orElseThrow(() -> new IllegalArgumentException("Global account not found"));
        String email = trim(current.getEmail());
        if (email.isBlank()) {
            throw new IllegalArgumentException("启用统一认证后，邀请成员必须提供邮箱");
        }
        String publicId = trim(current.getPublicId());
        if (publicId.isBlank()) {
            throw new IllegalStateException("Global account public ID is not available");
        }
        try {
            String adminToken = obtainAdminToken();
            String subject = findUserId(adminToken, publicId);
            if (subject == null) {
                subject = createUser(adminToken, current, publicId, email);
            }
            identityRepository.save(new AccountExternalIdentityEntity(current, issuer, subject));
            sendActivationEmail(adminToken, subject);
            return new ProvisionResult(false, true, subject);
        } catch (ForbiddenException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Keycloak 用户创建或邀请发送失败", ex);
        }
    }

    public boolean isEnabled() {
        return humanProvisioningEnabled;
    }

    public boolean isMachineProvisioningEnabled() {
        return machineProvisioningEnabled;
    }

    public String issuer() {
        return issuer;
    }

    /** Creates a confidential Keycloak client and returns its secret exactly once. */
    public ServiceClientCredentials createServiceClient(String clientId, String audience) {
        if (!machineProvisioningEnabled) {
            throw new IllegalStateException("统一身份机器账户开通尚未启用");
        }
        try {
            String token = obtainAdminToken();
            if (findClientInternalId(token, clientId) != null) {
                throw new IllegalArgumentException("Keycloak client ID 已存在");
            }
            Map<String, Object> body = Map.of(
                    "clientId", clientId,
                    "name", clientId,
                    "enabled", true,
                    "protocol", "openid-connect",
                    "publicClient", false,
                    "standardFlowEnabled", false,
                    "directAccessGrantsEnabled", false,
                    "serviceAccountsEnabled", true,
                    "clientAuthenticatorType", "client-secret",
                    "attributes", Map.of("oauth2.device.authorization.grant.enabled", "false",
                            "agentcici_token_audience", audience));
            HttpRequest request = adminRequest("/clients", token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 201) {
                throw new IllegalStateException("Keycloak 机器账户创建失败");
            }
            String internalId = findClientInternalId(token, clientId);
            if (internalId == null) {
                throw new IllegalStateException("Keycloak 机器账户创建后不可查询");
            }
            HttpRequest secretRequest = adminRequest("/clients/" + encode(internalId) + "/client-secret", token).POST(HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<String> secretResponse = httpClient.send(secretRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String secret = secretResponse.statusCode() == 200 ? objectMapper.readTree(secretResponse.body()).path("value").asText("") : "";
            if (secret.isBlank()) {
                throw new IllegalStateException("Keycloak 机器账户密钥生成失败");
            }
            HttpRequest serviceAccountRequest = adminRequest("/clients/" + encode(internalId) + "/service-account-user", token).GET().build();
            HttpResponse<String> serviceAccountResponse = httpClient.send(serviceAccountRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String subject = serviceAccountResponse.statusCode() == 200
                    ? trim(objectMapper.readTree(serviceAccountResponse.body()).path("id").asText()) : "";
            if (subject.isBlank()) {
                throw new IllegalStateException("Keycloak 机器账户主体读取失败");
            }
            return new ServiceClientCredentials(clientId, secret, subject);
        } catch (Exception ex) {
            if (ex instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalStateException("Keycloak 机器账户创建失败", ex);
        }
    }

    /** Rotates a confidential client's secret and returns the replacement exactly once. */
    public String rotateServiceClientSecret(String clientId) {
        requireMachineProvisioning();
        try {
            String token = obtainAdminToken();
            String internalId = requireServiceClient(token, clientId);
            HttpRequest request = adminRequest("/clients/" + encode(internalId) + "/client-secret", token)
                    .POST(HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String secret = response.statusCode() == 200
                    ? trim(objectMapper.readTree(response.body()).path("value").asText()) : "";
            if (secret.isBlank()) {
                throw new IllegalStateException("Keycloak 机器账户密钥轮换失败");
            }
            return secret;
        } catch (Exception ex) {
            if (ex instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalStateException("Keycloak 机器账户密钥轮换失败", ex);
        }
    }

    /** Removes a just-created client when the authoritative database transaction cannot be persisted. */
    public void deleteServiceClient(String clientId) {
        requireMachineProvisioning();
        try {
            String token = obtainAdminToken();
            String internalId = requireServiceClient(token, clientId);
            HttpRequest request = adminRequest("/clients/" + encode(internalId), token).DELETE().build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 204) {
                throw new IllegalStateException("Keycloak 机器账户补偿删除失败");
            }
        } catch (Exception ex) {
            if (ex instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalStateException("Keycloak 机器账户补偿删除失败", ex);
        }
    }

    /** Enables or disables a governed confidential client without exposing its credentials. */
    public void setServiceClientEnabled(String clientId, boolean enabled) {
        requireMachineProvisioning();
        try {
            String token = obtainAdminToken();
            String internalId = requireServiceClient(token, clientId);
            HttpRequest read = adminRequest("/clients/" + encode(internalId), token).GET().build();
            HttpResponse<String> readResponse = httpClient.send(read, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (readResponse.statusCode() != 200) {
                throw new IllegalStateException("Keycloak 机器账户读取失败");
            }
            JsonNode representation = objectMapper.readTree(readResponse.body());
            if (!(representation instanceof com.fasterxml.jackson.databind.node.ObjectNode object)) {
                throw new IllegalStateException("Keycloak 机器账户响应无效");
            }
            object.put("enabled", enabled);
            HttpRequest update = adminRequest("/clients/" + encode(internalId), token)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(object), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<Void> updateResponse = httpClient.send(update, HttpResponse.BodyHandlers.discarding());
            if (updateResponse.statusCode() != 204) {
                throw new IllegalStateException(enabled ? "Keycloak 机器账户恢复失败" : "Keycloak 机器账户暂停失败");
            }
        } catch (Exception ex) {
            if (ex instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalStateException("Keycloak 机器账户状态变更失败", ex);
        }
    }

    private void requireMachineProvisioning() {
        if (!machineProvisioningEnabled) {
            throw new IllegalStateException("统一身份机器账户开通尚未启用");
        }
    }

    private String requireServiceClient(String token, String clientId) throws Exception {
        String normalized = trim(clientId);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("clientId is required");
        }
        String internalId = findClientInternalId(token, normalized);
        if (internalId == null || internalId.isBlank()) {
            throw new IllegalArgumentException("Keycloak 机器账户不存在");
        }
        return internalId;
    }

    private String obtainAdminToken() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(issuer + "/protocol/openid-connect/token"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(Map.of(
                        "grant_type", "client_credentials",
                        "client_id", adminClientId,
                        "client_secret", adminClientSecret))))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new ForbiddenException("Keycloak 邀请服务凭据无效");
        }
        String token = objectMapper.readTree(response.body()).path("access_token").asText("");
        if (token.isBlank()) {
            throw new ForbiddenException("Keycloak 邀请服务未返回访问令牌");
        }
        return token;
    }

    private String findUserId(String token, String username) throws Exception {
        HttpRequest request = adminRequest("/users?username=" + encode(username) + "&exact=true", token).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Keycloak 用户查询失败");
        }
        JsonNode users = objectMapper.readTree(response.body());
        return users.isArray() && !users.isEmpty() ? trim(users.get(0).path("id").asText()) : null;
    }

    private String findClientInternalId(String token, String clientId) throws Exception {
        HttpRequest request = adminRequest("/clients?clientId=" + encode(clientId), token).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Keycloak client 查询失败");
        }
        JsonNode clients = objectMapper.readTree(response.body());
        return clients.isArray() && !clients.isEmpty() ? trim(clients.get(0).path("id").asText()) : null;
    }

    private String createUser(String token, UserAccountEntity account, String username, String email) throws Exception {
        Map<String, Object> body = Map.of(
                "username", username,
                "enabled", true,
                "email", email,
                "emailVerified", false,
                "requiredActions", List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"),
                "attributes", Map.of("agentcici_public_id", List.of(username), "agentcici_account_id", List.of(account.getId())));
        HttpRequest request = adminRequest("/users", token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 201) {
            throw new IllegalStateException("Keycloak 用户创建失败");
        }
        String location = response.headers().firstValue("Location").orElse("");
        String subject = location.substring(location.lastIndexOf('/') + 1);
        if (subject.isBlank() || subject.equals(location)) {
            throw new IllegalStateException("Keycloak 用户创建未返回 subject");
        }
        return subject;
    }

    private void sendActivationEmail(String token, String subject) throws Exception {
        String query = "?client_id=agentcici-bff&redirect_uri=" + encode(invitationRedirectUri) + "&lifespan=86400";
        HttpRequest request = adminRequest("/users/" + encode(subject) + "/execute-actions-email" + query, token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"))))
                .build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() != 204) {
            throw new IllegalStateException("Keycloak 激活邮件发送失败");
        }
    }

    private HttpRequest.Builder adminRequest(String path, String token) {
        return HttpRequest.newBuilder(URI.create(adminBaseUrl() + path))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token);
    }

    private String adminBaseUrl() {
        int marker = issuer.lastIndexOf("/realms/");
        if (marker < 0 || marker + 8 >= issuer.length()) {
            throw new IllegalStateException("Keycloak issuer must include /realms/{realm}");
        }
        return issuer.substring(0, marker) + "/admin/realms/" + issuer.substring(marker + 8);
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right).orElse("");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimTrailingSlash(String value) {
        String result = trim(value);
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    public record ProvisionResult(boolean alreadyBound, boolean activationRequired, String subject) {
    }

    public record ServiceClientCredentials(String clientId, String clientSecret, String subject) {
    }
}
