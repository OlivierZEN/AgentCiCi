package com.codehouse.ciciassistant.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.codehouse.ciciassistant.auth.domain.AccountExternalIdentityRepository;
import com.codehouse.ciciassistant.auth.service.AuthService;
import com.codehouse.ciciassistant.auth.service.KeycloakOidcLoginService;
import com.codehouse.ciciassistant.auth.service.OidcLoginStateStore;
import com.codehouse.ciciassistant.common.error.UnauthorizedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KeycloakOidcLoginServiceTest {

    @Test
    void canonicalizesNonCallbackHostsBeforeCreatingOidcState() {
        KeycloakOidcLoginService service = service();

        assertThat(service.isCanonicalStartHost("agentcici.com")).isFalse();
        assertThat(service.isCanonicalStartHost("x.agentcici.com")).isTrue();
        assertThat(service.canonicalStartUri("/admin/ops?tab=access").toString())
                .isEqualTo("https://x.agentcici.com/auth/oidc/login?return_to=%2Fadmin%2Fops%3Ftab%3Daccess");
    }

    @Test
    void rejectsLookalikeOrMalformedStartHosts() {
        KeycloakOidcLoginService service = service();

        assertThat(service.isCanonicalStartHost("x.agentcici.com.evil.example")).isFalse();
        assertThat(service.isCanonicalStartHost("x.agentcici.com:444")).isFalse();
        assertThat(service.isCanonicalStartHost("not a host")).isFalse();
    }

    @Test
    void retainsFailClosedStateComparisonAtCallback() {
        KeycloakOidcLoginService service = service(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.complete("authorization-code", "expected-state", "other-state"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid OIDC login state");
    }

    @Test
    void validatesKeycloakClientCredentialsTokenAgainstJwksAndAzp() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        String keyId = "keycloak-test-key";
        String jwks = new ObjectMapper().writeValueAsString(Map.of("keys", List.of(Map.of(
                "kid", keyId, "kty", "RSA", "alg", "RS256",
                "n", unsignedBase64(publicKey.getModulus()),
                "e", unsignedBase64(publicKey.getPublicExponent())))));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/realms/agentcici/protocol/openid-connect/certs", exchange -> {
            byte[] body = jwks.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String issuer = "http://127.0.0.1:" + server.getAddress().getPort() + "/realms/agentcici";
            String token = Jwts.builder().header().keyId(keyId).and()
                    .issuer(issuer).subject("service-account-agentcici-data-sync")
                    .claim("azp", "agentcici-data-sync")
                    .issuedAt(java.util.Date.from(Instant.now()))
                    .expiration(java.util.Date.from(Instant.now().plusSeconds(60)))
                    .signWith(keyPair.getPrivate(), Jwts.SIG.RS256).compact();
            KeycloakOidcLoginService service = new KeycloakOidcLoginService(
                    org.mockito.Mockito.mock(AccountExternalIdentityRepository.class),
                    org.mockito.Mockito.mock(AuthService.class),
                    org.mockito.Mockito.mock(OidcLoginStateStore.class),
                    new ObjectMapper(), true, issuer, "agentcici-bff", "test-client-secret",
                    "https://x.agentcici.com/auth/oidc/callback");

            assertThat(service.verifyServiceAccessToken(token))
                    .isEqualTo(new KeycloakOidcLoginService.ServiceAccessToken(
                            "service-account-agentcici-data-sync", "agentcici-data-sync"));
            assertThatThrownBy(() -> service.verifyServiceAccessToken("not.a.valid.jwt"))
                    .isInstanceOf(UnauthorizedException.class);
        } finally {
            server.stop(0);
        }
    }

    private KeycloakOidcLoginService service() {
        return service(false);
    }

    private KeycloakOidcLoginService service(boolean enabled) {
        return new KeycloakOidcLoginService(
                org.mockito.Mockito.mock(AccountExternalIdentityRepository.class),
                org.mockito.Mockito.mock(AuthService.class),
                org.mockito.Mockito.mock(OidcLoginStateStore.class),
                new ObjectMapper(),
                enabled,
                "https://sso.agentcici.com/realms/agentcici",
                "agentcici-bff",
                "test-client-secret",
                "https://x.agentcici.com/auth/oidc/callback");
    }

    private static String unsignedBase64(BigInteger value) {
        byte[] bytes = value.toByteArray();
        int offset = bytes.length > 1 && bytes[0] == 0 ? 1 : 0;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(java.util.Arrays.copyOfRange(bytes, offset, bytes.length));
    }
}
