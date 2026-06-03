from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="SCANNER_")

    workspace_root: str = "/tmp/skill-security-scans"
    max_package_size_bytes: int = Field(default=50 * 1024 * 1024, ge=1)
    max_file_count: int = Field(default=500, ge=1)
    max_uncompressed_size_bytes: int = Field(default=100 * 1024 * 1024, ge=1)
    policy_version: str = "2026-06-02.1"
    semgrep_config: str = "/app/rules/semgrep"
    gitleaks_config: str = "/app/rules/gitleaks/skillhub-gitleaks.toml"


settings = Settings()
