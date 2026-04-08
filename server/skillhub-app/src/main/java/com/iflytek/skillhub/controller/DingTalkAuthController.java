package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.dingtalk.DingTalkAuthProperties;
import com.iflytek.skillhub.auth.dingtalk.DingTalkLoginFlowService;
import com.iflytek.skillhub.auth.dingtalk.DingTalkLoginService;
import com.iflytek.skillhub.auth.oauth.AccountDisabledException;
import com.iflytek.skillhub.auth.oauth.AccountPendingException;
import com.iflytek.skillhub.auth.oauth.OAuthLoginRedirectSupport;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.AuthMeResponse;
import com.iflytek.skillhub.dto.DingTalkH5LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.view.RedirectView;

/**
 * DingTalk-specific login entry points for browser redirects and in-app SSO.
 */
@Controller
@RequestMapping("/api/v1/auth/dingtalk")
public class DingTalkAuthController extends BaseApiController {

    private static final Logger log = LoggerFactory.getLogger(DingTalkAuthController.class);

    private final DingTalkAuthProperties properties;
    private final DingTalkLoginFlowService dingTalkLoginFlowService;
    private final DingTalkLoginService dingTalkLoginService;
    private final PlatformSessionService platformSessionService;
    private final String publicBaseUrl;

    public DingTalkAuthController(ApiResponseFactory responseFactory,
                                  DingTalkAuthProperties properties,
                                  DingTalkLoginFlowService dingTalkLoginFlowService,
                                  DingTalkLoginService dingTalkLoginService,
                                  PlatformSessionService platformSessionService,
                                  @Value("${skillhub.public.base-url:}") String publicBaseUrl) {
        super(responseFactory);
        this.properties = properties;
        this.dingTalkLoginFlowService = dingTalkLoginFlowService;
        this.dingTalkLoginService = dingTalkLoginService;
        this.platformSessionService = platformSessionService;
        this.publicBaseUrl = publicBaseUrl;
    }

    @GetMapping("/config")
    @ResponseBody
    public ApiResponse<Map<String, Object>> config() {
        return ok("response.success.read", Map.of(
            "enabled", properties.isConfigured(),
            "inAppEnabled", properties.isInAppConfigured(),
            "displayName", properties.getDisplayName(),
            "provider", "dingtalk",
            "corpId", properties.getCorpId(),
            "agentId", properties.getAgentId(),
            "redirectUri", properties.getRedirectUri(),
            "authorizationUrl", "/api/v1/auth/dingtalk/authorize",
            "autoLoginInDingtalk", properties.isAutoLoginInDingtalk()
        ));
    }

    @GetMapping("/authorize")
    public RedirectView authorize(@RequestParam(name = "returnTo", required = false) String returnTo,
                                  HttpServletRequest request) {
        if (!properties.isConfigured()) {
            return new RedirectView(buildLoginRedirect(returnTo, "dingtalkDisabled"), true);
        }
        DingTalkLoginFlowService.AuthorizationRequest authorizationRequest =
            dingTalkLoginFlowService.beginAuthorization(request, returnTo);
        return new RedirectView(authorizationRequest.authorizationUrl(), true);
    }

    @GetMapping("/callback")
    public RedirectView callback(@RequestParam(name = "state", required = false) String state,
                                 @RequestParam(name = "authCode", required = false) String authCode,
                                 @RequestParam(name = "code", required = false) String code,
                                 @RequestParam(name = "error", required = false) String error,
                                 HttpServletRequest request) {
        String authorizationCode = firstNonBlank(authCode, code);
        String fallbackReturnTo = dingTalkLoginFlowService.peekReturnTo(request.getSession(false));
        if (error != null || authorizationCode == null || state == null) {
            return new RedirectView(buildLoginRedirect(
                dingTalkLoginFlowService.consumeReturnTo(request.getSession(false)),
                null
            ), true);
        }

        try {
            String returnTo = dingTalkLoginFlowService.validateStateAndConsumeReturnTo(request.getSession(false), state);
            PlatformPrincipal principal = dingTalkLoginService.authenticateBrowserLogin(authorizationCode);
            platformSessionService.establishSession(principal, request);
            String target = returnTo != null ? returnTo : OAuthLoginRedirectSupport.DEFAULT_TARGET_URL;
            return new RedirectView(buildFrontendRedirect(target), true);
        } catch (AccountDisabledException ex) {
            log.warn("DingTalk callback failed: account disabled");
            return new RedirectView(buildLoginRedirect(fallbackReturnTo, "accountDisabled"), true);
        } catch (AccountPendingException ex) {
            log.warn("DingTalk callback failed: account pending");
            return new RedirectView(buildLoginRedirect(fallbackReturnTo, "accountPending"), true);
        } catch (RuntimeException ex) {
            log.error("DingTalk callback failed", ex);
            return new RedirectView(buildLoginRedirect(fallbackReturnTo, "dingtalkCallbackFailed"), true);
        }
    }

    @PostMapping("/h5-login")
    @ResponseBody
    public ApiResponse<AuthMeResponse> h5Login(@Valid @RequestBody DingTalkH5LoginRequest request,
                                               HttpServletRequest httpRequest) {
        PlatformPrincipal principal = dingTalkLoginService.authenticateInAppLogin(request.code());
        platformSessionService.establishSession(principal, httpRequest);
        return ok("response.success.read", AuthMeResponse.from(principal));
    }

    private String buildFrontendRedirect(String path) {
        if (path == null || path.isBlank()) {
            path = "/";
        }
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            String base = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            return base + path;
        }
        return path;
    }

    private String buildLoginRedirect(String returnTo, String reason) {
        StringBuilder builder = new StringBuilder(buildFrontendRedirect("/login"));
        boolean hasQuery = false;
        if (reason != null && !reason.isBlank()) {
            builder.append("?reason=").append(urlEncode(reason));
            hasQuery = true;
        }
        if (returnTo != null && !returnTo.isBlank()) {
            builder.append(hasQuery ? "&" : "?")
                .append("returnTo=")
                .append(urlEncode(returnTo));
        }
        return builder.toString();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
