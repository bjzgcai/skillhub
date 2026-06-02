from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import time
from dataclasses import dataclass
from pathlib import Path

from .config import settings
from .models import Finding, FindingSeverity, ScannerStatus

_TEXT_EXTENSIONS = {
    "",
    ".md",
    ".txt",
    ".py",
    ".js",
    ".jsx",
    ".ts",
    ".tsx",
    ".sh",
    ".bash",
    ".zsh",
    ".yaml",
    ".yml",
    ".json",
    ".toml",
    ".ini",
    ".cfg",
    ".conf",
}


@dataclass(frozen=True)
class AdapterResult:
    status: ScannerStatus
    findings: list[Finding]


def run_all_adapters(skill_dir: str, timeout_ms: int) -> list[AdapterResult]:
    return [
        run_skill_vetter(skill_dir),
        run_semgrep(skill_dir, timeout_ms),
        run_osv_scanner(skill_dir, timeout_ms),
    ]


def run_skill_vetter(skill_dir: str) -> AdapterResult:
    started = time.monotonic()
    root = Path(skill_dir)
    findings: list[Finding] = []

    rules: list[tuple[str, re.Pattern[str], FindingSeverity, str, str]] = [
        (
            "credential-file-access",
            re.compile(r"(~/(?:\.ssh|\.aws|\.config)|/etc/(?:shadow|passwd))", re.IGNORECASE),
            FindingSeverity.HIGH,
            "credential_access",
            "Skill references sensitive credential or system files.",
        ),
        (
            "private-agent-memory-access",
            re.compile(r"\b(?:MEMORY|USER|SOUL|IDENTITY)\.md\b"),
            FindingSeverity.HIGH,
            "private_memory_access",
            "Skill references private OpenClaw memory/persona files.",
        ),
        (
            "download-and-execute",
            re.compile(r"\b(?:curl|wget)\b[^\n|;&]*(?:\||&&|;)\s*(?:sh|bash|zsh|python|python3|node)\b", re.IGNORECASE),
            FindingSeverity.HIGH,
            "external_execute",
            "Skill appears to download remote content and execute it.",
        ),
        (
            "privilege-escalation",
            re.compile(r"\b(?:sudo|su\s+-|chmod\s+777|chown\s+root)\b", re.IGNORECASE),
            FindingSeverity.HIGH,
            "privilege_escalation",
            "Skill appears to request elevated or overly broad filesystem permissions.",
        ),
        (
            "dynamic-code-execution",
            re.compile(r"\b(?:eval|exec)\s*\(", re.IGNORECASE),
            FindingSeverity.MEDIUM,
            "dynamic_execution",
            "Skill uses dynamic code execution.",
        ),
        (
            "base64-decode",
            re.compile(r"\bbase64\b[^\n]*(?:-d|--decode|decode|b64decode)", re.IGNORECASE),
            FindingSeverity.MEDIUM,
            "obfuscation",
            "Skill decodes base64 content, which can hide behavior.",
        ),
        (
            "browser-cookie-access",
            re.compile(r"\b(?:Cookies|Login Data|Local State)\b", re.IGNORECASE),
            FindingSeverity.HIGH,
            "credential_access",
            "Skill references browser credential/session storage files.",
        ),
    ]

    for path in _iter_text_files(root):
        rel = path.relative_to(root).as_posix()
        try:
            text = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        for line_no, line in enumerate(text.splitlines(), start=1):
            for rule_id, pattern, severity, category, message in rules:
                if pattern.search(line):
                    findings.append(
                        Finding(
                            scanner="skill-vetter",
                            rule_id=rule_id,
                            severity=severity,
                            category=category,
                            file=rel,
                            line=line_no,
                            message=message,
                        )
                    )

    duration = round(time.monotonic() - started, 3)
    return AdapterResult(
        status=ScannerStatus(
            name="skill-vetter",
            status="completed",
            version="1.0.0",
            duration_seconds=duration,
            message=f"Found {len(findings)} finding(s)",
        ),
        findings=findings,
    )


def run_semgrep(skill_dir: str, timeout_ms: int) -> AdapterResult:
    started = time.monotonic()
    semgrep = shutil.which("semgrep")
    if semgrep is None:
        return _skipped("semgrep", started, "semgrep binary not found")

    timeout = max(1, timeout_ms // 1000)
    command = [semgrep, "scan", "--json", f"--config={settings.semgrep_config}", "--error", skill_dir]
    try:
        completed = subprocess.run(command, capture_output=True, text=True, timeout=timeout, check=False)
    except subprocess.TimeoutExpired:
        return _failed("semgrep", started, "semgrep timed out")

    findings = _parse_semgrep_findings(completed.stdout)
    if completed.returncode not in (0, 1):
        return AdapterResult(
            status=ScannerStatus(
                name="semgrep",
                status="failed",
                duration_seconds=round(time.monotonic() - started, 3),
                message=_short(completed.stderr or completed.stdout or "semgrep failed"),
            ),
            findings=findings,
        )
    return AdapterResult(
        status=ScannerStatus(
            name="semgrep",
            status="completed",
            version=_tool_version(semgrep),
            duration_seconds=round(time.monotonic() - started, 3),
            message=f"Found {len(findings)} finding(s)",
        ),
        findings=findings,
    )


def run_osv_scanner(skill_dir: str, timeout_ms: int) -> AdapterResult:
    started = time.monotonic()
    osv = shutil.which("osv-scanner")
    if osv is None:
        return _skipped("osv-scanner", started, "osv-scanner binary not found")

    timeout = max(1, timeout_ms // 1000)
    command = [osv, "--format", "json", "-r", skill_dir]
    try:
        completed = subprocess.run(command, capture_output=True, text=True, timeout=timeout, check=False)
    except subprocess.TimeoutExpired:
        return _failed("osv-scanner", started, "osv-scanner timed out")

    findings = _parse_osv_findings(completed.stdout)
    combined_output = f"{completed.stdout}\n{completed.stderr}"
    if completed.returncode == 128 and "No package sources found" in combined_output:
        return AdapterResult(
            status=ScannerStatus(
                name="osv-scanner",
                status="completed",
                version=_tool_version(osv),
                duration_seconds=round(time.monotonic() - started, 3),
                message="No supported dependency manifests found",
            ),
            findings=[],
        )
    if completed.returncode not in (0, 1):
        return AdapterResult(
            status=ScannerStatus(
                name="osv-scanner",
                status="failed",
                duration_seconds=round(time.monotonic() - started, 3),
                message=_short(completed.stderr or completed.stdout or "osv-scanner failed"),
            ),
            findings=findings,
        )
    return AdapterResult(
        status=ScannerStatus(
            name="osv-scanner",
            status="completed",
            version=_tool_version(osv),
            duration_seconds=round(time.monotonic() - started, 3),
            message=f"Found {len(findings)} finding(s)",
        ),
        findings=findings,
    )


def _parse_semgrep_findings(raw_json: str) -> list[Finding]:
    if not raw_json.strip():
        return []
    try:
        payload = json.loads(raw_json)
    except json.JSONDecodeError:
        return []

    findings: list[Finding] = []
    for item in payload.get("results", []):
        extra = item.get("extra", {})
        metadata = extra.get("metadata", {}) if isinstance(extra.get("metadata", {}), dict) else {}
        severity = _map_semgrep_severity(extra.get("severity"))
        findings.append(
            Finding(
                scanner="semgrep",
                rule_id=str(item.get("check_id", "semgrep-rule")),
                severity=severity,
                category=str(metadata.get("category") or metadata.get("cwe") or "static_analysis"),
                file=item.get("path"),
                line=(item.get("start") or {}).get("line"),
                message=str(extra.get("message") or "Semgrep finding"),
                metadata={"raw_severity": extra.get("severity")},
            )
        )
    return findings


def _parse_osv_findings(raw_json: str) -> list[Finding]:
    if not raw_json.strip():
        return []
    try:
        payload = json.loads(raw_json)
    except json.JSONDecodeError:
        return []

    findings: list[Finding] = []
    for result in payload.get("results", []):
        source = result.get("source") or {}
        path = source.get("path")
        for package in result.get("packages", []):
            package_info = package.get("package") or {}
            for vuln in package.get("vulnerabilities", []):
                severity = _map_osv_severity(vuln)
                vuln_id = vuln.get("id") or "osv-vulnerability"
                package_name = package_info.get("name") or "unknown package"
                findings.append(
                    Finding(
                        scanner="osv-scanner",
                        rule_id=str(vuln_id),
                        severity=severity,
                        category="dependency_vulnerability",
                        file=path,
                        message=f"{package_name} is affected by {vuln_id}",
                        metadata={
                            "package": package_name,
                            "ecosystem": package_info.get("ecosystem"),
                            "installed_version": package_info.get("version"),
                            "summary": vuln.get("summary"),
                        },
                    )
                )
    return findings


def _iter_text_files(root: Path):
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        if path.stat().st_size > 1024 * 1024:
            continue
        if path.suffix.lower() in _TEXT_EXTENSIONS:
            yield path


def _map_semgrep_severity(value: object) -> FindingSeverity:
    normalized = str(value or "INFO").upper()
    if normalized in {"ERROR", "HIGH", "CRITICAL"}:
        return FindingSeverity.HIGH
    if normalized in {"WARNING", "MEDIUM"}:
        return FindingSeverity.MEDIUM
    if normalized == "LOW":
        return FindingSeverity.LOW
    return FindingSeverity.INFO


def _map_osv_severity(vuln: dict[str, object]) -> FindingSeverity:
    severities = vuln.get("severity") or []
    values = " ".join(str(item.get("score", "")) for item in severities if isinstance(item, dict)).upper()
    if "CRITICAL" in values:
        return FindingSeverity.CRITICAL
    if "HIGH" in values:
        return FindingSeverity.HIGH
    if "MEDIUM" in values or severities:
        return FindingSeverity.MEDIUM
    return FindingSeverity.HIGH


def _tool_version(binary: str) -> str | None:
    try:
        completed = subprocess.run([binary, "--version"], capture_output=True, text=True, timeout=5, check=False)
    except Exception:
        return None
    output = (completed.stdout or completed.stderr).strip().splitlines()
    return output[0] if output else None


def _skipped(name: str, started: float, message: str) -> AdapterResult:
    return AdapterResult(
        status=ScannerStatus(name=name, status="skipped", duration_seconds=round(time.monotonic() - started, 3), message=message),
        findings=[],
    )


def _failed(name: str, started: float, message: str) -> AdapterResult:
    return AdapterResult(
        status=ScannerStatus(name=name, status="failed", duration_seconds=round(time.monotonic() - started, 3), message=message),
        findings=[],
    )


def _short(value: str, limit: int = 240) -> str:
    single_line = " ".join(value.split())
    return single_line if len(single_line) <= limit else single_line[:limit] + "..."
