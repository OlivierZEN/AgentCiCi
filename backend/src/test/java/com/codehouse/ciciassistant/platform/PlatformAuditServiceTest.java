package com.codehouse.ciciassistant.platform;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.platform.domain.PlatformAuditLogRepository;
import com.codehouse.ciciassistant.platform.service.PlatformAuditService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class PlatformAuditServiceTest {

    private final PlatformAuditLogRepository repository = org.mockito.Mockito.mock(PlatformAuditLogRepository.class);
    private final PlatformAuditService service = new PlatformAuditService(repository);

    @Test
    void queryWithoutKeywordUsesFilterQueryWithoutLikeParameter() {
        when(repository.filterPlatformAuditLogs(eq("demo-org"), any(Instant.class), any(Instant.class),
                any(), any(), any(Pageable.class))).thenReturn(List.of());

        service.query("demo-org", new PlatformAuditService.PlatformAuditLogQuery(
                null, null, null, null, null, 100));

        verify(repository).filterPlatformAuditLogs(eq("demo-org"), any(Instant.class), any(Instant.class),
                any(), any(), any(Pageable.class));
        verify(repository, never()).searchPlatformAuditLogs(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void queryWithKeywordUsesSearchQuery() {
        when(repository.searchPlatformAuditLogs(eq("demo-org"), any(Instant.class), any(Instant.class),
                any(), any(), eq("model"), any(Pageable.class))).thenReturn(List.of());

        service.query("demo-org", new PlatformAuditService.PlatformAuditLogQuery(
                null, null, null, null, " model ", 100));

        verify(repository).searchPlatformAuditLogs(eq("demo-org"), any(Instant.class), any(Instant.class),
                any(), any(), eq("model"), any(Pageable.class));
        verify(repository, never()).filterPlatformAuditLogs(any(), any(), any(), any(), any(), any());
    }
}
