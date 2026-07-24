package com.codehouse.ciciassistant.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.common.error.ResourceNotFoundException;
import com.codehouse.ciciassistant.security.domain.SecurityDetectionEventRepository;
import com.codehouse.ciciassistant.security.domain.SecurityRuleEntity;
import com.codehouse.ciciassistant.security.domain.SecurityRuleRepository;
import com.codehouse.ciciassistant.security.service.SafetyGatewayService;
import com.codehouse.ciciassistant.security.service.SecurityRedactionService;
import com.codehouse.ciciassistant.security.service.SecurityRulesService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SecurityRulesServiceTest {

    private final SecurityRuleRepository ruleRepository = mock(SecurityRuleRepository.class);
    private final SecurityDetectionEventRepository eventRepository = mock(SecurityDetectionEventRepository.class);
    private final SafetyGatewayService gateway = new SafetyGatewayService(
            new SecurityRedactionService(), ruleRepository, eventRepository);
    private final SecurityRulesService service = new SecurityRulesService(ruleRepository, eventRepository, gateway);

    @Test
    void rejectsInvalidRegexRuleBeforeSaving() {
        SecurityRulesService.RuleCommand command = new SecurityRulesService.RuleCommand(
                "错误正则", "SENSITIVE_WORD", "SECRET", "REGEX", "([", "HIGH", "BLOCK", true, "");

        assertThatThrownBy(() -> service.createRule("org-a", command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("正则");
    }

    @Test
    void createsEnabledRuleWithNormalizedFields() {
        when(ruleRepository.save(any(SecurityRuleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SecurityRulesService.RuleCommand command = new SecurityRulesService.RuleCommand(
                "  黑名单词  ", "sensitive_word", "secret", "keyword", "违规词", "high", "block", true, "说明");

        SecurityRulesService.RuleView view = service.createRule("org-a", command);

        assertThat(view.name()).isEqualTo("黑名单词");
        assertThat(view.ruleType()).isEqualTo("SENSITIVE_WORD");
        assertThat(view.category()).isEqualTo("SECRET");
        assertThat(view.action()).isEqualTo("BLOCK");
    }

    @Test
    void testsDraftRuleWithoutPersistingIt() {
        SecurityRulesService.RuleCommand command = new SecurityRulesService.RuleCommand(
                "黑名单词", "SENSITIVE_WORD", "BUSINESS_COMPLIANCE", "KEYWORD", "违规词", "MEDIUM", "REVIEW", true, "");
        when(ruleRepository.findByCompanyIdAndEnabledTrueOrderByUpdatedAtDescIdDesc("org-a")).thenReturn(List.of());

        SecurityRulesService.TestResult result = service.testRule("org-a", "文本有违规词", command);

        assertThat(result.action()).isEqualTo("REVIEW");
        assertThat(result.findings())
                .extracting(SafetyGatewayService.SecurityFinding::ruleName)
                .contains("黑名单词");
    }

    @Test
    void updatingMissingRuleFails() {
        when(ruleRepository.findByIdAndCompanyId(404L, "org-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRule("org-a", 404L, new SecurityRulesService.RuleCommand(
                "规则", "SENSITIVE_WORD", "SECRET", "KEYWORD", "词", "LOW", "WARN", true, "")))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
