# TeamFlow AI 轻量本地向量 RAG 运维说明

## 目标

本方案用于当前 2C2G 服务器上的最小可用真实向量 RAG：

- MySQL 保留知识库文档、切片元数据和异步索引任务。
- Qdrant 存储 512 维 dense 向量和必要 payload。
- `embedding-service` 使用 `BAAI/bge-small-zh-v1.5` 在 CPU 上生成向量。
- 检索采用 Qdrant dense 召回 + MySQL 关键词召回 + RRF 融合。

这不是高并发方案，不部署 BGE-M3，也不部署本地 reranker。

本机 Docker 验证请优先使用 [LOCAL_DOCKER.md](LOCAL_DOCKER.md) 中的 `docker-compose.local.yml`；该配置只容器化 MinIO、Qdrant、Embedding、后端和前端，MySQL/Redis 继续走本机已有服务。

## 资源门槛

启用新的 RAG 服务前，先确认腾讯云服务器上的非业务浏览器服务已经停止：

```bash
systemctl is-active lighthouse-chromium.service || true
sudo -u root XDG_RUNTIME_DIR=/run/user/0 systemctl --user is-active openclaw-gateway.service || true
free -h
```

建议部署前 `MemAvailable` 约 800MB 或更高。

运行中止门槛：如果 `MemAvailable` 低于 250MB，或 swap 持续增长，应暂停本地 embedding 路线。

## 关键环境变量

```bash
RAG_ENABLED=true
RAG_LOCAL_EMBEDDING=true
RAG_MIN_AVAILABLE_MEMORY_MB=250

QDRANT_COLLECTION=teamflow_ai_knowledge_chunks
QDRANT_MEM_LIMIT=384m

EMBEDDING_MODEL=BAAI/bge-small-zh-v1.5
EMBEDDING_DIMENSION=512
EMBEDDING_BATCH_SIZE=1
EMBEDDING_MEM_LIMIT=768m

JAVA_TOOL_OPTIONS="-Xms128m -Xmx256m -Duser.timezone=Asia/Shanghai"
```

## 验收检查

检查容器状态和资源占用：

```bash
docker compose ps
docker stats --no-stream
```

通过后端检查 RAG 状态：

```bash
curl -H "Authorization: Bearer <token>" http://127.0.0.1/api/rag/status
```

期望结果：

- `qdrantAvailable=true`
- `embeddingAvailable=true`
- `memoryGatePassed=true`
- `memAvailableMb >= 250`
- `failedJobs=0`

## 索引重建

重建单个文档：

```bash
curl -X POST -H "Authorization: Bearer <token>" \
  http://127.0.0.1/api/rag/index/documents/<docId>/rebuild
```

重建全部已发布文档，或通过 `?spaceId=` 重建单个知识空间：

```bash
curl -X POST -H "Authorization: Bearer <token>" \
  http://127.0.0.1/api/rag/index/rebuild
```

发布或恢复知识库文档后，系统会自动提交重建任务；删除文档会清理 MySQL 切片并提交 Qdrant 清理任务。

## 回退方式

暂停本地索引 worker：

```bash
RAG_INDEX_WORKER_ENABLED=false
```

完全回退到关键词检索：

```bash
RAG_ENABLED=false
```
