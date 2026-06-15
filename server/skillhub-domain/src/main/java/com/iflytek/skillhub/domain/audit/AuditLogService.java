package com.iflytek.skillhub.domain.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Records audit log entries for administrative and security-relevant actions.
 */
@Service
public class AuditLogService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String AUTH_TOKEN_ID = "skillhub.auth.tokenId";
    private static final String AUTH_TOKEN_PREFIX = "skillhub.auth.tokenPrefix";
    private static final String AUTH_SUBJECT_TYPE = "skillhub.auth.subjectType";
    private static final String AUTH_SUBJECT_ID = "skillhub.auth.subjectId";

    private final AuditLogRepository auditLogRepository;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, Clock clock, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AuditLog record(String actorUserId,
                           String action,
                           String targetType,
                           Long targetId,
                           String requestId,
                           String clientIp,
                           String userAgent,
                           String detailJson) {
        Instant createdAt = Instant.now(clock);
        return auditLogRepository.save(new AuditLog(
            actorUserId,
            action,
            targetType,
            targetId,
            requestId,
            clientIp,
            userAgent,
            enrichAuthContext(detailJson),
            createdAt
        ));
    }

    private String enrichAuthContext(String detailJson) {
        String subjectType = trimToNull(MDC.get(AUTH_SUBJECT_TYPE));
        if (subjectType == null || "USER".equals(subjectType)) {
            return detailJson;
        }

        Map<String, Object> enriched = parseDetailJson(detailJson);
        Map<String, Object> auth = new LinkedHashMap<>();
        putIfPresent(auth, "tokenId", MDC.get(AUTH_TOKEN_ID));
        putIfPresent(auth, "tokenPrefix", MDC.get(AUTH_TOKEN_PREFIX));
        putIfPresent(auth, "subjectType", subjectType);
        putIfPresent(auth, "subjectId", MDC.get(AUTH_SUBJECT_ID));
        enriched.put("auth", auth);
        try {
            return objectMapper.writeValueAsString(enriched);
        } catch (JsonProcessingException e) {
            return detailJson;
        }
    }

    private Map<String, Object> parseDetailJson(String detailJson) {
        String normalized = trimToNull(detailJson);
        if (normalized == null) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(objectMapper.readValue(normalized, MAP_TYPE));
        } catch (Exception ignored) {
            Map<String, Object> wrapped = new LinkedHashMap<>();
            wrapped.put("detail", detailJson);
            return wrapped;
        }
    }

    private void putIfPresent(Map<String, Object> target, String key, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            target.put(key, normalized);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
