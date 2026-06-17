package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.controller.support.SkillPackageArchiveExtractor;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PublishDisplayMetadataPreviewResponse;
import com.iflytek.skillhub.dto.PublishResponse;
import com.iflytek.skillhub.metrics.SkillHubMetrics;
import com.iflytek.skillhub.ratelimit.RateLimit;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Upload endpoints for skill packages.
 *
 * <p>The controller is responsible for archive extraction and request shaping,
 * while the domain service owns all publication validation and state changes.
 */
@RestController
@RequestMapping({"/api/v1/skills", "/api/web/skills"})
public class SkillPublishController extends BaseApiController {

    private final SkillPublishService skillPublishService;
    private final SkillPackageArchiveExtractor skillPackageArchiveExtractor;
    private final SkillHubMetrics skillHubMetrics;

    public SkillPublishController(SkillPublishService skillPublishService,
                                  SkillPackageArchiveExtractor skillPackageArchiveExtractor,
                                  ApiResponseFactory responseFactory,
                                  SkillHubMetrics skillHubMetrics) {
        super(responseFactory);
        this.skillPublishService = skillPublishService;
        this.skillPackageArchiveExtractor = skillPackageArchiveExtractor;
        this.skillHubMetrics = skillHubMetrics;
    }

    /**
     * Publishes an uploaded package into the target namespace after archive
     * extraction and visibility parsing.
     */
    @PostMapping("/{namespace}/publish")
    @RateLimit(category = "publish", authenticated = 10, anonymous = 0)
    public ApiResponse<PublishResponse> publish(
            @PathVariable String namespace,
            @RequestParam("file") MultipartFile file,
            @RequestParam("visibility") String visibility,
            @RequestParam(value = "displayName", required = false) String displayName,
            @RequestParam(value = "summary", required = false) String summary,
            @RequestParam(value = "allowFailedReview", defaultValue = "false") boolean allowFailedReview,
            @AuthenticationPrincipal PlatformPrincipal principal) throws IOException {

        SkillVisibility skillVisibility = SkillVisibility.valueOf(visibility.toUpperCase());

        List<PackageEntry> entries;
        try {
            entries = skillPackageArchiveExtractor.extract(file);
        } catch (IllegalArgumentException e) {
            throw new DomainBadRequestException("error.skill.publish.package.invalid", e.getMessage());
        }

        SkillPublishService.PublishResult publishResult = skillPublishService.publishFromEntries(
                namespace,
                entries,
                principal.userId(),
                skillVisibility,
                principal.platformRoles(),
                displayName,
                summary,
                allowFailedReview
        );

        PublishResponse response = new PublishResponse(
                publishResult.skillId(),
                namespace,
                publishResult.slug(),
                publishResult.version().getVersion(),
                publishResult.version().getStatus().name(),
                publishResult.version().getFileCount(),
                publishResult.version().getTotalSize()
        );
        skillHubMetrics.incrementSkillPublish(namespace, publishResult.version().getStatus().name());

        return ok("response.success.published", response);
    }

    @PostMapping("/{namespace}/publish/preview")
    @RateLimit(category = "publish", authenticated = 20, anonymous = 0)
    public ApiResponse<PublishDisplayMetadataPreviewResponse> previewDisplayMetadata(
            @PathVariable String namespace,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal PlatformPrincipal principal) throws IOException {
        List<PackageEntry> entries;
        try {
            entries = skillPackageArchiveExtractor.extract(file);
        } catch (IllegalArgumentException e) {
            throw new DomainBadRequestException("error.skill.publish.package.invalid", e.getMessage());
        }

        SkillPublishService.DisplayMetadataPreview preview = skillPublishService.previewDisplayMetadata(
                namespace,
                entries,
                principal.userId()
        );
        return ok("response.success", new PublishDisplayMetadataPreviewResponse(
                preview.slug(),
                preview.existingSkill(),
                preview.displayName(),
                preview.summary()
        ));
    }
}
