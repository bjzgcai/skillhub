package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.badge.SkillBadge;
import com.iflytek.skillhub.domain.badge.SkillBadgeRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillBadgeJpaRepository extends JpaRepository<SkillBadge, Long>, SkillBadgeRepository {
    List<SkillBadge> findBySkillId(Long skillId);
    List<SkillBadge> findBySkillIdIn(List<Long> skillIds);
    Optional<SkillBadge> findBySkillIdAndBadgeType(Long skillId, String badgeType);
}
