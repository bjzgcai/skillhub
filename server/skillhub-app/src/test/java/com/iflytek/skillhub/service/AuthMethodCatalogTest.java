package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.iflytek.skillhub.auth.dingtalk.DingTalkAuthProperties;
import org.junit.jupiter.api.Test;

class AuthMethodCatalogTest {


    @Test
    void listMethodsShouldAdvertiseConfiguredDingTalkLogin() {
        DingTalkAuthProperties dingTalkAuthProperties = new DingTalkAuthProperties();
        dingTalkAuthProperties.setEnabled(true);
        dingTalkAuthProperties.setAppKey("ding-app-key");
        dingTalkAuthProperties.setAppSecret("ding-app-secret");
        dingTalkAuthProperties.setRedirectUri("https://skillhub.example.com/api/v1/auth/dingtalk/callback");
        dingTalkAuthProperties.setDisplayName("DingTalk SSO");

        AuthMethodCatalog catalog = new AuthMethodCatalog(dingTalkAuthProperties);

        assertThat(catalog.listMethods("/dashboard"))
            .extracting(method -> method.id() + ":" + method.displayName() + ":" + method.actionUrl())
            .contains("oauth-dingtalk:DingTalk SSO:/api/v1/auth/dingtalk/authorize?returnTo=%2Fdashboard");
    }
}
