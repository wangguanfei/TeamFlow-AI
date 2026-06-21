package com.teamflow.ai.modules.ai.dto;

import java.util.List;

/**
 * 批量操作的 ID 列表请求体，用于批量删除会话、消息等场景。
 */
public record IdListRequest(List<Long> ids) {
}
