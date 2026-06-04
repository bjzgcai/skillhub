package com.iflytek.skillhub.domain.badge;

import java.util.List;
import java.util.Optional;

public interface SkillBadgeRepository {
    List<SkillBadge> findBySkillId(Long skillId);
    List<SkillBadge> findBySkillIdIn(List<Long> skillIds);
    Optional<SkillBadge> findBySkillIdAndBadgeType(Long skillId, String badgeType);
    SkillBadge save(SkillBadge badge);
    void delete(SkillBadge badge);
}
