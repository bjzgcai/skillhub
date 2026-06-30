package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.recommendation.OperationRecommendation;
import com.iflytek.skillhub.domain.recommendation.OperationRecommendationRepository;
import com.iflytek.skillhub.domain.recommendation.RecommendationCacheStatus;
import com.iflytek.skillhub.domain.recommendation.RecommendationStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OperationRecommendationJpaRepository
        extends JpaRepository<OperationRecommendation, Long>, OperationRecommendationRepository {

    @Query("SELECT r FROM OperationRecommendation r WHERE r.skillId = :skillId AND r.status <> com.iflytek.skillhub.domain.recommendation.RecommendationStatus.DELETED")
    Optional<OperationRecommendation> findNonDeletedBySkillId(@Param("skillId") Long skillId);

    @Query("""
            SELECT r FROM OperationRecommendation r
            JOIN Skill s ON r.skillId = s.id
            WHERE r.status = com.iflytek.skillhub.domain.recommendation.RecommendationStatus.ACTIVE
              AND r.cacheStatus = com.iflytek.skillhub.domain.recommendation.RecommendationCacheStatus.READY
              AND r.badge IN :badges
              AND s.status = com.iflytek.skillhub.domain.skill.SkillStatus.ACTIVE
              AND s.hidden = false
              AND (r.startAt IS NULL OR r.startAt <= :now)
              AND (r.endAt IS NULL OR r.endAt > :now)
              AND EXISTS (
                  SELECT v.id FROM SkillVersion v
                  WHERE v.skillId = s.id
                    AND v.status = com.iflytek.skillhub.domain.skill.SkillVersionStatus.PUBLISHED
                    AND v.downloadReady = true
              )
            ORDER BY r.priority DESC, r.updatedAt DESC, r.id DESC
            """)
    List<OperationRecommendation> findCurrentWeekly(
            @Param("now") Instant now,
            @Param("badges") Collection<String> badges,
            Pageable pageable);

    @Query("""
            SELECT r FROM OperationRecommendation r
            WHERE r.status = com.iflytek.skillhub.domain.recommendation.RecommendationStatus.ACTIVE
              AND r.badge IN :badges
            """)
    List<OperationRecommendation> findActiveWeekly(@Param("badges") Collection<String> badges);

    @Query("""
            SELECT r FROM OperationRecommendation r
            JOIN Skill s ON r.skillId = s.id
            WHERE r.status = com.iflytek.skillhub.domain.recommendation.RecommendationStatus.ACTIVE
              AND r.cacheStatus = com.iflytek.skillhub.domain.recommendation.RecommendationCacheStatus.READY
              AND r.badge IN :badges
              AND r.endAt < :now
              AND s.status = com.iflytek.skillhub.domain.skill.SkillStatus.ACTIVE
              AND s.hidden = false
              AND EXISTS (
                  SELECT v.id FROM SkillVersion v
                  WHERE v.skillId = s.id
                    AND v.status = com.iflytek.skillhub.domain.skill.SkillVersionStatus.PUBLISHED
                    AND v.downloadReady = true
              )
            ORDER BY r.endAt DESC, r.id DESC
            """)
    Page<OperationRecommendation> findHistoryWeekly(
            @Param("now") Instant now,
            @Param("badges") Collection<String> badges,
            Pageable pageable);

    @Query("""
            SELECT r FROM OperationRecommendation r
            JOIN Skill s ON r.skillId = s.id
            WHERE r.status = com.iflytek.skillhub.domain.recommendation.RecommendationStatus.ACTIVE
              AND r.cacheStatus = com.iflytek.skillhub.domain.recommendation.RecommendationCacheStatus.READY
              AND s.status = com.iflytek.skillhub.domain.skill.SkillStatus.ACTIVE
              AND s.hidden = false
              AND (r.startAt IS NULL OR r.startAt <= :now)
              AND (r.endAt IS NULL OR r.endAt > :now)
              AND EXISTS (
                  SELECT v.id FROM SkillVersion v
                  WHERE v.skillId = s.id
                    AND v.status = com.iflytek.skillhub.domain.skill.SkillVersionStatus.PUBLISHED
                    AND v.downloadReady = true
              )
            ORDER BY r.priority DESC, r.updatedAt DESC, r.id DESC
            """)
    Page<OperationRecommendation> findDisplayable(@Param("now") Instant now, Pageable pageable);

    @Query("""
            SELECT r FROM OperationRecommendation r
            WHERE (:status IS NULL OR r.status = :status)
              AND (:cacheStatus IS NULL OR r.cacheStatus = :cacheStatus)
            ORDER BY r.priority DESC, r.updatedAt DESC, r.id DESC
            """)
    Page<OperationRecommendation> findForAdmin(
            @Param("status") RecommendationStatus status,
            @Param("cacheStatus") RecommendationCacheStatus cacheStatus,
            Pageable pageable);
}
