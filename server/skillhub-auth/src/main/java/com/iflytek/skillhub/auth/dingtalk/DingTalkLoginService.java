package com.iflytek.skillhub.auth.dingtalk;

import com.iflytek.skillhub.auth.identity.IdentityBindingService;
import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.user.UserStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Maps DingTalk identities to SkillHub users and sessions.
 */
@Service
public class DingTalkLoginService {

    private final DingTalkAuthProperties properties;
    private final DingTalkAuthClient dingTalkAuthClient;
    private final IdentityBindingService identityBindingService;

    public DingTalkLoginService(DingTalkAuthProperties properties,
                                DingTalkAuthClient dingTalkAuthClient,
                                IdentityBindingService identityBindingService) {
        this.properties = properties;
        this.dingTalkAuthClient = dingTalkAuthClient;
        this.identityBindingService = identityBindingService;
    }

    public PlatformPrincipal authenticateBrowserLogin(String authorizationCode) {
        return bindIdentity(dingTalkAuthClient.resolveBrowserIdentity(authorizationCode));
    }

    public PlatformPrincipal authenticateInAppLogin(String code) {
        return bindIdentity(dingTalkAuthClient.resolveInAppIdentity(code));
    }

    private PlatformPrincipal bindIdentity(DingTalkAuthClient.DingTalkUserIdentity identity) {
        Map<String, Object> extra = new LinkedHashMap<>(identity.rawClaims());
        if (identity.avatarUrl() != null && !identity.avatarUrl().isBlank()) {
            extra.put("avatar_url", identity.avatarUrl());
        }
        if (identity.userId() != null && !identity.userId().isBlank()) {
            extra.put("userid", identity.userId());
        }
        if (properties.getCorpId() != null && !properties.getCorpId().isBlank()) {
            extra.put("corp_id", properties.getCorpId());
        }

        OAuthClaims claims = new OAuthClaims(
            "dingtalk",
            identity.unionId(),
            identity.email(),
            identity.email() != null && !identity.email().isBlank(),
            identity.displayName(),
            extra
        );

        UserStatus initialStatus = properties.isAutoProvisionUser() ? UserStatus.ACTIVE : UserStatus.PENDING;
        return identityBindingService.bindOrCreate(claims, initialStatus);
    }
}
