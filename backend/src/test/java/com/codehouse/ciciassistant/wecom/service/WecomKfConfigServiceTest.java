package com.codehouse.ciciassistant.wecom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.agent.service.AgentDefinitionService;
import com.codehouse.ciciassistant.common.crypto.SecretCipherService;
import com.codehouse.ciciassistant.wecom.domain.WecomKfAccountEntity;
import com.codehouse.ciciassistant.wecom.domain.WecomKfAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WecomKfConfigServiceTest {

    @Test
    void shouldEncryptAndPersistWecomKfAccountConfig() {
        WecomKfAccountRepository accountRepository = mock(WecomKfAccountRepository.class);
        AgentDefinitionService agentDefinitionService = mock(AgentDefinitionService.class);
        AgentDefinitionRepository agentDefinitionRepository = mock(AgentDefinitionRepository.class);
        SecretCipherService cipherService = new SecretCipherService("");
        WecomKfConfigService service = new WecomKfConfigService(
                accountRepository,
                cipherService,
                agentDefinitionService,
                agentDefinitionRepository);
        when(accountRepository.findByOrgIdAndOpenKfId("org-1", "wk-demo")).thenReturn(Optional.empty());
        when(agentDefinitionRepository.findByOrgIdAndAgentId("org-1", "after-sales-agent"))
                .thenReturn(Optional.of(new AgentDefinitionEntity(
                        "org-1",
                        "after-sales-agent",
                        "售后服务 Agent",
                        "",
                        "",
                        "gpt-4.1",
                        "",
                        "",
                        "BALANCED",
                        "COPILOT",
                        "v0.1",
                        null,
                        true,
                        true)));
        when(accountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WecomKfAccountEntity saved = service.upsert("org-1", "user-1", new WecomKfConfigService.UpsertCommand(
                "ww-demo",
                "wk-demo",
                "售后客服",
                "kf-secret",
                "callback-token",
                "abcdefghijklmnopqrstuvwxyzABCDEFG1234567890",
                "",
                "",
                true));

        assertThat(saved.getAgentId()).isEqualTo("after-sales-agent");
        assertThat(saved.getRunAsUserId()).isEqualTo("user-1");
        assertThat(saved.getSecretCipher()).isNotEqualTo("kf-secret");
        assertThat(cipherService.decryptUtf8(saved.getSecretCipher(), saved.getSecretIv())).isEqualTo("kf-secret");
        assertThat(service.toPayload(saved)).doesNotContainKeys("secret", "encodingAesKey");
        verify(agentDefinitionService).warmupBuiltinAgents("org-1");
    }
}
