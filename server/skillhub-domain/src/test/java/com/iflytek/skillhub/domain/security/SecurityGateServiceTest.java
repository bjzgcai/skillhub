package com.iflytek.skillhub.domain.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import org.junit.jupiter.api.Test;

import java.util.List;

class SecurityGateServiceTest {

    @Test
    void disabledGateAllowsDownloadWithoutRepositoryLookup() {
        SecurityGateService gate = SecurityGateService.disabled();

        assertThatCode(() -> gate.assertDownloadAllowed(version(10L, "1.0.0")))
                .doesNotThrowAnyException();
    }

    @Test
    void enabledGateBlocksDangerousVerdict() {
        SecurityAuditRepository repository = mock(SecurityAuditRepository.class);
        SecurityAudit audit = new SecurityAudit(10L, ScannerType.SKILL_SCANNER);
        audit.setVerdict(SecurityVerdict.DANGEROUS);
        when(repository.findLatestActiveByVersionId(10L)).thenReturn(List.of(audit));
        SecurityGateService gate = new SecurityGateService(repository, true, false);

        assertThatThrownBy(() -> gate.assertDownloadAllowed(version(10L, "1.0.0")))
                .isInstanceOf(DomainForbiddenException.class)
                .hasMessageContaining("error.skill.security.downloadBlocked");
    }

    @Test
    void enabledGateAllowsSuspiciousByDefault() {
        SecurityAuditRepository repository = mock(SecurityAuditRepository.class);
        SecurityAudit audit = new SecurityAudit(10L, ScannerType.SKILL_SCANNER);
        audit.setVerdict(SecurityVerdict.SUSPICIOUS);
        when(repository.findLatestActiveByVersionId(10L)).thenReturn(List.of(audit));
        SecurityGateService gate = new SecurityGateService(repository, true, false);

        assertThatCode(() -> gate.assertDownloadAllowed(version(10L, "1.0.0")))
                .doesNotThrowAnyException();
    }

    private SkillVersion version(Long id, String version) {
        SkillVersion skillVersion = mock(SkillVersion.class);
        when(skillVersion.getId()).thenReturn(id);
        when(skillVersion.getVersion()).thenReturn(version);
        return skillVersion;
    }
}
