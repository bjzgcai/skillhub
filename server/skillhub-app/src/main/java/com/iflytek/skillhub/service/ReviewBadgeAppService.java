package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.badge.SkillBadge;
import com.iflytek.skillhub.domain.badge.SkillBadgeRepository;
import com.iflytek.skillhub.domain.badge.SkillBadgeTypes;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.dto.ReviewBadgeOptionResponse;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewBadgeAppService {

    private static final String MANUAL_RISK_REVIEW_SOURCE = "MANUAL_RISK_REVIEW";

    private static final Map<String, BadgeOption> REVIEW_BADGE_OPTIONS = createReviewBadgeOptions();

    private final SkillBadgeRepository skillBadgeRepository;

    private static Map<String, BadgeOption> createReviewBadgeOptions() {
        LinkedHashMap<String, BadgeOption> options = new LinkedHashMap<>();
        options.put(SkillBadgeTypes.CREDENTIAL_RISK, new BadgeOption("凭证风险", "技能文档涉及 API Key / token 的本地保存或读取，建议使用环境变量或平台 secret 管理，避免明文落盘。"));
        options.put(SkillBadgeTypes.MEMORY_WRITE, new BadgeOption("记忆写入", "技能会写入或更新长期记忆，请确认写入范围、隐私边界与用户授权。"));
        options.put(SkillBadgeTypes.LOCAL_FILE_SYNC, new BadgeOption("本地文件同步", "技能会访问或同步本地文件，请确认路径范围、权限边界与数据外传风险。"));
        options.put(SkillBadgeTypes.FALSE_POSITIVE_ALLOWED, new BadgeOption("误报放行", "安全扫描命中项已人工复核为可接受风险或误报，后续版本仍需重新审查。"));
        options.put(SkillBadgeTypes.REQUIRES_API_KEY, new BadgeOption("需要 API Key", "技能运行需要配置 API Key，请优先使用环境变量或平台 secret 管理。"));
        options.put(SkillBadgeTypes.REQUIRES_OAUTH, new BadgeOption("需要 OAuth 授权", "技能运行需要 OAuth 授权，请确认授权范围与回调地址配置。"));
        options.put(SkillBadgeTypes.PENDING_REVIEW, new BadgeOption("待进一步确认", "当前技能仍需要进一步安全确认，不建议作为已扫描安全技能展示。"));
        return Collections.unmodifiableMap(options);
    }

    public ReviewBadgeAppService(SkillBadgeRepository skillBadgeRepository) {
        this.skillBadgeRepository = skillBadgeRepository;
    }

    public List<ReviewBadgeOptionResponse> listReviewBadgeOptions() {
        return REVIEW_BADGE_OPTIONS.entrySet().stream()
                .map(entry -> new ReviewBadgeOptionResponse(entry.getKey(), entry.getValue().displayName(), entry.getValue().description()))
                .toList();
    }

    @Transactional
    public List<String> attachReviewBadges(Long skillId, Long skillVersionId, List<String> badgeTypes, String reviewerId) {
        List<String> normalizedBadgeTypes = normalizeBadgeTypes(badgeTypes);
        for (String badgeType : normalizedBadgeTypes) {
            SkillBadge badge = skillBadgeRepository.findBySkillIdAndBadgeType(skillId, badgeType)
                    .orElseGet(() -> new SkillBadge(
                            skillId,
                            badgeType,
                            MANUAL_RISK_REVIEW_SOURCE,
                            skillVersionId,
                            null,
                            reviewerId
                    ));
            badge.refresh(MANUAL_RISK_REVIEW_SOURCE, skillVersionId, null, reviewerId);
            badge.updateDescription(REVIEW_BADGE_OPTIONS.get(badgeType).description());
            skillBadgeRepository.save(badge);
        }
        return normalizedBadgeTypes;
    }

    public List<String> normalizeBadgeTypes(List<String> badgeTypes) {
        if (badgeTypes == null || badgeTypes.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String badgeType : badgeTypes) {
            if (badgeType == null || badgeType.isBlank()) {
                continue;
            }
            String normalizedBadgeType = badgeType.trim().toUpperCase();
            if (!REVIEW_BADGE_OPTIONS.containsKey(normalizedBadgeType)) {
                throw new DomainBadRequestException("review.badge.unsupported", normalizedBadgeType);
            }
            normalized.add(normalizedBadgeType);
        }
        return List.copyOf(normalized);
    }

    private record BadgeOption(String displayName, String description) {
    }
}
