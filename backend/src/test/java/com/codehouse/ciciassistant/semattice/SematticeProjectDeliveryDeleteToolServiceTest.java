package com.codehouse.ciciassistant.semattice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SematticeProjectDeliveryDeleteToolServiceTest {

    @Test
    void exactTaskConfirmationPreservesEntityTypeAndRecordReference() throws Exception {
        var intent = SematticeProjectDeliveryDeleteToolService.confirmedIntent(
                "确认删除任务：019ffde1-7b82-7f33-93c3-985921aca699").orElseThrow();

        assertThat(intent.entityType()).isEqualTo("task");
        assertThat(intent.reference()).isEqualTo("019ffde1-7b82-7f33-93c3-985921aca699");
        var arguments = new ObjectMapper().readTree(intent.toArguments(new ObjectMapper()));
        assertThat(arguments.path("entity_type").asText()).isEqualTo("task");
        assertThat(arguments.path("reference").asText())
                .isEqualTo("019ffde1-7b82-7f33-93c3-985921aca699");
    }

    @Test
    void draftDeleteRequestStillRequiresExactConfirmation() {
        assertThat(SematticeProjectDeliveryDeleteToolService.isDraftRequest("删除这条旧任务 task-1")).isTrue();
        assertThat(SematticeProjectDeliveryDeleteToolService.confirmedIntent("删除这条旧任务 task-1")).isEmpty();
        assertThat(SematticeProjectDeliveryDeleteToolService.isDraftRequest("确认删除任务：task-1")).isFalse();
    }
}
