from __future__ import annotations

from .models import Finding, FindingSeverity, GateVerdict, RiskLevel, ScanSummary

_BLOCKING_SKILL_VETTER_CATEGORIES = {
    "credential_access",
    "external_execute",
    "private_memory_access",
    "privilege_escalation",
    "obfuscation",
}


def summarize_findings(findings: list[Finding]) -> ScanSummary:
    summary = ScanSummary()
    for finding in findings:
        match finding.severity:
            case FindingSeverity.CRITICAL:
                summary.critical += 1
            case FindingSeverity.HIGH:
                summary.high += 1
            case FindingSeverity.MEDIUM:
                summary.medium += 1
            case FindingSeverity.LOW:
                summary.low += 1
            case FindingSeverity.INFO:
                summary.info += 1
    return summary


def evaluate_policy(findings: list[Finding]) -> tuple[GateVerdict, RiskLevel]:
    if not findings:
        return GateVerdict.PASS, RiskLevel.NONE

    if any(f.severity == FindingSeverity.CRITICAL for f in findings):
        return GateVerdict.FAIL, RiskLevel.CRITICAL

    has_blocking_skill_vetter = any(
        f.scanner == "skill-vetter"
        and f.severity == FindingSeverity.HIGH
        and f.category in _BLOCKING_SKILL_VETTER_CATEGORIES
        for f in findings
    )
    if has_blocking_skill_vetter:
        return GateVerdict.FAIL, RiskLevel.HIGH

    if any(f.severity == FindingSeverity.HIGH for f in findings):
        return GateVerdict.MANUAL_REVIEW, RiskLevel.HIGH

    if any(f.severity == FindingSeverity.MEDIUM for f in findings):
        return GateVerdict.WARN, RiskLevel.MEDIUM

    if any(f.severity == FindingSeverity.LOW for f in findings):
        return GateVerdict.WARN, RiskLevel.LOW

    return GateVerdict.PASS, RiskLevel.NONE
