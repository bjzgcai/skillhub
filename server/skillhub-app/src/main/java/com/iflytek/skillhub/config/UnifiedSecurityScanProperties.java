package com.iflytek.skillhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "skillhub.security.unified-scan")
public class UnifiedSecurityScanProperties {

    private boolean enabled = false;
    private String baseUrl = "http://skill-security-scanner:8020";
    private String syncScanPath = "/v1/scans:sync";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 60000;
    private int maxFindings = 20;
    private boolean failClosed = true;
    private boolean blockManualReview = false;
    private boolean blockWarn = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getSyncScanPath() {
        return syncScanPath;
    }

    public void setSyncScanPath(String syncScanPath) {
        this.syncScanPath = syncScanPath;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getMaxFindings() {
        return maxFindings;
    }

    public void setMaxFindings(int maxFindings) {
        this.maxFindings = maxFindings;
    }

    public boolean isFailClosed() {
        return failClosed;
    }

    public void setFailClosed(boolean failClosed) {
        this.failClosed = failClosed;
    }

    public boolean isBlockManualReview() {
        return blockManualReview;
    }

    public void setBlockManualReview(boolean blockManualReview) {
        this.blockManualReview = blockManualReview;
    }

    public boolean isBlockWarn() {
        return blockWarn;
    }

    public void setBlockWarn(boolean blockWarn) {
        this.blockWarn = blockWarn;
    }
}
