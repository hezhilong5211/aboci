-- GPT Image 提示词上限放宽至 32000 个 Unicode 字符，TEXT 无法可靠容纳等量中文内容。
ALTER TABLE `generation_task`
    MODIFY COLUMN `prompt` MEDIUMTEXT NOT NULL COMMENT '该任务提示词，最多32000个Unicode字符';
