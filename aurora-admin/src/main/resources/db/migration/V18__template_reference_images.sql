-- 案例/模板:图生图模板预置参考图
-- template 增加 reference_images:JSON 数组字符串,存图生图模板预置的参考图 /profile URL(1~4);
-- 仅 image 类且 params.mode=img2img 的模板用;小程序「用此模板」带入时预填(用户可改/增删)。
-- 约束:Flyway 仅写增量,禁 DROP/TRUNCATE;字段可空,存量行不受影响。
ALTER TABLE `template`
    ADD COLUMN `reference_images` TEXT DEFAULT NULL COMMENT 'JSON 数组:图生图模板预置参考图 /profile URL(1~4)' AFTER `result_media`;
