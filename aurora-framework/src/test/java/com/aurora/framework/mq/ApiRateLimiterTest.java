package com.aurora.framework.mq;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiRateLimiterTest {

    @Test
    void shouldEnforceConfiguredGlobalConcurrency() {
        ApiRateLimiter limiter = new ApiRateLimiter(2, 0, 1);

        assertTrue(limiter.tryAcquire("yunwu"));
        assertTrue(limiter.tryAcquire("other"));
        assertFalse(limiter.tryAcquire("yunwu"));
        assertEquals(0, limiter.getAvailablePermits("chat"));

        limiter.release("other");
        assertTrue(limiter.tryAcquire("yunwu"));
        limiter.release("yunwu");
        limiter.release("yunwu");
        assertEquals(2, limiter.getAvailablePermits("chat"));
    }

    @Test
    void shouldWarnOnceThenTimeoutWithoutCallingModel() throws Exception {
        ApiRateLimiter limiter = new ApiRateLimiter(1, 0, 0);
        AtomicInteger warningCount = new AtomicInteger();
        assertTrue(limiter.tryAcquire("chat"));

        assertFalse(limiter.acquireWithWaitNotice(waiting -> warningCount.incrementAndGet()));

        assertEquals(1, warningCount.get());
        assertEquals(0, limiter.getQueueLength("chat"));
        limiter.release("chat");
    }

    @Test
    void shouldRemoveInterruptedWaiterWithoutLeakingPermit() throws Exception {
        ApiRateLimiter limiter = new ApiRateLimiter(1, 30, 60);
        assertTrue(limiter.tryAcquire("chat"));
        CountDownLatch interrupted = new CountDownLatch(1);
        Thread waiter = new Thread(() -> {
            try {
                limiter.acquireWithWaitNotice(null);
            } catch (InterruptedException e) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
        });

        waiter.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (limiter.getQueueLength("chat") == 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(1, limiter.getQueueLength("chat"));

        waiter.interrupt();
        assertTrue(interrupted.await(2, TimeUnit.SECONDS));
        waiter.join(2000);
        assertEquals(0, limiter.getQueueLength("chat"));
        assertEquals(0, limiter.getAvailablePermits("chat"));

        limiter.release("chat");
        assertEquals(1, limiter.getAvailablePermits("chat"));
    }
}
