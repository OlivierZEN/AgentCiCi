package com.codehouse.ciciassistant.auth.service;

import com.codehouse.ciciassistant.auth.domain.AccountExternalIdentityEntity;
import com.codehouse.ciciassistant.auth.domain.AccountExternalIdentityRepository;
import com.codehouse.ciciassistant.common.error.UnauthorizedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * AgentCiCi's OIDC BFF entrypoint. Passwords and Keycloak refresh tokens never reach the browser.
 */
@Service
public class KeycloakOidcLoginService {

    public static final String STATE_COOKIE = "CICI_OIDC_STATE";
    private static final Duration TRANSACTION_TTL = Duration.ofMinutes(5);
    private static final Duration COMPLETION_TTL = Duration.ofMinutes(1);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountExternalIdentityRepository identityRepository;
    private final AuthService authService;
    private final OidcLoginStateStore stateStore;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String issuer;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public KeycloakOidcLoginService(AccountExternalIdentityRepository identityRepository,
                                    AuthService authService,
                                    OidcLoginStateStore stateStore,
                                    ObjectMapper objectMapper,
                                    @Value("${app.auth.oidc.enabled:false}") boolean enabled,
                                    @Value("${app.auth.oidc.issuer:}") String issuer,
                                    @Value("${app.auth.oidc.client-id:agentcici-bff}") String clientId,
                                    @Value("${app.auth.oidc.client-secret:}") String clientSecret,
                                    @Value("${app.auth.oidc.redirect-uri:}") String redirectUri) {
        this.identityRepository = identityRepository;
        this.authService = authService;
        this.stateStore = stateStore;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.issuer = trimTrailingSlash(issuer);
        this.clientId = trim(clientId);
        this.clientSecret = trim(clientSecret);
        this.redirectUri = trim(redirectUri);
        if (enabled && (this.issuer.isBlank() || this.clientId.isBlank() || this.clientSecret.isBlank() || this.redirectUri.isBlank())) {
            throw new IllegalArgumentException("OIDC issuer, client ID, client secret and redirect URI are required when enabled");
        }
    }

    public LoginStart start(String requestedReturnTo) {
        requireEnabled();
        String state = randomUrlValue();
        String nonce = randomUrlValue();
        String verifier = randomUrlValue();
        String returnTo = safeReturnTo(requestedReturnTo);
        stateStore.saveTransaction(state, new OidcLoginStateStore.LoginTransaction(nonce, verifier, returnTo), TRANSACTION_TTL);
        Map<String, String> query = new LinkedHashMap<>();
        query.put("client_id", clientId);
        query.put("redirect_uri", redirectUri);
        query.put("response_type", "code");
        query.put("scope", "openid");
        query.put("state", state);
        query.put("nonce", nonce);
        query.put("code_challenge", sha256Url(verifier));
        query.put("code_challenge_method", "S256");
        return new LoginStart(URI.create(issuer + "/protocol/openid-connect/auth?" + queryString(query)), state);
    }

    public URI complete(String code, String state, String stateCookie) {
        requireEnabled();
        if (!hasText(code) || !hasText(state) || !MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8), blank(stateCookie).getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException("Invalid OIDC login state");
        }
        OidcLoginStateStore.LoginTransaction transaction = stateStore.consumeTransaction(state);
        if (transaction == null) {
            throw new UnauthorizedException("OIDC login transaction expired");
        }
        TokenResponse tokens = exchangeCode(code, transaction.pkceVerifier());
        Claims claims = verifyIdToken(tokens.idToken(), transaction.nonce());
        String subject = trim(claims.getSubject());
        AccountExternalIdentityEntity identity = identityRepository.findByIssuerAndSubject(issuer, subject)
                .orElseThrow(() -> new UnauthorizedException("统一身份尚未绑定 AgentCiCi 账号"));
        String sessionId = UUID.randomUUID().toString();
        long refreshSeconds = Math.max(60, tokens.refreshExpiresIn());
        stateStore.saveRefreshSession(sessionId, tokens.refreshToken(), Duration.ofSeconds(refreshSeconds));
        Map<String, Object> login = authService.loginByExternalIdentityAccount(identity.getAccount().getId(), sessionId);
        String ticket = randomUrlValue();
        stateStore.saveCompletion(ticket, new OidcLoginStateStore.LoginCompletion(login), COMPLETION_TTL);
        return URI.create(appendQuery(transaction.returnTo(), "oidc_ticket", ticket));
    }

    public Map<String, Object> consumeCompletion(String ticket) {
        if (!hasText(ticket)) {
            throw new UnauthorizedException("OIDC login ticket is required");
        }
        OidcLoginStateStore.LoginCompletion completion = stateStore.consumeCompletion(ticket);
        if (completion == null) {
            throw new UnauthorizedException("OIDC login ticket expired");
        }
        return completion.login();
    }

    private TokenResponse exchangeCode(String code, String verifier) {
        try {
            Map<String, String> form = Map.of(
                    "grant_type", "authorization_code",
                    "code", code,
                    "redirect_uri", redirectUri,
                    "client_id", clientId,
                    "client_secret", clientSecret,
                    "code_verifier", verifier);
            HttpRequest request = HttpRequest.newBuilder(URI.create(issuer + "/protocol/openid-connect/token"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(queryString(form)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new UnauthorizedException("Keycloak authorization code exchange failed");
            }
            JsonNode body = objectMapper.readTree(response.body());
            String idToken = body.path("id_token").asText("");
            String refreshToken = body.path("refresh_token").asText("");
            int refreshExpiresIn = body.path("refresh_expires_in").asInt(0);
            if (idToken.isBlank() || refreshToken.isBlank()) {
                throw new UnauthorizedException("Keycloak response is incomplete");
            }
            return new TokenResponse(idToken, refreshToken, refreshExpiresIn);
        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new UnauthorizedException("Keycloak authorization code exchange failed");
        }
    }

    private Claims verifyIdToken(String token, String expectedNonce) {
        try {
            String kid = objectMapper.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[0])).path("kid").asText("");
            String alg = objectMapper.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[0])).path("alg").asText("");
            if (!"RS256".equals(alg) || kid.isBlank()) {
                throw new UnauthorizedException("Keycloak ID token algorithm is invalid");
            }
            PublicKey key = resolveJwk(kid);
            Claims claims = Jwts.parser().verifyWith((RSAPublicKey) key).build().parseSignedClaims(token).getPayload();
            if (!issuer.equals(trimTrailingSlash(claims.getIssuer())) || !claims.getAudience().contains(clientId)
                    || !MessageDigest.isEqual(expectedNonce.getBytes(StandardCharsets.UTF_8), blank(claims.get("nonce", String.class)).getBytes(StandardCharsets.UTF_8))
                    || !hasText(claims.getSubject())) {
                throw new UnauthorizedException("Keycloak ID token claims are invalid");
            }
            return claims;
        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new UnauthorizedException("Keycloak ID token verification failed");
        }
    }

    private PublicKey resolveJwk(String kid) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(issuer + "/protocol/openid-connect/certs"))
                .timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new UnauthorizedException("Keycloak JWKS is unavailable");
        }
        // Keycloak returns a JWKS object; parse through its keys array to reject non-RSA material.
        JsonNode jwks = objectMapper.readTree(response.body());
        for (JsonNode candidate : jwks.path("keys")) {
            if (kid.equals(candidate.path("kid").asText()) && "RSA".equals(candidate.path("kty").asText())
                    && "RS256".equals(candidate.path("alg").asText("RS256"))) {
                BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(candidate.path("n").asText()));
                BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(candidate.path("e").asText()));
                return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
            }
        }
        throw new UnauthorizedException("Keycloak signing key is not trusted");
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new UnauthorizedException("统一登录尚未启用");
        }
    }

    private static String safeReturnTo(String value) {
        String path = trim(value);
        return path.startsWith("/") && !path.startsWith("//") ? path : "/";
    }

    private static String appendQuery(String path, String key, String value) {
        return path + (path.contains("?") ? "&" : "?") + encode(key) + "=" + encode(value);
    }

    private static String queryString(Map<String, String> values) {
        return values.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String randomUrlValue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Url(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create PKCE challenge", ex);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimTrailingSlash(String value) {
        String out = trim(value);
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    public record LoginStart(URI redirectUri, String state) {
    }

    private record TokenResponse(String idToken, String refreshToken, int refreshExpiresIn) {
    }
}
