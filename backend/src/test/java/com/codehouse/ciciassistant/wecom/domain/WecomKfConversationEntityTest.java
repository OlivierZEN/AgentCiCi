package com.codehouse.ciciassistant.wecom.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class WecomKfConversationEntityTest {

    @Test
    void shouldFenceAiWhenHandoffIsReservedAndMapAuthoritativeStates() {
        WecomKfConversationEntity conversation = conversation();

        assertThat(conversation.aiOwned()).isTrue();
        assertThat(conversation.getStateRevision()).isZero();

        conversation.reserveHandoff("mobile_force_takeover");

        assertThat(conversation.aiOwned()).isFalse();
        assertThat(conversation.getOwnerMode()).isEqualTo(WecomKfConversationEntity.OWNER_HANDOFF);
        assertThat(conversation.getStateRevision()).isEqualTo(1);

        conversation.synchronizeRemoteState(3, "agent-1", "readback", Instant.parse("2026-08-21T00:00:00Z"));

        assertThat(conversation.getRemoteServiceState()).isEqualTo(3);
        assertThat(conversation.getOwnerMode()).isEqualTo(WecomKfConversationEntity.OWNER_HUMAN);
        assertThat(conversation.getServicerUserId()).isEqualTo("agent-1");
        assertThat(conversation.getStateRevision()).isEqualTo(2);
    }

    @Test
    void shouldNotAdvanceRevisionForIdenticalReadback() {
        WecomKfConversationEntity conversation = conversation();
        conversation.synchronizeRemoteState(1, null, "first", Instant.now());
        long revision = conversation.getStateRevision();

        conversation.synchronizeRemoteState(1, null, "second", Instant.now());

        assertThat(conversation.getStateRevision()).isEqualTo(revision);
    }

    @Test
    void shouldKeepHandoffFenceWhileRemoteTransferIsStillPending() {
        WecomKfConversationEntity conversation = conversation();
        conversation.reserveHandoff("mobile_force_takeover");

        conversation.synchronizeRemoteState(1, null, "mobile_refresh", Instant.now());

        assertThat(conversation.getOwnerMode()).isEqualTo(WecomKfConversationEntity.OWNER_HANDOFF);
        assertThat(conversation.aiOwned()).isFalse();

        conversation.synchronizeRemoteState(1, null, "handoff_failed", Instant.now());
        assertThat(conversation.getOwnerMode()).isEqualTo(WecomKfConversationEntity.OWNER_AI);
    }

    private WecomKfConversationEntity conversation() {
        return new WecomKfConversationEntity("org-1", "ww-demo", "wk-demo", "external-1",
                "session-1", "after-sales-agent", "user-1");
    }
}
