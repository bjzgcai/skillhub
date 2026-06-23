package com.iflytek.skillhub.controller.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.RecommendationResponse;
import com.iflytek.skillhub.service.RecommendationAppService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;

@ExtendWith(MockitoExtension.class)
class RecommendationControllerTest {

    @Mock
    private RecommendationAppService recommendationAppService;

    private RecommendationController controller;

    @BeforeEach
    void setUp() {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("response.success.read", java.util.Locale.getDefault(), "ok");
        ApiResponseFactory responseFactory = new ApiResponseFactory(
                messageSource,
                Clock.fixed(Instant.parse("2026-06-22T00:00:00Z"), ZoneOffset.UTC)
        );
        controller = new RecommendationController(responseFactory, recommendationAppService);
    }

    @Test
    void currentWeekly_shouldReturnServiceResult() {
        RecommendationResponse weekly = response("skill-vetter", "WEEKLY_SKILL");
        when(recommendationAppService.getCurrentWeekly()).thenReturn(weekly);

        RecommendationResponse response = controller.currentWeekly().data();

        verify(recommendationAppService).getCurrentWeekly();
        assertThat(response.slug()).isEqualTo("skill-vetter");
        assertThat(response.badge()).isEqualTo("WEEKLY_SKILL");
    }

    @Test
    void list_shouldDelegatePagination() {
        PageResponse<RecommendationResponse> page = new PageResponse<>(List.of(response("demo", null)), 1, 2, 10);
        when(recommendationAppService.listPublic(2, 10)).thenReturn(page);

        PageResponse<RecommendationResponse> response = controller.list(2, 10).data();

        verify(recommendationAppService).listPublic(2, 10);
        assertThat(response.items()).singleElement().satisfies(item -> assertThat(item.slug()).isEqualTo("demo"));
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(10);
    }

    private RecommendationResponse response(String slug, String badge) {
        Instant now = Instant.parse("2026-06-22T00:00:00Z");
        return new RecommendationResponse(
                "LOCAL_SKILL",
                "ACTIVE",
                "READY",
                42L,
                "global",
                slug,
                "Demo",
                "Summary",
                "Reason",
                badge,
                "/recommendation-banners/weekly/demo.jpg",
                20_000,
                now,
                now.plusSeconds(604800),
                null,
                null,
                now,
                now
        );
    }
}
