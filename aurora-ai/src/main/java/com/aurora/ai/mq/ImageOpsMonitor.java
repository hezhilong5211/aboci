package com.aurora.ai.mq;

import com.aurora.ai.provider.support.ImageConcurrencyGate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 出图运行态监控(P1-④ 死信可视/重放 + P2 观测)。
 *
 * <p>定时上报出图队列深度、死信积压、并发闸状态,便于上量后感知瓶颈;
 * 死信非空时 WARN 告警。提供 {@link #replayDlq()} 手动把死信重投主队列(供后续接管理端触发)。</p>
 */
@Slf4j
@Component
public class ImageOpsMonitor {

    private static final String MAIN_STREAM = "stream:generation-task";
    private static final String DLQ_STREAM = "stream:generation-task:dlq";

    private final StringRedisTemplate redisTemplate;
    private final ImageConcurrencyGate imageConcurrencyGate;

    public ImageOpsMonitor(StringRedisTemplate redisTemplate, ImageConcurrencyGate imageConcurrencyGate) {
        this.redisTemplate = redisTemplate;
        this.imageConcurrencyGate = imageConcurrencyGate;
    }

    /** 每 60 秒上报一次运行态;死信积压则 WARN */
    @Scheduled(fixedDelay = 60_000L)
    public void report() {
        try {
            Long mainSize = redisTemplate.opsForStream().size(MAIN_STREAM);
            Long dlqSize = redisTemplate.opsForStream().size(DLQ_STREAM);
            long dlq = dlqSize == null ? 0 : dlqSize;
            if (dlq > 0) {
                log.warn("⚠️ 出图死信积压 {} 条,需人工排查/重放({})", dlq, DLQ_STREAM);
            }
            log.info("📊 出图运行态: 队列深度={}, 死信={}, 云雾闸[可用={}/{}, 排队={}]",
                    mainSize == null ? 0 : mainSize, dlq,
                    imageConcurrencyGate.available("yunwu"),
                    imageConcurrencyGate.max("yunwu"),
                    imageConcurrencyGate.waitingCount("yunwu"));
        } catch (Exception e) {
            log.warn("出图运行态上报失败: {}", e.getMessage());
        }
    }

    /**
     * 死信重放:把死信流全部消息重投回主队列并从死信流删除(手动运维,供后续接管理端)。
     * 仅搬运原始字段 map(mapBacked 保留 range() 读出的原始字段,不重解析 JSON),由主队列消费者按正常流程重试。
     *
     * <p><b>⚠️ 当前未接任何调用方(无管理端入口),接线到端点前需先实测重放后消费者能否正确反序列化</b>
     * (objectBacked 写入 vs mapBacked 重投的字段结构等价性);监控/告警(report)已验证可用,重放为待验证增益。</p>
     *
     * @return 成功重放的条数
     */
    public long replayDlq() {
        List<MapRecord<String, Object, Object>> records =
                redisTemplate.opsForStream().range(DLQ_STREAM, Range.unbounded());
        if (records == null || records.isEmpty()) {
            return 0;
        }
        long replayed = 0;
        for (MapRecord<String, Object, Object> rec : records) {
            try {
                Map<Object, Object> value = rec.getValue();
                redisTemplate.opsForStream().add(
                        StreamRecords.mapBacked(value).withStreamKey(MAIN_STREAM));
                redisTemplate.opsForStream().delete(DLQ_STREAM, rec.getId());
                replayed++;
            } catch (Exception e) {
                log.warn("死信重放单条失败,保留待重试: id={}, err={}", rec.getId(), e.getMessage());
            }
        }
        log.info("♻️ 死信重放完成: {} 条 → {}", replayed, MAIN_STREAM);
        return replayed;
    }
}
