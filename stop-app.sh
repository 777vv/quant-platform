#!/bin/sh
# 停止本机正在运行的个人量化投资助手应用（释放 quant-web-1.0.0.jar 的文件锁，解决 mvn clean 删不掉的问题）
# 用法：sh stop-app.sh   （Git Bash / WSL / Linux 通用）
PORT="${1:-8080}"
PIDS=$(netstat -ano 2>/dev/null | grep ":$PORT.*LISTENING" | awk '{print $5}' | sort -u)
if [ -z "$PIDS" ]; then
  echo "端口 $PORT 无监听进程，无需停止"
  exit 0
fi
for pid in $PIDS; do
  case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) taskkill //F //PID "$pid" && echo "已终止 PID $pid" ;;
    *) kill -9 "$pid" && echo "已终止 PID $pid" ;;
  esac
done
sleep 1
netstat -ano 2>/dev/null | grep ":$PORT.*LISTENING" >/dev/null \
  && echo "警告：端口 $PORT 仍被占用，请手动检查" \
  || echo "端口 $PORT 已释放，可以执行 mvn clean package"
