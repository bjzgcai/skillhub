package com.iflytek.skillhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.badge.SkillBadgeTypes;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.review.ReviewService;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.dto.ReviewTaskResponse;
import com.iflytek.skillhub.repository.GovernanceQueryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReviewPortalAppServiceTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Mock
    private ReviewService reviewService;
    @Mock
    private ReviewTaskRepository reviewTaskRepository;
    @Mock
    private NamespaceRepository namespaceRepository;
    @Mock
    private GovernanceQueryRepository governanceQueryRepository;
    @Mock
    private RbacService rbacService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private SkillVersionRepository skillVersionRepository;
    @Mock
    private ReviewBadgeAppService reviewBadgeAppService;

    private ObjectMapper objectMapper;
    private ReviewPortalAppService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new ReviewPortalAppService(
                reviewService,
                reviewTaskRepository,
                namespaceRepository,
                governanceQueryRepository,
                rbacService,
                auditLogService,
                skillVersionRepository,
                reviewBadgeAppService,
                objectMapper
        );
    }

    @Test
    void approveReviewRejectsBadgeMutationWithoutPlatformReviewRole() {
        when(reviewBadgeAppService.normalizeBadgeTypes(List.of(SkillBadgeTypes.CREDENTIAL_RISK)))
                .thenReturn(List.of(SkillBadgeTypes.CREDENTIAL_RISK));
        when(rbacService.getUserRoleCodes("namespace-admin")).thenReturn(Set.of());

        assertThrows(DomainForbiddenException.class, () -> service.approveReview(
                1L,
                "looks ok",
                List.of(SkillBadgeTypes.CREDENTIAL_RISK),
                "namespace-admin",
                Map.of(10L, NamespaceRole.ADMIN),
                null
        ));

        verify(reviewService, never()).approveReview(any(), any(), any(), any(), any());
        verify(reviewBadgeAppService, never()).attachReviewBadges(any(), any(), any(), any());
    }

    @Test
    void approveReviewWritesStructuredAuditJsonForMultilineCommentsAndBadges() throws Exception {
        ReviewTask task = new ReviewTask(20L, 10L, "submitter");
        ReflectionTestUtils.setField(task, "id", 1L);
        SkillVersion version = new SkillVersion(30L, "1.0.0", "submitter");
        ReflectionTestUtils.setField(version, "id", 20L);
        List<String> badges = List.of(SkillBadgeTypes.CREDENTIAL_RISK, SkillBadgeTypes.REQUIRES_API_KEY);

        when(reviewBadgeAppService.normalizeBadgeTypes(badges)).thenReturn(badges);
        when(rbacService.getUserRoleCodes("reviewer")).thenReturn(Set.of("SKILL_ADMIN"));
        when(reviewService.approveReview(eq(1L), eq("reviewer"), eq("line 1\nline 2"), any(), eq(Set.of("SKILL_ADMIN"))))
                .thenReturn(task);
        when(skillVersionRepository.findById(20L)).thenReturn(Optional.of(version));
        when(governanceQueryRepository.getReviewTaskResponse(task)).thenReturn(new ReviewTaskResponse(
                1L,
                20L,
                "global",
                "demo",
                "1.0.0",
                "APPROVED",
                "submitter",
                "Submitter",
                "reviewer",
                "Reviewer",
                "line 1\nline 2",
                Instant.EPOCH,
                Instant.EPOCH
        ));

        service.approveReview(1L, "line 1\nline 2", badges, "reviewer", Map.of(), null);

        verify(reviewBadgeAppService).attachReviewBadges(30L, 20L, badges, "reviewer");
        ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).record(
                eq("reviewer"),
                eq("REVIEW_APPROVE"),
                eq("REVIEW_TASK"),
                eq(1L),
                any(),
                any(),
                any(),
                detailCaptor.capture()
        );
        Map<String, Object> detail = objectMapper.readValue(detailCaptor.getValue(), MAP_TYPE);
        assertEquals("line 1\nline 2", detail.get("comment"));
        assertEquals(badges, detail.get("badgeTypes"));
        assertTrue(detailCaptor.getValue().contains("\\n"));
    }
}
