import zipfile

import pytest

from app.zip_guard import PackageValidationError, extract_zip_package, validate_zip_package


def make_zip(path, names):
    with zipfile.ZipFile(path, "w") as archive:
        for name in names:
            archive.writestr(name, "content")


def test_accepts_safe_zip(tmp_path):
    package = tmp_path / "safe.zip"
    make_zip(package, ["SKILL.md", "src/main.py"])

    validate_zip_package(str(package), max_file_count=10, max_uncompressed_size_bytes=1024)


def test_rejects_zip_slip_path(tmp_path):
    package = tmp_path / "unsafe.zip"
    make_zip(package, ["../evil.txt"])

    with pytest.raises(PackageValidationError, match="Unsafe zip entry path"):
        validate_zip_package(str(package), max_file_count=10, max_uncompressed_size_bytes=1024)


def test_extracts_safe_zip(tmp_path):
    package = tmp_path / "safe.zip"
    destination = tmp_path / "out"
    make_zip(package, ["SKILL.md", "src/main.py"])

    extract_zip_package(str(package), str(destination))

    assert (destination / "SKILL.md").read_text() == "content"
    assert (destination / "src" / "main.py").read_text() == "content"
