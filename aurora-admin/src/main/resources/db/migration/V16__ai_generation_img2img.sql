-- AI 闭环 · 生成页图生图(C 组)
-- 为 generation_task 增加参考图列,支撑独立出图端点 POST /api/mobile/agent/generate 的图生图模式:
--   ref_images 存参考图的 /profile/*.png URL JSON 数组串(1~4 张);为空/NULL 即文生图(text-to-image)。
--   消费者(GenerationTaskConsumer)按本列判定:非空 → 走云雾 image-edit;空 → 走文生图。
-- 约束:Flyway 仅写增量,禁 DROP/TRUNCATE;字段可空,存量行不受影响。
ALTER TABLE `generation_task`
    ADD COLUMN `ref_images` TEXT DEFAULT NULL COMMENT 'JSON 数组:图生图参考图 /profile URL(1~4);空=文生图' AFTER `aspect_ratio`;
