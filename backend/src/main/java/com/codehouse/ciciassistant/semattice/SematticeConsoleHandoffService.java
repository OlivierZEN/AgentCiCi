package com.codehouse.ciciassistant.semattice;

import com.codehouse.ciciassistant.auth.service.AuthService;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.codehouse.ciciassistant.common.error.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Server-side, single-use browser handoff. Raw tickets and OACTs are never persisted. */
@Service
public class SematticeConsoleHandoffService {
    private static final Duration TICKET_TTL = Duration.ofSeconds(60);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final AuthService authService;
    private final OfficialAccessTokenService tokenService;

    public SematticeConsoleHandoffService(JdbcTemplate jdbc,
                                          AuthService authService,
                                          OfficialAccessTokenService tokenService) {
        this.jdbc = jdbc;
        this.authService = authService;
        this.tokenService = tokenService;
    }

    @Transactional
    public HandoffTicket issue(String companyId, String memberId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(TICKET_TTL);
        jdbc.update("""
                INSERT INTO semattice_console_handoff(ticket_digest, company_id, company_member_id, expires_at)
                VALUES (?, ?, ?, ?)
                """, digest(ticket), companyId, memberId, java.sql.Timestamp.from(expiresAt));
        jdbc.update("DELETE FROM semattice_console_handoff WHERE expires_at < CURRENT_TIMESTAMP - INTERVAL '1 day'");
        return new HandoffTicket(ticket, TICKET_TTL.toSeconds());
    }

    @Transactional
    public ExchangedAccess redeem(String ticket) {
        if (ticket == null || !ticket.matches("^[A-Za-z0-9_-]{43}$")) {
            throw invalidTicket();
        }
        List<Subject> subjects = jdbc.query("""
                UPDATE semattice_console_handoff
                   SET consumed_at = CURRENT_TIMESTAMP
                 WHERE ticket_digest = ?
                   AND consumed_at IS NULL
                   AND expires_at > CURRENT_TIMESTAMP
                RETURNING company_id, company_member_id
                """, (result, row) -> new Subject(result.getString("company_id"),
                result.getString("company_member_id")), digest(ticket));
        if (subjects.size() != 1) {
            throw invalidTicket();
        }
        Subject subject = subjects.get(0);
        OfficialAccessTokenService.IssuedToken issued = authService.issueSematticeOfficialAccess(
                subject.companyId(), subject.memberId(), tokenService);
        return new ExchangedAccess(issued.token(), issued.expiresAt(), issued.tenantId(), issued.companyId());
    }

    private static String digest(String ticket) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(ticket.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static UnauthorizedException invalidTicket() {
        return new UnauthorizedException("Semattice 登录票据无效、已过期或已使用");
    }

    private record Subject(String companyId, String memberId) { }
    public record HandoffTicket(String ticket, long expiresInSeconds) { }
    public record ExchangedAccess(String accessToken, Instant expiresAt, String tenantId, String companyId) { }
}
