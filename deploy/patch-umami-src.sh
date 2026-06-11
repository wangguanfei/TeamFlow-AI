#!/usr/bin/env bash
# 给 umami-src/Dockerfile 注入国内镜像源。
# 背景：腾讯云服务器访问 registry.npmjs.org 单请求 15~45s，pnpm install 必超时；
#       npmmirror 的个别老包 tarball 被重新打包，integrity 与 lockfile 不符
#       （如 intl-messageformat-parser-5.5.1 报 ERR_PNPM_TARBALL_INTEGRITY），
#       故 npm registry 用腾讯云镜像（透明代理官方 tarball，字节一致），
#       prisma engine 二进制仍走 npmmirror（腾讯镜像无此产物）。
#       另：Dockerfile 装的是最新 pnpm，pnpm 10 默认拒绝依赖的 build scripts
#       （ERR_PNPM_IGNORED_BUILDS），lockfile 是 9.0 格式，固定 pnpm@9。
# 用法：clone umami 源码后执行（幂等，可重复执行刷新补丁）：
#   git clone --depth 1 --branch v2.20.2 git@github.com:umami-software/umami.git umami-src
#   bash deploy/patch-umami-src.sh
set -euo pipefail
cd "$(dirname "$0")/.."

DF=umami-src/Dockerfile
[ -f "$DF" ] || { echo "错误：$DF 不存在，请先 clone umami 源码到 umami-src/"; exit 1; }
# 重复执行时先恢复原始 Dockerfile 再打补丁
[ -f "$DF.bak" ] && cp "$DF.bak" "$DF"

sed -i.bak \
  -e 's#^FROM node:22-alpine AS deps$#&\nENV PRISMA_ENGINES_MIRROR=https://registry.npmmirror.com/-/binary/prisma#' \
  -e 's#^FROM node:22-alpine AS builder$#&\nENV PRISMA_ENGINES_MIRROR=https://registry.npmmirror.com/-/binary/prisma#' \
  -e 's#RUN npm install -g pnpm#RUN npm config set registry https://mirrors.cloud.tencent.com/npm/ \&\& npm install -g pnpm@9#g' \
  "$DF"

echo "补丁完成，diff："
diff "$DF.bak" "$DF" || true
