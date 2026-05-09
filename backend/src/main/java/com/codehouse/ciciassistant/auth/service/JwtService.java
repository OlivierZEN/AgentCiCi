package com.codehouse.ciciassistant.auth.service;

import com.codehouse.ciciassistant.auth.domain.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
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
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expirationSeconds);
        return Jwts.builder()
                .subject(user.getId())
                .claim("org_id", user.getOrg().getId())
                .claim("member_id", user.getId())
                .claim("account_id", user.getAccountId())
                .claim("roles", roles == null ? List.of(user.getRoleCode()) : roles)
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
