package com.iflytek.skillhub.domain.security;

import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SecurityGateService {

    private final SecurityAuditRepository securityAuditRepository;
    private final boolean downloadGateEnabled;
    private final boolean blockSuspicious;

    public SecurityGateService(SecurityAuditRepository securityAuditRepository,
                               @Value("${skillhub.security.download-gate.enabled:false}") boolean downloadGateEnabled,
                               @Value("${skillhub.security.download-gate.block-suspicious:false}") boolean blockSuspicious) {
        this.securityAuditRepository = securityAuditRepository;
        this.downloadGateEnabled = downloadGateEnabled;
        this.blockSuspicious = blockSuspicious;
    }

    private SecurityGateService() {
        this.securityAuditRepository = null;
        this.downloadGateEnabled = false;
        this.blockSuspicious = false;
    }

    public static SecurityGateService disabled() {
        return new SecurityGateService();
    }

    public void assertDownloadAllowed(SkillVersion version) {
        if (!downloadGateEnabled || version == null || securityAuditRepository == null) {
            return;
        }
        List<SecurityAudit> audits = securityAuditRepository.findLatestActiveByVersionId(version.getId());
        for (SecurityAudit audit : audits) {
            SecurityVerdict verdict = audit.getVerdict();
            if (verdict == SecurityVerdict.BLOCKED || verdict == SecurityVerdict.DANGEROUS
                    || (blockSuspicious && verdict == SecurityVerdict.SUSPICIOUS)) {
                throw new DomainForbiddenException("error.skill.security.downloadBlocked", version.getVersion());
            }
        }
    }
}
