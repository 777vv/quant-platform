#!/bin/sh
# 一键构建（解决 Windows 下 jar 被运行中应用锁定导致 clean 失败的问题）：
# 先自动停止 8080 端口的应用，再执行 mvn clean package。额外参数会透传给 mvn。
# 用法：sh build.sh            （等价 mvn clean package -DskipTests）
#       sh build.sh verify     （等价 mvn clean package -DskipTests verify）
cd "$(dirname "$0")"

sh stop-app.sh

mvn clean package -DskipTests "$@"
