# RAG 上线评估与优化说明

## 上线门槛

- `/api/rag/status` 中 `enabled`、`workerEnabled`、`qdrantAvailable`、`embeddingAvailable`、`memoryGatePassed` 必须为 `true`。
- `failedJobs=0`，`pendingJobs` 不应连续增长，`memAvailableMb` 不低于 `250`。
- 发布、回滚、删除知识文档后，`ai_index_job` 进入 `DONE`，对应 `ai_embedding.index_status` 进入 `READY`。
- 普通文档发布到全部切片 `READY` 的 P95 控制在 5 分钟内，10 篇文档批量重建控制在 20 分钟内。

## 质量指标

- Top1 引用命中率不低于 65%。
- Top3 引用命中率不低于 85%。
- 可回答问题零引用率低于 5%。
- 无答案问题误召回率不高于 10%。
- 人工评分平均不低于 4/5。

## 固定评测集格式

```json
[
  {
    "id": "exact-001",
    "question": "TeamFlow AI 的 RAG 索引使用什么向量库？",
    "spaceId": 1,
    "expectedDocIds": [7],
    "answerable": true
  },
  {
    "id": "reject-001",
    "question": "公司 2029 年未公开财报是多少？",
    "answerable": false
  }
]
```

评测集建议保持 80 到 120 条，覆盖精确事实、流程步骤、跨文档聚合、同义改写和无答案拒答。

## 自动评测

```bash
RAG_EVAL_TOKEN=<token> node scripts/rag-evaluate.mjs rag-eval-dataset.json logs/rag-eval-report.json
```

可选环境变量：

- `RAG_EVAL_BASE_URL`：默认 `http://127.0.0.1:8080/api`。
- `RAG_EVAL_TOKEN`：必填，需有 `ai:chat` 权限。

脚本使用 `/api/ai/knowledge/ask`，不会走 SSE。输出报告包含每条问题的引用、答案、耗时、Top1/Top3 命中结果、零引用标记和聚合指标。

## 权限与索引

RAG 检索按知识空间可见性过滤：

- `PUBLIC`：所有已登录用户可检索。
- `TEAM`：团队负责人或团队成员可检索。
- `PRIVATE`：仅空间负责人可检索。
- `SUPER_ADMIN`：系统级管理员不受空间限制。

Qdrant payload 已包含 `teamId`、`visibility`、`ownerId`。旧索引不会因为缺少这些字段而越权返回，但建议上线后执行一次全量重建，让向量侧元数据和 MySQL 保持一致。

## 后续优化

- 将当前自定义关键词打分升级为 BM25、MySQL FULLTEXT 或 OpenSearch。
- 按 Markdown 标题和段落做语义切片，减少固定长度切片截断上下文。
- 基于评测结果调参 `denseTopK`、`keywordTopK`、`finalTopK`、`denseWeight`、`keywordWeight`。
- 当 2C2G 资源水位接近门槛时，优先迁出 Embedding 服务或升级规格，再考虑 reranker。
