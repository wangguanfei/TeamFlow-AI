#!/usr/bin/env node

import { readFile, writeFile } from 'node:fs/promises'
import { performance } from 'node:perf_hooks'

const [, , datasetPath, outputPath] = process.argv
const baseUrl = (process.env.RAG_EVAL_BASE_URL || 'http://127.0.0.1:8080/api').replace(/\/+$/, '')
const token = process.env.RAG_EVAL_TOKEN

if (!datasetPath) {
  console.error('Usage: RAG_EVAL_TOKEN=<token> node scripts/rag-evaluate.mjs <dataset.json> [output.json]')
  process.exit(1)
}

if (!token) {
  console.error('RAG_EVAL_TOKEN is required')
  process.exit(1)
}

const dataset = JSON.parse(await readFile(datasetPath, 'utf8'))
if (!Array.isArray(dataset)) {
  console.error('Dataset must be a JSON array')
  process.exit(1)
}

// 数据集每条样例形如：
// { id, question, spaceId?, expectedDocIds?, answerable?, model? }。
// 脚本直接调用线上/本地 /ai/knowledge/ask，用真实后端链路评估召回质量。
const results = []
for (const item of dataset) {
  const startedAt = performance.now()
  const response = await fetch(`${baseUrl}/ai/knowledge/ask`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({
      spaceId: item.spaceId,
      message: item.question,
      mode: 'KNOWLEDGE',
      useKnowledge: true,
      model: item.model,
    }),
  })
  const elapsedMs = Math.round(performance.now() - startedAt)
  const payload = await readResponse(response)
  const data = payload?.data ?? payload
  const references = Array.isArray(data?.references) ? data.references : []
  const expectedDocIds = new Set((item.expectedDocIds || []).map(Number))
  const topDocIds = references.map((reference) => Number(reference.docId)).filter(Boolean)
  const answer = data?.assistantMessage?.content || ''
  const answerable = item.answerable !== false
  // expectedHitRank 只看引用文档命中，不让模型生成文本的好坏影响召回指标。
  const expectedHitRank = topDocIds.findIndex((docId) => expectedDocIds.has(docId))

  results.push({
    id: item.id,
    question: item.question,
    answerable,
    status: response.status,
    elapsedMs,
    expectedDocIds: [...expectedDocIds],
    topDocIds,
    references,
    answer,
    top1Hit: expectedHitRank === 0,
    top3Hit: expectedHitRank >= 0 && expectedHitRank < 3,
    zeroReference: references.length === 0,
    unexpectedAnswered: !answerable && references.length > 0,
    error: response.ok ? undefined : payload,
  })
}

const metrics = summarize(results)
const report = {
  generatedAt: new Date().toISOString(),
  baseUrl,
  total: results.length,
  metrics,
  results,
}

if (outputPath) {
  await writeFile(outputPath, JSON.stringify(report, null, 2) + '\n')
} else {
  console.log(JSON.stringify(report, null, 2))
}

async function readResponse(response) {
  const text = await response.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

function summarize(items) {
  const answerable = items.filter((item) => item.answerable)
  const unanswerable = items.filter((item) => !item.answerable)
  const ok = items.filter((item) => item.status >= 200 && item.status < 300)
  return {
    // successRate 衡量接口可用性；top1/top3 衡量可回答问题的召回质量；
    // unanswerableUnexpectedReferenceRate 衡量不可回答问题是否错误给了引用。
    successRate: ratio(ok.length, items.length),
    p95LatencyMs: percentile(ok.map((item) => item.elapsedMs), 0.95),
    top1HitRate: ratio(answerable.filter((item) => item.top1Hit).length, answerable.length),
    top3HitRate: ratio(answerable.filter((item) => item.top3Hit).length, answerable.length),
    zeroReferenceRate: ratio(answerable.filter((item) => item.zeroReference).length, answerable.length),
    unanswerableUnexpectedReferenceRate: ratio(
      unanswerable.filter((item) => item.unexpectedAnswered).length,
      unanswerable.length
    ),
  }
}

function ratio(value, total) {
  return total > 0 ? Number((value / total).toFixed(4)) : 0
}

function percentile(values, p) {
  if (!values.length) return 0
  const sorted = [...values].sort((a, b) => a - b)
  const index = Math.min(sorted.length - 1, Math.ceil(sorted.length * p) - 1)
  return sorted[index]
}
