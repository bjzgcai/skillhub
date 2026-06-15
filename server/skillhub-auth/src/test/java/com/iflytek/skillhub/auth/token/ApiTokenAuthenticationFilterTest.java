package com.iflytek.skillhub.auth.token;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import com.iflytek.skillhub.auth.entity.ApiToken;
import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiTokenAuthenticationFilterTest {

    private final ApiTokenService apiTokenService = mock(ApiTokenService.class);
    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final UserRoleBindingRepository roleBindingRepository = mock(UserRoleBindingRepository.class);
    private final ApiTokenScopeService scopeService =
            new ApiTokenScopeService(new ObjectMapper(), new RouteSecurityPolicyRegistry());
    private final ApiTokenAuthenticationFilter filter = new ApiTokenAuthenticationFilter(
        apiTokenService,
        userAccountRepository,
        roleBindingRepository,
        scopeService
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void shouldPopulateRoleAndScopeAuthoritiesForActiveUser() throws Exception {
        ApiToken token = new ApiToken("user-1", "cli", "sk_test", "hash", "[\"skill:publish\",\"token:manage\"]");
        UserAccount user = new UserAccount("user-1", "Alice", "alice@example.com", "");
        UserRoleBinding binding = mock(UserRoleBinding.class);
        Role role = mock(Role.class);

        when(apiTokenService.validateToken("raw-token")).thenReturn(Optional.of(token));
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(roleBindingRepository.findByUserId("user-1")).thenReturn(List.of(binding));
        when(binding.getRole()).thenReturn(role);
        when(role.getCode()).thenReturn("SKILL_ADMIN");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/whoami");
        request.addHeader("Authorization", "Bearer raw-token");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertTrue(authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_SKILL_ADMIN")));
        assertTrue(authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("SCOPE_skill:publish")));
        assertTrue(authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("SCOPE_token:manage")));
        verify(apiTokenService).touchLastUsed(token);
        assertNull(MDC.get(ApiTokenAuthenticationFilter.AUTH_SUBJECT_TYPE));
    }

    @Test
    void shouldExposeAgentTokenContextDuringRequestAndClearMdcAfterward() throws Exception {
        ApiToken token = new ApiToken("user-1", "AGENT", "agent-1", "agent token", "sk_agent", "hash", "[\"skill:publish\"]");
        org.springframework.test.util.ReflectionTestUtils.setField(token, "id", 7L);
        UserAccount user = new UserAccount("user-1", "Alice", "alice@example.com", "");

        when(apiTokenService.validateToken("raw-token")).thenReturn(Optional.of(token));
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(roleBindingRepository.findByUserId("user-1")).thenReturn(List.of());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/publish");
        request.addHeader("Authorization", "Bearer raw-token");
        jakarta.servlet.FilterChain chain = (servletRequest, servletResponse) -> {
            assertEquals("AGENT", MDC.get(ApiTokenAuthenticationFilter.AUTH_SUBJECT_TYPE));
            assertEquals("agent-1", MDC.get(ApiTokenAuthenticationFilter.AUTH_SUBJECT_ID));
            assertEquals("7", MDC.get(ApiTokenAuthenticationFilter.AUTH_TOKEN_ID));
            assertEquals("sk_agent", MDC.get(ApiTokenAuthenticationFilter.AUTH_TOKEN_PREFIX));
        };

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertEquals("AGENT", request.getAttribute(ApiTokenAuthenticationFilter.AUTH_SUBJECT_TYPE));
        assertEquals("agent-1", request.getAttribute(ApiTokenAuthenticationFilter.AUTH_SUBJECT_ID));
        assertEquals("7", request.getAttribute(ApiTokenAuthenticationFilter.AUTH_TOKEN_ID));
        assertEquals("sk_agent", request.getAttribute(ApiTokenAuthenticationFilter.AUTH_TOKEN_PREFIX));
        assertNull(MDC.get(ApiTokenAuthenticationFilter.AUTH_SUBJECT_TYPE));
    }

    @Test
    void shouldRejectDisabledUsers() throws Exception {
        ApiToken token = new ApiToken("user-2", "cli", "sk_test", "hash", "[\"skill:publish\"]");
        UserAccount user = new UserAccount("user-2", "Bob", "bob@example.com", "");
        user.setStatus(UserStatus.DISABLED);

        when(apiTokenService.validateToken("raw-token")).thenReturn(Optional.of(token));
        when(userAccountRepository.findById("user-2")).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/publish");
        request.addHeader("Authorization", "Bearer raw-token");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(apiTokenService, never()).touchLastUsed(token);
    }

    @Test
    void shouldAuthenticateBearerTokensForApiWebRequests() throws Exception {
        ApiToken token = new ApiToken("user-3", "cli", "sk_test", "hash", "[\"skill:publish\"]");
        UserAccount user = new UserAccount("user-3", "Carol", "carol@example.com", "");

        when(apiTokenService.validateToken("raw-token")).thenReturn(Optional.of(token));
        when(userAccountRepository.findById("user-3")).thenReturn(Optional.of(user));
        when(roleBindingRepository.findByUserId("user-3")).thenReturn(List.of());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/web/skills/global/publish");
        request.addHeader("Authorization", "Bearer raw-token");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(apiTokenService).touchLastUsed(token);
    }
}
