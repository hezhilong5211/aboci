package com.aurora.framework.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 任务消费者基类
 * 子类继承此类并实现 processTask 方法来处理具体任务
 * 
 * 使用示例:
 * 
 * <pre>
 * &#64;Component
 * public class GraphExtractConsumer extends TaskConsumer {
 * 
 *     public GraphExtractConsumer(StringRedisTemplate redisTemplate,
 *             ObjectMapper objectMapper,
 *             TaskProducer taskProducer) {
 *         super(redisTemplate, objectMapper, taskProducer,
 *                 "stream:my-task", "my-task-consumer-group");
 *     }
 * 
 *     @Override
 *     protected void processTask(TaskMessage message) {
 *         // 处理任务
 *     }
 * }
 * </pre>
 */
@Slf4j
public abstract class TaskConsumer implements StreamListener<String, ObjectRecord<String, String>> {

    protected final StringRedisTemplate redisTemplate;
    protected final ObjectMapper objectMapper;
    protected final TaskProducer taskProducer;
    protected final String streamKey;
    protected final String consumerGroup;

    private StreamMessageListenerContainer<String, ObjectRecord<String, String>> container;
    /** 并行消费:每个并行度一个订阅(消费者组自动分派消息),各订阅一条轮询循环串行处理 */
    private final java.util.List<Subscription> subscriptions = new java.util.ArrayList<>();
    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    /** 单条消息最大重试次数,超过后转入死信流 */
    private static final int MAX_RETRY = 3;

    /**
     * 并行消费者数(订阅数)。Spring StreamMessageListenerContainer 单订阅按序处理,
     * 需注册多订阅才能并行;默认 1(串行),出图消费者传更大值提吞吐。
     */
    private final int parallelism;

    protected TaskConsumer(StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            TaskProducer taskProducer,
            String streamKey,
            String consumerGroup) {
        this(redisTemplate, objectMapper, taskProducer, streamKey, consumerGroup, 1);
    }

    protected TaskConsumer(StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            TaskProducer taskProducer,
            String streamKey,
            String consumerGroup,
            int parallelism) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.taskProducer = taskProducer;
        this.streamKey = streamKey;
        this.consumerGroup = consumerGroup;
        this.parallelism = Math.max(1, parallelism);
    }

    /**
     * 初始化消费者
     */
    @PostConstruct
    public void init() {
        try {
            // 确保 Stream 和消费者组存在
            createConsumerGroupIfNotExists();

            // 创建消息监听容器 - 真正的虚拟线程模式
            // 每条消息都在独立虚拟线程中处理，无数量限制
            StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, ObjectRecord<String, String>> options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                    .builder()
                    .pollTimeout(Duration.ofSeconds(1)) // 更短的轮询间隔，更快响应
                    .batchSize(100) // 每次拉取更多消息
                    .targetType(String.class)
                    .executor(executor) // 虚拟线程执行器，每条消息独立线程
                    .build();

            container = StreamMessageListenerContainer.create(
                    redisTemplate.getConnectionFactory(), options);

            // 注册 parallelism 个订阅(同一消费者组,不同消费者名):消费者组把消息分派到各订阅,
            // 每订阅一条轮询循环串行处理 → 整体并行度 = parallelism。默认 1(串行)。
            for (int i = 0; i < parallelism; i++) {
                Subscription sub = container.receive(
                        Consumer.from(consumerGroup, "consumer-vt-" + i),
                        StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                        this);
                subscriptions.add(sub);
            }

            container.start();
            log.info("任务消费者已启动: stream={}, group={}, 并行度={}", streamKey, consumerGroup, parallelism);

        } catch (Exception e) {
            log.error("❌ 启动任务消费者失败: stream={}", streamKey, e);
        }
    }

    /**
     * 接收消息
     */
    @Override
    public void onMessage(ObjectRecord<String, String> record) {
        String taskJson = record.getValue();
        TaskMessage message = null;

        try {
            message = objectMapper.readValue(taskJson, TaskMessage.class);
            log.info("📥 收到任务: taskId={}, type={}", message.getTaskId(), message.getTaskType());

            // 标记为处理中
            message.markRunning();
            taskProducer.saveTaskStatus(message);

            // 处理任务（子类实现）
            processTask(message);

            // 标记为成功
            message.markSuccess("处理完成");
            taskProducer.saveTaskStatus(message);

            // 确认消息(XACK):任务已成功处理,从 PEL 移除,消费者组不再重投
            redisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, record.getId());
            // 删除已确认消息(XDEL):纯清理(Stream 不自动清理已消费消息,防无限增长)。
            // 已 ACK 故必须容错——XDEL 失败绝不能触发重试(否则已成功任务被重跑),仅告警
            deleteQuietly(record.getId());
            log.info("✅ 任务完成: taskId={}", message.getTaskId());

        } catch (Exception e) {
            log.error("❌ 任务处理失败: {}", e.getMessage(), e);

            if (message != null) {
                message.markFailed(e.getMessage());
                int retry = message.getRetryCount() + 1;
                message.setRetryCount(retry);
                taskProducer.saveTaskStatus(message);
                try {
                    String payload = objectMapper.writeValueAsString(message);
                    if (retry < MAX_RETRY) {
                        // 未达上限:重投回原 stream,由消费组再次消费(避免静默丢失)
                        redisTemplate.opsForStream().add(
                                StreamRecords.objectBacked(payload).withStreamKey(streamKey));
                        log.warn("任务重投 {}/{}: taskId={}", retry, MAX_RETRY, message.getTaskId());
                    } else {
                        // 达到上限:转入死信流,保留 FAILED 状态供人工排查,绝不静默丢弃
                        redisTemplate.opsForStream().add(
                                StreamRecords.objectBacked(payload).withStreamKey(streamKey + ":dlq"));
                        log.error("任务超过最大重试({}),转入死信流 {}:dlq: taskId={}",
                                MAX_RETRY, streamKey, message.getTaskId());
                    }
                } catch (Exception ex) {
                    log.error("重投/死信写入失败,消息将留在 PEL 等待重投: {}", ex.getMessage(), ex);
                    // 重投失败则不 ACK,留在 PEL 由后续 pending 处理,避免丢失
                    return;
                }
            }

            // 已重投或已进死信流,ACK 当前记录避免 PEL 堆积;XDEL 纯清理,容错不影响主流程
            redisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, record.getId());
            deleteQuietly(record.getId());
        }
    }

    /**
     * 删除已确认的 Stream 消息(纯清理);失败仅告警不抛,避免影响已完成的处理流程。
     */
    private void deleteQuietly(RecordId recordId) {
        try {
            redisTemplate.opsForStream().delete(streamKey, recordId);
        } catch (Exception e) {
            log.warn("清理已处理消息失败(不影响正确性,仅 Stream 略增): stream={}, id={}, {}",
                    streamKey, recordId, e.getMessage());
        }
    }

    /**
     * 处理任务（子类实现）
     * 
     * @param message 任务消息
     */
    protected abstract void processTask(TaskMessage message) throws Exception;

    /**
     * 获取消费者名称
     */
    protected String getConsumerName() {
        return "consumer-" + Thread.currentThread().threadId();
    }

    /**
     * 创建消费者组（如果不存在）
     */
    private void createConsumerGroupIfNotExists() {
        try {
            // 先尝试创建 Stream（如果不存在）
            if (Boolean.FALSE.equals(redisTemplate.hasKey(streamKey))) {
                redisTemplate.opsForStream().add(streamKey, java.util.Map.of("init", "true"));
                log.debug("创建 Stream: {}", streamKey);
            }

            // 创建消费者组
            redisTemplate.opsForStream().createGroup(streamKey, consumerGroup);
            log.debug("创建消费者组: {}", consumerGroup);
        } catch (Exception e) {
            // 消费者组已存在，忽略错误(判空防 NPE)
            if (e.getMessage() == null || !e.getMessage().contains("BUSYGROUP")) {
                log.debug("消费者组可能已存在: {}", e.getMessage());
            }
        }
    }

    /**
     * 停止消费者(容器销毁时优雅关闭,避免线程与连接泄露)
     */
    @PreDestroy
    public void stop() {
        try {
            for (Subscription sub : subscriptions) {
                if (sub != null) {
                    sub.cancel();
                }
            }
            if (container != null) {
                container.stop();
            }
            if (executor instanceof java.util.concurrent.ExecutorService es) {
                es.shutdown();
            }
        } catch (Exception e) {
            log.warn("停止任务消费者异常: stream={}, {}", streamKey, e.getMessage());
        }
        log.debug("任务消费者已停止: stream={}", streamKey);
    }
}
