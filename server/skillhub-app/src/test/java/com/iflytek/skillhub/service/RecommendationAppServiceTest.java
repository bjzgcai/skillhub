package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.label.LabelDefinition;
import com.iflytek.skillhub.domain.label.LabelDefinitionService;
import com.iflytek.skillhub.domain.label.LabelTranslation;
import com.iflytek.skillhub.domain.label.LabelType;
import com.iflytek.skillhub.domain.label.SkillLabel;
import com.iflytek.skillhub.domain.label.SkillLabelRepository;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.recommendation.OperationRecommendation;
import com.iflytek.skillhub.domain.recommendation.OperationRecommendationRepository;
import com.iflytek.skillhub.domain.recommendation.RecommendationCacheStatus;
import com.iflytek.skillhub.domain.recommendation.RecommendationStatus;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillLifecycleProjectionService;
import com.iflytek.skillhub.dto.RecommendationCreateRequest;
import com.iflytek.skillhub.dto.RecommendationResponse;
import com.iflytek.skillhub.dto.RecommendationUpdateRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationAppServiceTest {

    @Mock
    private OperationRecommendationRepository recommendationRepository;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private SkillVersionRepository skillVersionRepository;

    @Mock
    private NamespaceRepository namespaceRepository;

    @Mock
    private SkillLifecycleProjectionService projectionService;

    @Mock
    private SkillLabelRepository skillLabelRepository;

    @Mock
    private LabelDefinitionService labelDefinitionService;

    @Mock
    private LabelLocalizationService labelLocalizationService;

    @Mock
    private SkillBadgeAppService skillBadgeAppService;

    private RecommendationAppService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationAppService(
                recommendationRepository,
                skillRepository,
                skillVersionRepository,
                namespaceRepository,
                projectionService,
                skillLabelRepository,
                labelDefinitionService,
                labelLocalizationService,
                skillBadgeAppService
        );
        when(recommendationRepository.save(any(OperationRecommendation.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void create_shouldBindLocalSkillRecommendation() {
        Skill skill = recommendableSkill();
        stubSkillLookup(skill);
        when(recommendationRepository.findNonDeletedBySkillId(42L)).thenReturn(Optional.empty());
        stubResponseProjection(skill);

        RecommendationResponse response = service.create(
                new RecommendationCreateRequest(null, "demo-skill", null, null, "精选", "推荐", 100, null, null),
                "admin-1"
        );

        assertEquals("LOCAL_SKILL", response.sourceType());
        assertEquals("READY", response.cacheStatus());
        assertEquals("ACTIVE", response.status());
        assertEquals(42L, response.skillId());
        assertEquals("global", response.namespace());
        assertEquals("demo-skill", response.slug());
        assertEquals("Demo Skill", response.title());
        assertEquals("A useful demo skill", response.summary());
        assertEquals(100, response.priority());
        assertNotNull(response.skill());
        assertEquals("global", response.skill().namespace());
    }

    @Test
    void create_shouldIncludeSkillBadgesInRecommendationSkillSummary() {
        Skill skill = recommendableSkill();
        stubSkillLookup(skill);
        when(recommendationRepository.findNonDeletedBySkillId(42L)).thenReturn(Optional.empty());
        stubResponseProjection(skill);
        when(skillBadgeAppService.buildBadgesBySkillId(List.of(42L)))
                .thenReturn(Map.of(42L, List.of(new com.iflytek.skillhub.dto.SkillBadgeDto("SCANNED_SAFE", "扫描安全", "SCANNER_PASS"))));

        RecommendationResponse response = service.create(
                new RecommendationCreateRequest(null, "demo-skill", null, null, "精选", "推荐", 100, null, null),
                "admin-1"
        );

        assertNotNull(response.skill());
        assertEquals(1, response.skill().badges().size());
        assertEquals("SCANNED_SAFE", response.skill().badges().get(0).type());
        assertEquals("扫描安全", response.skill().badges().get(0).displayName());
    }

    @Test
    void createForSkill_shouldReactivateExistingRecommendation() {
        Skill skill = recommendableSkill();
        stubSkillLookup(skill);
        OperationRecommendation existing = new OperationRecommendation(
                com.iflytek.skillhub.domain.recommendation.RecommendationSourceType.LOCAL_SKILL,
                42L,
                "global",
                "demo-skill");
        existing.setStatus(RecommendationStatus.OFFLINE);
        existing.setCacheStatus(RecommendationCacheStatus.READY);
        existing.setTitle("Old title");
        when(recommendationRepository.findNonDeletedBySkillId(42L)).thenReturn(Optional.of(existing));
        stubResponseProjection(skill);

        RecommendationResponse response = service.createForSkill(
                "global",
                "demo-skill",
                new RecommendationUpdateRequest("New title", null, null, null, 10, null, null),
                "admin-1");

        assertEquals("ACTIVE", response.status());
        assertEquals("New title", response.title());
        assertEquals(10, response.priority());
    }

    @Test
    void offlineAndOnline_shouldManageRecommendationByNamespaceAndSlug() {
        Skill skill = recommendableSkill();
        stubSkillLookup(skill);
        OperationRecommendation existing = new OperationRecommendation(
                com.iflytek.skillhub.domain.recommendation.RecommendationSourceType.LOCAL_SKILL,
                42L,
                "global",
                "demo-skill");
        existing.setStatus(RecommendationStatus.ACTIVE);
        existing.setCacheStatus(RecommendationCacheStatus.READY);
        existing.setTitle("Demo Skill");
        when(recommendationRepository.findNonDeletedBySkillId(42L)).thenReturn(Optional.of(existing));
        stubResponseProjection(skill);

        RecommendationResponse offline = service.offline("global", "demo-skill", "admin-1");
        RecommendationResponse online = service.online("global", "demo-skill", "admin-1");

        assertEquals("OFFLINE", offline.status());
        assertEquals("ACTIVE", online.status());
        assertEquals("READY", online.cacheStatus());
    }

    private Skill recommendableSkill() {
        Skill skill = new Skill(7L, "demo-skill", "owner-1", SkillVisibility.PUBLIC);
        setField(skill, "id", 42L);
        skill.setDisplayName("Demo Skill");
        skill.setSummary("A useful demo skill");
        return skill;
    }

    private void stubSkillLookup(Skill skill) {
        SkillVersion version = new SkillVersion(42L, "1.0.0", "owner-1");
        version.setStatus(SkillVersionStatus.PUBLISHED);
        version.setDownloadReady(true);
        when(skillRepository.findByNamespaceSlugAndSlug("global", "demo-skill")).thenReturn(List.of(skill));
        when(skillVersionRepository.findBySkillIdAndStatus(42L, SkillVersionStatus.PUBLISHED)).thenReturn(List.of(version));
    }

    private void stubResponseProjection(Skill skill) {
        Namespace namespace = new Namespace("global", "Global", "owner-1");
        setField(namespace, "id", 7L);
        when(skillRepository.findByIdIn(List.of(42L))).thenReturn(List.of(skill));
        when(namespaceRepository.findByIdIn(List.of(7L))).thenReturn(List.of(namespace));
        when(projectionService.projectPublishedSummaries(List.of(skill))).thenReturn(Map.of());
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
