#!/usr/bin/env bash
#
# 本地启动后端（带真实 AI Key），避免每次重启忘记注入而退回 Mock。
#
# 用法：
#   ./start-local.sh           # 用现有 jar 重启
#   ./start-local.sh --build   # 先 mvn package 再重启
#
# 前置：在你的 ~/.zshrc 中导出真实 key（脚本本身不含 key）：
#   export AI_API_KEY=sk-你的真实key
# 然后 `source ~/.zshrc` 或新开终端。
#
set -euo pipefail

cd "$(dirname "$0")"

JAR="target/teamflow-ai-backend-0.1.0-SNAPSHOT.jar"
PORT=8080

# AI 配置：key 必须来自环境变量，其余给本地默认值
: "${AI_API_KEY:?未设置 AI_API_KEY，请先在 ~/.zshrc 中 export AI_API_KEY=你的key 并 source}"
export AI_PROVIDER="${AI_PROVIDER:-deepseek}"
export AI_BASE_URL="${AI_BASE_URL:-https://api.deepseek.com/v1}"
export AI_MODEL="${AI_MODEL:-deepseek-chat}"

# 部署功能配置（可选，默认关闭）
# 启用方式：export DEPLOY_ENABLED=true && export DEPLOY_SCRIPT_PATH=$(realpath ../deploy.sh)
export DEPLOY_ENABLED="${DEPLOY_ENABLED:-false}"
export DEPLOY_SCRIPT_PATH="${DEPLOY_SCRIPT_PATH:-}"
export DEPLOY_LOG_DIR="${DEPLOY_LOG_DIR:-../logs/deploy}"

# 可选：先打包
if [[ "${1:-}" == "--build" ]]; then
  echo "==> mvn package -DskipTests"
  mvn package -DskipTests -q
fi

if [[ ! -f "$JAR" ]]; then
  echo "未找到 $JAR，请先执行：./start-local.sh --build" >&2
  exit 1
fi

# 停掉旧进程
if lsof -tiTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "==> 停止 :$PORT 上的旧进程"
  kill $(lsof -tiTCP:"$PORT" -sTCP:LISTEN) 2>/dev/null || true
  sleep 3
fi

echo "==> 启动后端 (provider=${AI_PROVIDER}, model=${AI_MODEL})"
nohup java -jar "$JAR" > ../logs/backend.log 2>&1 &

# 等待就绪
echo -n "==> 等待 :$PORT 就绪"
until curl -s -o /dev/null -w "%{http_code}" "http://localhost:$PORT/v3/api-docs" | grep -q 200; do
  echo -n "."
  sleep 2
done
echo " 就绪"
echo "==> 日志：tail -f logs/backend.log"
