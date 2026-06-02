from __future__ import annotations

import os
import shutil
import tempfile
import time
import uuid
from typing import Annotated

from fastapi import FastAPI, File, Form, HTTPException, UploadFile

from .config import settings
from .adapters import run_all_adapters
from .models import ScanStatus, SyncScanResponse
from .policy import evaluate_policy, summarize_findings
from .zip_guard import PackageValidationError, extract_zip_package, validate_zip_package

app = FastAPI(
    title="Skill Security Scanner",
    version="0.1.0",
    description="Unified security scanning service for SkillHub skill bundles.",
)


@app.get("/health")
def health() -> dict[str, object]:
    return {
        "ok": True,
        "service": "skill-security-scanner",
        "version": "0.1.0",
        "policy_version": settings.policy_version,
    }


@app.post("/v1/scans:sync", response_model=SyncScanResponse)
async def sync_scan(
    file: Annotated[UploadFile, File(description="Skill bundle zip")],
    namespace: Annotated[str | None, Form()] = None,
    slug: Annotated[str | None, Form()] = None,
    version: Annotated[str | None, Form()] = None,
    skill_id: Annotated[str | None, Form()] = None,
    version_id: Annotated[str | None, Form()] = None,
    publisher_id: Annotated[str | None, Form()] = None,
    source: Annotated[str, Form()] = "skillhub_publish",
    policy_preset: Annotated[str, Form()] = "balanced",
    timeout_ms: Annotated[int, Form()] = 60000,
) -> SyncScanResponse:
    del namespace, slug, version, skill_id, version_id, publisher_id, source, policy_preset

    started = time.monotonic()
    scan_id = f"scan_{uuid.uuid4().hex}"
    os.makedirs(settings.workspace_root, exist_ok=True)
    workspace = tempfile.mkdtemp(prefix=f"{scan_id}-", dir=settings.workspace_root)
    package_path = os.path.join(workspace, "bundle.zip")

    try:
        size = 0
        with open(package_path, "wb") as output:
            while chunk := await file.read(1024 * 1024):
                size += len(chunk)
                if size > settings.max_package_size_bytes:
                    raise HTTPException(status_code=413, detail="Package size exceeds scanner limit")
                output.write(chunk)

        validate_zip_package(
            package_path,
            max_file_count=settings.max_file_count,
            max_uncompressed_size_bytes=settings.max_uncompressed_size_bytes,
        )
        extract_dir = os.path.join(workspace, "extracted")
        os.makedirs(extract_dir, exist_ok=True)
        extract_zip_package(package_path, extract_dir)
        adapter_results = run_all_adapters(extract_dir, timeout_ms)
    except PackageValidationError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    finally:
        await file.close()
        shutil.rmtree(workspace, ignore_errors=True)

    findings = [finding for result in adapter_results for finding in result.findings]
    verdict, risk_level = evaluate_policy(findings)
    duration = round(time.monotonic() - started, 3)
    scanners = [result.status for result in adapter_results]
    return SyncScanResponse(
        scan_id=scan_id,
        status=ScanStatus.COMPLETED,
        verdict=verdict,
        risk_level=risk_level,
        policy_version=settings.policy_version,
        scanner_versions={status.name: status.version for status in scanners},
        scanners=scanners,
        summary=summarize_findings(findings),
        findings=findings,
        duration_seconds=duration,
    )
