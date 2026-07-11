package com.codehouse.ciciassistant.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "spring.profiles.active=default")
class CustomerSignalRepositoryIntegrationTest {

    @Autowired
    private CustomerSignalRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void upsertStartsItsOwnTransactionAndUpdatesTheStableSignal() {
        String publicId = "sig-test-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        Instant now = Instant.now();
        try {
            repository.upsertSignal(publicId, "demo-org", "account-1", "NEW", "NEXT_STEP_GAP",
                    "初始标题", "初始详情", "MEDIUM", "[]", now, now);
            repository.upsertSignal(publicId, "demo-org", "account-1", "NEW", "NEXT_STEP_GAP",
                    "更新标题", "更新详情", "HIGH", "[\"task-1\"]", now.plusSeconds(1), now.plusSeconds(1));

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM customer_signal WHERE public_id = ?", Long.class, publicId))
                    .isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT title FROM customer_signal WHERE public_id = ?", String.class, publicId))
                    .isEqualTo("更新标题");
        } finally {
            jdbcTemplate.update("DELETE FROM customer_signal WHERE public_id = ?", publicId);
        }
    }
}
