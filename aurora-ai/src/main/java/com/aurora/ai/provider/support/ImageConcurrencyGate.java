package com.aurora.ai.provider.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 出图并发闸(按 provider 原始名限并发,防触发中转站并发上限)。
 *
 * <p>与框架 {@code ApiRateLimiter} 的区别:后者按对话 provider 归一化(会把 yunwu→default、gptsapi→openai),
 * 限值也非出图口径;本闸按 provider 原始名(yunwu/gptsapi)键,限值配置化,专供出图。</p>
 *
 * <p>用法:出图前 {@link #acquire(String)} 占一个令牌(拿不到则阻塞排队,不失败),
 * 出图完成/失败后必须在 finally 里 {@link #release(String)} 归还,否则漏令牌越用越少。</p>
 *
 * <p>单实例语义(java.util.concurrent.Semaphore):当前部署单实例够用;将来扩多实例时,
 * 落点不变、只需把 Semaphore 换成 Redis 分布式信号量。</p>
 */
@Slf4j
@Component
public class ImageConcurrencyGate {

    /** 各 provider 的信号量(懒创建) */
    private final Map<String, Semaphore> gates = new ConcurrentHashMap<>();
    /** 各 provider 的排队计数(仅观测用) */
    private final Map<String, AtomicInteger> waiting = new ConcurrentHashMap<>();

    /** 云雾出图并发上限。 */
    @Value("${ai.image.concurrency.yunwu:30}")
    private int yunwuLimit;

    @Value("${ai.image.concurrency.luxai:5}")
    private int luxaiLimit = 5;

    /** 其余出图 provider(含 gptsapi 备用)的并发上限默认值。 */
    @Value("${ai.image.concurrency.default:30}")
    private int defaultLimit;

    /** provider 归一化 key(小写;空则 default) */
    private String keyOf(String provider) {
        return (provider == null || provider.isEmpty()) ? "default" : provider.toLowerCase();
    }

    /** 该 provider 的并发上限 */
    private int limitOf(String key) {
        return "yunwu".equals(key) ? yunwuLimit : "luxai".equals(key) ? luxaiLimit : defaultLimit;
    }

    private Semaphore gateOf(String provider) {
        String key = keyOf(provider);
        return gates.computeIfAbsent(key, k -> {
            int max = limitOf(k);
            log.info("📊 出图并发闸创建: provider={}, max={}", k, max);
            return new Semaphore(max, true); // fair=true 先到先得
        });
    }

    private AtomicInteger waitOf(String provider) {
        return waiting.computeIfAbsent(keyOf(provider), k -> new AtomicInteger());
    }

    /** 获取令牌(阻塞排队);拿不到则等待,直到有空位 */
    public void acquire(String provider) throws InterruptedException {
        Semaphore gate = gateOf(provider);
        if (gate.availablePermits() == 0) {
            int q = waitOf(provider).incrementAndGet();
            log.info("⏳ 出图并发闸排队: provider={}, 排队位置={}", provider, q);
            try {
                gate.acquire();
            } finally {
                waitOf(provider).decrementAndGet();
            }
        } else {
            gate.acquire();
        }
    }

    /** 归还令牌(必须在 finally 调用) */
    public void release(String provider) {
        gateOf(provider).release();
    }

    /** 可用令牌数(观测) */
    public int available(String provider) {
        return gateOf(provider).availablePermits();
    }

    /** 排队数(观测) */
    public int waitingCount(String provider) {
        return waitOf(provider).get();
    }

    /** 并发上限(观测) */
    public int max(String provider) {
        return limitOf(keyOf(provider));
    }

    /** 已创建闸的 provider 列表(观测) */
    public Set<String> providers() {
        return gates.keySet();
    }
}
