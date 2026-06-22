package com.codehouse.ciciassistant.agent.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentRuntimeConcurrencyService {

    private static final int MAX_ORG_CONCURRENT_RUNS = 128;
    private static final int MAX_AGENT_CONCURRENT_RUNS = 64;
    private static final int MAX_USER_CONCURRENT_RUNS = 16;

    private final ConcurrentHashMap<String, LockRef> sessionLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    public <T> T run(String orgId, String userId, String agentId, String sessionId, Supplier<T> supplier) {
        String safeOrgId = normalize(orgId, "unknown-org");
        String safeUserId = normalize(userId, "unknown-user");
        String safeAgentId = normalize(agentId, "unknown-agent");
        String safeSessionId = normalize(sessionId, "unknown-session");
        String orgKey = "org:" + safeOrgId;
        String agentKey = "agent:" + safeOrgId + ":" + safeAgentId;
        String userKey = "user:" + safeOrgId + ":" + safeUserId;
        acquire(orgKey, MAX_ORG_CONCURRENT_RUNS);
        acquire(agentKey, MAX_AGENT_CONCURRENT_RUNS);
        acquire(userKey, MAX_USER_CONCURRENT_RUNS);
        LockRef lockRef = acquireSessionLock(safeOrgId + ":" + safeSessionId);
        try {
            return supplier.get();
        } finally {
            releaseSessionLock(safeOrgId + ":" + safeSessionId, lockRef);
            release(userKey);
            release(agentKey);
            release(orgKey);
        }
    }

    public int activeSessionLockCount() {
        return sessionLocks.size();
    }

    private void acquire(String key, int limit) {
        AtomicInteger counter = counters.computeIfAbsent(key, ignored -> new AtomicInteger());
        int current = counter.incrementAndGet();
        if (current > limit) {
            release(key);
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Agent runtime concurrency limit exceeded for " + key);
        }
    }

    private void release(String key) {
        AtomicInteger counter = counters.get(key);
        if (counter == null) {
            return;
        }
        if (counter.decrementAndGet() <= 0) {
            counters.remove(key, counter);
        }
    }

    private LockRef acquireSessionLock(String key) {
        LockRef ref = sessionLocks.compute(key, (ignored, existing) -> {
            LockRef target = existing == null ? new LockRef() : existing;
            target.refs.incrementAndGet();
            return target;
        });
        ref.lock.lock();
        return ref;
    }

    private void releaseSessionLock(String key, LockRef ref) {
        try {
            ref.lock.unlock();
        } finally {
            if (ref.refs.decrementAndGet() <= 0) {
                sessionLocks.remove(key, ref);
            }
        }
    }

    private static String normalize(String value, String fallback) {
        String text = value == null ? "" : value.trim();
        return text.isEmpty() ? fallback : text;
    }

    private static final class LockRef {
        private final ReentrantLock lock = new ReentrantLock(true);
        private final AtomicInteger refs = new AtomicInteger();
    }
}
