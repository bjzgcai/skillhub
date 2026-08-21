from app.config import Settings


def test_package_limit_defaults():
    settings = Settings()

    assert settings.max_package_size_bytes == 200 * 1024 * 1024
    assert settings.max_repacked_package_size_bytes == 320 * 1024 * 1024
    assert settings.max_file_count == 20_000
    assert settings.max_single_file_size_bytes == 20 * 1024 * 1024
    assert settings.max_uncompressed_size_bytes == 300 * 1024 * 1024
