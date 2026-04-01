package com.iflytek.skillhub.auth.dingtalk;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Runtime configuration for DingTalk-based browser login and in-app SSO.
 */
@Component
@ConfigurationProperties(prefix = "skillhub.auth.dingtalk")
public class DingTalkAuthProperties {

    private boolean enabled = false;
    private String displayName = "DingTalk";
    private String appKey;
    private String appSecret;
    private String corpId;
    private String agentId;
    private String redirectUri;
    private String authorizationUrl = "https://login.dingtalk.com/oauth2/auth";
    private String browserUserTokenUrl = "https://api.dingtalk.com/v1.0/oauth2/userAccessToken";
    private String browserUserInfoUrl = "https://api.dingtalk.com/v1.0/contact/users/me";
    private String appTokenUrl = "https://api.dingtalk.com/v1.0/oauth2/accessToken";
    private String h5UserInfoUrl = "https://oapi.dingtalk.com/topapi/v2/user/getuserinfo";
    private String userIdByUnionIdUrl = "https://oapi.dingtalk.com/topapi/user/getbyunionid";
    private String userDetailUrl = "https://oapi.dingtalk.com/topapi/v2/user/get";
    private String browserScope = "openid";
    private boolean requireCorpMembership = true;
    private boolean autoProvisionUser = true;
    private boolean autoLoginInDingtalk = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getCorpId() {
        return corpId;
    }

    public void setCorpId(String corpId) {
        this.corpId = corpId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getAuthorizationUrl() {
        return authorizationUrl;
    }

    public void setAuthorizationUrl(String authorizationUrl) {
        this.authorizationUrl = authorizationUrl;
    }

    public String getBrowserUserTokenUrl() {
        return browserUserTokenUrl;
    }

    public void setBrowserUserTokenUrl(String browserUserTokenUrl) {
        this.browserUserTokenUrl = browserUserTokenUrl;
    }

    public String getBrowserUserInfoUrl() {
        return browserUserInfoUrl;
    }

    public void setBrowserUserInfoUrl(String browserUserInfoUrl) {
        this.browserUserInfoUrl = browserUserInfoUrl;
    }

    public String getAppTokenUrl() {
        return appTokenUrl;
    }

    public void setAppTokenUrl(String appTokenUrl) {
        this.appTokenUrl = appTokenUrl;
    }

    public String getH5UserInfoUrl() {
        return h5UserInfoUrl;
    }

    public void setH5UserInfoUrl(String h5UserInfoUrl) {
        this.h5UserInfoUrl = h5UserInfoUrl;
    }

    public String getUserIdByUnionIdUrl() {
        return userIdByUnionIdUrl;
    }

    public void setUserIdByUnionIdUrl(String userIdByUnionIdUrl) {
        this.userIdByUnionIdUrl = userIdByUnionIdUrl;
    }

    public String getUserDetailUrl() {
        return userDetailUrl;
    }

    public void setUserDetailUrl(String userDetailUrl) {
        this.userDetailUrl = userDetailUrl;
    }

    public String getBrowserScope() {
        return browserScope;
    }

    public void setBrowserScope(String browserScope) {
        this.browserScope = browserScope;
    }

    public boolean isRequireCorpMembership() {
        return requireCorpMembership;
    }

    public void setRequireCorpMembership(boolean requireCorpMembership) {
        this.requireCorpMembership = requireCorpMembership;
    }

    public boolean isAutoProvisionUser() {
        return autoProvisionUser;
    }

    public void setAutoProvisionUser(boolean autoProvisionUser) {
        this.autoProvisionUser = autoProvisionUser;
    }

    public boolean isAutoLoginInDingtalk() {
        return autoLoginInDingtalk;
    }

    public void setAutoLoginInDingtalk(boolean autoLoginInDingtalk) {
        this.autoLoginInDingtalk = autoLoginInDingtalk;
    }

    public boolean isConfigured() {
        return enabled
            && hasText(appKey)
            && hasText(appSecret)
            && hasText(redirectUri);
    }

    public boolean isInAppConfigured() {
        return isConfigured()
            && hasText(corpId)
            && hasText(agentId);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
