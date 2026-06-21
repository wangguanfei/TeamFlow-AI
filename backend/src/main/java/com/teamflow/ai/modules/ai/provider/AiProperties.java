package com.teamflow.ai.modules.ai.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI Provider 配置属性，从环境变量或 application.yml 读取（前缀 teamflow.ai）。
 * <p>
 * 对应的环境变量：AI_PROVIDER、AI_BASE_URL、AI_API_KEY、AI_MODEL。
 * apiKey 为空时整个系统自动回退 MockAiProvider，不影响演示。
 */
@ConfigurationProperties(prefix = "teamflow.ai")
public class AiProperties {

    /** 供应商标识，当前主要用于状态展示；真实调用路径由 baseUrl/apiKey 是否配置决定。 */
    private String provider = "mock";
    /** OpenAI-compatible Chat Completions 基础地址。 */
    private String baseUrl = "";
    /** 模型服务 API Key；为空时自动进入 Mock 模式。 */
    private String apiKey = "";
    /** 默认聊天模型，前端未指定 model 时使用。 */
    private String model = "deepseek-chat";
    /** 演示账号每日 AI 调用上限，防止公开演示账号被刷爆上游额度。 */
    private int demoDailyLimit = 100;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getDemoDailyLimit() { return demoDailyLimit; }
    public void setDemoDailyLimit(int demoDailyLimit) { this.demoDailyLimit = demoDailyLimit; }
}
