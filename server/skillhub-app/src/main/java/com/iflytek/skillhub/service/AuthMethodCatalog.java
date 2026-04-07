package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.dingtalk.DingTalkAuthProperties;
import com.iflytek.skillhub.auth.oauth.OAuthLoginRedirectSupport;
import com.iflytek.skillhub.dto.AuthMethodResponse;
import com.iflytek.skillhub.dto.AuthProviderResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Builds the catalog of authentication methods and OAuth providers that the UI
 * can render dynamically.
 */
@Service
public class AuthMethodCatalog {

    private final DingTalkAuthProperties dingTalkAuthProperties;

    public AuthMethodCatalog(DingTalkAuthProperties dingTalkAuthProperties) {
        this.dingTalkAuthProperties = dingTalkAuthProperties;
    }

    public List<AuthProviderResponse> listOAuthProviders(String returnTo) {
        String sanitizedReturnTo = OAuthLoginRedirectSupport.sanitizeReturnTo(returnTo);
        List<AuthProviderResponse> providers = new ArrayList<>();
        if (dingTalkAuthProperties.isConfigured()) {
            providers.add(new AuthProviderResponse(
                "dingtalk",
                dingTalkAuthProperties.getDisplayName(),
                buildDingTalkAuthorizationUrl(sanitizedReturnTo)
            ));
        }
        return providers;
    }

    public List<AuthMethodResponse> listMethods(String returnTo) {
        String sanitizedReturnTo = OAuthLoginRedirectSupport.sanitizeReturnTo(returnTo);
        List<AuthMethodResponse> methods = new ArrayList<>();

        if (dingTalkAuthProperties.isConfigured()) {
            methods.add(new AuthMethodResponse(
                "oauth-dingtalk",
                "OAUTH_REDIRECT",
                "dingtalk",
                dingTalkAuthProperties.getDisplayName(),
                buildDingTalkAuthorizationUrl(sanitizedReturnTo)
            ));
        }

        return methods;
    }

    private String buildDingTalkAuthorizationUrl(String returnTo) {
        String baseUrl = "/api/v1/auth/dingtalk/authorize";
        if (returnTo == null) {
            return baseUrl;
        }
        return baseUrl + "?returnTo=" + URLEncoder.encode(returnTo, StandardCharsets.UTF_8);
    }
}
