package com.iflytek.skillhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the trusted admin exchange endpoint used by agent platforms.
 */
@Component
@ConfigurationProperties(prefix = "skillhub.auth.admin-exchange")
public class AdminExchangeProperties {
    private boolean enabled = false;
    private String tokenHash;
    private List<String> allowedSources = new ArrayList<>(List.of("agent-platform"));
    private List<String> defaultScopes = new ArrayList<>(List.of("skill:read", "skill:publish"));
    private List<String> allowedScopes = new ArrayList<>(List.of("skill:read", "skill:publish"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public List<String> getAllowedSources() {
        return allowedSources;
    }

    public void setAllowedSources(List<String> allowedSources) {
        this.allowedSources = allowedSources == null ? new ArrayList<>() : new ArrayList<>(allowedSources);
    }

    public List<String> getDefaultScopes() {
        return defaultScopes;
    }

    public void setDefaultScopes(List<String> defaultScopes) {
        this.defaultScopes = defaultScopes == null ? new ArrayList<>() : new ArrayList<>(defaultScopes);
    }

    public List<String> getAllowedScopes() {
        return allowedScopes;
    }

    public void setAllowedScopes(List<String> allowedScopes) {
        this.allowedScopes = allowedScopes == null ? new ArrayList<>() : new ArrayList<>(allowedScopes);
    }
}
