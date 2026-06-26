package com.codehouse.ciciassistant.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.codehouse.ciciassistant.ai.domain.ChatSessionStateRepository;
import com.codehouse.ciciassistant.ai.service.ChatSessionStateService;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "spring.profiles.active=default")
class ChatSessionStateServiceIntegrationTest {

    private static final String SESSION_ID = "workbench:cici-system";

    @Autowired
    private ChatSessionStateService service;

    @Autowired
    private ChatSessionStateRepository repository;

    private String orgA;
    private String orgB;

    @AfterEach
    void cleanUp() {
        if (orgA != null) {
            repository.deleteBySessionIdAndOrgId(SESSION_ID, orgA);
        }
        if (orgB != null) {
            repository.deleteBySessionIdAndOrgId(SESSION_ID, orgB);
        }
    }

    @Test
    void storesSameWorkbenchSessionIdSeparatelyForEachOrganization() {
        orgA = "org-a-" + UUID.randomUUID().toString().substring(0, 8);
        orgB = "org-b-" + UUID.randomUUID().toString().substring(0, 8);

        service.mergeUserTurn(orgA, SESSION_ID, "cici-system", "继续看今天的客户邮件");
        service.mergeUserTurn(orgB, SESSION_ID, "cici-system", "帮我看下今天的邮件");
        service.mergeUserTurn(orgB, SESSION_ID, "cici-system", "继续");

        var stateA = repository.findBySessionIdAndOrgId(SESSION_ID, orgA);
        var stateB = repository.findBySessionIdAndOrgId(SESSION_ID, orgB);

        assertThat(stateA).isPresent();
        assertThat(stateB).isPresent();
        assertThat(stateA.get().getOrgId()).isEqualTo(orgA);
        assertThat(stateB.get().getOrgId()).isEqualTo(orgB);
        assertThat(stateA.get().getVersion()).isEqualTo(0L);
        assertThat(stateB.get().getVersion()).isGreaterThanOrEqualTo(1L);
    }
}
