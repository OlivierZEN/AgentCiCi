package com.codehouse.ciciassistant.ai.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ChatOrchestratorSseErrorTest {

    @Test
    void completesCommittedEventStreamNormallyAfterStructuredErrorEvent() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);

        ChatOrchestratorService.completeStreamWithErrorEvent(
                emitter, new IllegalStateException("delegation denied"), "run-1");

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
        verify(emitter, never()).completeWithError(any(Throwable.class));
    }
}
