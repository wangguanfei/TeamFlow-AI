#!/usr/bin/env bash
# 给 umami-src/Dockerfile 注入国内镜像源。
# 背景：腾讯云服务器访问 registry.npmjs.org 单请求 15~45s，pnpm install 必超时；
#       prisma postinstall 还要从国外下载 engine 二进制。
# 用法：clone umami 源码后执行一次（幂等）：
#   git clone --depth 1 --branch v2.20.2 git@github.com:umami-software/umami.git umami-src
#   bash deploy/patch-umami-src.sh
set -euo pipefail
cd "$(dirname "$0")/.."

DF=umami-src/Dockerfile
[ -f "$DF" ] || { echo "错误：$DF 不存在，请先 clone umami 源码到 umami-src/"; exit 1; }
if grep -q npmmirror "$DF"; then
  echo "umami-src/Dockerfile 已打过镜像源补丁，跳过"
  exit 0
fi

sed -i.bak \
  -e 's#^FROM node:22-alpine AS deps$#&\nENV PRISMA_ENGINES_MIRROR=https://registry.npmmirror.com/-/binary/prisma#' \
  -e 's#^FROM node:22-alpine AS builder$#&\nENV PRISMA_ENGINES_MIRROR=https://registry.npmmirror.com/-/binary/prisma#' \
  -e 's#RUN npm install -g pnpm#RUN npm config set registry https://registry.npmmirror.com \&\& npm install -g pnpm#g' \
  "$DF"

echo "补丁完成，diff："
diff "$DF.bak" "$DF" || true
