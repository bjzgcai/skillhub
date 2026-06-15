package com.iflytek.skillhub.domain.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditLogService auditLogService;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-03-18T02:03:04Z"), ZoneOffset.UTC);
        auditLogService = new AuditLogService(auditLogRepository, clock, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void record_usesInjectedClockForCreatedAt() {
        when(auditLogRepository.save(any(AuditLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuditLog result = auditLogService.record(
                "user-1",
                "SKILL_PUBLISH",
                "SKILL",
                7L,
                "req-1",
                "127.0.0.1",
                "JUnit",
                "{\"version\":\"1.0.0\"}"
        );

        assertThat(result.getCreatedAt()).isEqualTo(Instant.now(clock));
        assertThat(result.getAction()).isEqualTo("SKILL_PUBLISH");
        assertThat(result.getTargetId()).isEqualTo(7L);
    }

    @Test
    void record_enrichesDetailJsonWithAgentTokenContext() {
        when(auditLogRepository.save(any(AuditLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        MDC.put("skillhub.auth.subjectType", "AGENT");
        MDC.put("skillhub.auth.subjectId", "agent-1");
        MDC.put("skillhub.auth.tokenId", "7");
        MDC.put("skillhub.auth.tokenPrefix", "sk_abcd");

        AuditLog result = auditLogService.record(
                "user-1",
                "SKILL_PUBLISH",
                "SKILL",
                7L,
                "req-1",
                "127.0.0.1",
                "JUnit",
                "{\"version\":\"1.0.0\"}"
        );

        assertThat(result.getDetailJson()).contains("\"version\":\"1.0.0\"");
        assertThat(result.getDetailJson()).contains("\"auth\"");
        assertThat(result.getDetailJson()).contains("\"subjectType\":\"AGENT\"");
        assertThat(result.getDetailJson()).contains("\"subjectId\":\"agent-1\"");
        assertThat(result.getDetailJson()).contains("\"tokenPrefix\":\"sk_abcd\"");
    }
}
