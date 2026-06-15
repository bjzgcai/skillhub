package com.iflytek.skillhub.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.entity.IdentityBinding;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.config.AdminExchangeProperties;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.adminexchange.AdminExchangeEffectiveUserResponse;
import com.iflytek.skillhub.dto.adminexchange.AdminExchangeRequest;
import com.iflytek.skillhub.dto.adminexchange.AdminExchangeResponse;
import com.iflytek.skillhub.dto.adminexchange.AdminExchangeSubjectResponse;
import com.iflytek.skillhub.exception.BadRequestException;
import com.iflytek.skillhub.exception.ForbiddenException;
import com.iflytek.skillhub.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Exchanges a trusted admin bearer token for an agent-scoped API token.
 */
@Service
public class AdminExchangeService {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PROVIDER_DINGTALK = "dingtalk";
    private static final String SUBJECT_TYPE_AGENT = "AGENT";
    private static final String TOKEN_NAME_PREFIX = "Admin Exchange Agent";

    private final AdminExchangeProperties properties;
    private final IdentityBindingRepository identityBindingRepository;
    private final UserAccountRepository userAccountRepository;
    private final ApiTokenService apiTokenService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminExchangeService(AdminExchangeProperties properties,
                                IdentityBindingRepository identityBindingRepository,
                                UserAccountRepository userAccountRepository,
                                ApiTokenService apiTokenService,
                                AuditLogService auditLogService,
                                ObjectMapper objectMapper) {
        this.properties = properties;
        this.identityBindingRepository = identityBindingRepository;
        this.userAccountRepository = userAccountRepository;
        this.apiTokenService = apiTokenService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AdminExchangeResponse exchange(String authorizationHeader,
                                          AdminExchangeRequest request,
                                          HttpServletRequest httpRequest) {
        validatePlatformToken(authorizationHeader);
        validateSource(request.source());
        String provider = normalizeProvider(request.owner().provider());
        if (!PROVIDER_DINGTALK.equals(provider)) {
            throw new BadRequestException("error.adminExchange.ownerProviderUnsupported");
        }

        IdentityBinding binding = resolveDingTalkBinding(request);
        UserAccount user = userAccountRepository.findById(binding.getUserId())
                .orElseThrow(() -> new BadRequestException("error.adminExchange.ownerNotBound"));
        if (!user.isActive()) {
            throw new ForbiddenException("error.adminExchange.ownerNotActive");
        }

        List<String> scopes = resolveScopes(request.scopes());
        String scopeJson = toJson(scopes);
        String tokenName = buildTokenName(request.agentName(), request.agentId());
        var result = apiTokenService.createTokenForSubject(
                user.getId(),
                SUBJECT_TYPE_AGENT,
                request.agentId().trim(),
                tokenName,
                scopeJson,
                request.expiresAt()
        );

        auditLogService.record(
                user.getId(),
                "ADMIN_EXCHANGE_TOKEN_ISSUED",
                "API_TOKEN",
                result.entity().getId(),
                firstNonBlank(request.requestId(), MDC.get("requestId")),
                resolveClientIp(httpRequest),
                httpRequest.getHeader("User-Agent"),
                auditDetailJson(request, binding, scopes, result.entity().getId(), result.entity().getTokenPrefix())
        );

        return new AdminExchangeResponse(
                "Bearer",
                result.rawToken(),
                result.entity().getId(),
                formatInstant(result.entity().getExpiresAt()),
                request.source().trim(),
                new AdminExchangeSubjectResponse(SUBJECT_TYPE_AGENT, request.agentId().trim(), trimToNull(request.agentName())),
                new AdminExchangeEffectiveUserResponse(
                        user.getId(),
                        user.getDisplayName(),
                        provider,
                        extraValue(binding, "corp_id"),
                        binding.getSubject()
                ),
                scopes
        );
    }

    private void validatePlatformToken(String authorizationHeader) {
        if (!properties.isEnabled()) {
            throw new ForbiddenException("error.adminExchange.disabled");
        }
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException("error.adminExchange.tokenMissing");
        }
        String configuredHash = trimToNull(properties.getTokenHash());
        if (configuredHash == null) {
            throw new ForbiddenException("error.adminExchange.tokenNotConfigured");
        }
        String providedHash = sha256(authorizationHeader.substring(BEARER_PREFIX.length()).trim());
        if (!MessageDigest.isEqual(
                configuredHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                providedHash.getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException("error.adminExchange.tokenInvalid");
        }
    }

    private void validateSource(String source) {
        String normalized = trimToNull(source);
        if (normalized == null || properties.getAllowedSources().stream().noneMatch(normalized::equals)) {
            throw new ForbiddenException("error.adminExchange.sourceNotAllowed");
        }
    }

    private IdentityBinding resolveDingTalkBinding(AdminExchangeRequest request) {
        String unionId = trimToNull(request.owner().unionId());
        if (unionId != null) {
            return identityBindingRepository.findByProviderCodeAndSubject(PROVIDER_DINGTALK, unionId)
                    .orElseThrow(() -> new BadRequestException("error.adminExchange.ownerNotBound"));
        }

        String corpId = trimToNull(request.owner().corpId());
        String userId = trimToNull(request.owner().userId());
        String openId = trimToNull(request.owner().openId());
        if (corpId == null || (userId == null && openId == null)) {
            throw new BadRequestException("error.adminExchange.ownerIdentifierMissing");
        }

        if (userId != null) {
            Optional<IdentityBinding> byUserId = identityBindingRepository.findByProviderAndCorpIdAndDingTalkUserId(
                    PROVIDER_DINGTALK,
                    corpId,
                    userId
            );
            if (byUserId.isPresent()) {
                return byUserId.get();
            }
        }
        if (openId != null) {
            return identityBindingRepository.findByProviderAndCorpIdAndDingTalkOpenId(PROVIDER_DINGTALK, corpId, openId)
                    .orElseThrow(() -> new BadRequestException("error.adminExchange.ownerNotBound"));
        }
        throw new BadRequestException("error.adminExchange.ownerNotBound");
    }

    private List<String> resolveScopes(List<String> requestedScopes) {
        Set<String> allowed = new LinkedHashSet<>(properties.getAllowedScopes());
        List<String> raw = requestedScopes == null || requestedScopes.isEmpty()
                ? properties.getDefaultScopes()
                : requestedScopes;
        List<String> result = raw.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(scope -> !scope.isEmpty())
                .distinct()
                .toList();
        if (result.isEmpty()) {
            throw new BadRequestException("error.adminExchange.scopeEmpty");
        }
        if (!allowed.containsAll(result)) {
            throw new ForbiddenException("error.adminExchange.scopeNotAllowed");
        }
        return result;
    }

    private String buildTokenName(String agentName, String agentId) {
        String label = firstNonBlank(agentName, agentId, "agent");
        String suffix = randomSuffix();
        String base = TOKEN_NAME_PREFIX + " " + label;
        int maxBase = Math.max(1, 64 - suffix.length() - 1);
        if (base.length() > maxBase) {
            base = base.substring(0, maxBase);
        }
        return base + " " + suffix;
    }

    private String randomSuffix() {
        byte[] bytes = new byte[6];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String auditDetailJson(AdminExchangeRequest request, IdentityBinding binding, List<String> scopes, Long tokenId, String tokenPrefix) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("source", request.source());
        detail.put("agentId", request.agentId());
        detail.put("agentName", request.agentName());
        detail.put("ownerProvider", normalizeProvider(request.owner().provider()));
        detail.put("ownerCorpId", request.owner().corpId());
        detail.put("ownerUnionId", request.owner().unionId());
        detail.put("ownerUserId", request.owner().userId());
        detail.put("ownerOpenId", request.owner().openId());
        detail.put("bindingId", binding.getId());
        detail.put("tokenId", tokenId);
        detail.put("tokenPrefix", tokenPrefix);
        detail.put("creationMethod", "ADMIN_EXCHANGE");
        detail.put("scopes", scopes);
        detail.put("expiresAt", request.expiresAt());
        detail.put("externalRequestId", request.requestId());
        return toJson(detail);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("error.badRequest");
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String normalizeProvider(String provider) {
        return provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
    }

    private String extraValue(IdentityBinding binding, String key) {
        if (binding.getExtraJson() == null) {
            return null;
        }
        Object value = binding.getExtraJson().get(key);
        return value == null ? null : value.toString();
    }

    private String formatInstant(Instant value) {
        return value == null ? null : value.toString();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return realIp == null || realIp.isBlank() ? request.getRemoteAddr() : realIp;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }
}
