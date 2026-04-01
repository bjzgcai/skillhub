package com.iflytek.skillhub.auth.dingtalk;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.oauth.OAuthLoginRedirectSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Handles state and return-target bookkeeping for DingTalk browser login.
 */
@Service
public class DingTalkLoginFlowService {

    static final String SESSION_STATE_ATTRIBUTE = "skillhub.dingtalk.state";
    static final String SESSION_RETURN_TO_ATTRIBUTE = "skillhub.dingtalk.returnTo";

    private final DingTalkAuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public DingTalkLoginFlowService(DingTalkAuthProperties properties) {
        this.properties = properties;
    }

    public AuthorizationRequest beginAuthorization(HttpServletRequest request, String returnTo) {
        if (!properties.isConfigured()) {
            throw new AuthFlowException(HttpStatus.FORBIDDEN, "error.auth.dingtalk.disabled");
        }

        String sanitizedReturnTo = OAuthLoginRedirectSupport.sanitizeReturnTo(returnTo);
        HttpSession session = request.getSession(true);
        String state = generateState();
        session.setAttribute(SESSION_STATE_ATTRIBUTE, state);
        if (sanitizedReturnTo == null) {
            session.removeAttribute(SESSION_RETURN_TO_ATTRIBUTE);
        } else {
            session.setAttribute(SESSION_RETURN_TO_ATTRIBUTE, sanitizedReturnTo);
        }

        String redirect = properties.getAuthorizationUrl()
            + "?client_id=" + urlEncode(properties.getAppKey())
            + "&redirect_uri=" + urlEncode(properties.getRedirectUri())
            + "&response_type=code"
            + "&scope=" + urlEncode(properties.getBrowserScope())
            + "&state=" + urlEncode(state);

        return new AuthorizationRequest(redirect, sanitizedReturnTo);
    }

    public String validateStateAndConsumeReturnTo(HttpSession session, String state) {
        if (session == null) {
            throw new AuthFlowException(HttpStatus.BAD_REQUEST, "error.auth.dingtalk.stateInvalid");
        }
        Object expectedState = session.getAttribute(SESSION_STATE_ATTRIBUTE);
        session.removeAttribute(SESSION_STATE_ATTRIBUTE);
        Object returnTo = session.getAttribute(SESSION_RETURN_TO_ATTRIBUTE);
        session.removeAttribute(SESSION_RETURN_TO_ATTRIBUTE);

        if (!(expectedState instanceof String expected) || state == null || !expected.equals(state)) {
            throw new AuthFlowException(HttpStatus.BAD_REQUEST, "error.auth.dingtalk.stateInvalid");
        }
        if (returnTo instanceof String returnToString) {
            return OAuthLoginRedirectSupport.sanitizeReturnTo(returnToString);
        }
        return null;
    }

    public String consumeReturnTo(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object returnTo = session.getAttribute(SESSION_RETURN_TO_ATTRIBUTE);
        session.removeAttribute(SESSION_RETURN_TO_ATTRIBUTE);
        session.removeAttribute(SESSION_STATE_ATTRIBUTE);
        if (returnTo instanceof String returnToString) {
            return OAuthLoginRedirectSupport.sanitizeReturnTo(returnToString);
        }
        return null;
    }

    public String peekReturnTo(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object returnTo = session.getAttribute(SESSION_RETURN_TO_ATTRIBUTE);
        if (returnTo instanceof String returnToString) {
            return OAuthLoginRedirectSupport.sanitizeReturnTo(returnToString);
        }
        return null;
    }

    private String generateState() {
        byte[] buffer = new byte[24];
        secureRandom.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record AuthorizationRequest(String authorizationUrl, String returnTo) {}
}
