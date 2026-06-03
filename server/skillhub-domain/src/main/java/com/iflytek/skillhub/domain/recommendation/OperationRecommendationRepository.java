package com.iflytek.skillhub.domain.recommendation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.Optional;

public interface OperationRecommendationRepository {
    OperationRecommendation save(OperationRecommendation recommendation);
    Optional<OperationRecommendation> findNonDeletedBySkillId(Long skillId);
    Page<OperationRecommendation> findDisplayable(Instant now, Pageable pageable);
    Page<OperationRecommendation> findForAdmin(RecommendationStatus status, RecommendationCacheStatus cacheStatus, Pageable pageable);
}
