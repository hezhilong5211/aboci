-- Agent 批量生图：同一任务内保存每张图的独立提示词与参考图编号解析结果。
ALTER TABLE `generation_task`
    ADD COLUMN `request_items` LONGTEXT DEFAULT NULL COMMENT '批量生图任务项 JSON；空沿用 prompt+n' AFTER `ref_images`;
