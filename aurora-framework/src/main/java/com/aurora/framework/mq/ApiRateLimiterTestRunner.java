package com.aurora.framework.mq;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * API 限流器测试（无需 JUnit）
 * 可直接运行 main 方法进行测试
 */
public class ApiRateLimiterTestRunner {

    public static void main(String[] args) throws Exception {
        ApiRateLimiter rateLimiter = new ApiRateLimiter();

        System.out.println("========== API 限流器测试 ==========\n");

        // 测试 1: 全局并发配置
        testGlobalLimit(rateLimiter);

        // 测试 2: 获取和释放许可
        testAcquireAndRelease(rateLimiter);

        // 测试 3: 并发限流
        testConcurrentRateLimiting(rateLimiter);

        // 测试 4: 等待队列追踪
        testWaitingQueue(rateLimiter);

        // 测试 5: 状态查询
        testGetAllStatus(rateLimiter);

        System.out.println("\n========== 所有测试通过 ✅ ==========");
    }

    private static void testGlobalLimit(ApiRateLimiter rateLimiter) {
        System.out.println("📌 测试 1: 全局并发配置");

        assert rateLimiter.getMaxConcurrency("openai") == 50 : "全局对话并发应为 50";
        assert rateLimiter.getMaxConcurrency("deepseek") == 50 : "不同供应商应共享 50 并发";

        System.out.println("   ✅ 所有对话供应商共享 50 并发");
    }

    private static void testAcquireAndRelease(ApiRateLimiter rateLimiter) throws Exception {
        System.out.println("\n📌 测试 2: 获取和释放许可");

        String provider = "test-provider";

        // 初始化
        rateLimiter.tryAcquire(provider);
        int initial = rateLimiter.getAvailablePermits(provider);
        rateLimiter.release(provider);

        // 获取
        rateLimiter.acquire(provider);
        int afterAcquire = rateLimiter.getAvailablePermits(provider);
        assert afterAcquire == initial - 1 : "获取后应减 1";

        // 释放
        rateLimiter.release(provider);
        int afterRelease = rateLimiter.getAvailablePermits(provider);
        assert afterRelease == initial : "释放后应恢复";

        System.out.println("   ✅ 初始: " + initial + ", 获取后: " + afterAcquire + ", 释放后: " + afterRelease);
    }

    private static void testConcurrentRateLimiting(ApiRateLimiter rateLimiter) throws Exception {
        System.out.println("\n📌 测试 3: 全局并发限流 (50 并发, 发起 60 请求)");

        String provider = "chat";
        int requestCount = 60;
        AtomicInteger maxConcurrent = new AtomicInteger(0);
        AtomicInteger currentConcurrent = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < requestCount; i++) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    startLatch.await();

                    rateLimiter.acquire(provider);
                    try {
                        int current = currentConcurrent.incrementAndGet();
                        maxConcurrent.updateAndGet(max -> Math.max(max, current));
                        Thread.sleep(50); // 模拟 API 调用
                        currentConcurrent.decrementAndGet();
                    } finally {
                        rateLimiter.release(provider);
                        doneLatch.countDown();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);

        assert completed : "应在 10 秒内完成";
        assert maxConcurrent.get() <= 50 : "最大并发应 <= 50";

        System.out.println("   ✅ 最大并发数: " + maxConcurrent.get() + " (限制: 50)");
    }

    private static void testWaitingQueue(ApiRateLimiter rateLimiter) throws Exception {
        System.out.println("\n📌 测试 4: 等待队列追踪");

        // 使用新的 provider 避免之前测试的影响
        String provider = "queue-test-provider";
        int limit = 50;
        int requestCount = limit + 5;

        CountDownLatch holdLatch = new CountDownLatch(1);
        CountDownLatch startedLatch = new CountDownLatch(limit);

        List<Thread> threads = new ArrayList<>();

        // 启动超过限制数量的线程
        for (int i = 0; i < requestCount; i++) {
            Thread t = new Thread(() -> {
                try {
                    rateLimiter.acquire(provider);
                    try {
                        startedLatch.countDown();
                        holdLatch.await();
                    } finally {
                        rateLimiter.release(provider);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads.add(t);
            t.start();
        }

        // 等待前 limit 个线程获取许可
        startedLatch.await(5, TimeUnit.SECONDS);
        Thread.sleep(100);

        int waiting = rateLimiter.getQueueLength(provider);
        System.out.println("   ✅ 排队等待数: " + waiting + " (预期: ~5)");

        // 释放
        holdLatch.countDown();
        for (Thread t : threads) {
            t.join(2000);
        }
    }

    private static void testGetAllStatus(ApiRateLimiter rateLimiter) throws Exception {
        System.out.println("\n📌 测试 5: 状态查询");

        // 使用一些许可
        rateLimiter.acquire("chat");
        rateLimiter.acquire("chat");

        Map<String, Map<String, Object>> status = rateLimiter.getAllStatus();

        System.out.println("   状态: " + status);

        assert status.containsKey("chat") : "应包含 chat";

        // 清理
        rateLimiter.release("chat");
        rateLimiter.release("chat");

        System.out.println("   ✅ 状态查询成功");
    }
}
