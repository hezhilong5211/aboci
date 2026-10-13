#!/usr/bin/env bash
# ============================================================
# 极光框架部署脚本。在仓库根目录的服务器上执行。
#   ./deploy/deploy.sh init     首次部署(起全栈,MySQL 自动导入基线,Flyway baseline V1)
#   ./deploy/deploy.sh update   迭代部署(备份库 → 重建 app/web → Flyway 自动跑 V2+;存量数据不动)
#   ./deploy/deploy.sh backup   仅备份数据库
# 前置:已装 docker + docker compose;已 cp .env.example .env 并改好密钥。
# ============================================================
set -euo pipefail
cd "$(dirname "$0")/.."   # 切到仓库根目录

[ -f .env ] || { echo "✗ 缺少 .env,请先:cp .env.example .env 并填写密钥"; exit 1; }
set -a; . ./.env; set +a

CMD="${1:-update}"

backup_db() {
  local f="backup_$(date +%Y%m%d_%H%M%S).sql"
  echo "→ 备份数据库到 $f ..."
  docker compose exec -T mysql sh -c "exec mysqldump -uroot -p\"$MYSQL_PASSWORD\" \"$MYSQL_DB\"" > "$f" \
    && echo "✓ 备份完成:$f" || echo "⚠ 备份跳过(库未启动?首次部署可忽略)"
}

case "$CMD" in
  init)
    echo "=== 首次部署 ==="
    docker compose up -d --build
    echo "✓ 全栈已启动。MySQL 首次自动导入基线 schema,app 启动后 Flyway 自动 baseline 为 V1。"
    echo "  查看日志:docker compose logs -f app"
    ;;
  update)
    echo "=== 迭代部署 ==="
    backup_db
    echo "→ 重建并重启 app / web(数据库等有状态容器不动)..."
    docker compose up -d --build app web
    echo "✓ 完成。app 启动时 Flyway 自动执行新增量迁移(V2+),只补新表/字段,存量数据保留。"
    echo "  如需回滚:docker compose up -d 旧镜像 + 用备份 SQL 恢复库。"
    ;;
  backup)
    backup_db
    ;;
  *)
    echo "用法: $0 [init|update|backup]"; exit 1
    ;;
esac
