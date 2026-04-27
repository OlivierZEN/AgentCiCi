package com.codehouse.ciciassistant.ai.service;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class SessionRealtimeEventService {

    private static final Logger log = LoggerFactory.getLogger(SessionRealtimeEventService.class);
    private static final long EMITTER_TIMEOUT_MS = 30L * 60L * 1000L;

    private final ConcurrentMap<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String orgId, String userId) {
        String subscriptionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        Subscription subscription = new Subscription(subscriptionId, orgId, userId, emitter);
        subscriptions.put(subscriptionId, subscription);

        emitter.onCompletion(() -> remove(subscriptionId));
        emitter.onTimeout(() -> remove(subscriptionId));
        emitter.onError(error -> remove(subscriptionId));

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of(
                            "subscriptionId", subscriptionId,
                            "timestamp", Instant.now().toString()
                    )));
        } catch (IOException ex) {
            remove(subscriptionId);
            throw new IllegalStateException("Failed to establish session realtime stream", ex);
        }
        return emitter;
    }

    public void publishSessionUpdated(String orgId, String userId, String sessionId, boolean orgScoped, String trigger) {
        Map<String, Object> payload = Map.of(
                "sessionId", sessionId,
                "scope", orgScoped ? "org" : "user",
                "trigger", trigger,
                "updatedAt", Instant.now().toString()
        );
        subscriptions.forEach((subscriptionId, subscription) -> {
            if (!subscription.orgId().equals(orgId)) {
                return;
            }
            if (!orgScoped && !subscription.userId().equals(userId)) {
                return;
            }
            try {
                subscription.emitter().send(SseEmitter.event()
                        .name("session_updated")
                        .data(payload));
            } catch (IOException ex) {
                log.debug("Drop broken session realtime subscriber {}", subscriptionId, ex);
                remove(subscriptionId);
            }
        });
    }

    private void remove(String subscriptionId) {
        subscriptions.remove(subscriptionId);
    }

    private record Subscription(String id, String orgId, String userId, SseEmitter emitter) {
    }
}
