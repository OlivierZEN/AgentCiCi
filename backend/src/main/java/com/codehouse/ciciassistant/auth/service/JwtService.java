package com.codehouse.ciciassistant.auth.service;

import com.codehouse.ciciassistant.auth.domain.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationSeconds;

    public JwtService(@Value("${app.auth.jwt-secret}") String secret,
                      @Value("${app.auth.jwt-expiration-seconds:7200}") long expirationSeconds) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    public String issueToken(UserEntity user) {
        return issueToken(user, List.of(user.getRoleCode()));
    }

    public String issueToken(UserEntity user, List<String> roles) {
        return issueToken(user, roles, Map.of());
    }

    public String issueToken(UserEntity user, List<String> roles, Map<String, Object> additionalClaims) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expirationSeconds);
        Map<String, Object> claims = new java.util.LinkedHashMap<>();
        claims.put("company_id", user.getCompany().getId());
        claims.put("member_id", user.getId());
        claims.put("account_id", user.getAccountId());
        claims.put("roles", roles == null ? List.of(user.getRoleCode()) : roles);
        if (additionalClaims != null) {
            additionalClaims.forEach((key, value) -> {
                if (key != null && value != null && !claims.containsKey(key)) {
                    claims.put(key, value);
                }
            });
        }
        return Jwts.builder()
                .subject(user.getId())
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey)
                .compact();
    }

    public String issuePlatformToken(String platformAccountId, List<String> roles) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expirationSeconds);
        return Jwts.builder()
                .subject(platformAccountId)
                .claim("typ", "platform")
                .claim("platform_account_id", platformAccountId)
                .claim("roles", roles == null ? List.of() : roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey)
                .compact();
    }

    public String issueToken(String subject, Map<String, Object> claims, long ttlSeconds) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds);
        return Jwts.builder()
                .subject(subject)
                .claims(claims == null ? Map.of() : claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
