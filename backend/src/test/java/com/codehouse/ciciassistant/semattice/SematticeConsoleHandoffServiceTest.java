package com.codehouse.ciciassistant.semattice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.service.AuthService;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.codehouse.ciciassistant.common.error.UnauthorizedException;
import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SematticeConsoleHandoffServiceTest {
    @Test
    void persistsOnlyTheTicketDigest() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(any(String.class), any(), any(), any(), any())).thenReturn(1);
        when(jdbc.update(any(String.class))).thenReturn(0);
        SematticeConsoleHandoffService service = new SematticeConsoleHandoffService(
                jdbc, mock(AuthService.class), mock(OfficialAccessTokenService.class));

        SematticeConsoleHandoffService.HandoffTicket issued = service.issue("org00000000000000001", "member-test");

        assertThat(issued.ticket()).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(issued.expiresInSeconds()).isEqualTo(60);
        verify(jdbc).update(contains("INSERT INTO semattice_console_handoff"),
                org.mockito.ArgumentMatchers.argThat(value -> value instanceof String digest
                        && digest.length() == 64 && !digest.contains(issued.ticket())),
                eq("org00000000000000001"), eq("member-test"), any(Timestamp.class));
    }

    @Test
    void rejectsMalformedTicketsBeforeDatabaseOrTokenAccess() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuthService auth = mock(AuthService.class);
        OfficialAccessTokenService tokens = mock(OfficialAccessTokenService.class);
        SematticeConsoleHandoffService service = new SematticeConsoleHandoffService(jdbc, auth, tokens);

        assertThatThrownBy(() -> service.redeem("not-a-ticket"))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(jdbc, auth, tokens);
    }
}
