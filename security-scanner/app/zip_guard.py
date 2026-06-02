from __future__ import annotations

import os
import zipfile
from pathlib import PurePosixPath


class PackageValidationError(ValueError):
    pass


def validate_zip_package(
    package_path: str,
    *,
    max_file_count: int,
    max_uncompressed_size_bytes: int,
) -> None:
    try:
        with zipfile.ZipFile(package_path) as archive:
            entries = archive.infolist()
            if len(entries) > max_file_count:
                raise PackageValidationError(f"Package contains too many files: {len(entries)} > {max_file_count}")

            total_size = 0
            for entry in entries:
                name = entry.filename.replace("\\", "/")
                if not name or name.endswith("/"):
                    continue
                path = PurePosixPath(name)
                if path.is_absolute() or ".." in path.parts:
                    raise PackageValidationError(f"Unsafe zip entry path: {entry.filename}")
                total_size += entry.file_size
                if total_size > max_uncompressed_size_bytes:
                    raise PackageValidationError(
                        "Package uncompressed size exceeds limit: "
                        f"{total_size} > {max_uncompressed_size_bytes}"
                    )
    except zipfile.BadZipFile as exc:
        raise PackageValidationError("Uploaded file is not a valid zip package") from exc


def extract_zip_package(package_path: str, destination: str) -> None:
    with zipfile.ZipFile(package_path) as archive:
        for entry in archive.infolist():
            name = entry.filename.replace("\\", "/")
            if not name or name.endswith("/"):
                continue
            path = PurePosixPath(name)
            if path.is_absolute() or ".." in path.parts:
                raise PackageValidationError(f"Unsafe zip entry path: {entry.filename}")
            target = os.path.join(destination, path.as_posix())
            os.makedirs(os.path.dirname(target), exist_ok=True)
            with archive.open(entry) as source, open(target, "wb") as output:
                output.write(source.read())
