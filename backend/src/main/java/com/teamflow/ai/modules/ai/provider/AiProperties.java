package com.teamflow.ai.modules.ai.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "teamflow.ai")
public class AiProperties {

    private String provider = "mock";
    private String baseUrl = "";
    private String apiKey = "";
    private String model = "deepseek-chat";

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}
