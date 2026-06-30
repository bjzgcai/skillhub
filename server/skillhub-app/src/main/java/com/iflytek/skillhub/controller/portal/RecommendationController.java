package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.RecommendationResponse;
import com.iflytek.skillhub.service.RecommendationAppService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/recommendations", "/api/web/recommendations"})
public class RecommendationController extends BaseApiController {

    private final RecommendationAppService recommendationAppService;

    public RecommendationController(ApiResponseFactory responseFactory, RecommendationAppService recommendationAppService) {
        super(responseFactory);
        this.recommendationAppService = recommendationAppService;
    }

    @GetMapping("/weekly-current")
    public ApiResponse<RecommendationResponse> currentWeekly() {
        return ok("response.success.read", recommendationAppService.getCurrentWeekly());
    }

    @GetMapping
    public ApiResponse<PageResponse<RecommendationResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok("response.success.read", recommendationAppService.listPublic(page, size));
    }

    @GetMapping("/weekly-history")
    public ApiResponse<PageResponse<RecommendationResponse>> historyWeekly(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok("response.success.read", recommendationAppService.listHistoryWeekly(page, size));
    }
}
