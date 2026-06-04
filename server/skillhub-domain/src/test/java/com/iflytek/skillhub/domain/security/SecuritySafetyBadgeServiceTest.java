package com.iflytek.skillhub.domain.security;

import com.iflytek.skillhub.domain.badge.SkillBadge;
import com.iflytek.skillhub.domain.badge.SkillBadgeRepository;
import com.iflytek.skillhub.domain.badge.SkillBadgeTypes;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SecuritySafetyBadgeServiceTest {

    @Mock
    private SkillBadgeRepository skillBadgeRepository;

    @Mock
    private SecurityAuditRepository securityAuditRepository;

    private SecuritySafetyBadgeService service;

    @BeforeEach
    void setUp() {
        service = new SecuritySafetyBadgeService(skillBadgeRepository, securityAuditRepository);
    }

    @Test
    void syncSafetyBadge_attachesBadgeWhenPublishedScanIsSafe() throws Exception {
        SkillVersion version = publishedVersion();
        given(skillBadgeRepository.findBySkillIdAndBadgeType(8L, SkillBadgeTypes.SCANNED_SAFE)).willReturn(Optional.empty());

        service.syncSafetyBadge(version, SecurityVerdict.SAFE);

        ArgumentCaptor<SkillBadge> captor = ArgumentCaptor.forClass(SkillBadge.class);
        verify(skillBadgeRepository).save(captor.capture());
        assertThat(captor.getValue().getSkillId()).isEqualTo(8L);
        assertThat(captor.getValue().getBadgeType()).isEqualTo(SkillBadgeTypes.SCANNED_SAFE);
        assertThat(captor.getValue().getSource()).isEqualTo("SCANNER_PASS");
        assertThat(captor.getValue().getSkillVersionId()).isEqualTo(42L);
    }

    @Test
    void syncSafetyBadge_removesBadgeWhenPublishedScanIsNotSafe() throws Exception {
        SkillVersion version = publishedVersion();
        SkillBadge attached = new SkillBadge(8L, SkillBadgeTypes.SCANNED_SAFE, "SCANNER_PASS", 42L, null, "security-scanner");
        given(skillBadgeRepository.findBySkillIdAndBadgeType(8L, SkillBadgeTypes.SCANNED_SAFE)).willReturn(Optional.of(attached));

        service.syncSafetyBadge(version, SecurityVerdict.BLOCKED);

        verify(skillBadgeRepository).delete(attached);
        verify(skillBadgeRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void syncSafetyBadge_skipsPendingReviewVersion() throws Exception {
        SkillVersion version = versionWithStatus(SkillVersionStatus.PENDING_REVIEW);

        service.syncSafetyBadge(version, SecurityVerdict.SAFE);

        verify(skillBadgeRepository, never()).findBySkillIdAndBadgeType(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
        verify(skillBadgeRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private SkillVersion publishedVersion() throws Exception {
        return versionWithStatus(SkillVersionStatus.PUBLISHED);
    }

    private SkillVersion versionWithStatus(SkillVersionStatus status) throws Exception {
        SkillVersion version = new SkillVersion(8L, "1.0.0", "publisher-1");
        version.setStatus(status);
        Field field = SkillVersion.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(version, 42L);
        return version;
    }
}
