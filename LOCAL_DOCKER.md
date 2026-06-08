# 本机 Docker 验证说明

本机验证采用 Docker 运行 TeamFlow 的业务容器，尽量贴近腾讯云 compose 部署方式；MySQL 和 Redis 继续使用本机已安装的服务，不再额外启动 MySQL/Redis 容器。

## 当前本机约定

- Docker 引擎：Colima。
- 前端容器：`http://localhost:5173`。
- 后端容器：`http://localhost:8080`。
- MinIO：API `http://localhost:19000`，Console `http://localhost:19001`。
- Qdrant：`http://localhost:6333`。
- Embedding 服务：`http://localhost:18000`。
- MySQL/Redis：容器通过 `host.docker.internal` 访问宿主机已有服务。

## 首次准备

如果 Docker/Colima 没有启动：

```bash
colima start --cpu 2 --memory 4 --disk 40
docker version
docker compose version
```

准备本机环境变量：

```bash
cp local.env.example .env.local
```

按本机实际情况修改 `.env.local`，重点确认：

- `LOCAL_MYSQL_PASSWORD`
- `LOCAL_MYSQL_DATABASE`
- `AI_API_KEY`

## 启动

```bash
docker compose --env-file .env.local -f docker-compose.local.yml up -d --build
```

启动完成后检查：

```bash
docker compose --env-file .env.local -f docker-compose.local.yml ps
curl -fsS http://localhost:8080/healthz
curl -fsS http://localhost:5173/healthz
curl -fsS http://localhost:8080/api/rag/status
```

## 停止

```bash
docker compose --env-file .env.local -f docker-compose.local.yml down
```

如需清理本机 Docker 生成的 MinIO、Qdrant、Embedding 模型缓存数据：

```bash
docker compose --env-file .env.local -f docker-compose.local.yml down -v
```

## 注意事项

- 本机不要再同时启动 `backend/start-local.sh` 和 `frontend/npm run dev`，否则会占用 `8080` 或 `5173`。
- 如果后端容器连不上本机 MySQL，先确认本机 MySQL 正在监听 `3306`，再确认账号允许从 Docker 虚拟机访问。
- 如果 `host.docker.internal` 在当前 Docker 引擎下不可用，可把 `.env.local` 里的 `LOCAL_MYSQL_HOST` 和 `LOCAL_REDIS_HOST` 临时改成 `host.lima.internal` 后重试。
- Embedding 服务首次启动会下载模型，耗时取决于网络；后续模型缓存保存在 Docker volume 中。
- 如果本机内存吃紧，可以先把 `.env.local` 中 `RAG_LOCAL_EMBEDDING=false`，改用云端 Embedding 后再启动。
