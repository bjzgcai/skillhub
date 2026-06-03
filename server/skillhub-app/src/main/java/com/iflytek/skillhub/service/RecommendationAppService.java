package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.recommendation.OperationRecommendation;
import com.iflytek.skillhub.domain.recommendation.OperationRecommendationRepository;
import com.iflytek.skillhub.domain.recommendation.RecommendationCacheStatus;
import com.iflytek.skillhub.domain.recommendation.RecommendationSourceType;
import com.iflytek.skillhub.domain.recommendation.RecommendationStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.service.SkillLifecycleProjectionService;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.RecommendationCreateRequest;
import com.iflytek.skillhub.dto.RecommendationResponse;
import com.iflytek.skillhub.dto.RecommendationUpdateRequest;
import com.iflytek.skillhub.dto.SkillLifecycleVersionResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RecommendationAppService {

    private static final String DEFAULT_NAMESPACE = "global";

    private final OperationRecommendationRepository recommendationRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final NamespaceRepository namespaceRepository;
    private final SkillLifecycleProjectionService projectionService;
    private final Clock clock;

    public RecommendationAppService(
            OperationRecommendationRepository recommendationRepository,
            SkillRepository skillRepository,
            SkillVersionRepository skillVersionRepository,
            NamespaceRepository namespaceRepository,
            SkillLifecycleProjectionService projectionService) {
        this.recommendationRepository = recommendationRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.namespaceRepository = namespaceRepository;
        this.projectionService = projectionService;
        this.clock = Clock.systemUTC();
    }

    public PageResponse<RecommendationResponse> listPublic(int page, int size) {
        Page<OperationRecommendation> recommendations = recommendationRepository.findDisplayable(
                Instant.now(clock), PageRequest.of(Math.max(0, page), clampSize(size)));
        return toPageResponse(recommendations);
    }

    public PageResponse<RecommendationResponse> listAdmin(String status, String cacheStatus, int page, int size) {
        RecommendationStatus parsedStatus = parseEnum(status, RecommendationStatus.class, "error.recommendation.status.invalid");
        RecommendationCacheStatus parsedCacheStatus = parseEnum(cacheStatus, RecommendationCacheStatus.class, "error.recommendation.cacheStatus.invalid");
        Page<OperationRecommendation> recommendations = recommendationRepository.findForAdmin(
                parsedStatus, parsedCacheStatus, PageRequest.of(Math.max(0, page), clampSize(size)));
        return toPageResponse(recommendations);
    }

    @Transactional
    public RecommendationResponse create(RecommendationCreateRequest request, String actorUserId) {
        String namespaceSlug = normalizeOrDefault(request.namespace(), DEFAULT_NAMESPACE);
        String skillSlug = requireText(request.slug(), "error.recommendation.slug.required");
        return createForSkill(namespaceSlug, skillSlug, request, actorUserId);
    }

    @Transactional
    public RecommendationResponse createForSkill(
            String namespaceSlug,
            String skillSlug,
            RecommendationUpdateRequest request,
            String actorUserId) {
        RecommendationCreateRequest createRequest = new RecommendationCreateRequest(
                namespaceSlug,
                skillSlug,
                request == null ? null : request.title(),
                request == null ? null : request.summary(),
                request == null ? null : request.reason(),
                request == null ? null : request.badge(),
                request == null ? null : request.priority(),
                request == null ? null : request.startAt(),
                request == null ? null : request.endAt());
        return createForSkill(namespaceSlug, skillSlug, createRequest, actorUserId);
    }

    @Transactional
    public RecommendationResponse update(String namespaceSlug, String skillSlug, RecommendationUpdateRequest request, String actorUserId) {
        OperationRecommendation recommendation = getRecommendation(namespaceSlug, skillSlug);
        applyEditableFields(recommendation, request.title(), request.summary(), request.reason(), request.badge(), request.priority(), request.startAt(), request.endAt());
        recommendation.setUpdatedBy(actorUserId);
        return toResponse(recommendationRepository.save(recommendation), loadSkillMap(List.of(recommendation)));
    }

    @Transactional
    public RecommendationResponse offline(String namespaceSlug, String skillSlug, String actorUserId) {
        OperationRecommendation recommendation = getRecommendation(namespaceSlug, skillSlug);
        recommendation.setStatus(RecommendationStatus.OFFLINE);
        recommendation.setUpdatedBy(actorUserId);
        return toResponse(recommendationRepository.save(recommendation), loadSkillMap(List.of(recommendation)));
    }

    @Transactional
    public RecommendationResponse online(String namespaceSlug, String skillSlug, String actorUserId) {
        OperationRecommendation recommendation = getRecommendation(namespaceSlug, skillSlug);
        ensurePublishedDownloadableVersion(recommendation.getSkillId());
        recommendation.setCacheStatus(RecommendationCacheStatus.READY);
        recommendation.setStatus(RecommendationStatus.ACTIVE);
        recommendation.setUpdatedBy(actorUserId);
        return toResponse(recommendationRepository.save(recommendation), loadSkillMap(List.of(recommendation)));
    }

    private RecommendationResponse createForSkill(
            String namespaceSlug,
            String skillSlug,
            RecommendationCreateRequest request,
            String actorUserId) {
        String normalizedNamespace = normalizeOrDefault(namespaceSlug, DEFAULT_NAMESPACE);
        String normalizedSlug = requireText(skillSlug, "error.recommendation.slug.required");
        Skill skill = findRecommendableSkill(normalizedNamespace, normalizedSlug);
        ensurePublishedDownloadableVersion(skill.getId());

        OperationRecommendation recommendation = recommendationRepository.findNonDeletedBySkillId(skill.getId())
                .orElseGet(() -> new OperationRecommendation(RecommendationSourceType.LOCAL_SKILL, skill.getId(), normalizedNamespace, skill.getSlug()));
        recommendation.setSkillId(skill.getId());
        recommendation.setNamespace(normalizedNamespace);
        recommendation.setSlug(skill.getSlug());
        recommendation.setStatus(RecommendationStatus.ACTIVE);
        recommendation.setCacheStatus(RecommendationCacheStatus.READY);
        recommendation.setCacheError(null);
        recommendation.setTitle(firstText(request.title(), skill.getDisplayName(), skill.getSlug()));
        recommendation.setSummary(firstText(request.summary(), skill.getSummary(), ""));
        recommendation.setReason(trimToNull(request.reason()));
        recommendation.setBadge(trimToNull(request.badge()));
        recommendation.setPriority(request.priority() == null ? 0 : request.priority());
        recommendation.setStartAt(request.startAt());
        recommendation.setEndAt(request.endAt());
        if (recommendation.getCreatedBy() == null) {
            recommendation.setCreatedBy(actorUserId);
        }
        recommendation.setUpdatedBy(actorUserId);
        return toResponse(recommendationRepository.save(recommendation), loadSkillMap(List.of(recommendation)));
    }

    private Skill findRecommendableSkill(String namespaceSlug, String skillSlug) {
        return skillRepository.findByNamespaceSlugAndSlug(namespaceSlug, skillSlug).stream()
                .filter(candidate -> candidate.getStatus() == SkillStatus.ACTIVE && !candidate.isHidden())
                .findFirst()
                .orElseThrow(() -> new DomainBadRequestException("error.skill.notFound", namespaceSlug + "/" + skillSlug));
    }

    private void ensurePublishedDownloadableVersion(Long skillId) {
        boolean hasPublished = skillVersionRepository.findBySkillIdAndStatus(skillId, SkillVersionStatus.PUBLISHED).stream()
                .anyMatch(SkillVersion::isDownloadReady);
        if (!hasPublished) {
            throw new DomainBadRequestException("error.recommendation.noPublishedVersion");
        }
    }

    private PageResponse<RecommendationResponse> toPageResponse(Page<OperationRecommendation> page) {
        Map<Long, SkillSummaryResponse> skillsById = loadSkillMap(page.getContent());
        return new PageResponse<>(
                page.getContent().stream().map(item -> toResponse(item, skillsById)).toList(),
                page.getTotalElements(),
                page.getNumber(),
                page.getSize());
    }

    private Map<Long, SkillSummaryResponse> loadSkillMap(List<OperationRecommendation> recommendations) {
        List<Long> skillIds = recommendations.stream()
                .map(OperationRecommendation::getSkillId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (skillIds.isEmpty()) {
            return Map.of();
        }
        List<Skill> skills = skillRepository.findByIdIn(skillIds);
        Map<Long, Namespace> namespacesById = namespaceRepository.findByIdIn(skills.stream()
                        .map(Skill::getNamespaceId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Namespace::getId, Function.identity()));
        Map<Long, SkillLifecycleProjectionService.Projection> projections = projectionService.projectPublishedSummaries(skills);
        return skills.stream().collect(Collectors.toMap(Skill::getId, skill -> toSkillSummary(skill, namespacesById, projections.get(skill.getId()))));
    }

    private SkillSummaryResponse toSkillSummary(
            Skill skill,
            Map<Long, Namespace> namespacesById,
            SkillLifecycleProjectionService.Projection projection) {
        Namespace namespace = namespacesById.get(skill.getNamespaceId());
        return new SkillSummaryResponse(
                skill.getId(),
                skill.getSlug(),
                skill.getDisplayName(),
                skill.getSummary(),
                skill.getStatus().name(),
                skill.getDownloadCount(),
                skill.getStarCount(),
                skill.getRatingAvg(),
                skill.getRatingCount(),
                namespace != null ? namespace.getSlug() : null,
                skill.getUpdatedAt(),
                false,
                toLifecycleVersion(projection != null ? projection.headlineVersion() : null),
                toLifecycleVersion(projection != null ? projection.publishedVersion() : null),
                toLifecycleVersion(projection != null ? projection.ownerPreviewVersion() : null),
                projection != null ? projection.resolutionMode().name() : null);
    }

    private SkillLifecycleVersionResponse toLifecycleVersion(SkillLifecycleProjectionService.VersionProjection projection) {
        if (projection == null) {
            return null;
        }
        return new SkillLifecycleVersionResponse(projection.id(), projection.version(), projection.status());
    }

    private RecommendationResponse toResponse(OperationRecommendation recommendation, Map<Long, SkillSummaryResponse> skillsById) {
        return new RecommendationResponse(
                recommendation.getSourceType().name(),
                recommendation.getStatus().name(),
                recommendation.getCacheStatus().name(),
                recommendation.getSkillId(),
                recommendation.getNamespace(),
                recommendation.getSlug(),
                recommendation.getTitle(),
                recommendation.getSummary(),
                recommendation.getReason(),
                recommendation.getBadge(),
                recommendation.getPriority(),
                recommendation.getStartAt(),
                recommendation.getEndAt(),
                recommendation.getCacheError(),
                recommendation.getSkillId() == null ? null : skillsById.get(recommendation.getSkillId()),
                recommendation.getCreatedAt(),
                recommendation.getUpdatedAt());
    }

    private OperationRecommendation getRecommendation(String namespaceSlug, String skillSlug) {
        Skill skill = findRecommendableSkill(normalizeOrDefault(namespaceSlug, DEFAULT_NAMESPACE), skillSlug);
        return recommendationRepository.findNonDeletedBySkillId(skill.getId())
                .orElseThrow(() -> new DomainBadRequestException("error.recommendation.notFound", namespaceSlug + "/" + skillSlug));
    }

    private void applyEditableFields(OperationRecommendation recommendation, String title, String summary, String reason,
                                     String badge, Integer priority, Instant startAt, Instant endAt) {
        if (title != null) recommendation.setTitle(requireText(title, "error.recommendation.title.required"));
        if (summary != null) recommendation.setSummary(trimToNull(summary));
        if (reason != null) recommendation.setReason(trimToNull(reason));
        if (badge != null) recommendation.setBadge(trimToNull(badge));
        if (priority != null) recommendation.setPriority(priority);
        recommendation.setStartAt(startAt);
        recommendation.setEndAt(endAt);
    }

    private <E extends Enum<E>> E parseEnum(String value, Class<E> type, String errorCode) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainBadRequestException(errorCode, value);
        }
    }

    private String requireText(String value, String errorCode) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new DomainBadRequestException(errorCode);
        }
        return normalized;
    }

    private String normalizeOrDefault(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private String firstText(String... values) {
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return "";
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }
}
