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

    validate_zip_package(
        str(package),
        max_file_count=10,
        max_single_file_size_bytes=1024,
        max_uncompressed_size_bytes=1024,
    )


def test_rejects_zip_slip_path(tmp_path):
    package = tmp_path / "unsafe.zip"
    make_zip(package, ["../evil.txt"])

    with pytest.raises(PackageValidationError, match="Unsafe zip entry path"):
        validate_zip_package(
            str(package),
            max_file_count=10,
            max_single_file_size_bytes=1024,
            max_uncompressed_size_bytes=1024,
        )


def test_counts_only_files(tmp_path):
    package = tmp_path / "directories.zip"
    make_zip(package, ["src/", "src/main.py"])

    validate_zip_package(
        str(package),
        max_file_count=1,
        max_single_file_size_bytes=1024,
        max_uncompressed_size_bytes=1024,
    )


def test_rejects_too_many_files(tmp_path):
    package = tmp_path / "too-many.zip"
    make_zip(package, ["one.txt", "two.txt"])

    with pytest.raises(PackageValidationError, match="too many files"):
        validate_zip_package(
            str(package),
            max_file_count=1,
            max_single_file_size_bytes=1024,
            max_uncompressed_size_bytes=1024,
        )


def test_rejects_oversized_file(tmp_path):
    package = tmp_path / "large-file.zip"
    with zipfile.ZipFile(package, "w") as archive:
        archive.writestr("large.bin", b"x" * 11)

    with pytest.raises(PackageValidationError, match="file size exceeds limit"):
        validate_zip_package(
            str(package),
            max_file_count=10,
            max_single_file_size_bytes=10,
            max_uncompressed_size_bytes=1024,
        )


def test_rejects_oversized_uncompressed_package(tmp_path):
    package = tmp_path / "large-package.zip"
    with zipfile.ZipFile(package, "w") as archive:
        archive.writestr("one.txt", b"x" * 6)
        archive.writestr("two.txt", b"x" * 6)

    with pytest.raises(PackageValidationError, match="uncompressed size exceeds limit"):
        validate_zip_package(
            str(package),
            max_file_count=10,
            max_single_file_size_bytes=10,
            max_uncompressed_size_bytes=11,
        )


def test_accepts_file_count_single_file_and_total_size_at_exact_limits(tmp_path):
    package = tmp_path / "exact-limits.zip"
    with zipfile.ZipFile(package, "w") as archive:
        archive.writestr("one.txt", b"12345")
        archive.writestr("two.txt", b"67890")

    validate_zip_package(
        str(package),
        max_file_count=2,
        max_single_file_size_bytes=5,
        max_uncompressed_size_bytes=10,
    )


def test_extracts_safe_zip(tmp_path):
    package = tmp_path / "safe.zip"
    destination = tmp_path / "out"
    make_zip(package, ["SKILL.md", "src/main.py"])

    extract_zip_package(str(package), str(destination))

    assert (destination / "SKILL.md").read_text() == "content"
    assert (destination / "src" / "main.py").read_text() == "content"
