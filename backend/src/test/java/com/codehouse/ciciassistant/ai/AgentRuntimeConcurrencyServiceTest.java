package com.codehouse.ciciassistant.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.codehouse.ciciassistant.agent.service.AgentRuntimeConcurrencyService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AgentRuntimeConcurrencyServiceTest {

    @Test
    void serializesRunsForSameSession() throws Exception {
        AgentRuntimeConcurrencyService service = new AgentRuntimeConcurrencyService();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger entered = new AtomicInteger();
        try {
            var first = executor.submit(() -> service.run("org", "user", "agent", "session", () -> {
                entered.incrementAndGet();
                firstEntered.countDown();
                await(releaseFirst);
                return "first";
            }));
            assertThat(firstEntered.await(1, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> service.run("org", "user", "agent", "session", () -> {
                entered.incrementAndGet();
                return "second";
            }));
            Thread.sleep(120L);
            assertThat(entered.get()).isEqualTo(1);
            releaseFirst.countDown();
            assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(second.get(1, TimeUnit.SECONDS)).isEqualTo("second");
            assertThat(entered.get()).isEqualTo(2);
            assertThat(service.activeSessionLockCount()).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void allowsParallelRunsForDifferentSessions() throws Exception {
        AgentRuntimeConcurrencyService service = new AgentRuntimeConcurrencyService();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        try {
            var first = executor.submit(() -> service.run("org", "user-a", "agent", "session-a", () -> {
                markActive(active, maxActive);
                entered.countDown();
                await(release);
                active.decrementAndGet();
                return "first";
            }));
            var second = executor.submit(() -> service.run("org", "user-b", "agent", "session-b", () -> {
                markActive(active, maxActive);
                entered.countDown();
                await(release);
                active.decrementAndGet();
                return "second";
            }));
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(maxActive.get()).isEqualTo(2);
            release.countDown();
            assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(second.get(1, TimeUnit.SECONDS)).isEqualTo("second");
            assertThat(service.activeSessionLockCount()).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    private static void markActive(AtomicInteger active, AtomicInteger maxActive) {
        int now = active.incrementAndGet();
        maxActive.accumulateAndGet(now, Math::max);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}

