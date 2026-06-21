package com.teamflow.ai.modules.agent.tool;

import com.teamflow.ai.common.exception.BusinessException;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.modules.ai.dto.AiReferenceItem;
import com.teamflow.ai.modules.ai.service.AiKnowledgeIndexService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 知识库检索工具。
 *
 * <p>普通聊天的知识库增强由 AiService 调用；Agent 模式下模型需要主动决定何时 search_knowledge。
 * 工具返回的 references 会被编排器整理成人类可读内容，同时保留 retrievalSource 供排查召回质量。</p>
 */
@Component
public class KnowledgeAgentTools {

    @Component
    public static class SearchKnowledgeTool implements AgentTool {

        private final AiKnowledgeIndexService knowledgeIndexService;

        public SearchKnowledgeTool(AiKnowledgeIndexService knowledgeIndexService) {
            this.knowledgeIndexService = knowledgeIndexService;
        }

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "search_knowledge",
                    "查询企业知识/制度/资料",
                    "检索企业知识库、制度文档和资料片段，返回引用来源，适合回答制度、流程、规范类问题。",
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "query", Map.of("type", "string", "description", "检索问题或关键词"),
                                    "spaceId", Map.of("type", "integer", "description", "知识库空间ID，可选"),
                                    "topK", Map.of("type", "integer", "description", "返回条数，默认5")
                            ),
                            "required", List.of("query")
                    ),
                    false,
                    "knowledge:view"
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> arguments, UserPrincipal user) {
            String query = stringArg(arguments, "query");
            if (query == null || query.isBlank()) {
                throw new BusinessException("请提供要查询的知识库问题");
            }
            Long spaceId = longArg(arguments, "spaceId");
            int topK = intArg(arguments, "topK", 5);
            // 始终传入当前用户 ID，让 RAG 服务按团队/空间权限过滤引用，避免 Agent 越权读知识库。
            List<AiReferenceItem> references = knowledgeIndexService.searchReferences(query, spaceId, Math.max(1, Math.min(topK, 10)), user.getUserId());
            List<Map<String, Object>> items = references.stream().map(this::referenceItem).toList();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("query", query.trim());
            data.put("spaceId", spaceId);
            data.put("references", items);
            data.put("count", items.size());
            String summary = items.isEmpty()
                    ? "未在企业知识库中找到匹配资料"
                    : "已找到 " + items.size() + " 条企业知识库资料";
            return ToolResult.ok(summary, data, null, null, "/knowledge");
        }

        private Map<String, Object> referenceItem(AiReferenceItem reference) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("docId", reference.docId());
            item.put("title", reference.title());
            item.put("snippet", reference.snippet());
            item.put("spaceId", reference.spaceId());
            item.put("spaceName", reference.spaceName());
            item.put("versionNo", reference.versionNo());
            item.put("chunkIndex", reference.chunkIndex());
            item.put("score", reference.score());
            item.put("retrievalSource", reference.retrievalSource());
            return item;
        }
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object value = args == null ? null : args.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Long longArg(Map<String, Object> args, String key) {
        Object value = args == null ? null : args.get(key);
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static int intArg(Map<String, Object> args, String key, int fallback) {
        Object value = args == null ? null : args.get(key);
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
