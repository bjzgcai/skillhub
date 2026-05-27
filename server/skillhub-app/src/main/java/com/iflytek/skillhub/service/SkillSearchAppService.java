package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.entity.IdentityBinding;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.domain.label.LabelDefinition;
import com.iflytek.skillhub.domain.label.LabelDefinitionService;
import com.iflytek.skillhub.domain.label.LabelTranslation;
import com.iflytek.skillhub.domain.label.SkillLabel;
import com.iflytek.skillhub.domain.label.SkillLabelRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.service.SkillLifecycleProjectionService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.SkillLabelDto;
import com.iflytek.skillhub.dto.SkillOwnerResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.search.SearchQuery;
import com.iflytek.skillhub.search.SearchQueryService;
import com.iflytek.skillhub.search.SearchResult;
import com.iflytek.skillhub.search.SearchVisibilityScope;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Application service that assembles discovery responses from search matches.
 *
 * <p>{@link com.iflytek.skillhub.search.SearchQueryService} remains the match
 * engine, while this service enriches matched ids into API-facing summaries.
 * Authoritative detail, version, and file reads remain in
 * {@link com.iflytek.skillhub.domain.skill.service.SkillQueryService}.
 */
@Service
public class SkillSearchAppService {

    private record LabelSummary(LabelDefinition definition, List<LabelTranslation> translations) {
    }

    private final SearchQueryService searchQueryService;
    private final SkillRepository skillRepository;
    private final NamespaceRepository namespaceRepository;
    private final NamespaceService namespaceService;
    private final SkillLifecycleProjectionService skillLifecycleProjectionService;
    private final UserAccountRepository userAccountRepository;
    private final SkillLabelRepository skillLabelRepository;
    private final LabelDefinitionService labelDefinitionService;
    private final LabelLocalizationService labelLocalizationService;
    private final IdentityBindingRepository identityBindingRepository;

    public SkillSearchAppService(
            SearchQueryService searchQueryService,
            SkillRepository skillRepository,
            NamespaceRepository namespaceRepository,
            NamespaceService namespaceService,
            SkillLifecycleProjectionService skillLifecycleProjectionService,
            UserAccountRepository userAccountRepository,
            SkillLabelRepository skillLabelRepository,
            LabelDefinitionService labelDefinitionService,
            LabelLocalizationService labelLocalizationService,
            IdentityBindingRepository identityBindingRepository) {
        this.searchQueryService = searchQueryService;
        this.skillRepository = skillRepository;
        this.namespaceRepository = namespaceRepository;
        this.namespaceService = namespaceService;
        this.skillLifecycleProjectionService = skillLifecycleProjectionService;
        this.userAccountRepository = userAccountRepository;
        this.skillLabelRepository = skillLabelRepository;
        this.labelDefinitionService = labelDefinitionService;
        this.labelLocalizationService = labelLocalizationService;
        this.identityBindingRepository = identityBindingRepository;
    }

    public record SearchResponse(
            List<SkillSummaryResponse> items,
            long total,
            int page,
            int size
    ) {}

    public SearchResponse search(
            String keyword,
            String namespaceSlug,
            String sortBy,
            int page,
            int size,
            String userId,
            Map<Long, NamespaceRole> userNsRoles) {
        return search(keyword, namespaceSlug, sortBy, page, size, List.of(), "all", userId, userNsRoles);
    }

    public SearchResponse search(
            String keyword,
            String namespaceSlug,
            String sortBy,
            int page,
            int size,
            List<String> labelSlugs,
            String userId,
            Map<Long, NamespaceRole> userNsRoles) {
        return search(keyword, namespaceSlug, sortBy, page, size, labelSlugs, "all", userId, userNsRoles);
    }

    public SearchResponse search(
            String keyword,
            String namespaceSlug,
            String sortBy,
            int page,
            int size,
            List<String> labelSlugs,
            String source,
            String userId,
            Map<Long, NamespaceRole> userNsRoles) {

        Long namespaceId = resolveNamespaceId(namespaceSlug, userId, userNsRoles);

        SearchVisibilityScope scope = buildVisibilityScope(userId, userNsRoles);

        return searchVisibleSkills(keyword, namespaceId, sortBy != null ? sortBy : "newest", page, size, labelSlugs, source, scope);
    }

    private Long resolveNamespaceId(String namespaceSlug, String userId, Map<Long, NamespaceRole> userNsRoles) {
        if (namespaceSlug == null || namespaceSlug.isBlank()) {
            return null;
        }
        return namespaceService.getNamespaceBySlugForRead(namespaceSlug, userId, userNsRoles != null ? userNsRoles : Map.of()).getId();
    }

    private SearchVisibilityScope buildVisibilityScope(String userId, Map<Long, NamespaceRole> userNsRoles) {
        if (userId == null || userNsRoles == null) {
            return SearchVisibilityScope.anonymous();
        }

        Set<Long> memberNamespaceIds = userNsRoles.keySet();
        Set<Long> adminNamespaceIds = userNsRoles.entrySet().stream()
                .filter(e -> e.getValue() == NamespaceRole.ADMIN)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
        adminNamespaceIds.addAll(userNsRoles.entrySet().stream()
                .filter(e -> e.getValue() == NamespaceRole.OWNER)
                .map(Map.Entry::getKey)
                .toList());

        return new SearchVisibilityScope(userId, memberNamespaceIds, adminNamespaceIds);
    }

    private SearchResponse searchVisibleSkills(
            String keyword,
            Long namespaceId,
            String sortBy,
            int page,
            int size,
            List<String> labelSlugs,
            String source,
            SearchVisibilityScope scope) {
        SearchResult result = searchQueryService.search(new SearchQuery(
                keyword,
                namespaceId,
                scope,
                sortBy,
                page,
                size,
                normalizeLabelSlugs(labelSlugs),
                normalizeSource(source)
        ));
        List<SkillSummaryResponse> pageItems = mapVisibleSkillSummaries(result.skillIds());
        return new SearchResponse(pageItems, result.total(), page, size);
    }

    private String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return "all";
        }
        String normalized = source.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "internal", "clawhub" -> normalized;
            default -> "all";
        };
    }

    private List<String> normalizeLabelSlugs(List<String> labelSlugs) {
        if (labelSlugs == null || labelSlugs.isEmpty()) {
            return List.of();
        }
        return labelSlugs.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(java.util.Locale.ROOT))
                .distinct()
                .toList();
    }

    private List<SkillSummaryResponse> mapVisibleSkillSummaries(List<Long> skillIds) {
        if (skillIds.isEmpty()) {
            return List.of();
        }

        List<Skill> matchedSkills = skillRepository.findByIdIn(skillIds);
        Map<Long, Skill> skillsById = matchedSkills.stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity()));

        List<Long> namespaceIds = matchedSkills.stream()
                .map(Skill::getNamespaceId)
                .distinct()
                .toList();
        Map<Long, Namespace> namespacesById = namespaceIds.isEmpty()
                ? Map.of()
                : namespaceRepository.findByIdIn(namespaceIds).stream()
                .collect(Collectors.toMap(Namespace::getId, Function.identity()));
        Map<Long, String> namespaceSlugsById = namespacesById.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getSlug()));
        Map<Long, SkillLifecycleProjectionService.Projection> projectionsBySkillId =
                skillLifecycleProjectionService.projectPublishedSummaries(matchedSkills);
        Map<String, SkillOwnerResponse> ownersByUserId = buildOwnersByUserId(matchedSkills);
        Map<Long, List<SkillLabelDto>> labelsBySkillId = buildLabelsBySkillId(skillIds);

        return skillIds.stream()
                .map(skillsById::get)
                .filter(java.util.Objects::nonNull)
                .map(skill -> toSummaryResponse(
                        skill,
                        namespaceSlugsById,
                        projectionsBySkillId.get(skill.getId()),
                        ownersByUserId.get(skill.getOwnerId()),
                        labelsBySkillId.getOrDefault(skill.getId(), List.of())))
                .toList();
    }

    private Map<String, SkillOwnerResponse> buildOwnersByUserId(List<Skill> matchedSkills) {
        List<String> ownerIds = matchedSkills.stream()
                .map(Skill::getOwnerId)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
        if (ownerIds.isEmpty()) {
            return Map.of();
        }
        Map<String, UserAccount> usersById = userAccountRepository.findByIdIn(ownerIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, Function.identity()));
        Map<String, String> dingtalkUserIdsByUserId = identityBindingRepository
                .findByProviderCodeAndUserIdIn("dingtalk", ownerIds)
                .stream()
                .map(binding -> new java.util.AbstractMap.SimpleImmutableEntry<>(
                        binding.getUserId(),
                        resolveDingtalkUserId(binding)))
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left));

        return ownerIds.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        userId -> toOwnerResponse(usersById.get(userId), dingtalkUserIdsByUserId.get(userId))));
    }

    private SkillOwnerResponse toOwnerResponse(UserAccount user, String dingtalkUserId) {
        return new SkillOwnerResponse(
                user != null ? user.getDisplayName() : null,
                user != null ? user.getAvatarUrl() : null,
                dingtalkUserId
        );
    }

    private String resolveDingtalkUserId(IdentityBinding binding) {
        if (binding.getExtraJson() == null) {
            return null;
        }
        Object userId = binding.getExtraJson().get("userid");
        return userId == null ? null : userId.toString();
    }

    private Map<Long, List<SkillLabelDto>> buildLabelsBySkillId(List<Long> skillIds) {
        List<SkillLabel> skillLabels = skillLabelRepository.findBySkillIdIn(skillIds);
        if (skillLabels.isEmpty()) {
            return Map.of();
        }
        List<Long> labelIds = skillLabels.stream()
                .map(SkillLabel::getLabelId)
                .distinct()
                .toList();
        Map<Long, LabelSummary> labelsById = labelDefinitionService.listByIds(labelIds).stream()
                .collect(Collectors.toMap(
                        LabelDefinition::getId,
                        definition -> new LabelSummary(definition, List.of())));
        Map<Long, List<LabelTranslation>> translationsByLabelId = labelDefinitionService.listTranslationsByLabelIds(labelIds);
        labelsById = labelsById.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new LabelSummary(
                                entry.getValue().definition(),
                                translationsByLabelId.getOrDefault(entry.getKey(), List.of()))));

        Map<Long, LabelSummary> finalLabelsById = labelsById;
        return skillLabels.stream()
                .map(skillLabel -> new java.util.AbstractMap.SimpleImmutableEntry<>(
                        skillLabel,
                        finalLabelsById.get(skillLabel.getLabelId())))
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.groupingBy(
                        entry -> entry.getKey().getSkillId(),
                        Collectors.collectingAndThen(Collectors.toList(), labels -> labels.stream()
                                .map(Map.Entry::getValue)
                                .sorted(java.util.Comparator
                                        .comparingInt((LabelSummary label) -> label.definition().getSortOrder())
                                        .thenComparing(label -> label.definition().getSlug()))
                                .map(label -> toLabelDto(label.definition(), label.translations()))
                                .toList())));
    }

    private SkillLabelDto toLabelDto(LabelDefinition definition, List<LabelTranslation> translations) {
        return new SkillLabelDto(
                definition.getSlug(),
                definition.getType().name(),
                labelLocalizationService.resolveDisplayName(definition.getSlug(), translations)
        );
    }

    private SkillSummaryResponse toSummaryResponse(
            Skill skill,
            Map<Long, String> namespaceSlugsById,
            SkillLifecycleProjectionService.Projection projection,
            SkillOwnerResponse owner,
            List<SkillLabelDto> labels) {
        String namespaceSlug = namespaceSlugsById.get(skill.getNamespaceId());

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
                namespaceSlug,
                owner,
                labels,
                skill.getUpdatedAt(),
                false,
                toLifecycleVersion(projection.headlineVersion()),
                toLifecycleVersion(projection.publishedVersion()),
                toLifecycleVersion(projection.ownerPreviewVersion()),
                projection.resolutionMode().name()
        );
    }

    private com.iflytek.skillhub.dto.SkillLifecycleVersionResponse toLifecycleVersion(
            SkillLifecycleProjectionService.VersionProjection projection) {
        if (projection == null) {
            return null;
        }
        return new com.iflytek.skillhub.dto.SkillLifecycleVersionResponse(
                projection.id(),
                projection.version(),
                projection.status()
        );
    }

}
