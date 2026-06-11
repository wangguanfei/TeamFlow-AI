#!/usr/bin/env bash
# 给 umami-src 注入国内镜像源并绕过外网依赖。
# 背景（腾讯云服务器出国网络不通/极慢导致的连环坑）：
#   1. registry.npmjs.org 单请求 15~45s，pnpm install 必超时
#      → npm registry 换腾讯云镜像（npmmirror 个别老包 tarball 被重新打包，
#        integrity 与 lockfile 不符，报 ERR_PNPM_TARBALL_INTEGRITY，故不用）
#   2. prisma engine 二进制走 npmmirror 镜像（腾讯镜像无此产物）
#   3. pnpm 10 默认拒绝依赖 build scripts（ERR_PNPM_IGNORED_BUILDS），
#      lockfile 是 9.0 格式 → 固定 pnpm@9
#   4. build-geo 从 raw.githubusercontent.com 下载 GeoLite2 且无超时，会永久挂起
#      → 预置 geo/GeoLite2-City.mmdb 后跳过该步骤，runner 阶段显式 COPY
#   5. cypress postinstall 从 download.cypress.io 下载 ~200MB 二进制（构建用不到）
#      → CYPRESS_INSTALL_BINARY=0 跳过
#
# 用法（幂等，可重复执行刷新补丁）：
#   git clone --depth 1 --branch v2.20.2 git@github.com:umami-software/umami.git umami-src
#   # 在能访问 GitHub 的机器下载 GeoLite2-City.tar.gz 解出 mmdb，放到：
#   #   umami-src/geo/GeoLite2-City.mmdb
#   # 下载地址: https://raw.githubusercontent.com/GitSquared/node-geolite2-redist/master/redist/GeoLite2-City.tar.gz
#   bash deploy/patch-umami-src.sh
set -euo pipefail
cd "$(dirname "$0")/.."

DF=umami-src/Dockerfile
PJ=umami-src/package.json
[ -f "$DF" ] || { echo "错误：$DF 不存在，请先 clone umami 源码到 umami-src/"; exit 1; }
[ -f umami-src/geo/GeoLite2-City.mmdb ] || {
  echo "错误：umami-src/geo/GeoLite2-City.mmdb 不存在。"
  echo "服务器连不上 raw.githubusercontent.com，需在本机下载后 scp 过来，见脚本头部注释。"
  exit 1
}

# 重复执行时先恢复原始文件再打补丁
[ -f "$DF.bak" ] && cp "$DF.bak" "$DF"
[ -f "$PJ.bak" ] && cp "$PJ.bak" "$PJ"

sed -i.bak \
  -e 's#^FROM node:22-alpine AS deps$#&\nENV PRISMA_ENGINES_MIRROR=https://registry.npmmirror.com/-/binary/prisma\nENV CYPRESS_INSTALL_BINARY=0#' \
  -e 's#^FROM node:22-alpine AS builder$#&\nENV PRISMA_ENGINES_MIRROR=https://registry.npmmirror.com/-/binary/prisma#' \
  -e 's#RUN npm install -g pnpm#RUN npm config set registry https://mirrors.cloud.tencent.com/npm/ \&\& npm install -g pnpm@9#g' \
  -e 's#^COPY --from=builder /app/scripts ./scripts$#&\nCOPY --from=builder --chown=nextjs:nodejs /app/geo ./geo#' \
  "$DF"

# geo 库已预置，构建链跳过 build-geo（仅改 build-docker 行，不动 build-geo 自身定义）
sed -i.bak '/"build-docker"/s/ build-geo//' "$PJ"

echo "补丁完成，Dockerfile diff："
diff "$DF.bak" "$DF" || true
echo "package.json diff："
diff "$PJ.bak" "$PJ" || true
