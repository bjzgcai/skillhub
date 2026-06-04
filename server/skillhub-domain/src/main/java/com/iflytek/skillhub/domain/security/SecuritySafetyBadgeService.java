package com.iflytek.skillhub.domain.security;

import com.iflytek.skillhub.domain.badge.SkillBadge;
import com.iflytek.skillhub.domain.badge.SkillBadgeRepository;
import com.iflytek.skillhub.domain.badge.SkillBadgeTypes;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecuritySafetyBadgeService {

    private static final String SCANNER_PASS_SOURCE = "SCANNER_PASS";

    private final SkillBadgeRepository skillBadgeRepository;
    private final SecurityAuditRepository securityAuditRepository;

    public SecuritySafetyBadgeService(
            SkillBadgeRepository skillBadgeRepository,
            SecurityAuditRepository securityAuditRepository) {
        this.skillBadgeRepository = skillBadgeRepository;
        this.securityAuditRepository = securityAuditRepository;
    }

    @Transactional
    public void syncSafetyBadge(SkillVersion version, SecurityVerdict verdict) {
        if (!isPublishedVersion(version)) {
            return;
        }
        applySafetyBadge(version, verdict);
    }

    @Transactional
    public void syncSafetyBadgeFromLatestAudits(SkillVersion version) {
        if (!isPublishedVersion(version)) {
            return;
        }
        List<SecurityAudit> audits = securityAuditRepository.findLatestActiveByVersionId(version.getId());
        SecurityVerdict verdict = !audits.isEmpty() && audits.stream().allMatch(audit -> Boolean.TRUE.equals(audit.getIsSafe()))
                ? SecurityVerdict.SAFE
                : SecurityVerdict.DANGEROUS;
        applySafetyBadge(version, verdict);
    }

    private boolean isPublishedVersion(SkillVersion version) {
        return version != null
                && version.getId() != null
                && version.getSkillId() != null
                && version.getStatus() == SkillVersionStatus.PUBLISHED;
    }

    private void applySafetyBadge(SkillVersion version, SecurityVerdict verdict) {
        if (verdict == SecurityVerdict.SAFE) {
            skillBadgeRepository.findBySkillIdAndBadgeType(version.getSkillId(), SkillBadgeTypes.SCANNED_SAFE)
                    .map(existing -> {
                        existing.refresh(SCANNER_PASS_SOURCE, version.getId(), null, null);
                        return skillBadgeRepository.save(existing);
                    })
                    .orElseGet(() -> skillBadgeRepository.save(new SkillBadge(
                            version.getSkillId(),
                            SkillBadgeTypes.SCANNED_SAFE,
                            SCANNER_PASS_SOURCE,
                            version.getId(),
                            null,
                            null)));
            return;
        }
        skillBadgeRepository.findBySkillIdAndBadgeType(version.getSkillId(), SkillBadgeTypes.SCANNED_SAFE)
                .ifPresent(skillBadgeRepository::delete);
    }
}
