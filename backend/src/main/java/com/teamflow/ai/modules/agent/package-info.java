/**
 * TeamFlow AI 企业助理模块。
 *
 * <p>Agent 和普通聊天共用 AI 会话表，但增加了工具调用能力。模型只负责返回 tool_call；
 * {@code AgentOrchestrator} 负责把工具调用拆成状态事件、工具预览、审计记录和最终执行结果。
 * 读工具可直接执行，写工具必须先生成预览并等待用户确认。</p>
 *
 * <p>新增工具时优先实现 {@code AgentTool}，在 definition 中声明稳定的 name、参数 schema、
 * write 标记和所需权限；业务执行统一经过 {@code AgentToolExecutor}，不要在模型提示词或前端
 * 侧绕过权限和确认流程。</p>
 */
package com.teamflow.ai.modules.agent;
