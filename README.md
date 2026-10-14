# Aboci 美业助手

面向美业场景的 AI 文案与图片创作项目，包含微信小程序、管理后台和 Java 后端。

## 功能

- AI 对话、文案生成、文生图与参考图编辑。
- Luxai 对话及出图模型接入、模型选择与豆子计费。
- 对话记忆、多会话并行、任务状态恢复。
- 管理后台配置模型、价格及小程序业务功能。

## 目录

| 目录 | 内容 |
| --- | --- |
| `hagent/` | 微信小程序，uni-app / Vue 3 |
| `aurora-adui/` | 管理后台，Vue 3 / TypeScript / Vite |
| `aurora-admin/` | 后端启动模块、业务接口、数据库迁移 |
| `aurora-ai/` | 模型适配、对话记忆、出图任务队列 |
| `aurora-common/`、`aurora-framework/` | 公共组件与基础框架 |
| `aurora-test/` | 测试模块 |
| `sql/` | 数据库初始化结构与系统种子数据 |
| `deploy/` | Docker 部署脚本及说明 |
| `文档/` | 接口、功能验证及发布记录 |

## 运行环境

- Java 21、Maven（可使用项目自带 `./mvnw`）。
- MySQL 8、Redis。
- Node.js 与 npm（管理后台及前端依赖）。
- HBuilderX、微信开发者工具（微信小程序）。

## 配置与部署

复制 `.env.example` 为 `.env`，填写数据库、Redis、令牌签名及数据加密配置。
Docker 部署方式见 [部署指南](deploy/README.md)。模型 API 密钥与微信密钥需在部署环境单独配置。

管理后台开发：

```bash
cd aurora-adui
npm ci
npm run dev
```

小程序开发：在 `hagent/` 安装 npm 依赖后，使用 HBuilderX 打开该目录并运行到微信开发者工具。
发行构建使用 `hagent/utils/config.js` 中的生产地址 `https://wxagentshow.mguit.com`；开发运行使用本机后端地址。

数据库升级约定见 [迁移说明](aurora-admin/src/main/resources/db/migration/README.md)。

## 仓库范围

仓库保存完整源码、静态资源、依赖锁文件、数据库迁移和项目文档。
本私有仓库同时保存原始交付文档，文档中包含账号、密钥等配置记录，仅供获授权的项目成员使用。
本地运行配置（如 `.env`、`application-dev.yml`）、数据库备份、日志、依赖目录和编译产物不纳入版本管理。

原始交付文档：

- [项目信息与配置记录](<黄总agent项目信息记录(1).docx>)
- [交接说明](文档/交接说明.docx)
- [接口文档](文档/接口文档.docx)

最近的功能与发布记录：

- [新增功能实施与验证](文档/新增功能实施与验证-20261013.md)
- [对话记忆与多会话回归](文档/对话记忆与多会话回归-20261013.md)
- [线上发布记录](文档/线上发布记录-20261014.md)
