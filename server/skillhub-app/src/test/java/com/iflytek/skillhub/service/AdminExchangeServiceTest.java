package com.iflytek.skillhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.entity.ApiToken;
import com.iflytek.skillhub.auth.entity.IdentityBinding;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.config.AdminExchangeProperties;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.adminexchange.AdminExchangeOwnerRequest;
import com.iflytek.skillhub.dto.adminexchange.AdminExchangeRequest;
import com.iflytek.skillhub.exception.ForbiddenException;
import com.iflytek.skillhub.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminExchangeServiceTest {
    private final IdentityBindingRepository identityBindingRepository = mock(IdentityBindingRepository.class);
    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final ApiTokenService apiTokenService = mock(ApiTokenService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final AdminExchangeProperties properties = new AdminExchangeProperties();
    private AdminExchangeService service;

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        properties.setTokenHash(sha256("platform-secret"));
        properties.setAllowedSources(List.of("agent-platform"));
        properties.setDefaultScopes(List.of("skill:read", "skill:publish"));
        properties.setAllowedScopes(List.of("skill:read", "skill:publish"));
        service = new AdminExchangeService(
                properties,
                identityBindingRepository,
                userAccountRepository,
                apiTokenService,
                auditLogService,
                new ObjectMapper()
        );
    }

    @Test
    void exchange_issuesAgentTokenWithDefaultScopesAndNoExpiration() {
        IdentityBinding binding = new IdentityBinding("user-1", "dingtalk", "union-1", "Alice");
        binding.setExtraJson(Map.of("corp_id", "corp-1", "userid", "ding-user-1"));
        ReflectionTestUtils.setField(binding, "id", 99L);
        UserAccount user = new UserAccount("user-1", "Alice", "alice@example.com", "");
        ApiToken token = new ApiToken("user-1", "AGENT", "agent-1", "agent token", "sk_abc", "hash", "[]");
        ReflectionTestUtils.setField(token, "id", 7L);

        when(identityBindingRepository.findByProviderCodeAndSubject("dingtalk", "union-1")).thenReturn(Optional.of(binding));
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(apiTokenService.createTokenForSubject(
                eq("user-1"), eq("AGENT"), eq("agent-1"), any(),
                eq("[\"skill:read\",\"skill:publish\"]"), eq(null)))
                .thenReturn(new ApiTokenService.TokenCreateResult("sk_raw", token));

        var request = new AdminExchangeRequest(
                "agent-platform",
                "agent-1",
                "龙虾",
                new AdminExchangeOwnerRequest("dingtalk", "corp-1", "union-1", "ding-user-1", null),
                null,
                null,
                "req-1"
        );
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("127.0.0.1");
        httpRequest.addHeader("User-Agent", "JUnit");

        var response = service.exchange("Bearer platform-secret", request, httpRequest);

        assertThat(response.accessToken()).isEqualTo("sk_raw");
        assertThat(response.expiresAt()).isNull();
        assertThat(response.scopes()).containsExactly("skill:read", "skill:publish");
        assertThat(response.subject().type()).isEqualTo("AGENT");
        assertThat(response.subject().agentId()).isEqualTo("agent-1");
        assertThat(response.effectiveUser().id()).isEqualTo("user-1");
        assertThat(response.effectiveUser().corpId()).isEqualTo("corp-1");
        verify(auditLogService).record(eq("user-1"), eq("ADMIN_EXCHANGE_TOKEN_ISSUED"), eq("API_TOKEN"), eq(7L), eq("req-1"), eq("127.0.0.1"), eq("JUnit"), any());
    }

    @Test
    void exchange_usesUnionIdDirectlyWhenPresent() {
        IdentityBinding binding = new IdentityBinding("user-1", "dingtalk", "union-1", "Alice");
        binding.setExtraJson(Map.of("corp_id", "different-corp", "userid", "different-user"));
        UserAccount user = new UserAccount("user-1", "Alice", "alice@example.com", "");
        ApiToken token = new ApiToken("user-1", "AGENT", "agent-1", "agent token", "sk_abc", "hash", "[]");
        ReflectionTestUtils.setField(token, "id", 7L);

        when(identityBindingRepository.findByProviderCodeAndSubject("dingtalk", "union-1")).thenReturn(Optional.of(binding));
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(apiTokenService.createTokenForSubject(eq("user-1"), eq("AGENT"), eq("agent-1"), any(), any(), eq(null)))
                .thenReturn(new ApiTokenService.TokenCreateResult("sk_raw", token));

        var response = service.exchange("Bearer platform-secret", minimalRequest(List.of("skill:read")), new MockHttpServletRequest());

        assertThat(response.effectiveUser().id()).isEqualTo("user-1");
        verify(identityBindingRepository, never()).findByProviderAndCorpIdAndDingTalkUserId(any(), any(), any());
        verify(identityBindingRepository, never()).findByProviderAndCorpIdAndDingTalkOpenId(any(), any(), any());
    }

    @Test
    void exchange_fallsBackToCorpIdAndUserIdWhenUnionIdMissing() {
        IdentityBinding binding = new IdentityBinding("user-1", "dingtalk", "union-1", "Alice");
        binding.setExtraJson(Map.of("corp_id", "corp-1", "userid", "ding-user-1"));
        UserAccount user = new UserAccount("user-1", "Alice", "alice@example.com", "");
        ApiToken token = new ApiToken("user-1", "AGENT", "agent-1", "agent token", "sk_abc", "hash", "[]");
        ReflectionTestUtils.setField(token, "id", 7L);

        when(identityBindingRepository.findByProviderAndCorpIdAndDingTalkUserId("dingtalk", "corp-1", "ding-user-1"))
                .thenReturn(Optional.of(binding));
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(apiTokenService.createTokenForSubject(eq("user-1"), eq("AGENT"), eq("agent-1"), any(), any(), eq(null)))
                .thenReturn(new ApiTokenService.TokenCreateResult("sk_raw", token));

        var request = new AdminExchangeRequest(
                "agent-platform",
                "agent-1",
                "龙虾",
                new AdminExchangeOwnerRequest("dingtalk", "corp-1", null, "ding-user-1", null),
                List.of("skill:read"),
                null,
                "req-1"
        );

        var response = service.exchange("Bearer platform-secret", request, new MockHttpServletRequest());

        assertThat(response.effectiveUser().id()).isEqualTo("user-1");
        verify(identityBindingRepository, never()).findByProviderCodeAndSubject(eq("dingtalk"), any());
    }

    @Test
    void exchange_fallsBackToCorpIdAndOpenIdWhenUnionIdAndUserIdAreMissing() {
        IdentityBinding binding = new IdentityBinding("user-1", "dingtalk", "union-1", "Alice");
        binding.setExtraJson(Map.of("corp_id", "corp-1", "openId", "open-1"));
        UserAccount user = new UserAccount("user-1", "Alice", "alice@example.com", "");
        ApiToken token = new ApiToken("user-1", "AGENT", "agent-1", "agent token", "sk_abc", "hash", "[]");
        ReflectionTestUtils.setField(token, "id", 7L);

        when(identityBindingRepository.findByProviderAndCorpIdAndDingTalkOpenId("dingtalk", "corp-1", "open-1"))
                .thenReturn(Optional.of(binding));
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(apiTokenService.createTokenForSubject(eq("user-1"), eq("AGENT"), eq("agent-1"), any(), any(), eq(null)))
                .thenReturn(new ApiTokenService.TokenCreateResult("sk_raw", token));

        var request = new AdminExchangeRequest(
                "agent-platform",
                "agent-1",
                "龙虾",
                new AdminExchangeOwnerRequest("dingtalk", "corp-1", null, null, "open-1"),
                List.of("skill:read"),
                null,
                "req-1"
        );

        var response = service.exchange("Bearer platform-secret", request, new MockHttpServletRequest());

        assertThat(response.effectiveUser().id()).isEqualTo("user-1");
    }

    @Test
    void exchange_rejectsFallbackWhenCorpIdIsMissing() {
        var request = new AdminExchangeRequest(
                "agent-platform",
                "agent-1",
                "龙虾",
                new AdminExchangeOwnerRequest("dingtalk", null, null, "ding-user-1", null),
                List.of("skill:read"),
                null,
                "req-1"
        );

        assertThatThrownBy(() -> service.exchange("Bearer platform-secret", request, new MockHttpServletRequest()))
                .isInstanceOf(com.iflytek.skillhub.exception.BadRequestException.class);
        verify(identityBindingRepository, never()).findByProviderAndCorpIdAndDingTalkUserId(any(), any(), any());
        verify(identityBindingRepository, never()).findByProviderAndCorpIdAndDingTalkOpenId(any(), any(), any());
    }

    @Test
    void exchange_rejectsInvalidPlatformToken() {
        var request = minimalRequest(List.of("skill:read"));
        var httpRequest = new MockHttpServletRequest();

        assertThatThrownBy(() -> service.exchange("Bearer wrong", request, httpRequest))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void exchange_rejectsScopeOutsideAllowlist() {
        IdentityBinding binding = new IdentityBinding("user-1", "dingtalk", "union-1", "Alice");
        binding.setExtraJson(Map.of("corp_id", "corp-1"));
        when(identityBindingRepository.findByProviderCodeAndSubject("dingtalk", "union-1")).thenReturn(Optional.of(binding));
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(new UserAccount("user-1", "Alice", null, null)));

        assertThatThrownBy(() -> service.exchange("Bearer platform-secret", minimalRequest(List.of("token:manage")), new MockHttpServletRequest()))
                .isInstanceOf(ForbiddenException.class);
    }

    private AdminExchangeRequest minimalRequest(List<String> scopes) {
        return new AdminExchangeRequest(
                "agent-platform",
                "agent-1",
                "龙虾",
                new AdminExchangeOwnerRequest("dingtalk", "corp-1", "union-1", null, null),
                scopes,
                null,
                "req-1"
        );
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
