package com.codehouse.ciciassistant.semattice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SematticeProjectDeliveryTransferToolServiceTest {

    @Test
    void parsesNameBasedDraftAndExactConfirmationWithoutInternalIds() {
        var draft = SematticeProjectDeliveryTransferToolService.draftIntent("鲁班下班了，把鲁班的任务都转交给哪吒");
        assertThat(draft).isPresent();
        assertThat(draft.orElseThrow().from()).isEqualTo("鲁班");
        var confirmation = SematticeProjectDeliveryTransferToolService.confirmedIntent("确认将鲁班的任务转交给哪吒").orElseThrow();
        assertThat(confirmation.from()).isEqualTo("鲁班");
        assertThat(confirmation.to()).isEqualTo("哪吒");
    }

    @Test
    void rejectsSelfTransferAndBareConfirmation() {
        assertThat(SematticeProjectDeliveryTransferToolService.draftIntent("把鲁班的任务转交给鲁班")).isEmpty();
        assertThat(SematticeProjectDeliveryTransferToolService.confirmedIntent("确认转派")).isEmpty();
    }
}
