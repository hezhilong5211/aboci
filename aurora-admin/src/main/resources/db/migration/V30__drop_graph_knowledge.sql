-- 迁移:摘除知识图谱 + 向量库/知识库RAG + 知识角色(表 + 菜单 + 参数配置)
-- 日期:2026-07-07
-- 背景:图谱(Neo4j 双向多点交汇检索)、向量库/知识库RAG、知识角色 均属 Aurora 框架自研扩展,
--   移动端二期产品(微信AI创作小程序)不使用,整体摘除。
--   代码侧:删 aurora-graph 整模块 + aurora-ai 向量/知识库/角色对话 + aurora-common 相关实体/SPI(见本次提交)。
--   数据侧:本迁移 DROP 相关表、清理菜单权限与 sys_config 参数。
-- 说明:这些表/菜单原本只在基线 dump(aurora_framework_full_20260617.sql)里,无独立建表迁移;
--   本迁移在 baseline(V1)之后执行,对全新库同样生效(dump 先建、本迁移后删,DROP IF EXISTS 幂等)。
-- 保留(不动):sys_ai_model(AI 模型配置)、global_prompt / user_prompt / skill(产品三层提示词,
--   与被删的 sys_prompt_template 是不同表)、会员/豆子/支付/案例模板/tabbar 等全部产品表。

-- 1) DROP 图谱表(7)
DROP TABLE IF EXISTS `sys_graph_entity_type_rel`;
DROP TABLE IF EXISTS `sys_graph_relation_type_rel`;
DROP TABLE IF EXISTS `sys_graph_entity_type`;
DROP TABLE IF EXISTS `sys_graph_relation_type`;
DROP TABLE IF EXISTS `sys_graph_file_chunk`;
DROP TABLE IF EXISTS `sys_graph_file`;
DROP TABLE IF EXISTS `sys_graph`;

-- 2) DROP 知识库/向量/角色表(10)
DROP TABLE IF EXISTS `sys_knowledge_role_graph`;
DROP TABLE IF EXISTS `sys_knowledge_role_kb`;
DROP TABLE IF EXISTS `sys_knowledge_role_model`;
DROP TABLE IF EXISTS `sys_knowledge_role_category_rel`;
DROP TABLE IF EXISTS `sys_knowledge_role_category`;
DROP TABLE IF EXISTS `sys_knowledge_role`;
DROP TABLE IF EXISTS `sys_knowledge_file`;
DROP TABLE IF EXISTS `sys_knowledge_config`;
DROP TABLE IF EXISTS `sys_knowledge_base`;
DROP TABLE IF EXISTS `sys_prompt_template`;

-- 3) 清理菜单:图谱树 + 知识库树 + 知识角色树(全部)
--   先按权限串删所有子菜单/按钮(graph:* / ai:knowledge:* / ai:role:* / ai:prompt:* / ai:graph:detail)
DELETE FROM `sys_menu`
 WHERE `perms` LIKE 'graph:%'
    OR `perms` LIKE 'ai:knowledge:%'
    OR `perms` LIKE 'ai:role:%'
    OR `perms` LIKE 'ai:prompt:%'
    OR `perms` = 'ai:graph:detail';
--   再删 M 根节点(空 perms)与两个异常 perms 的图谱子菜单(perms='graph' / 'icon-experiment')
DELETE FROM `sys_menu` WHERE `menu_id` IN (2056, 2060, 2066, 2079);

-- 4) 清理 sys_config 图谱参数
DELETE FROM `sys_config` WHERE `config_key` IN
  ('graph.knowledgeBase.maxCount', 'role.graph.max_count', 'graph.traversal.depth');
