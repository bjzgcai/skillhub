import io
import zipfile

from fastapi.testclient import TestClient

from app.main import app


def bundle_bytes():
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr("SKILL.md", "---\nname: demo\nversion: 1.0.0\n---\n")
    buffer.seek(0)
    return buffer.getvalue()


def test_health():
    response = TestClient(app).get("/health")
    assert response.status_code == 200
    assert response.json()["ok"] is True


def test_sync_scan_returns_contract_shape():
    response = TestClient(app).post(
        "/v1/scans:sync",
        files={"file": ("bundle.zip", bundle_bytes(), "application/zip")},
        data={"namespace": "global", "slug": "demo", "version": "1.0.0"},
    )
    assert response.status_code == 200
    data = response.json()
    assert data["scan_id"].startswith("scan_")
    assert data["verdict"] == "PASS"
    assert {item["name"] for item in data["scanners"]} == {"skill-vetter", "semgrep", "osv-scanner"}
    assert next(item for item in data["scanners"] if item["name"] == "skill-vetter")["status"] == "completed"


def test_sync_scan_blocks_skill_vetter_red_flag():
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr("SKILL.md", "---\nname: unsafe\nversion: 1.0.0\n---\n")
        archive.writestr("run.sh", "cat ~/.ssh/id_rsa\n")
    buffer.seek(0)

    response = TestClient(app).post(
        "/v1/scans:sync",
        files={"file": ("bundle.zip", buffer.getvalue(), "application/zip")},
    )

    assert response.status_code == 200
    data = response.json()
    assert data["verdict"] == "FAIL"
    assert data["risk_level"] == "HIGH"
    assert data["summary"]["high"] == 1
    assert data["findings"][0]["scanner"] == "skill-vetter"



def test_sync_scan_warns_on_medium_skill_vetter_finding():
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr("SKILL.md", "---\nname: warn\nversion: 1.0.0\n---\n")
        archive.writestr("main.py", "eval(user_input)\n")
    buffer.seek(0)

    response = TestClient(app).post(
        "/v1/scans:sync",
        files={"file": ("bundle.zip", buffer.getvalue(), "application/zip")},
    )

    assert response.status_code == 200
    data = response.json()
    assert data["verdict"] == "WARN"
    assert data["risk_level"] == "MEDIUM"
    assert data["summary"]["medium"] == 1
    assert data["findings"][0]["rule_id"] == "dynamic-code-execution"


def test_sync_scan_routes_generic_high_to_manual_review(monkeypatch):
    from app import main
    from app.adapters import AdapterResult
    from app.models import Finding, FindingSeverity, ScannerStatus

    def fake_run_all_adapters(_extract_dir, _timeout_ms):
        return [
            AdapterResult(
                status=ScannerStatus(name="semgrep", status="completed", version="test", duration_seconds=0.01),
                findings=[
                    Finding(
                        scanner="semgrep",
                        rule_id="generic-high",
                        severity=FindingSeverity.HIGH,
                        category="static_analysis",
                        file="main.py",
                        line=1,
                        message="Generic high severity finding requires review.",
                    )
                ],
            )
        ]

    monkeypatch.setattr(main, "run_all_adapters", fake_run_all_adapters)

    response = TestClient(app).post(
        "/v1/scans:sync",
        files={"file": ("bundle.zip", bundle_bytes(), "application/zip")},
    )

    assert response.status_code == 200
    data = response.json()
    assert data["verdict"] == "MANUAL_REVIEW"
    assert data["risk_level"] == "HIGH"
    assert data["summary"]["high"] == 1
    assert data["findings"][0]["rule_id"] == "generic-high"
