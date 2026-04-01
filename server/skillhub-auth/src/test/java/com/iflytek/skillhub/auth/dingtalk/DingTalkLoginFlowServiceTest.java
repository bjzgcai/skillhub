package com.iflytek.skillhub.auth.dingtalk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class DingTalkLoginFlowServiceTest {

    @Test
    void beginAuthorizationStoresSanitizedReturnToAndBuildsAuthorizationUrl() {
        DingTalkAuthProperties properties = configuredProperties();
        DingTalkLoginFlowService service = new DingTalkLoginFlowService(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();

        DingTalkLoginFlowService.AuthorizationRequest result =
            service.beginAuthorization(request, "/workspace?tab=recent");

        HttpSession session = request.getSession(false);
        assertThat(session).isNotNull();
        assertThat(result.returnTo()).isEqualTo("/workspace?tab=recent");
        assertThat(result.authorizationUrl())
            .startsWith("https://login.dingtalk.com/oauth2/auth?")
            .contains("client_id=app-key")
            .contains("redirect_uri=https%3A%2F%2Fskillhub.example.com%2Fapi%2Fv1%2Fauth%2Fdingtalk%2Fcallback")
            .contains("scope=openid")
            .contains("state=");
        assertThat(session.getAttribute(DingTalkLoginFlowService.SESSION_RETURN_TO_ATTRIBUTE))
            .isEqualTo("/workspace?tab=recent");
        assertThat(session.getAttribute(DingTalkLoginFlowService.SESSION_STATE_ATTRIBUTE))
            .isInstanceOf(String.class);
    }

    @Test
    void beginAuthorizationRejectsDisabledConfiguration() {
        DingTalkAuthProperties properties = new DingTalkAuthProperties();
        DingTalkLoginFlowService service = new DingTalkLoginFlowService(properties);

        assertThatThrownBy(() -> service.beginAuthorization(new MockHttpServletRequest(), "/dashboard"))
            .isInstanceOf(AuthFlowException.class)
            .satisfies(ex -> {
                AuthFlowException authException = (AuthFlowException) ex;
                assertThat(authException.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(authException.getMessageCode()).isEqualTo("error.auth.dingtalk.disabled");
            });
    }

    @Test
    void validateStateAndConsumeReturnToRejectsMismatchedStateAndClearsSessionValues() {
        DingTalkAuthProperties properties = configuredProperties();
        DingTalkLoginFlowService service = new DingTalkLoginFlowService(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        service.beginAuthorization(request, "https://malicious.example.com/callback");

        HttpSession session = request.getSession(false);

        assertThatThrownBy(() -> service.validateStateAndConsumeReturnTo(session, "unexpected-state"))
            .isInstanceOf(AuthFlowException.class)
            .satisfies(ex -> {
                AuthFlowException authException = (AuthFlowException) ex;
                assertThat(authException.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(authException.getMessageCode()).isEqualTo("error.auth.dingtalk.stateInvalid");
            });
        assertThat(session.getAttribute(DingTalkLoginFlowService.SESSION_STATE_ATTRIBUTE)).isNull();
        assertThat(session.getAttribute(DingTalkLoginFlowService.SESSION_RETURN_TO_ATTRIBUTE)).isNull();
    }

    private DingTalkAuthProperties configuredProperties() {
        DingTalkAuthProperties properties = new DingTalkAuthProperties();
        properties.setEnabled(true);
        properties.setAppKey("app-key");
        properties.setAppSecret("app-secret");
        properties.setRedirectUri("https://skillhub.example.com/api/v1/auth/dingtalk/callback");
        return properties;
    }
}
