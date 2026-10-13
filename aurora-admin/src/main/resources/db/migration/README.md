# Flyway 数据库迁移目录

本目录存放**增量**数据库迁移脚本,由 Flyway 在应用启动时自动按版本顺序执行。

## 版本约定
- **V1 = 基线**:整库的基线 schema 是 `sql/aurora_framework_full_*.sql`(首次部署手动导入)。
  导入后应用首次启动时,Flyway(`baseline-on-migrate=true` / `baseline-version=1`)会把当前库标记为 V1,**不重跑、不动存量数据**。
- **增量从 V2 起**:每次新功能涉及的 schema 变更(新增表、加字段等),新建一个迁移脚本:
  ```
  V2__add_xxx_table.sql
  V3__alter_yyy_add_field.sql
  ...
  ```
  文件名格式 `V{版本}__{描述}.sql`(双下划线),随功能代码一起提交。

## 迭代部署如何保证存量数据不丢
- 迭代脚本**只写增量**(`CREATE TABLE` 新表、`ALTER TABLE ... ADD` 新字段),**禁止** `DROP`/`TRUNCATE` 已有业务表。
- 应用启动时 Flyway 只执行 `flyway_schema_history` 里没记录过的新版本,已跑过的不重复、已有数据不触碰。
- 部署前仍应 `mysqldump` 备份生产库兜底。

## 首次部署 vs 迭代部署
| 场景 | 数据库操作 |
|---|---|
| 首次部署 | 手动导入 `sql/aurora_framework_full_*.sql` → 启动(Flyway 自动 baseline V1) |
| 迭代部署 | 不导库;新增量脚本已在本目录 → 启动(Flyway 自动执行 V2+) |
