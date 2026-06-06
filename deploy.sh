#!/bin/bash
# TeamFlow AI 部署脚本
#
# 用法:
#   ./deploy.sh                  # 全量部署（默认）
#   ./deploy.sh all              # 全量部署
#   ./deploy.sh backend          # 只重建后端
#   ./deploy.sh frontend         # 只重建前端
#
# 可选参数（可与上面组合）:
#   --skip-pull                  # 跳过 git pull，直接用本地代码

set -euo pipefail

# ── 颜色 ──────────────────────────────────────────────────
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'
CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'

# ── 默认参数 ──────────────────────────────────────────────
TARGET="all"
SKIP_PULL=false

for arg in "$@"; do
  case $arg in
    all|backend|frontend) TARGET=$arg ;;
    --skip-pull) SKIP_PULL=true ;;
    *) echo "未知参数: $arg  (all|backend|frontend|--skip-pull)"; exit 1 ;;
  esac
done

# ── 目录与日志 ────────────────────────────────────────────
DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$DEPLOY_DIR/logs"
BUILD_LOG="$LOG_DIR/deploy-$(date +%Y%m%d-%H%M%S)-${TARGET}.log"
BRANCH="main"

mkdir -p "$LOG_DIR"

log()  { echo -e "${CYAN}[$(date +%H:%M:%S)]${NC} $*" | tee -a "$BUILD_LOG"; }
ok()   { echo -e "${GREEN}[$(date +%H:%M:%S)] ✓${NC} $*" | tee -a "$BUILD_LOG"; }
warn() { echo -e "${YELLOW}[$(date +%H:%M:%S)] ⚠${NC} $*" | tee -a "$BUILD_LOG"; }
err()  { echo -e "${RED}[$(date +%H:%M:%S)] ✗ $*${NC}" | tee -a "$BUILD_LOG"; exit 1; }
sep()  { echo -e "${BOLD}──────────────────────────────────────────${NC}" | tee -a "$BUILD_LOG"; }

# 模式标签
case $TARGET in
  all)      MODE_LABEL="全量部署" ;;
  backend)  MODE_LABEL="仅后端" ;;
  frontend) MODE_LABEL="仅前端" ;;
esac

sep
echo -e "${BOLD}  TeamFlow AI 部署 [$MODE_LABEL]${NC}" | tee -a "$BUILD_LOG"
echo -e "  $(date '+%Y-%m-%d %H:%M:%S')  日志: $BUILD_LOG" | tee -a "$BUILD_LOG"
sep

cd "$DEPLOY_DIR"

# ── 1. 检查 Docker ────────────────────────────────────────
log "检查 Docker 环境..."
if ! docker info &>/dev/null; then
  warn "当前用户无 Docker 权限，尝试 sudo..."
  DOCKER_CMD="sudo docker"
  COMPOSE_CMD="sudo docker compose"
  if ! sudo docker info &>/dev/null; then
    err "Docker 不可用，请检查安装或将用户加入 docker 组: sudo usermod -aG docker \$USER"
  fi
else
  DOCKER_CMD="docker"
  COMPOSE_CMD="docker compose"
fi
ok "Docker $(${DOCKER_CMD} --version | head -1)"

# ── 2. 检查 .env ──────────────────────────────────────────
log "检查 .env 配置..."
if [[ ! -f "$DEPLOY_DIR/.env" ]]; then
  if [[ -f "$DEPLOY_DIR/.env.example" ]]; then
    cp "$DEPLOY_DIR/.env.example" "$DEPLOY_DIR/.env"
    chmod 600 "$DEPLOY_DIR/.env"
    err ".env 不存在，已从模板创建，请先填写真实值: nano $DEPLOY_DIR/.env"
  else
    err ".env 和 .env.example 都不存在，请手动创建 .env"
  fi
fi

MISSING=()
for key in JWT_SECRET MYSQL_ROOT_PASSWORD MINIO_ROOT_PASSWORD; do
  val=$(grep -E "^${key}=" "$DEPLOY_DIR/.env" | cut -d= -f2- | tr -d '"' | tr -d "'" | xargs)
  if [[ -z "$val" || "$val" == "change-me"* ]]; then
    MISSING+=("$key")
  fi
done
[[ ${#MISSING[@]} -gt 0 ]] && err ".env 未填写必填项: ${MISSING[*]}"
ok ".env 检查通过"

# ── 3. 检查单服务部署时依赖是否在线 ──────────────────────
check_running() {
  local name="$1"
  local status
  status=$($DOCKER_CMD inspect --format='{{.State.Status}}' "$name" 2>/dev/null || echo "absent")
  [[ "$status" == "running" ]]
}

check_healthy() {
  local name="$1"
  local health
  health=$($DOCKER_CMD inspect --format='{{.State.Health.Status}}' "$name" 2>/dev/null || echo "absent")
  [[ "$health" == "healthy" ]]
}

if [[ "$TARGET" == "backend" ]]; then
  log "检查基础服务是否在线..."
  for svc in teamflow-mysql teamflow-redis teamflow-minio; do
    if check_running "$svc"; then
      ok "$svc 运行中"
    else
      err "$svc 未运行，请先执行全量部署: ./deploy.sh all"
    fi
  done
fi

if [[ "$TARGET" == "frontend" ]]; then
  log "检查后端是否在线..."
  if check_healthy "teamflow-backend"; then
    ok "teamflow-backend 健康"
  elif check_running "teamflow-backend"; then
    warn "teamflow-backend 运行中但未通过健康检查，前端可能无法正常工作"
  else
    err "teamflow-backend 未运行，请先部署后端: ./deploy.sh backend"
  fi
fi

# ── 4. 检查端口 80（仅 frontend 相关时）────────────────────
if [[ "$TARGET" == "all" || "$TARGET" == "frontend" ]]; then
  log "检查端口 80..."
  if ss -tlnp 2>/dev/null | grep -q ':80 '; then
    if $DOCKER_CMD ps --filter "name=teamflow-frontend" --filter "status=running" \
        --format '{{.Names}}' 2>/dev/null | grep -q teamflow-frontend; then
      ok "80 端口由 teamflow-frontend 占用，正常"
    else
      warn "80 端口被其他进程占用，可能导致 frontend 启动失败"
      ss -tlnp | grep ':80 ' | tee -a "$BUILD_LOG" || true
    fi
  fi
fi

# ── 5. 拉取代码 ───────────────────────────────────────────
if [[ "$SKIP_PULL" == false ]]; then
  log "拉取最新代码（branch: $BRANCH）..."
  if [[ ! -d "$DEPLOY_DIR/.git" ]]; then
    err "当前目录不是 git 仓库，无法拉取代码"
  fi
  # 用 HTTPS 拉取，避免服务器 SSH 22 端口被代理拦截
  HTTPS_URL="https://github.com/wangguanfei/TeamFlow-AI.git"
  GIT_TERMINAL_PROMPT=0 timeout 60 git fetch "$HTTPS_URL" "$BRANCH:refs/remotes/origin/$BRANCH" >> "$BUILD_LOG" 2>&1 \
    || err "git fetch 失败（超时或网络错误），部署中止。如需跳过拉取请用 --skip-pull"
  git reset --hard "origin/$BRANCH" >> "$BUILD_LOG" 2>&1 \
    || err "git reset 失败，部署中止"
  ok "当前版本: $(git log -1 --format='%h %s' 2>/dev/null)"
else
  ok "跳过 git pull（--skip-pull）"
fi

mkdir -p "$DEPLOY_DIR/logs/backend"

# ── 6. 构建并启动 ─────────────────────────────────────────
sep

build_service() {
  # $1: 服务名（空字符串表示全部）
  local svc="${1:-}"
  local label="${svc:-所有服务}"
  log "构建镜像 [$label]（--progress=plain 显示完整输出）..."
  sep
  # plain 模式：每条 RUN 指令的输出都完整打印，包括 Maven 编译、npm build 等
  $COMPOSE_CMD build --progress=plain $svc 2>&1 | tee -a "$BUILD_LOG"
  sep
  ok "镜像构建完成"
}

case $TARGET in
  all)
    log "全量部署：停止所有容器..."
    $COMPOSE_CMD down --remove-orphans 2>&1 | tee -a "$BUILD_LOG" || true
    ok "旧容器已清理"
    build_service ""
    log "启动所有服务..."
    $COMPOSE_CMD up -d 2>&1 | tee -a "$BUILD_LOG"
    ;;
  backend)
    log "重建后端（不影响其他服务）..."
    build_service "backend"
    log "重启后端容器..."
    $COMPOSE_CMD up -d backend 2>&1 | tee -a "$BUILD_LOG"
    ;;
  frontend)
    log "重建前端（不影响其他服务）..."
    build_service "frontend"
    log "重启前端容器..."
    $COMPOSE_CMD up -d frontend 2>&1 | tee -a "$BUILD_LOG"
    ;;
esac
ok "容器已启动"

# ── 7. 等待健康检查 ───────────────────────────────────────
sep
log "等待服务就绪..."

wait_healthy() {
  local name="$1"
  local timeout="${2:-120}"
  local elapsed=0
  while [[ $elapsed -lt $timeout ]]; do
    local status
    status=$($DOCKER_CMD inspect --format='{{.State.Health.Status}}' "$name" 2>/dev/null || echo "absent")
    case "$status" in
      healthy)   echo ""; ok "$name 健康"; return 0 ;;
      unhealthy) echo ""; warn "$name 不健康  →  docker logs $name --tail 30"; return 1 ;;
      absent)    echo ""; warn "$name 容器不存在"; return 1 ;;
    esac
    sleep 5; elapsed=$((elapsed + 5))
    printf "  %-24s %3ds / %ds\r" "$name" "$elapsed" "$timeout"
  done
  echo ""
  warn "$name 超时 ${timeout}s，当前: $status"
  return 1
}

case $TARGET in
  all)
    wait_healthy "teamflow-mysql"    120
    wait_healthy "teamflow-redis"     60
    wait_healthy "teamflow-backend"  180
    wait_healthy "teamflow-frontend"  60
    ;;
  backend)
    wait_healthy "teamflow-backend"  180
    ;;
  frontend)
    wait_healthy "teamflow-frontend"  60
    ;;
esac

# ── 8. 汇总 ───────────────────────────────────────────────
sep
log "容器状态："
$COMPOSE_CMD ps 2>&1 | tee -a "$BUILD_LOG"
sep

SERVER_IP=$(curl -s --max-time 3 http://ip.sb 2>/dev/null || hostname -I | awk '{print $1}')

echo ""
echo -e "${BOLD}${GREEN}  [$MODE_LABEL] 完成！${NC}"
echo -e "  访问地址:  ${CYAN}http://${SERVER_IP}${NC}"
echo -e "  构建日志:  $BUILD_LOG"
case $TARGET in
  all|backend) echo -e "  后端日志:  docker logs teamflow-backend -f" ;;
  frontend)    echo -e "  前端日志:  docker logs teamflow-frontend -f" ;;
esac
sep
