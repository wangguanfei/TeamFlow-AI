/**
 * TeamFlow AI 基础聊天与 RAG 模块。
 *
 * <p>核心入口是 {@code AiService}：负责会话、消息、提示词、流式输出和模型供应商调用。
 * 知识库检索与索引由 {@code AiKnowledgeIndexService} 负责，它把已发布知识文档切块后写入
 * MySQL 元数据表和 Qdrant 向量库，并在聊天时返回带来源的引用片段。</p>
 *
 * <p>Provider 层使用 OpenAI-compatible 协议，配置缺失或上游不可用时降级到 MockAI，
 * 因此本模块可以同时支持本地演示、Docker 验证和线上真实模型。</p>
 */
package com.teamflow.ai.modules.ai;
