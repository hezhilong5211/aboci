package com.aurora.admin.mq;

import com.aurora.framework.mq.TaskConsumer;
import com.aurora.framework.mq.TaskMessage;
import com.aurora.framework.mq.TaskProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 示例任务消费者
 * 演示如何实现一个任务消费者
 */
@Slf4j
@Component
public class DemoTaskConsumer extends TaskConsumer {

    public DemoTaskConsumer(StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            TaskProducer taskProducer) {
        super(redisTemplate, objectMapper, taskProducer,
                "stream:test-task", // 队列名称
                "demo-consumer-group"); // 消费者组名称
    }

    @Override
    protected void processTask(TaskMessage message) throws Exception {
        log.info("🔧 开始处理任务: taskId={}", message.getTaskId());

        // 模拟任务处理过程
        for (int i = 1; i <= 5; i++) {
            // 更新进度
            int progress = i * 20;
            message.updateProgress(progress, "处理中... " + progress + "%");
            taskProducer.saveTaskStatus(message);

            log.info("📊 任务进度: {}%", progress);

            // 模拟耗时操作
            Thread.sleep(500);
        }

        log.info("✅ 任务处理完成: taskId={}", message.getTaskId());
    }
}
