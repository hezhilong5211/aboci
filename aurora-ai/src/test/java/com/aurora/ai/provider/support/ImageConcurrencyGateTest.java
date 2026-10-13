package com.aurora.ai.provider.support;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageConcurrencyGateTest {

    @Test
    void shouldQueueThirtyFirstImageWithoutDroppingIt() throws Exception {
        ImageConcurrencyGate gate = new ImageConcurrencyGate();
        ReflectionTestUtils.setField(gate, "yunwuLimit", 30);
        ReflectionTestUtils.setField(gate, "defaultLimit", 30);
        for (int i = 0; i < 30; i++) {
            gate.acquire("yunwu");
        }

        CountDownLatch acquired = new CountDownLatch(1);
        Thread waiter = new Thread(() -> {
            try {
                gate.acquire("yunwu");
                acquired.countDown();
                gate.release("yunwu");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        waiter.start();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (gate.waitingCount("yunwu") == 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(30, gate.max("yunwu"));
        assertEquals(1, gate.waitingCount("yunwu"));
        assertEquals(0, gate.available("yunwu"));

        gate.release("yunwu");
        assertTrue(acquired.await(2, TimeUnit.SECONDS));
        waiter.join(2000);
        for (int i = 0; i < 29; i++) {
            gate.release("yunwu");
        }
        assertEquals(30, gate.available("yunwu"));
        assertEquals(0, gate.waitingCount("yunwu"));
    }
}
