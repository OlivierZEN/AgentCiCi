package com.codehouse.ciciassistant.semattice;

import static org.assertj.core.api.Assertions.assertThat;

import com.codehouse.ciciassistant.common.error.ForbiddenException;
import org.junit.jupiter.api.Test;

class SematticeProjectDeliveryTransferToolServiceTest {

    @Test
    void parsesOnlyExactConfirmationWithoutInternalIds() {
        var confirmation = SematticeProjectDeliveryTransferToolService.confirmedIntent("确认将鲁班的任务转交给哪吒").orElseThrow();
        assertThat(confirmation.from()).isEqualTo("鲁班");
        assertThat(confirmation.to()).isEqualTo("哪吒");
    }

    @Test
    void parsesConfirmationCopiedFromChatWithTerminalPunctuation() {
        var confirmation = SematticeProjectDeliveryTransferToolService
                .confirmedIntent("`确认将鲁班的任务转交给哪吒。`").orElseThrow();
        assertThat(confirmation.from()).isEqualTo("鲁班");
        assertThat(confirmation.to()).isEqualTo("哪吒");
    }

    @Test
    void rejectsSelfTransferAndBareConfirmation() {
        assertThat(SematticeProjectDeliveryTransferToolService.confirmedIntent("确认将鲁班的任务转交给鲁班")).isEmpty();
        assertThat(SematticeProjectDeliveryTransferToolService.confirmedIntent("把鲁班的任务转交给哪吒")).isEmpty();
        assertThat(SematticeProjectDeliveryTransferToolService.confirmedIntent("确认转派")).isEmpty();
    }

    @Test
    void explainsMissingTransferScopeWithoutExposingInternalPrincipalIds() {
        assertThat(SematticeProjectDeliveryTransferToolService.failureMessage(
                new ForbiddenException("机器执行身份缺少本次操作所需的 Semattice scope")))
                .contains("runtime.record.transfer")
                .doesNotContain("principal");
    }
}
