package com.iflytek.skillhub.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.RecommendationResponse;
import com.iflytek.skillhub.dto.RecommendationUpdateRequest;
import com.iflytek.skillhub.service.RecommendationAppService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;

@ExtendWith(MockitoExtension.class)
class AdminRecommendationControllerTest {

    @Mock
    private RecommendationAppService recommendationAppService;

    private AdminRecommendationController controller;
    private PlatformPrincipal principal;

    @BeforeEach
    void setUp() {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("response.success.updated", java.util.Locale.getDefault(), "ok");
        messageSource.addMessage("response.success.created", java.util.Locale.getDefault(), "ok");
        messageSource.addMessage("response.success.read", java.util.Locale.getDefault(), "ok");
        ApiResponseFactory responseFactory = new ApiResponseFactory(
                messageSource,
                Clock.fixed(Instant.parse("2026-06-22T00:00:00Z"), ZoneOffset.UTC)
        );
        controller = new AdminRecommendationController(responseFactory, recommendationAppService);
        principal = new PlatformPrincipal("admin-1", "Admin", "admin@example.com", null, null, Set.of("SKILL_ADMIN"));
    }

    @Test
    void setWeeklySkill_shouldDelegateWithActorAndPayload() {
        Instant startAt = Instant.parse("2026-06-22T00:00:00Z");
        Instant endAt = Instant.parse("2026-06-29T00:00:00Z");
        RecommendationUpdateRequest request = new RecommendationUpdateRequest(
                "Weekly Skill",
                "Summary",
                "Reason",
                "ignored",
                "/recommendation-banners/weekly/demo.jpg",
                12,
                startAt,
                endAt,
                null
        );
        RecommendationResponse serviceResponse = response(startAt, endAt);
        when(recommendationAppService.setWeeklySkill(eq("global"), eq("skill-vetter"), eq(request), eq("admin-1")))
                .thenReturn(serviceResponse);

        RecommendationResponse response = controller.setWeeklySkill("global", "skill-vetter", request, principal).data();

        verify(recommendationAppService).setWeeklySkill("global", "skill-vetter", request, "admin-1");
        assertThat(response.slug()).isEqualTo("skill-vetter");
        assertThat(response.badge()).isEqualTo("WEEKLY_SKILL");
        assertThat(response.startAt()).isEqualTo(startAt);
        assertThat(response.endAt()).isEqualTo(endAt);
    }

    private RecommendationResponse response(Instant startAt, Instant endAt) {
        Instant now = Instant.parse("2026-06-22T00:00:00Z");
        return new RecommendationResponse(
                "LOCAL_SKILL",
                "ACTIVE",
                "READY",
                42L,
                "global",
                "skill-vetter",
                "Weekly Skill",
                "Summary",
                "Reason",
                "WEEKLY_SKILL",
                "/recommendation-banners/weekly/demo.jpg",
                20_000,
                startAt,
                endAt,
                null,
                null,
                null,
                now,
                now
        );
    }
}
