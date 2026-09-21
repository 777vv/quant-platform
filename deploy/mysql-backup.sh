#!/bin/sh
# MySQL 每日自动备份（M6-02）
# 用途：容器内循环执行 mysqldump，产出 gzip 压缩包到挂载卷 /backup，并按保留天数清理旧备份。
# 设计说明：不依赖宿主机 cron，备份文件落在命名卷中，容器重建不丢；保留下载/恢复能力见 README。
set -eu

# 连接与备份参数（由 docker-compose 注入，均带默认值便于单机手动运行）
: "${DB_HOST:=mysql}"          # MySQL 主机（compose 服务名）
: "${DB_NAME:=quant}"          # 业务库名
: "${DB_USER:=root}"           # 备份账号（需 SELECT/LOCK TABLES 权限，容器内用 root）
: "${BACKUP_DIR:=/backup}"     # 备份输出目录（挂载卷）
: "${RETENTION_DAYS:=14}"      # 保留天数，超期自动删除
: "${INTERVAL_SECONDS:=86400}" # 备份间隔（秒），默认每日一次
: "${BACKUP_ON_START:=true}"   # 启动后是否立即备份一次（避免首次要等 24 小时）

log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

# 首次启动时稍等 MySQL 完全就绪（healthcheck 之外的双保险）
wait_for_mysql() {
  i=0
  while [ "$i" -lt 30 ]; do
    if mysqladmin ping -h "$DB_HOST" --silent >/dev/null 2>&1; then
      return 0
    fi
    i=$((i + 1))
    sleep 2
  done
  log "等待 MySQL 就绪超时，仍继续尝试备份"
  return 0
}

do_backup() {
  ts=$(date '+%Y%m%d-%H%M%S')
  file="$BACKUP_DIR/${DB_NAME}-${ts}.sql.gz"
  log "开始备份 -> $file"
  # --single-transaction：InnoDB 一致性快照，不锁表；--routines/--triggers/--events 保证对象完整
  if mysqldump -h "$DB_HOST" -u "$DB_USER" \
      --single-transaction --routines --triggers --events \
      --default-character-set=utf8mb4 "$DB_NAME" 2>/dev/null | gzip > "$file"; then
    log "备份完成，大小 $(du -h "$file" | cut -f1)"
  else
    log "备份失败，已删除不完整文件"
    rm -f "$file"
  fi
  # 清理超过保留期的备份（-mtime +N 表示 N 天前修改）
  find "$BACKUP_DIR" -type f -name "${DB_NAME}-*.sql.gz" -mtime +"$RETENTION_DAYS" -delete 2>/dev/null || true
}

mkdir -p "$BACKUP_DIR"
wait_for_mysql

if [ "$BACKUP_ON_START" = "true" ]; then
  do_backup
fi

while true; do
  sleep "$INTERVAL_SECONDS"
  do_backup
done
