package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.badge.SkillBadge;
import com.iflytek.skillhub.domain.badge.SkillBadgeRepository;
import com.iflytek.skillhub.domain.badge.SkillBadgeTypes;
import com.iflytek.skillhub.dto.SkillBadgeDto;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SkillBadgeAppService {

    private final SkillBadgeRepository skillBadgeRepository;

    public SkillBadgeAppService(SkillBadgeRepository skillBadgeRepository) {
        this.skillBadgeRepository = skillBadgeRepository;
    }

    public Map<Long, List<SkillBadgeDto>> buildBadgesBySkillId(List<Long> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return Map.of();
        }
        return skillBadgeRepository.findBySkillIdIn(skillIds).stream()
                .sorted(Comparator.comparingInt((SkillBadge badge) -> sortOrder(badge.getBadgeType()))
                        .thenComparing(SkillBadge::getBadgeType))
                .collect(Collectors.groupingBy(
                        SkillBadge::getSkillId,
                        Collectors.mapping(this::toDto, Collectors.toList())));
    }

    private SkillBadgeDto toDto(SkillBadge badge) {
        return new SkillBadgeDto(badge.getBadgeType(), displayName(badge.getBadgeType()), badge.getSource(), badge.getDescription());
    }

    private int sortOrder(String type) {
        return switch (type) {
            case SkillBadgeTypes.SCANNED_SAFE -> 10;
            case SkillBadgeTypes.FALSE_POSITIVE_ALLOWED -> 20;
            case SkillBadgeTypes.CREDENTIAL_RISK -> 30;
            case SkillBadgeTypes.MEMORY_WRITE -> 40;
            case SkillBadgeTypes.LOCAL_FILE_SYNC -> 50;
            case SkillBadgeTypes.PENDING_REVIEW -> 60;
            case SkillBadgeTypes.REQUIRES_API_KEY -> 70;
            case SkillBadgeTypes.REQUIRES_OAUTH -> 80;
            default -> 100;
        };
    }

    private String displayName(String type) {
        return switch (type) {
            case SkillBadgeTypes.SCANNED_SAFE -> "扫描安全";
            case SkillBadgeTypes.FALSE_POSITIVE_ALLOWED -> "误报放行";
            case SkillBadgeTypes.CREDENTIAL_RISK -> "凭证风险";
            case SkillBadgeTypes.MEMORY_WRITE -> "记忆写入";
            case SkillBadgeTypes.LOCAL_FILE_SYNC -> "本地文件同步";
            case SkillBadgeTypes.PENDING_REVIEW -> "待审核";
            case SkillBadgeTypes.REQUIRES_API_KEY -> "需要 API Key";
            case SkillBadgeTypes.REQUIRES_OAUTH -> "需要 OAuth 授权";
            default -> type;
        };
    }
}
