package com.iflytek.skillhub.domain.recommendation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OperationRecommendationRepository {
    OperationRecommendation save(OperationRecommendation recommendation);
    Optional<OperationRecommendation> findNonDeletedBySkillId(Long skillId);
    List<OperationRecommendation> findCurrentWeekly(Instant now, Collection<String> badges, Pageable pageable);
    List<OperationRecommendation> findActiveWeekly(Collection<String> badges);
    Page<OperationRecommendation> findDisplayable(Instant now, Pageable pageable);
    Page<OperationRecommendation> findForAdmin(RecommendationStatus status, RecommendationCacheStatus cacheStatus, Pageable pageable);
}
