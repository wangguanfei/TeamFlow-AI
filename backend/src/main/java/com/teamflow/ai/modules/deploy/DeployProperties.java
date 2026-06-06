package com.teamflow.ai.modules.deploy;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "teamflow.deploy")
public class DeployProperties {

    private boolean enabled = false;
    private String scriptPath = "";
    private String logDir = "logs/deploy";
    private int timeoutMinutes = 30;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getScriptPath() { return scriptPath; }
    public void setScriptPath(String scriptPath) { this.scriptPath = scriptPath; }
    public String getLogDir() { return logDir; }
    public void setLogDir(String logDir) { this.logDir = logDir; }
    public int getTimeoutMinutes() { return timeoutMinutes; }
    public void setTimeoutMinutes(int timeoutMinutes) { this.timeoutMinutes = timeoutMinutes; }
}
