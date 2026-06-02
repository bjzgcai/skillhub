package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.security.ScannerType;
import com.iflytek.skillhub.domain.security.SecurityAudit;
import com.iflytek.skillhub.domain.security.SecurityAuditRepository;
import com.iflytek.skillhub.domain.security.SecurityFinding;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.SecurityAuditResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/skills/{skillId}/versions/{versionId}/security-audit")
public class SecurityAuditController extends BaseApiController {

    private final SecurityAuditRepository securityAuditRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final VisibilityChecker visibilityChecker;
    private final ObjectMapper objectMapper;

    public SecurityAuditController(SecurityAuditRepository securityAuditRepository,
                                   SkillRepository skillRepository,
                                   SkillVersionRepository skillVersionRepository,
                                   VisibilityChecker visibilityChecker,
                                   ApiResponseFactory responseFactory,
                                   ObjectMapper objectMapper) {
        super(responseFactory);
        this.securityAuditRepository = securityAuditRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.visibilityChecker = visibilityChecker;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ApiResponse<List<SecurityAuditResponse>> getSecurityAudits(
            @PathVariable Long skillId,
            @PathVariable Long versionId,
            @RequestParam(required = false) String scannerType,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        SkillVersion version = skillVersionRepository.findById(versionId)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.version.notFound", versionId));
        if (!version.getSkillId().equals(skillId)) {
            throw new DomainBadRequestException("error.skill.version.notFound", versionId);
        }
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.notFound", skillId));
        if (!canViewAudit(skill, principal, userNsRoles)) {
            throw new DomainForbiddenException("error.forbidden");
        }

        List<SecurityAudit> audits;
        if (scannerType != null && !scannerType.isBlank()) {
            ScannerType type = ScannerType.fromValue(scannerType);
            audits = securityAuditRepository
                    .findLatestActiveByVersionIdAndScannerType(versionId, type)
                    .map(List::of)
                    .orElse(List.of());
        } else {
            audits = securityAuditRepository.findLatestActiveByVersionId(versionId);
        }

        List<SecurityAuditResponse> responses = audits.stream()
                .map(this::toResponse)
                .toList();
        return ok("security_audit.found", responses);
    }

    private boolean canViewAudit(Skill skill,
                                 PlatformPrincipal principal,
                                 Map<Long, NamespaceRole> userNsRoles) {
        if (principal == null) {
            return false;
        }
        Set<String> platformRoles = principal.platformRoles() != null ? principal.platformRoles() : Set.of();
        if (platformRoles.contains("SUPER_ADMIN") || platformRoles.contains("SKILL_ADMIN")) {
            return true;
        }
        Map<Long, NamespaceRole> namespaceRoles = userNsRoles != null ? userNsRoles : Map.of();
        return visibilityChecker.canAccess(skill, principal.userId(), namespaceRoles);
    }

    private SecurityAuditResponse toResponse(SecurityAudit audit) {
        List<SecurityFinding> findings = deserializeFindings(audit.getFindings());
        return new SecurityAuditResponse(
                audit.getId(),
                audit.getScanId(),
                audit.getScannerType().getValue(),
                audit.getVerdict(),
                audit.getIsSafe(),
                audit.getMaxSeverity(),
                resolveRiskLevel(audit, findings),
                resolveStringMetadata(findings, "policyVersion"),
                resolveStringMapMetadata(findings, "scannerVersions"),
                summarizeFindings(audit, findings),
                audit.getFindingsCount(),
                findings,
                audit.getScanDurationSeconds(),
                audit.getScannedAt(),
                audit.getCreatedAt()
        );
    }


    private String resolveRiskLevel(SecurityAudit audit, List<SecurityFinding> findings) {
        String metadataRisk = resolveStringMetadata(findings, "riskLevel");
        if (metadataRisk != null && !metadataRisk.isBlank()) {
            return metadataRisk;
        }
        return audit.getMaxSeverity();
    }

    private Map<String, Integer> summarizeFindings(SecurityAudit audit, List<SecurityFinding> findings) {
        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("critical", 0);
        summary.put("high", 0);
        summary.put("medium", 0);
        summary.put("low", 0);
        summary.put("info", 0);
        for (SecurityFinding finding : findings) {
            if (finding.severity() == null || finding.severity().isBlank()) {
                continue;
            }
            String key = finding.severity().toLowerCase(Locale.ROOT);
            if (summary.containsKey(key)) {
                summary.put(key, summary.get(key) + 1);
            }
        }
        if (findings.isEmpty() && audit.getFindingsCount() != null && audit.getFindingsCount() > 0) {
            String key = audit.getMaxSeverity() == null ? "info" : audit.getMaxSeverity().toLowerCase(Locale.ROOT);
            if (!summary.containsKey(key)) {
                key = "info";
            }
            summary.put(key, audit.getFindingsCount());
        }
        return summary;
    }

    private String resolveStringMetadata(List<SecurityFinding> findings, String key) {
        for (SecurityFinding finding : findings) {
            Object value = finding.metadata() == null ? null : finding.metadata().get(key);
            if (value instanceof String stringValue && !stringValue.isBlank()) {
                return stringValue;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> resolveStringMapMetadata(List<SecurityFinding> findings, String key) {
        for (SecurityFinding finding : findings) {
            Object value = finding.metadata() == null ? null : finding.metadata().get(key);
            if (value instanceof Map<?, ?> mapValue) {
                Map<String, String> result = new LinkedHashMap<>();
                mapValue.forEach((mapKey, mapEntryValue) -> {
                    if (mapKey != null && mapEntryValue != null) {
                        result.put(String.valueOf(mapKey), String.valueOf(mapEntryValue));
                    }
                });
                return result;
            }
        }
        return Map.of();
    }

    private List<SecurityFinding> deserializeFindings(String findingsJson) {
        if (findingsJson == null || findingsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(findingsJson, new TypeReference<List<SecurityFinding>>() {
            });
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }
}
