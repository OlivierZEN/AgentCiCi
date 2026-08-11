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
        if (humanProvisioningEnabled && !isInvitationLandingUri(this.invitationRedirectUri)) {
            throw new IllegalArgumentException("Keycloak invitation redirect URI must target /app");
        }
    }

    public ProvisionResult ensureHumanIdentity(UserAccountEntity account) {
        var existing = identityRepository.findByAccount_Id(account.getId());
        if (!humanProvisioningEnabled) {
            // Compatibility mode is deliberately explicit and must not be used in production
            // once the Keycloak invitation client has been configured.
            return existing.map(identity -> new ProvisionResult(true, false, identity.getSubject()))
                    .orElseGet(() -> new ProvisionResult(false, false, ""));
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
            if (existing.isPresent()) {
                KeycloakUser boundUser = readUser(adminToken, existing.get().getSubject());
                if (boundUser != null) {
                    boolean activationRequired = requiresActivation(boundUser);
                    if (activationRequired) {
                        requireMatchingEmail(boundUser, email);
                        sendActivationEmail(adminToken, boundUser.subject());
                    }
                    return new ProvisionResult(true, activationRequired, boundUser.subject());
                }
            }

            // A missing local binding, or a binding whose remote subject has been
            // deleted, can only be recovered through the immutable public ID and
            // both ownership attributes.  A username or email match alone is not
            // sufficient evidence to attach a Keycloak user to an AgentCiCi account.
            KeycloakUser recoveredUser = findUser(adminToken, publicId);
            boolean created = recoveredUser == null;
            if (created) {
                String subject = createUser(adminToken, current, publicId, email);
                recoveredUser = new KeycloakUser(subject, null);
            } else {
                recoveredUser = readUser(adminToken, recoveredUser.subject());
                requireRecoveredUserOwnership(recoveredUser, current, publicId, email);
            }

            if (existing.isPresent()) {
                existing.get().rebind(issuer, recoveredUser.subject());
                identityRepository.saveAndFlush(existing.get());
            } else {
                identityRepository.saveAndFlush(new AccountExternalIdentityEntity(current, issuer, recoveredUser.subject()));
            }

            boolean activationRequired = created || requiresActivation(recoveredUser);
            if (activationRequired) {
                sendActivationEmail(adminToken, recoveredUser.subject());
            }
            return new ProvisionResult(existing.isPresent(), activationRequired, recoveredUser.subject());
        } catch (ForbiddenException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Keycloak 用户创建或邀请发送失败", ex);
        }
    }

    /**
     * Re-sends the initial human-account actions when activation is still
     * required, or returns the already-active remote state without sending a
     * password-reset message. The caller owns the governed membership sync.
     */
    public ProvisionResult resendHumanActivation(UserAccountEntity account) {
        if (!humanProvisioningEnabled) {
            throw new IllegalStateException("统一身份邀请开通尚未启用");
        }
        return ensureHumanIdentity(account);
    }

    /**
     * Verifies that an already-bound HUMAN identity can immediately complete an
     * OIDC login.  This is intentionally read-only and is used by governed
     * tenant-owner recovery: it never creates a user, sends an activation mail,
     * or changes credentials.
     */
    public ActiveHumanIdentity requireActiveHumanIdentity(UserAccountEntity account) {
        if (!humanProvisioningEnabled) {
            throw new IllegalStateException("统一身份邀请开通尚未启用");
        }
        AccountExternalIdentityEntity identity = identityRepository.findByAccount_Id(account.getId())
                .orElseThrow(() -> new IllegalStateException("目标账号尚未绑定统一身份"));
        if (!issuer.equals(trimTrailingSlash(identity.getIssuer()))) {
            throw new IllegalStateException("目标账号统一身份签发方不匹配");
        }
        try {
            KeycloakUser user = readUser(obtainAdminToken(), identity.getSubject());
            if (user == null) {
                throw new IllegalStateException("目标账号统一身份不存在");
            }
            if (requiresActivation(user)) {
                throw new IllegalStateException("目标账号尚未完成统一身份激活");
            }
            return new ActiveHumanIdentity(identity.getSubject());
        } catch (Exception ex) {
            if (ex instanceof IllegalStateException illegalStateException) {
                throw illegalStateException;
            }
            throw new IllegalStateException("目标账号统一身份状态校验失败", ex);
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

    /** Renames a governed confidential client without rotating its service account or secret. */
    public void renameServiceClient(String clientId, String replacementClientId) {
        requireMachineProvisioning();
        String current = trim(clientId);
        String replacement = trim(replacementClientId);
        if (current.isBlank() || replacement.isBlank()) {
            throw new IllegalArgumentException("clientId is required");
        }
        if (current.equals(replacement)) {
            return;
        }
        try {
            String token = obtainAdminToken();
            String internalId = requireServiceClient(token, current);
            if (findClientInternalId(token, replacement) != null) {
                throw new IllegalArgumentException("Keycloak client ID 已存在");
            }
            HttpRequest read = adminRequest("/clients/" + encode(internalId), token).GET().build();
            HttpResponse<String> readResponse = httpClient.send(read, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (readResponse.statusCode() != 200) {
                throw new IllegalStateException("Keycloak 机器账户读取失败");
            }
            JsonNode representation = objectMapper.readTree(readResponse.body());
            if (!(representation instanceof com.fasterxml.jackson.databind.node.ObjectNode object)) {
                throw new IllegalStateException("Keycloak 机器账户响应无效");
            }
            object.put("clientId", replacement);
            if (current.equals(trim(object.path("name").asText()))) {
                object.put("name", replacement);
            }
            HttpRequest update = adminRequest("/clients/" + encode(internalId), token)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(object), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<Void> updateResponse = httpClient.send(update, HttpResponse.BodyHandlers.discarding());
            if (updateResponse.statusCode() != 204) {
                throw new IllegalStateException("Keycloak 机器账户 Client ID 更新失败");
            }
            if (!internalId.equals(findClientInternalId(token, replacement))) {
                throw new IllegalStateException("Keycloak 机器账户 Client ID 更新后不可验证");
            }
        } catch (Exception ex) {
            if (ex instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalStateException("Keycloak 机器账户 Client ID 更新失败", ex);
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

    private KeycloakUser findUser(String token, String username) throws Exception {
        HttpRequest request = adminRequest("/users?username=" + encode(username) + "&exact=true", token).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Keycloak 用户查询失败");
        }
        JsonNode users = objectMapper.readTree(response.body());
        if (!users.isArray() || users.isEmpty()) {
            return null;
        }
        JsonNode user = users.get(0);
        String subject = trim(user.path("id").asText());
        if (subject.isBlank()) {
            throw new IllegalStateException("Keycloak 用户查询响应无 subject");
        }
        return new KeycloakUser(subject, user);
    }

    private KeycloakUser readUser(String token, String subject) throws Exception {
        HttpRequest request = adminRequest("/users/" + encode(subject), token).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Keycloak 用户读取失败");
        }
        JsonNode user = objectMapper.readTree(response.body());
        String returnedSubject = trim(user.path("id").asText());
        if (returnedSubject.isBlank() || !returnedSubject.equals(subject)) {
            throw new IllegalStateException("Keycloak 用户读取响应无效");
        }
        return new KeycloakUser(returnedSubject, user);
    }

    private void requireRecoveredUserOwnership(
            KeycloakUser user,
            UserAccountEntity account,
            String publicId,
            String email) {
        JsonNode representation = requireRepresentation(user);
        if (!publicId.equalsIgnoreCase(trim(representation.path("username").asText()))) {
            throw new IllegalStateException("Keycloak 用户名与全局账号不匹配");
        }
        if (!attributeContains(representation, "agentcici_public_id", publicId)
                || !attributeContains(representation, "agentcici_account_id", account.getId())) {
            throw new IllegalStateException("Keycloak 用户归属无法安全确认");
        }
        requireMatchingEmail(user, email);
    }

    private boolean requiresActivation(KeycloakUser user) {
        JsonNode representation = requireRepresentation(user);
        if (!representation.path("enabled").asBoolean(false)) {
            throw new IllegalStateException("Keycloak 用户已停用，不能作为邀请身份使用");
        }
        if (!representation.path("emailVerified").asBoolean(false)) {
            return true;
        }
        JsonNode requiredActions = representation.path("requiredActions");
        if (!requiredActions.isArray()) {
            return false;
        }
        for (JsonNode action : requiredActions) {
            String value = trim(action.asText());
            if ("VERIFY_EMAIL".equals(value) || "UPDATE_PASSWORD".equals(value)) {
                return true;
            }
        }
        return false;
    }

    private void requireMatchingEmail(KeycloakUser user, String expectedEmail) {
        JsonNode representation = requireRepresentation(user);
        String actualEmail = trim(representation.path("email").asText());
        if (actualEmail.isBlank() || !actualEmail.equalsIgnoreCase(expectedEmail)) {
            throw new IllegalStateException("Keycloak 用户邮箱与全局账号不匹配");
        }
    }

    private static boolean attributeContains(JsonNode representation, String attributeName, String expectedValue) {
        JsonNode values = representation.path("attributes").path(attributeName);
        if (values.isArray()) {
            for (JsonNode value : values) {
                if (expectedValue.equals(trim(value.asText()))) {
                    return true;
                }
            }
        }
        return expectedValue.equals(trim(values.asText()));
    }

    private static JsonNode requireRepresentation(KeycloakUser user) {
        if (user.representation() == null || user.representation().isMissingNode()) {
            throw new IllegalStateException("Keycloak 用户读取响应无效");
        }
        return user.representation();
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

    private static boolean isInvitationLandingUri(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getUserInfo() == null
                    && uri.getHost() != null
                    && "/app".equals(uri.getPath())
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public record ProvisionResult(boolean alreadyBound, boolean activationRequired, String subject) {
    }

    public record ActiveHumanIdentity(String subject) {
    }

    private record KeycloakUser(String subject, JsonNode representation) {
    }

    public record ServiceClientCredentials(String clientId, String clientSecret, String subject) {
    }
}
