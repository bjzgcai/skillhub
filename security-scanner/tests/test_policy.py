from app.models import Finding, FindingSeverity, GateVerdict, RiskLevel
from app.policy import evaluate_policy, summarize_findings


def finding(severity, *, scanner="semgrep", category="static_analysis"):
    return Finding(
        scanner=scanner,
        rule_id="rule",
        severity=severity,
        category=category,
        message="message",
    )


def test_policy_fails_blocking_skill_vetter_high():
    verdict, risk = evaluate_policy([
        finding(FindingSeverity.HIGH, scanner="skill-vetter", category="credential_access")
    ])

    assert verdict == GateVerdict.FAIL
    assert risk == RiskLevel.HIGH


def test_policy_fails_gitleaks_secret_high():
    verdict, risk = evaluate_policy([
        finding(FindingSeverity.HIGH, scanner="gitleaks", category="secret_exposure")
    ])

    assert verdict == GateVerdict.FAIL
    assert risk == RiskLevel.HIGH


def test_policy_routes_generic_high_to_manual_review():
    verdict, risk = evaluate_policy([finding(FindingSeverity.HIGH)])

    assert verdict == GateVerdict.MANUAL_REVIEW
    assert risk == RiskLevel.HIGH


def test_summary_counts_findings():
    summary = summarize_findings([finding(FindingSeverity.HIGH), finding(FindingSeverity.LOW)])

    assert summary.high == 1
    assert summary.low == 1
