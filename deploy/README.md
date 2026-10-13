# 极光框架 · Docker 部署指南

一套 Docker 编排,支持**首次部署**与**迭代部署**两种模式,核心目标:迭代时**存量数据零丢失**(数据卷持久 + Flyway 只跑增量)。

## 组成
- `Dockerfile`(根目录):后端镜像(Maven 构建 → JRE21 跑 `aurora-admin -exec.jar`)
- `aurora-adui/Dockerfile` + `aurora-adui/nginx.conf`:前端镜像(Node 构建 → nginx 托管 + 反代 `/api`、SSE)
- `docker-compose.yml`:app + web + mysql + redis(数据库、Redis、上传文件均走数据卷持久)
- `.env.example`:全部密钥/配置(复制为 `.env` 改值,**勿提交**)
- `deploy/deploy.sh`:`init` / `update` / `backup`

## 前置
1. 服务器装 Docker + Docker Compose(宝塔「Docker」插件即可)。
2. 拉代码:`git clone <gitee 地址> && cd <项目>`。
3. `cp .env.example .env`,填写强密钥(`AURORA_TOKEN_SECRET` 必须 ≥64 字节、`MOBILE_TOKEN_SECRET` 必须 ≥32 字节,否则拒启)。

## 首次部署
```bash
./deploy/deploy.sh init
```
- MySQL 空数据卷首次启动 → 自动执行 `sql/aurora_framework_full_*.sql` 建库导入(全量结构 + 系统初始化数据,自带迁移版本记录);
- app 启动 → Flyway 识别到库已在脚本对应的版本,只执行其后新增的迁移(没有则不动);
- 访问 `http://服务器IP:WEB_PORT`(默认 80),`admin` / 初始密码登录后**先改密码**。

> 若用宝塔已装的 MySQL/Redis:删 compose 里对应 service,把 `.env` 的 `MYSQL_HOST` 等指向宝塔地址,并手动把基线 SQL 导入一次。

## 迭代部署(客户增项后,保证存量数据还在)
前提:新功能涉及的库变更,已写成 `aurora-admin/src/main/resources/db/migration/V2+__*.sql`(只 `CREATE`/`ALTER ADD`,禁 `DROP`/`TRUNCATE` 业务表)并随代码提交。
```bash
git pull
./deploy/deploy.sh update
```
- 自动备份数据库 → 重建 app/web 镜像 → 重启;
- **数据库容器/数据卷不动**,app 启动时 Flyway 只执行没跑过的 `V2+` → 只补新表/字段;
- 上传文件、AI 生成图、分销私有文件在数据卷 `app_files`(容器内 `/root/aurora`),重建 app 容器不受影响;
- 存量真实数据完整保留。

## HTTPS / 域名
前端容器对外 80。生产建议用宝塔的 nginx 做 `443(域名+证书) → 容器 WEB_PORT` 反代;或在 `web` 服务加 443 + 证书挂载。

## 回滚
`docker compose` 切回上一镜像 tag + 用 `backup_*.sql` 恢复库。建议每次 `update` 前的自动备份保留。

## 说明
- 2026-10-13 在开发机用 Docker 完整跑通过一次:四个容器正常启动,Flyway 识别库为最新版本,后台可登录,上传文件在重建 app 容器后仍在。当时 Redis 用的是本机已有的 `redis:alpine` 镜像(`redis:7-alpine` 未拉取成功)。上生产前仍建议先在测试服 `init` 跑一遍。
- 前端镜像用 `npx vite build` 构建,不走 `npm run build`(其中的 vue-tsc 与当前 TypeScript 版本不兼容)。
- app 容器设了 `TZ=Asia/Shanghai`;自行改编排时请保留,否则后端写入的时间会按 UTC 计。
- Flyway 迁移约定见 `aurora-admin/src/main/resources/db/migration/README.md`。
