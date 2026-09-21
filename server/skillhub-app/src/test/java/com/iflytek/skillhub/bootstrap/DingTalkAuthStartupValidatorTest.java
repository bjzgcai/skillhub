package com.iflytek.skillhub.bootstrap;

import com.iflytek.skillhub.auth.dingtalk.DingTalkAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DingTalkAuthStartupValidatorTest {

    @Test
    void disabledDingTalkAuthDoesNotRequireConfiguration() {
        DingTalkAuthProperties properties = new DingTalkAuthProperties();
        properties.setEnabled(false);

        assertDoesNotThrow(() -> new DingTalkAuthStartupValidator(properties)
                .run(new DefaultApplicationArguments()));
    }

    @Test
    void enabledDingTalkAuthRequiresCompleteConfiguration() {
        DingTalkAuthProperties properties = new DingTalkAuthProperties();
        properties.setEnabled(true);

        assertThrows(IllegalStateException.class, () -> new DingTalkAuthStartupValidator(properties)
                .run(new DefaultApplicationArguments()));
    }
}
