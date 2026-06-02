from enum import Enum
from typing import Literal

from pydantic import BaseModel, Field


class ScanStatus(str, Enum):
    COMPLETED = "completed"
    FAILED = "failed"


class GateVerdict(str, Enum):
    PASS = "PASS"
    WARN = "WARN"
    FAIL = "FAIL"
    MANUAL_REVIEW = "MANUAL_REVIEW"


class RiskLevel(str, Enum):
    NONE = "NONE"
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"


class FindingSeverity(str, Enum):
    INFO = "INFO"
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"


class ScannerStatus(BaseModel):
    name: Literal["skill-vetter", "semgrep", "osv-scanner"]
    status: Literal["pending", "completed", "failed", "skipped"]
    version: str | None = None
    duration_seconds: float = 0
    message: str | None = None


class Finding(BaseModel):
    scanner: str
    rule_id: str
    severity: FindingSeverity
    category: str
    file: str | None = None
    line: int | None = Field(default=None, ge=1)
    message: str
    metadata: dict[str, object] = Field(default_factory=dict)


class ScanSummary(BaseModel):
    critical: int = 0
    high: int = 0
    medium: int = 0
    low: int = 0
    info: int = 0


class SyncScanResponse(BaseModel):
    scan_id: str
    status: ScanStatus
    verdict: GateVerdict
    risk_level: RiskLevel
    policy_version: str
    scanner_versions: dict[str, str | None]
    scanners: list[ScannerStatus]
    summary: ScanSummary
    findings: list[Finding]
    duration_seconds: float
