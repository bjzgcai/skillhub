package com.iflytek.skillhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "skillhub.remote-registry.clawhub")
public class RemoteRegistryProperties {

    private boolean enabled = false;
    private String baseUrl = "https://clawhub.ai";
    private String apiBasePath = "/api/v1";
    private String userAgent = "SkillHub-RemoteRegistry/0.1";
    private String token;
    private String authScheme = "Bearer";

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

    public String getApiBasePath() {
        return apiBasePath;
    }

    public void setApiBasePath(String apiBasePath) {
        this.apiBasePath = apiBasePath;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getAuthScheme() {
        return authScheme;
    }

    public void setAuthScheme(String authScheme) {
        this.authScheme = authScheme;
    }

    public String buildAuthorizationHeader() {
        if (token == null || token.isBlank()) {
            return null;
        }
        if (authScheme == null || authScheme.isBlank()) {
            return token;
        }
        return authScheme + " " + token;
    }

}
