-- 小程序分享配置(设置页「分享设置」卡片可改;C 端经 /api/mobile/share 免鉴权读取):
--   mobile.share.title = 分享卡片标题(默认取小程序名,可后台改)
--   mobile.share.image = 分享卡片图 /profile URL(空=微信自动用页面截图;会话卡 5:4,朋友圈复用自动裁 1:1)
-- config_id 不显式指定,交由自增分配,避免与既有数据冲突;按现有 config_key/config_value 结构插入。

INSERT INTO `sys_config`
    (`config_name`, `config_key`, `config_value`, `config_type`,
     `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES
    ('小程序分享标题', 'mobile.share.title', '啊播吡 AI 创作', 'Y',
     'admin', NOW(), '', NULL, '分享到微信会话/朋友圈的卡片标题'),
    ('小程序分享卡片图', 'mobile.share.image', '', 'Y',
     'admin', NOW(), '', NULL, '分享卡片图 /profile URL;比例 5:4(建议 500x400);空=微信自动截图');
