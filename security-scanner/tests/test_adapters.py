import pytest

from app.adapters import run_skill_vetter


@pytest.mark.parametrize("filename", ["config.in", "config.example"])
def test_skill_vetter_scans_new_text_formats(tmp_path, filename):
    (tmp_path / filename).write_text("cat ~/.ssh/id_rsa\n", encoding="utf-8")

    result = run_skill_vetter(str(tmp_path))

    assert any(
        finding.file == filename and finding.rule_id == "credential-file-access"
        for finding in result.findings
    )
