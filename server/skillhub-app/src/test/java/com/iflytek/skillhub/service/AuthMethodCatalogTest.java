package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.iflytek.skillhub.auth.bootstrap.PassiveSessionAuthenticator;
import com.iflytek.skillhub.auth.direct.DirectAuthProvider;
import com.iflytek.skillhub.auth.dingtalk.DingTalkAuthProperties;
import com.iflytek.skillhub.config.AuthSessionBootstrapProperties;
import com.iflytek.skillhub.config.DirectAuthProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;

class AuthMethodCatalogTest {


    @Test
    void listMethodsShouldAdvertiseConfiguredDingTalkLogin() {
        OAuth2ClientProperties oauthProperties = new OAuth2ClientProperties();
        DingTalkAuthProperties dingTalkAuthProperties = new DingTalkAuthProperties();
        dingTalkAuthProperties.setEnabled(true);
        dingTalkAuthProperties.setAppKey("ding-app-key");
        dingTalkAuthProperties.setAppSecret("ding-app-secret");
        dingTalkAuthProperties.setRedirectUri("https://skillhub.example.com/api/v1/auth/dingtalk/callback");
        dingTalkAuthProperties.setDisplayName("DingTalk SSO");
        DirectAuthProperties directAuthProperties = new DirectAuthProperties();
        AuthSessionBootstrapProperties bootstrapProperties = new AuthSessionBootstrapProperties();

        AuthMethodCatalog catalog = new AuthMethodCatalog(
            oauthProperties,
            dingTalkAuthProperties,
            directAuthProperties,
            bootstrapProperties,
            List.of(),
            List.of()
        );

        assertThat(catalog.listMethods("/dashboard"))
            .extracting(method -> method.id() + ":" + method.displayName() + ":" + method.actionUrl())
            .contains("oauth-dingtalk:DingTalk SSO:/api/v1/auth/dingtalk/authorize?returnTo=%2Fdashboard");
    }
}
