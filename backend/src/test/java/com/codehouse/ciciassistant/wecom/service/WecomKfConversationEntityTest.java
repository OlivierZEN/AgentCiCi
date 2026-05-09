package com.codehouse.ciciassistant.wecom.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.codehouse.ciciassistant.wecom.domain.WecomKfConversationEntity;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WecomKfConversationEntityTest {

    @Test
    void shouldEnforceReplyWindowAndCount() {
        WecomKfConversationEntity conversation = new WecomKfConversationEntity(
                "org-1", "ww-demo", "wk-demo", "external-1", "wecom-kf:session", "after-sales-agent", "user-1");
        Instant now = Instant.parse("2026-05-08T08:00:00Z");

        assertThat(conversation.canReply(now)).isFalse();

        conversation.markCustomerMessage(now.minusSeconds(60));
        assertThat(conversation.canReply(now)).isTrue();

        for (int i = 0; i < 5; i++) {
            conversation.markReplySent();
        }
        assertThat(conversation.canReply(now)).isFalse();

        conversation.markCustomerMessage(now.minusSeconds(49 * 3600L));
        assertThat(conversation.canReply(now)).isFalse();
    }
}
