-- 迁移:清理向量/embedding 模型配置(embedding 能力已随知识库/向量库摘除,AiFactory.createEmbeddingModel 已删)
-- 日期:2026-07-07
-- 说明:
--   1) 删 sys_ai_model 里 embedding 类型的模型行(dev 库现已无、基线 dump 含 id=5「豆包-向量模型」)。
--   2) 删模型类型字典 sys_ai_model_type 的「向量模型 (Embedding)」选项——已无 embedding 能力,
--      后台模型管理不应再能选「embedding」类型建模型。
--   保留:chat / vision / image 三类模型与字典项(产品在用)。
-- ⚠️ 字典改动后需失效 Redis 缓存 key `dict::sys_ai_model_type`(SQL 直改不自动失效):
--    dev 库执行后手动 `redis-cli -p 6380 DEL dict::sys_ai_model_type`;
--    生产由应用重启或经后台「字典管理」增删触发 @CacheEvict。
DELETE FROM `sys_ai_model` WHERE `model_type` = 'embedding';
DELETE FROM `sys_dict_data` WHERE `dict_type` = 'sys_ai_model_type' AND `dict_value` = 'embedding';
