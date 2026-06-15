package com.iflytek.skillhub.auth.token;

import com.iflytek.skillhub.auth.entity.ApiToken;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.PlatformRoleDefaults;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authenticates bearer tokens and projects them into a Spring Security
 * principal with both roles and token scopes.
 */
@Component
public class ApiTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    public static final String AUTH_TOKEN_ID = "skillhub.auth.tokenId";
    public static final String AUTH_TOKEN_PREFIX = "skillhub.auth.tokenPrefix";
    public static final String AUTH_SUBJECT_TYPE = "skillhub.auth.subjectType";
    public static final String AUTH_SUBJECT_ID = "skillhub.auth.subjectId";

    private final ApiTokenService apiTokenService;
    private final UserAccountRepository userRepo;
    private final UserRoleBindingRepository roleBindingRepo;
    private final ApiTokenScopeService apiTokenScopeService;

    public ApiTokenAuthenticationFilter(ApiTokenService apiTokenService,
                                        UserAccountRepository userRepo,
                                        UserRoleBindingRepository roleBindingRepo,
                                        ApiTokenScopeService apiTokenScopeService) {
        this.apiTokenService = apiTokenService;
        this.userRepo = userRepo;
        this.roleBindingRepo = roleBindingRepo;
        this.apiTokenScopeService = apiTokenScopeService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String authHeader = request.getHeader(AUTH_HEADER);
            if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                String rawToken = authHeader.substring(BEARER_PREFIX.length());
                apiTokenService.validateToken(rawToken).ifPresent(token -> {
                    userRepo.findById(token.getUserId()).ifPresent(user -> {
                        if (!user.isActive()) {
                            return;
                        }
                        Set<String> roles = roleBindingRepo.findByUserId(user.getId()).stream()
                            .map(rb -> rb.getRole().getCode())
                            .collect(Collectors.toSet());
                        roles = PlatformRoleDefaults.withDefaultUserRole(roles);
                        Set<String> scopes = apiTokenScopeService.parseScopes(token.getScopeJson());
                        PlatformPrincipal principal = new PlatformPrincipal(
                            user.getId(), user.getDisplayName(), user.getEmail(),
                            user.getAvatarUrl(), "api_token", roles
                        );
                        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                        authorities.addAll(roles.stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                            .toList());
                        authorities.addAll(scopes.stream()
                            .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                            .toList());
                        var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(auth);
                        exposeTokenContext(request, token);
                        apiTokenService.touchLastUsed(token);
                    });
                });
            }
            filterChain.doFilter(request, response);
        } finally {
            clearTokenContext();
        }
    }

    private void exposeTokenContext(HttpServletRequest request, com.iflytek.skillhub.auth.entity.ApiToken token) {
        putTokenContext(request, AUTH_TOKEN_ID, token.getId() == null ? null : token.getId().toString());
        putTokenContext(request, AUTH_TOKEN_PREFIX, token.getTokenPrefix());
        putTokenContext(request, AUTH_SUBJECT_TYPE, token.getSubjectType());
        putTokenContext(request, AUTH_SUBJECT_ID, token.getSubjectId());
    }

    private void putTokenContext(HttpServletRequest request, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        request.setAttribute(key, value);
        MDC.put(key, value);
    }

    private void clearTokenContext() {
        MDC.remove(AUTH_TOKEN_ID);
        MDC.remove(AUTH_TOKEN_PREFIX);
        MDC.remove(AUTH_SUBJECT_TYPE);
        MDC.remove(AUTH_SUBJECT_ID);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/v1/")
            || path.startsWith("/api/web/"));
    }
}
