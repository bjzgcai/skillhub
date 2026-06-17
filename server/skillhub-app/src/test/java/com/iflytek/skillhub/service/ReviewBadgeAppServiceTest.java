package com.iflytek.skillhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.badge.SkillBadge;
import com.iflytek.skillhub.domain.badge.SkillBadgeRepository;
import com.iflytek.skillhub.domain.badge.SkillBadgeTypes;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewBadgeAppServiceTest {

    @Mock
    private SkillBadgeRepository skillBadgeRepository;

    @Test
    void normalizeBadgeTypesDeduplicatesAndRejectsUnsupportedValues() {
        ReviewBadgeAppService service = new ReviewBadgeAppService(skillBadgeRepository);

        assertEquals(
                List.of(SkillBadgeTypes.CREDENTIAL_RISK, SkillBadgeTypes.REQUIRES_API_KEY),
                service.normalizeBadgeTypes(List.of("credential_risk", " ", "CREDENTIAL_RISK", "requires_api_key"))
        );
        assertThrows(DomainBadRequestException.class, () -> service.normalizeBadgeTypes(List.of("SCANNED_SAFE")));
    }

    @Test
    void attachReviewBadgesCreatesManualRiskBadgeWithDefaultDescription() {
        ReviewBadgeAppService service = new ReviewBadgeAppService(skillBadgeRepository);
        when(skillBadgeRepository.findBySkillIdAndBadgeType(10L, SkillBadgeTypes.CREDENTIAL_RISK))
                .thenReturn(Optional.empty());
        when(skillBadgeRepository.save(any(SkillBadge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.attachReviewBadges(10L, 20L, List.of(SkillBadgeTypes.CREDENTIAL_RISK), "reviewer-1");

        ArgumentCaptor<SkillBadge> badgeCaptor = ArgumentCaptor.forClass(SkillBadge.class);
        verify(skillBadgeRepository).save(badgeCaptor.capture());
        SkillBadge saved = badgeCaptor.getValue();
        assertEquals(10L, saved.getSkillId());
        assertEquals(SkillBadgeTypes.CREDENTIAL_RISK, saved.getBadgeType());
        assertEquals("MANUAL_RISK_REVIEW", saved.getSource());
        assertEquals(20L, saved.getSkillVersionId());
        assertEquals("reviewer-1", saved.getCreatedBy());
        assertEquals("技能文档涉及 API Key / token 的本地保存或读取，建议使用环境变量或平台 secret 管理，避免明文落盘。", saved.getDescription());
    }
}
