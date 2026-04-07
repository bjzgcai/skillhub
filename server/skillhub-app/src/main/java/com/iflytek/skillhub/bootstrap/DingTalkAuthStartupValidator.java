package com.iflytek.skillhub.bootstrap;

import com.iflytek.skillhub.auth.dingtalk.DingTalkAuthProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Enforces the DingTalk-only deployment contract during application startup.
 */
@Component
public class DingTalkAuthStartupValidator implements ApplicationRunner {

    private final DingTalkAuthProperties properties;

    public DingTalkAuthStartupValidator(DingTalkAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException(
                "DingTalk SSO is required for this deployment. "
                    + "Please configure SKILLHUB_AUTH_DINGTALK_APP_KEY, "
                    + "SKILLHUB_AUTH_DINGTALK_APP_SECRET, and "
                    + "SKILLHUB_AUTH_DINGTALK_REDIRECT_URI before startup."
            );
        }
    }
}
