#!/usr/bin/env bash
# TeamFlow AI 部署代理 - 在宿主机运行，独立于 Docker 容器
# 监听触发文件目录，执行 deploy.sh，输出写入日志文件
#
# 安装为 systemd 服务（在项目目录下执行一次即可）：
#   sudo bash deploy-agent.sh --install
#
# 手动运行（测试）：
#   bash deploy-agent.sh

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEPLOY_SCRIPT="$SCRIPT_DIR/deploy.sh"
# 与后端容器共享卷对应：容器内 /app/logs/deploy ↔ 宿主机 logs/backend/deploy
TRIGGER_DIR="$SCRIPT_DIR/logs/backend/deploy/triggers"
LOG_BASE_DIR="$SCRIPT_DIR/logs/backend/deploy"

# ── 安装为 systemd 服务 ────────────────────────────────────────
if [[ "${1:-}" == "--install" ]]; then
  # sudo 运行时 $USER 是 root，用 $SUDO_USER 获取实际用户
  ACTUAL_USER="${SUDO_USER:-$USER}"
  ACTUAL_HOME=$(getent passwd "$ACTUAL_USER" | cut -d: -f6)

  SERVICE_FILE="/etc/systemd/system/teamflow-deploy-agent.service"
  cat > "$SERVICE_FILE" << EOF
[Unit]
Description=TeamFlow AI Deploy Agent
After=network.target docker.service

[Service]
Type=simple
User=$ACTUAL_USER
WorkingDirectory=$SCRIPT_DIR
ExecStart=/usr/bin/bash $SCRIPT_DIR/deploy-agent.sh
Restart=always
RestartSec=5
Environment=HOME=$ACTUAL_HOME
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF
  systemctl daemon-reload
  systemctl enable teamflow-deploy-agent
  systemctl start teamflow-deploy-agent
  echo "✅ deploy-agent 已安装并启动（用户: $ACTUAL_USER，HOME: $ACTUAL_HOME）"
  echo "   查看日志: journalctl -u teamflow-deploy-agent -f"
  exit 0
fi

# ── 主循环 ────────────────────────────────────────────────────
mkdir -p "$TRIGGER_DIR" "$LOG_BASE_DIR"
echo "[deploy-agent] 启动，监听: $TRIGGER_DIR"

while true; do
  for trigger in "$TRIGGER_DIR"/*.trigger; do
    [ -f "$trigger" ] || continue

    DEPLOY_ID=""
    TARGET="all"
    SKIP_PULL="false"
    while IFS='=' read -r key val; do
      case "$key" in
        id)       DEPLOY_ID="$val" ;;
        target)   TARGET="$val" ;;
        skipPull) SKIP_PULL="$val" ;;
      esac
    done < "$trigger"
    rm -f "$trigger"

    [ -z "$DEPLOY_ID" ] && continue

    LOG_FILE="$LOG_BASE_DIR/${DEPLOY_ID}.log"
    echo "[deploy-agent] 开始: id=$DEPLOY_ID target=$TARGET skipPull=$SKIP_PULL"

    CMD=("$DEPLOY_SCRIPT" "$TARGET")
    [ "$SKIP_PULL" = "true" ] && CMD+=("--skip-pull")

    {
      "${CMD[@]}" 2>&1
      echo "__EXIT__:$?"
    } > "$LOG_FILE"

    echo "[deploy-agent] 完成: id=$DEPLOY_ID log=$LOG_FILE"
  done
  sleep 1
done
