package com.iflytek.skillhub.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.dingtalk.DingTalkAuthProperties;
import com.iflytek.skillhub.auth.dingtalk.DingTalkLoginFlowService;
import com.iflytek.skillhub.auth.dingtalk.DingTalkLoginService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DingTalkAuthControllerTest {

    private DingTalkAuthProperties properties;
    private DingTalkLoginFlowService dingTalkLoginFlowService;
    private DingTalkLoginService dingTalkLoginService;
    private PlatformSessionService platformSessionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        properties = new DingTalkAuthProperties();
        properties.setEnabled(true);
        properties.setAppKey("app-key");
        properties.setAppSecret("app-secret");
        properties.setRedirectUri("https://api.example.com/api/v1/auth/dingtalk/callback");

        dingTalkLoginFlowService = org.mockito.Mockito.mock(DingTalkLoginFlowService.class);
        dingTalkLoginService = org.mockito.Mockito.mock(DingTalkLoginService.class);
        platformSessionService = org.mockito.Mockito.mock(PlatformSessionService.class);

        StaticMessageSource messageSource = new StaticMessageSource();
        Clock clock = Clock.fixed(Instant.parse("2026-04-02T06:00:00Z"), ZoneOffset.UTC);

        DingTalkAuthController controller = new DingTalkAuthController(
            new ApiResponseFactory(messageSource, clock),
            properties,
            dingTalkLoginFlowService,
            dingTalkLoginService,
            platformSessionService,
            "https://web.example.com"
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void callbackSuccessShouldRedirectToFrontendDashboard() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
            "user-1",
            "Ding User",
            "user@example.com",
            null,
            "dingtalk",
            Set.of("USER")
        );

        given(dingTalkLoginFlowService.peekReturnTo(any())).willReturn("/dashboard");
        given(dingTalkLoginFlowService.validateStateAndConsumeReturnTo(any(), eq("state-1")))
            .willReturn("/dashboard");
        given(dingTalkLoginService.authenticateBrowserLogin("auth-code-1")).willReturn(principal);

        mockMvc.perform(get("/api/v1/auth/dingtalk/callback")
                .param("state", "state-1")
                .param("authCode", "auth-code-1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("https://web.example.com/dashboard"));

        verify(platformSessionService).establishSession(eq(principal), any());
    }

    @Test
    void callbackFailureShouldRedirectToFrontendLogin() throws Exception {
        given(dingTalkLoginFlowService.peekReturnTo(any())).willReturn("/dashboard");
        given(dingTalkLoginFlowService.consumeReturnTo(any())).willReturn("/dashboard");

        mockMvc.perform(get("/api/v1/auth/dingtalk/callback")
                .param("state", "state-1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("https://web.example.com/login?returnTo=%2Fdashboard"));
    }

    @Test
    void callbackShouldAcceptLegacyCodeParameterWhenAuthCodeMissing() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
            "user-1",
            "Ding User",
            "user@example.com",
            null,
            "dingtalk",
            Set.of("USER")
        );

        given(dingTalkLoginFlowService.peekReturnTo(any())).willReturn("/dashboard");
        given(dingTalkLoginFlowService.validateStateAndConsumeReturnTo(any(), eq("state-1")))
            .willReturn("/dashboard");
        given(dingTalkLoginService.authenticateBrowserLogin("legacy-code-1")).willReturn(principal);

        mockMvc.perform(get("/api/v1/auth/dingtalk/callback")
                .param("state", "state-1")
                .param("code", "legacy-code-1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("https://web.example.com/dashboard"));

        verify(dingTalkLoginService).authenticateBrowserLogin("legacy-code-1");
    }
}
