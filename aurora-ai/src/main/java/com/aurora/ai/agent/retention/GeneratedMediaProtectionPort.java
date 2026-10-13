package com.aurora.ai.agent.retention;

import java.util.Set;

/**
 * 生成媒体清理保护端口。
 *
 * <p>上层业务（例如案例库）返回需长期保存的 /profile URL；清理任务命中这些 URL 时只删除
 * generation_task 流水和对话引用，不删除物理文件。</p>
 */
public interface GeneratedMediaProtectionPort {

    Set<String> protectedProfileUrls();
}
