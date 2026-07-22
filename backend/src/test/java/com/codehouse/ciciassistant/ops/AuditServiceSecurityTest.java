package com.codehouse.ciciassistant.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.codehouse.ciciassistant.ops.domain.AuditLogEntity;
import com.codehouse.ciciassistant.ops.domain.AuditLogRepository;
import com.codehouse.ciciassistant.ops.service.AuditService;
import com.codehouse.ciciassistant.security.service.SecurityRedactionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditServiceSecurityTest {

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final AuditService service = new AuditService(repository, new SecurityRedactionService());

    @Test
    void redactsSensitiveDetailBeforePersistingAuditLog() {
        service.log("org-a", "user-a", "tool.call",
                "call with mobile=13812345678 and apiKey=sk-test-abcdef1234567890");

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDetail())
                .contains("138****5678")
                .contains("apiKey=[redacted]")
                .doesNotContain("13812345678")
                .doesNotContain("sk-test-abcdef1234567890");
    }
}
