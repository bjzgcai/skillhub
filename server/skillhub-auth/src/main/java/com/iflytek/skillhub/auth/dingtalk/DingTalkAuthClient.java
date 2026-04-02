package com.iflytek.skillhub.auth.dingtalk;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Minimal DingTalk HTTP client used by both browser-based login and in-app SSO.
 *
 * <p>The exact upstream payloads vary by DingTalk deployment and API generation, so this client
 * normalizes a small set of commonly returned field aliases and keeps all endpoint URLs
 * configurable.
 */
@Service
public class DingTalkAuthClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
        new ParameterizedTypeReference<>() {};

    private final DingTalkAuthProperties properties;
    private final RestClient restClient;
    private final Clock clock;

    private volatile CachedToken cachedAppToken;

    @Autowired
    public DingTalkAuthClient(DingTalkAuthProperties properties) {
        this(properties, RestClient.builder().build(), Clock.systemUTC());
    }

    DingTalkAuthClient(DingTalkAuthProperties properties, RestClient restClient, Clock clock) {
        this.properties = properties;
        this.restClient = restClient;
        this.clock = clock;
    }

    public DingTalkUserIdentity resolveBrowserIdentity(String authorizationCode) {
        String userAccessToken = exchangeBrowserUserAccessToken(authorizationCode);
        Map<String, Object> browserPayload = Map.of();
        String unionId = null;
        try {
            browserPayload = getJson(properties.getBrowserUserInfoUrl(), userAccessToken);
            unionId = firstString(browserPayload, "unionId", "unionid", "openId", "openid", "sub");
        } catch (HttpClientErrorException.Forbidden ex) {
            browserPayload = Map.of();
        }
        if (unionId == null) {
            unionId = extractJwtClaim(userAccessToken, "unionId", "unionid", "sub", "openid", "openId");
        }
        if (unionId == null) {
            throw badRequest("error.auth.dingtalk.userInfoIncomplete");
        }

        String appAccessToken = getCachedAppAccessToken();
        String userId = properties.isRequireCorpMembership()
            ? resolveEnterpriseUserId(appAccessToken, unionId)
            : firstString(browserPayload, "userid", "userId");
        Map<String, Object> detailPayload = userId != null ? getEnterpriseUserDetail(appAccessToken, userId) : Map.of();
        return buildIdentity(unionId, userId, browserPayload, detailPayload);
    }

    public DingTalkUserIdentity resolveInAppIdentity(String code) {
        String appAccessToken = getCachedAppAccessToken();
        Map<String, Object> loginPayload = postJson(
            properties.getH5UserInfoUrl(),
            Map.of("code", code),
            appAccessToken
        );

        String userId = firstString(loginPayload, "userid", "userId");
        if (userId == null) {
            throw badRequest("error.auth.dingtalk.userInfoIncomplete");
        }

        Map<String, Object> detailPayload = getEnterpriseUserDetail(appAccessToken, userId);
        String unionId = firstString(detailPayload, "unionid", "unionId", "associatedUnionid", "associated_unionid");
        if (unionId == null) {
            unionId = firstString(loginPayload, "unionid", "unionId");
        }
        if (unionId == null) {
            throw badRequest("error.auth.dingtalk.userInfoIncomplete");
        }

        return buildIdentity(unionId, userId, loginPayload, detailPayload);
    }

    private DingTalkUserIdentity buildIdentity(String unionId,
                                               String userId,
                                               Map<String, Object> primaryPayload,
                                               Map<String, Object> detailPayload) {
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.putAll(primaryPayload);
        merged.putAll(detailPayload);

        String displayName = firstString(merged, "name", "nick", "displayName", "nickname", "userid", "userId");
        String email = firstString(merged, "email", "org_email", "orgEmail");
        String avatarUrl = firstString(merged, "avatar", "avatarUrl", "avatar_url");

        merged.put("unionid", unionId);
        if (userId != null) {
            merged.put("userid", userId);
        }
        if (properties.getCorpId() != null && !properties.getCorpId().isBlank()) {
            merged.put("corp_id", properties.getCorpId());
        }

        return new DingTalkUserIdentity(
            unionId,
            userId,
            displayName != null ? displayName : unionId,
            email,
            avatarUrl,
            merged
        );
    }

    private String exchangeBrowserUserAccessToken(String authorizationCode) {
        Map<String, Object> payload = postJson(
            properties.getBrowserUserTokenUrl(),
            Map.of(
                "clientId", properties.getAppKey(),
                "clientSecret", properties.getAppSecret(),
                "code", authorizationCode,
                "refreshToken", "",
                "grantType", "authorization_code"
            ),
            null
        );
        String token = firstString(payload, "accessToken", "access_token", "userAccessToken", "user_access_token");
        if (token == null) {
            throw badRequest("error.auth.dingtalk.tokenExchangeFailed");
        }
        return token;
    }

    private String resolveEnterpriseUserId(String appAccessToken, String unionId) {
        Map<String, Object> payload = postJson(
            properties.getUserIdByUnionIdUrl(),
            Map.of("unionid", unionId),
            appAccessToken
        );
        String userId = firstString(payload, "userid", "userId");
        if (userId == null) {
            throw badRequest("error.auth.dingtalk.enterpriseMembershipRequired");
        }
        return userId;
    }

    private Map<String, Object> getEnterpriseUserDetail(String appAccessToken, String userId) {
        return postJson(
            properties.getUserDetailUrl(),
            Map.of("userid", userId),
            appAccessToken
        );
    }

    private String getCachedAppAccessToken() {
        CachedToken current = cachedAppToken;
        Instant now = Instant.now(clock);
        if (current != null && current.expiresAt().isAfter(now.plusSeconds(30))) {
            return current.value();
        }

        synchronized (this) {
            current = cachedAppToken;
            now = Instant.now(clock);
            if (current != null && current.expiresAt().isAfter(now.plusSeconds(30))) {
                return current.value();
            }

            Map<String, Object> payload = postJson(
                properties.getAppTokenUrl(),
                Map.of(
                    "appKey", properties.getAppKey(),
                    "appSecret", properties.getAppSecret()
                ),
                null
            );
            String token = firstString(payload, "accessToken", "access_token");
            if (token == null) {
                throw badRequest("error.auth.dingtalk.appTokenFailed");
            }
            long expiresIn = firstLong(payload, "expireIn", "expiresIn", "expires_in");
            if (expiresIn <= 0) {
                expiresIn = 7200;
            }
            cachedAppToken = new CachedToken(token, now.plusSeconds(expiresIn));
            return token;
        }
    }

    private Map<String, Object> getJson(String url, String bearerToken) {
        RestClient.RequestHeadersSpec<?> request = restClient.get()
            .uri(url)
            .accept(MediaType.APPLICATION_JSON);
        if (bearerToken != null && !bearerToken.isBlank()) {
            request = request
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header("x-acs-dingtalk-access-token", bearerToken);
        }
        Map<String, Object> response = request
            .retrieve()
            .body(MAP_TYPE);
        return normalizeResponse(response);
    }

    private Map<String, Object> postJson(String url, Map<String, ?> body, String bearerToken) {
        String requestUrl = url;
        boolean topApiStyle = url.contains("/topapi/");
        if (topApiStyle && bearerToken != null && !bearerToken.isBlank()) {
            requestUrl = url + (url.contains("?") ? "&" : "?") + "access_token=" + bearerToken;
        }

        RestClient.RequestBodySpec request = restClient.post()
            .uri(requestUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON);
        if (bearerToken != null && !bearerToken.isBlank() && !topApiStyle) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
            request.header("x-acs-dingtalk-access-token", bearerToken);
        }

        Map<String, Object> response = request
            .body(body)
            .retrieve()
            .body(MAP_TYPE);
        return normalizeResponse(response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeResponse(Map<String, Object> response) {
        if (response == null || response.isEmpty()) {
            throw badRequest("error.auth.dingtalk.emptyResponse");
        }

        Object success = response.get("success");
        if (success instanceof Boolean flag && !flag) {
            throw badRequest("error.auth.dingtalk.upstreamRejected");
        }

        Object errcode = response.get("errcode");
        if (errcode instanceof Number number && number.intValue() != 0) {
            throw badRequest("error.auth.dingtalk.upstreamRejected");
        }

        Object code = response.get("code");
        if (code instanceof Number number && number.intValue() != 0 && !response.containsKey("accessToken")) {
            throw badRequest("error.auth.dingtalk.upstreamRejected");
        }

        for (String key : new String[] { "result", "data", "user_info", "userInfo" }) {
            Object nested = response.get(key);
            if (nested instanceof Map<?, ?> nestedMap) {
                return (Map<String, Object>) nestedMap;
            }
        }
        return response;
    }

    private String extractJwtClaim(String token, String... keys) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
            for (String key : keys) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]+)\"")
                    .matcher(payload);
                if (matcher.find()) {
                    String value = matcher.group(1);
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        return null;
    }

    private String firstString(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof String stringValue && !stringValue.isBlank()) {
                return stringValue;
            }
        }
        return null;
    }

    private long firstLong(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Number numberValue) {
                return numberValue.longValue();
            }
            if (value instanceof String stringValue && !stringValue.isBlank()) {
                try {
                    return Long.parseLong(stringValue);
                } catch (NumberFormatException ignored) {
                    // Ignore malformed upstream values and keep scanning aliases.
                }
            }
        }
        return -1;
    }

    private AuthFlowException badRequest(String messageCode) {
        return new AuthFlowException(HttpStatus.BAD_REQUEST, messageCode);
    }

    private record CachedToken(String value, Instant expiresAt) {}

    public record DingTalkUserIdentity(
        String unionId,
        String userId,
        String displayName,
        String email,
        String avatarUrl,
        Map<String, Object> rawClaims
    ) {}
}
