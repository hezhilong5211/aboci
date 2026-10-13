-- 迁移:移动端二期 · AI 使用闭环(Agent)数据底座
-- 日期:2026-06-23
-- 说明:
--   按 PRD「PRD-AI使用闭环-后端.md」§五建 4 张新表,均落业务库,字符集统一 utf8mb4_0900_ai_ci、引擎 InnoDB:
--     conversation     (§5.1) 对话会话头,业务主表:带审计五字段 + del_flag(0存在/2删除,@TableLogic),expire_time=create_time+30天供清理。
--     message          (§5.2) 对话消息明细,轻量流水(对齐 sys_ai_history 风格):无 del_flag,随会话过期级联清理;仅 create_time。
--     generation_task  (§5.3) 出图异步任务,业务主表:task_id 唯一(Redis 队列 taskId),状态机 CREATED/QUEUED/RUNNING/SUCCEEDED/FAILED;无逻辑删(出图任务为流水结果)。
--     user_prompt      (§5.4) 用户个性化提示词,业务主表:每用户唯一一条(uk_user),带审计五字段 + del_flag(@TableLogic)。
--   token_usage/bean_cost 为计费阶段预留列,本期值留空。
--   sys_ai_model 追加两条模型行(§5.5 / §9.1):
--     (a) Agent 专用 DeepSeek 行(deepseek-chat,支持 function calling),api_key/base_url 复制现有 id=1 行(同账号同 key,ENC2 可解密),勿硬编码密文。
--     (b) Gemini 视觉行,api_key/base_url 复制现有 id=12 行(云雾账号,P0 实测有效)。
--   两条 INSERT 用 INSERT...SELECT 复制密文 + NOT EXISTS 防重(避免多环境/重跑重复插入同名行)。
-- 约束:MySQL 9.6 已移除 MD5() 等函数,本脚本不使用;Flyway 仅写增量,禁 DROP/TRUNCATE。

-- ===== 对话会话头(业务主表)=====
CREATE TABLE IF NOT EXISTS `conversation` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    `user_id`           BIGINT        NOT NULL COMMENT 'mobile_user.id(取自登录态)',
    `title`             VARCHAR(255)  DEFAULT NULL COMMENT '会话标题(首条消息摘要或「新对话」)',
    `model_id`          BIGINT        DEFAULT NULL COMMENT 'DeepSeek 模型 id(默认取 active chat 模型)',
    `last_message_time` DATETIME      DEFAULT NULL COMMENT '最近消息时间(列表倒序用)',
    `expire_time`       DATETIME      NOT NULL COMMENT '过期时间(create_time + 30 天,清理用)',
    `create_by`         VARCHAR(64)   DEFAULT '' COMMENT '创建者',
    `create_time`       DATETIME      DEFAULT NULL COMMENT '创建时间',
    `update_by`         VARCHAR(64)   DEFAULT '' COMMENT '更新者',
    `update_time`       DATETIME      DEFAULT NULL COMMENT '更新时间',
    `remark`            VARCHAR(255)  DEFAULT NULL COMMENT '备注',
    `del_flag`          CHAR(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除标记(0存在/2删除)',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`, `del_flag`),
    KEY `idx_expire` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 闭环对话会话头';

-- ===== 对话消息明细(轻量流水,无 del_flag,随会话过期级联清理)=====
CREATE TABLE IF NOT EXISTS `message` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `conversation_id` BIGINT       NOT NULL COMMENT '所属会话ID',
    `user_id`         BIGINT       NOT NULL COMMENT '冗余 user_id(便于按人查/清理)',
    `role`            VARCHAR(20)  NOT NULL COMMENT '角色(user/assistant/tool)',
    `content`         LONGTEXT     DEFAULT NULL COMMENT '文本;assistant 结构化产出存其 JSON 串',
    `content_type`    VARCHAR(20)  DEFAULT 'text' COMMENT '内容类型(text/structured)',
    `media`           TEXT         DEFAULT NULL COMMENT 'JSON:用户上传图 URL 列表 或 产出 images 引用 [{taskId,seq}]',
    `tool_name`       VARCHAR(64)  DEFAULT NULL COMMENT 'role=tool 时:recognize_image / generate_image',
    `tool_call_id`    VARCHAR(64)  DEFAULT NULL COMMENT '工具调用关联 id(供审计/回灌)',
    `token_usage`     INT          DEFAULT NULL COMMENT '预留计费,本期 null',
    `bean_cost`       INT          DEFAULT NULL COMMENT '预留计费,本期 null',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_conv` (`conversation_id`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 闭环对话消息明细';

-- ===== 出图异步任务(业务主表)=====
CREATE TABLE IF NOT EXISTS `generation_task` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    `task_id`         VARCHAR(64)   NOT NULL COMMENT 'Redis 队列 taskId(UUID 去横线),唯一',
    `user_id`         BIGINT        NOT NULL COMMENT 'mobile_user.id',
    `conversation_id` BIGINT        DEFAULT NULL COMMENT '对话内生图来源会话',
    `message_id`      BIGINT        DEFAULT NULL COMMENT '触发的 assistant 消息(可空)',
    `task_type`       VARCHAR(20)   DEFAULT 'image' COMMENT '任务类型(本期固定 image,预留 video)',
    `model_id`        BIGINT        NOT NULL COMMENT '出图模型 id(yunwu/gptsapi)',
    `prompt`          TEXT          NOT NULL COMMENT '该任务提示词',
    `n`               INT           NOT NULL DEFAULT 1 COMMENT '张数(1~6)',
    `resolution`      VARCHAR(20)   DEFAULT NULL COMMENT '空走模型 default_resolution',
    `aspect_ratio`    VARCHAR(20)   DEFAULT NULL COMMENT '空走默认',
    `status`          VARCHAR(20)   NOT NULL DEFAULT 'CREATED' COMMENT '状态(CREATED/QUEUED/RUNNING/SUCCEEDED/FAILED)',
    `result_urls`     TEXT          DEFAULT NULL COMMENT 'JSON 数组:成功的 /profile/*.png URL',
    `success_count`   INT           DEFAULT 0 COMMENT '实际成功张数',
    `error_msg`       VARCHAR(500)  DEFAULT NULL COMMENT '失败原因(脱敏)',
    `bean_cost`       INT           DEFAULT NULL COMMENT '预留计费,本期 null',
    `create_time`     DATETIME      DEFAULT NULL COMMENT '创建时间',
    `update_time`     DATETIME      DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task` (`task_id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_conv` (`conversation_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 闭环出图异步任务';

-- ===== 用户个性化提示词(业务主表,每用户唯一一条)=====
CREATE TABLE IF NOT EXISTS `user_prompt` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     BIGINT        NOT NULL COMMENT 'mobile_user.id(唯一,每用户一条)',
    `content`     TEXT          DEFAULT NULL COMMENT '自由文本(写入前 HtmlUtils.htmlUnescape)',
    `create_by`   VARCHAR(64)   DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME      DEFAULT NULL COMMENT '创建时间',
    `update_by`   VARCHAR(64)   DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME      DEFAULT NULL COMMENT '更新时间',
    `remark`      VARCHAR(255)  DEFAULT NULL COMMENT '备注',
    `del_flag`    CHAR(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除标记(0存在/2删除)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 闭环用户个性化提示词';

-- ===== sys_ai_model 追加 Agent 专用 DeepSeek 行(api_key/base_url 复制 id=1,勿硬编码密文)=====
-- model_deployment=deepseek-chat(官方明确支持 function calling),provider=deepseek,model_type=chat,is_active=1;
-- temperature/max_tokens/sort_order 给合理默认。NOT EXISTS 防重(按 model_name + model_deployment 唯一判定)。
INSERT INTO `sys_ai_model`
    (`model_name`, `api_key`, `base_url`, `model_deployment`, `temperature`, `max_tokens`,
     `provider`, `model_type`, `is_active`, `sort_order`, `create_time`, `update_time`)
SELECT 'DeepSeek Chat(Agent)', `src`.`api_key`, `src`.`base_url`, 'deepseek-chat', 0.7, 2000,
       'deepseek', 'chat', 1, 0, NOW(), NOW()
FROM `sys_ai_model` `src`
WHERE `src`.`id` = 1
  AND NOT EXISTS (
      SELECT 1 FROM `sys_ai_model` `t`
      WHERE `t`.`model_name` = 'DeepSeek Chat(Agent)' AND `t`.`model_deployment` = 'deepseek-chat'
  );

-- ===== sys_ai_model 追加 Gemini 视觉行(api_key/base_url 复制 id=12,勿硬编码密文)=====
-- provider=yunwu,model_type=vision,model_deployment=gemini-2.5-flash,is_active=1。NOT EXISTS 防重。
INSERT INTO `sys_ai_model`
    (`model_name`, `api_key`, `base_url`, `model_deployment`, `temperature`, `max_tokens`,
     `provider`, `model_type`, `is_active`, `sort_order`, `create_time`, `update_time`)
SELECT 'Gemini 视觉', `src`.`api_key`, `src`.`base_url`, 'gemini-2.5-flash', 0.7, 2000,
       'yunwu', 'vision', 1, 0, NOW(), NOW()
FROM `sys_ai_model` `src`
WHERE `src`.`id` = 12
  AND NOT EXISTS (
      SELECT 1 FROM `sys_ai_model` `t`
      WHERE `t`.`model_name` = 'Gemini 视觉' AND `t`.`model_deployment` = 'gemini-2.5-flash'
  );
