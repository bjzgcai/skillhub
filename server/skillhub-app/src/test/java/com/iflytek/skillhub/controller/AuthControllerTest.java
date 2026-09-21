package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.security.AuthFailureThrottleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "skillhub.auth.dingtalk.enabled=true",
    "skillhub.auth.dingtalk.app-key=test-app-key",
    "skillhub.auth.dingtalk.app-secret=test-app-secret",
    "skillhub.auth.dingtalk.redirect-uri=https://skills.example.test/api/v1/auth/dingtalk/callback"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private AuthFailureThrottleService authFailureThrottleService;

    @MockBean
    private UserAccountRepository userAccountRepository;

    @MockBean
    private UserRoleBindingRepository userRoleBindingRepository;

    @Test
    void meShouldReturnUnauthorizedForAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void meShouldReturnCurrentPrincipal() throws Exception {
        given(namespaceMemberRepository.findByUserId("user-42")).willReturn(List.of());
        given(userAccountRepository.findById("user-42"))
            .willReturn(java.util.Optional.of(new UserAccount("user-42", "tester", "tester@example.com", "https://example.com/avatar.png")));
        given(userRoleBindingRepository.findByUserId("user-42")).willReturn(List.of());

        PlatformPrincipal principal = new PlatformPrincipal(
            "user-42",
            "tester",
            "tester@example.com",
            "https://example.com/avatar.png",
            "github",
            Set.of("SUPER_ADMIN")
        );

        var auth = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );

        mockMvc.perform(get("/api/v1/auth/me").with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.userId").value("user-42"))
            .andExpect(jsonPath("$.data.displayName").value("tester"))
            .andExpect(jsonPath("$.data.oauthProvider").value("github"))
            .andExpect(jsonPath("$.data.platformRoles[0]").value("USER"))
            .andExpect(jsonPath("$.timestamp").isNotEmpty())
            .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    // ===== AC-P-002: Session refresh when displayName changes =====

    @Test
    void meShouldRefreshSessionWhenDisplayNameChanges() throws Exception {
        given(namespaceMemberRepository.findByUserId("user-42")).willReturn(List.of());
        var user = new UserAccount("user-42", "UpdatedName", "tester@example.com", "https://example.com/avatar.png");
        given(userAccountRepository.findById("user-42")).willReturn(java.util.Optional.of(user));
        given(userRoleBindingRepository.findByUserId("user-42")).willReturn(List.of());

        PlatformPrincipal principal = new PlatformPrincipal(
            "user-42",
            "OldName",  // stale displayName in session
            "tester@example.com",
            "https://example.com/avatar.png",
            "github",
            Set.of("USER")
        );

        var auth = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        mockMvc.perform(get("/api/v1/auth/me").with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.displayName").value("UpdatedName"));  // should return DB value
    }

    @Test
    void providersShouldExposeDingTalkLoginEntry() throws Exception {
        mockMvc.perform(get("/api/v1/auth/providers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[*].id", hasItems("dingtalk")))
            .andExpect(jsonPath("$.data[*].authorizationUrl", hasItems(
                "/api/v1/auth/dingtalk/authorize"
            )))
            .andExpect(jsonPath("$.timestamp").isNotEmpty())
            .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void providersShouldAppendReturnToWhenRequested() throws Exception {
        mockMvc.perform(get("/api/v1/auth/providers").param("returnTo", "/dashboard/publish"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[*].authorizationUrl", hasItems(
                "/api/v1/auth/dingtalk/authorize?returnTo=%2Fdashboard%2Fpublish"
            )));
    }

    @Test
    void methodsShouldExposeStandardLoginCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/auth/methods").param("returnTo", "/dashboard/publish"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[*].id", hasItems("oauth-dingtalk")))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[?(@.id=='oauth-dingtalk')].methodType").value(hasItems("OAUTH_REDIRECT")))
            .andExpect(jsonPath("$.data[?(@.id=='oauth-dingtalk')].actionUrl")
                .value(hasItems("/api/v1/auth/dingtalk/authorize?returnTo=%2Fdashboard%2Fpublish")));
    }

    @Test
    void sessionBootstrapShouldBeForbiddenWhenFeatureIsDisabled() throws Exception {
        mockMvc.perform(post("/api/v1/auth/session/bootstrap")
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {"provider":"private-sso"}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void directLoginShouldBeForbiddenWhenFeatureIsDisabled() throws Exception {
        mockMvc.perform(post("/api/v1/auth/direct/login")
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {"provider":"private-sso","username":"alice","password":"secret"}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));
    }
}
