package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.RecommendationCreateRequest;
import com.iflytek.skillhub.dto.RecommendationResponse;
import com.iflytek.skillhub.dto.RecommendationUpdateRequest;
import com.iflytek.skillhub.service.RecommendationAppService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/recommendations")
@PreAuthorize("hasAnyRole('SKILL_ADMIN', 'SUPER_ADMIN')")
public class AdminRecommendationController extends BaseApiController {

    private final RecommendationAppService recommendationAppService;

    public AdminRecommendationController(ApiResponseFactory responseFactory, RecommendationAppService recommendationAppService) {
        super(responseFactory);
        this.recommendationAppService = recommendationAppService;
    }

    @GetMapping
    public ApiResponse<PageResponse<RecommendationResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cacheStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok("response.success.read", recommendationAppService.listAdmin(status, cacheStatus, page, size));
    }

    @PostMapping
    public ApiResponse<RecommendationResponse> create(
            @Valid @RequestBody RecommendationCreateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.created", recommendationAppService.create(request, principal.userId()));
    }

    @PostMapping("/{namespace}/{slug}")
    public ApiResponse<RecommendationResponse> createBySkill(
            @PathVariable String namespace,
            @PathVariable String slug,
            @Valid @RequestBody(required = false) RecommendationUpdateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.created", recommendationAppService.createForSkill(namespace, slug, request, principal.userId()));
    }

    @PutMapping("/{namespace}/{slug}")
    public ApiResponse<RecommendationResponse> update(
            @PathVariable String namespace,
            @PathVariable String slug,
            @Valid @RequestBody RecommendationUpdateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.updated", recommendationAppService.update(namespace, slug, request, principal.userId()));
    }

    @PostMapping("/{namespace}/{slug}/offline")
    public ApiResponse<RecommendationResponse> offline(
            @PathVariable String namespace,
            @PathVariable String slug,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.updated", recommendationAppService.offline(namespace, slug, principal.userId()));
    }

    @PostMapping("/{namespace}/{slug}/online")
    public ApiResponse<RecommendationResponse> online(
            @PathVariable String namespace,
            @PathVariable String slug,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.updated", recommendationAppService.online(namespace, slug, principal.userId()));
    }
}
