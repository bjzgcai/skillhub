services:
  postgres:
    image: ${POSTGRES_IMAGE:-postgres:16-alpine}
    restart: unless-stopped
    ports:
      - "${POSTGRES_BIND_ADDRESS:-127.0.0.1}:${POSTGRES_PORT:-5432}:5432"
    environment:
      POSTGRES_DB: ${POSTGRES_DB:?missing}
      POSTGRES_USER: ${POSTGRES_USER:?missing}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?missing}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 5s
      timeout: 5s
      retries: 10

  redis:
    image: ${REDIS_IMAGE:-redis:7-alpine}
    restart: unless-stopped
    ports:
      - "${REDIS_BIND_ADDRESS:-127.0.0.1}:${REDIS_PORT:-6379}:6379"
    command: ["redis-server", "--appendonly", "yes"]
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 5s
      retries: 10

  gitleaks-scanner:
    profiles: ["secret-scan"]
    image: ${SKILLHUB_GITLEAKS_SCANNER_IMAGE:-skillhub-gitleaks-scanner}:${SKILLHUB_GITLEAKS_SCANNER_TAG:-latest}
    restart: unless-stopped
    environment:
      GITLEAKS_TIMEOUT_SECONDS: ${GITLEAKS_TIMEOUT_SECONDS:-30}
      GITLEAKS_MAX_FINDINGS: ${GITLEAKS_MAX_FINDINGS:-50}
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://127.0.0.1:8015/health"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 10s

  security-scanner:
    profiles: ["unified-scan"]
    image: ${SKILLHUB_SECURITY_SCANNER_IMAGE:-skill-security-scanner}:${SKILLHUB_SECURITY_SCANNER_TAG:-latest}
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8020/health', timeout=2).read()"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 10s

  server:
    image: ${SKILLHUB_SERVER_IMAGE:?missing}:${SKILLHUB_SERVER_TAG:?missing}
    restart: unless-stopped
    ports:
      - "${API_PORT:-8080}:8080"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      SESSION_COOKIE_SECURE: ${SESSION_COOKIE_SECURE:-false}
      SKILLHUB_PUBLIC_BASE_URL: ${SKILLHUB_PUBLIC_BASE_URL:?missing}
      DEVICE_AUTH_VERIFICATION_URI: ${DEVICE_AUTH_VERIFICATION_URI:-}
      SKILLHUB_STORAGE_PROVIDER: ${SKILLHUB_STORAGE_PROVIDER:?missing}
      STORAGE_BASE_PATH: /var/lib/skillhub/storage
      SKILLHUB_STORAGE_S3_ENDPOINT: ${SKILLHUB_STORAGE_S3_ENDPOINT:-}
      SKILLHUB_STORAGE_S3_PUBLIC_ENDPOINT: ${SKILLHUB_STORAGE_S3_PUBLIC_ENDPOINT:-}
      SKILLHUB_STORAGE_S3_BUCKET: ${SKILLHUB_STORAGE_S3_BUCKET:-skillhub}
      SKILLHUB_STORAGE_S3_ACCESS_KEY: ${SKILLHUB_STORAGE_S3_ACCESS_KEY:-}
      SKILLHUB_STORAGE_S3_SECRET_KEY: ${SKILLHUB_STORAGE_S3_SECRET_KEY:-}
      SKILLHUB_STORAGE_S3_REGION: ${SKILLHUB_STORAGE_S3_REGION:-us-east-1}
      SKILLHUB_STORAGE_S3_FORCE_PATH_STYLE: ${SKILLHUB_STORAGE_S3_FORCE_PATH_STYLE:-false}
      SKILLHUB_STORAGE_S3_AUTO_CREATE_BUCKET: ${SKILLHUB_STORAGE_S3_AUTO_CREATE_BUCKET:-false}
      SKILLHUB_STORAGE_S3_PRESIGN_EXPIRY: ${SKILLHUB_STORAGE_S3_PRESIGN_EXPIRY:-PT10M}
      SKILLHUB_SECRET_SCAN_ENABLED: ${SKILLHUB_SECRET_SCAN_ENABLED:-false}
      SKILLHUB_SECRET_SCAN_BASE_URL: ${SKILLHUB_SECRET_SCAN_BASE_URL:-http://gitleaks-scanner:8015}
      SKILLHUB_SECRET_SCAN_READ_TIMEOUT: ${SKILLHUB_SECRET_SCAN_READ_TIMEOUT:-30000}
      SKILLHUB_SECRET_SCAN_FAIL_CLOSED: ${SKILLHUB_SECRET_SCAN_FAIL_CLOSED:-true}
      SKILLHUB_SECURITY_UNIFIED_SCAN_ENABLED: ${SKILLHUB_SECURITY_UNIFIED_SCAN_ENABLED:-false}
      SKILLHUB_SECURITY_UNIFIED_SCAN_BASE_URL: ${SKILLHUB_SECURITY_UNIFIED_SCAN_BASE_URL:-http://security-scanner:8020}
      SKILLHUB_SECURITY_UNIFIED_SCAN_BLOCK_WARN: ${SKILLHUB_SECURITY_UNIFIED_SCAN_BLOCK_WARN:-false}
      SKILLHUB_SECURITY_UNIFIED_SCAN_BLOCK_MANUAL_REVIEW: ${SKILLHUB_SECURITY_UNIFIED_SCAN_BLOCK_MANUAL_REVIEW:-false}
      SKILLHUB_SECURITY_UNIFIED_SCAN_FAIL_CLOSED: ${SKILLHUB_SECURITY_UNIFIED_SCAN_FAIL_CLOSED:-true}
      SKILLHUB_SECURITY_SCANNER_ENABLED: ${SKILLHUB_SECURITY_SCANNER_ENABLED:-false}
      BOOTSTRAP_ADMIN_ENABLED: ${BOOTSTRAP_ADMIN_ENABLED:-false}
      BOOTSTRAP_ADMIN_USER_ID: ${BOOTSTRAP_ADMIN_USER_ID:-docker-admin}
      BOOTSTRAP_ADMIN_USERNAME: ${BOOTSTRAP_ADMIN_USERNAME:-admin}
      BOOTSTRAP_ADMIN_PASSWORD: ${BOOTSTRAP_ADMIN_PASSWORD:?missing}
      BOOTSTRAP_ADMIN_DISPLAY_NAME: ${BOOTSTRAP_ADMIN_DISPLAY_NAME:-Admin}
      BOOTSTRAP_ADMIN_EMAIL: ${BOOTSTRAP_ADMIN_EMAIL:-admin@skillhub.local}
      OAUTH2_GITHUB_CLIENT_ID: ${OAUTH2_GITHUB_CLIENT_ID:-local-placeholder}
      OAUTH2_GITHUB_CLIENT_SECRET: ${OAUTH2_GITHUB_CLIENT_SECRET:-local-placeholder}
      SKILLHUB_AUTH_DINGTALK_ENABLED: ${SKILLHUB_AUTH_DINGTALK_ENABLED:-false}
      SKILLHUB_AUTH_DINGTALK_DISPLAY_NAME: ${SKILLHUB_AUTH_DINGTALK_DISPLAY_NAME:-DingTalk}
      SKILLHUB_AUTH_DINGTALK_APP_KEY: ${SKILLHUB_AUTH_DINGTALK_APP_KEY:-}
      SKILLHUB_AUTH_DINGTALK_APP_SECRET: ${SKILLHUB_AUTH_DINGTALK_APP_SECRET:-}
      SKILLHUB_AUTH_DINGTALK_CORP_ID: ${SKILLHUB_AUTH_DINGTALK_CORP_ID:-}
      SKILLHUB_AUTH_DINGTALK_AGENT_ID: ${SKILLHUB_AUTH_DINGTALK_AGENT_ID:-}
      SKILLHUB_AUTH_DINGTALK_REDIRECT_URI: ${SKILLHUB_AUTH_DINGTALK_REDIRECT_URI:-}
      SKILLHUB_AUTH_DINGTALK_REQUIRE_CORP_MEMBERSHIP: ${SKILLHUB_AUTH_DINGTALK_REQUIRE_CORP_MEMBERSHIP:-true}
      SKILLHUB_AUTH_DINGTALK_AUTO_PROVISION_USER: ${SKILLHUB_AUTH_DINGTALK_AUTO_PROVISION_USER:-true}
      SKILLHUB_AUTH_DINGTALK_AUTO_LOGIN_IN_DINGTALK: ${SKILLHUB_AUTH_DINGTALK_AUTO_LOGIN_IN_DINGTALK:-false}
      SKILLHUB_REMOTE_REGISTRY_CLAWHUB_ENABLED: ${SKILLHUB_REMOTE_REGISTRY_CLAWHUB_ENABLED:-false}
      SKILLHUB_REMOTE_REGISTRY_CLAWHUB_BASE_URL: ${SKILLHUB_REMOTE_REGISTRY_CLAWHUB_BASE_URL:-}
      SKILLHUB_REMOTE_REGISTRY_CLAWHUB_API_BASE_PATH: ${SKILLHUB_REMOTE_REGISTRY_CLAWHUB_API_BASE_PATH:-/api/v1}
      SKILLHUB_REMOTE_REGISTRY_CLAWHUB_AUTH_SCHEME: ${SKILLHUB_REMOTE_REGISTRY_CLAWHUB_AUTH_SCHEME:-Bearer}
      SKILLHUB_REMOTE_REGISTRY_CLAWHUB_TOKEN: ${SKILLHUB_REMOTE_REGISTRY_CLAWHUB_TOKEN:-}
    volumes:
      - skillhub_storage:/var/lib/skillhub/storage
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8080/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 60s

  web:
    image: ${SKILLHUB_WEB_IMAGE:?missing}:${SKILLHUB_WEB_TAG:?missing}
    restart: unless-stopped
    ports:
      - "${WEB_BIND_ADDRESS:-0.0.0.0}:${WEB_PORT:-80}:80"
    environment:
      SKILLHUB_API_UPSTREAM: ${SKILLHUB_API_UPSTREAM:?missing}
      SKILLHUB_WEB_API_BASE_URL: ${SKILLHUB_WEB_API_BASE_URL:-}
      SKILLHUB_PUBLIC_BASE_URL: ${SKILLHUB_PUBLIC_BASE_URL:?missing}
      SKILLHUB_WEB_AUTH_DINGTALK_ENABLED: ${SKILLHUB_WEB_AUTH_DINGTALK_ENABLED:-false}
      SKILLHUB_WEB_AUTH_DINGTALK_PROVIDER: ${SKILLHUB_WEB_AUTH_DINGTALK_PROVIDER:-dingtalk}
      SKILLHUB_WEB_AUTH_DINGTALK_AUTO: ${SKILLHUB_WEB_AUTH_DINGTALK_AUTO:-false}
      SKILLHUB_WEB_AUTH_DINGTALK_CORP_ID: ${SKILLHUB_WEB_AUTH_DINGTALK_CORP_ID:-}
    depends_on:
      server:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://127.0.0.1/nginx-health"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 10s

volumes:
  postgres_data:
    name: skillhub_postgres_data
  redis_data:
    name: skillhub_redis_data
  skillhub_storage:
    name: skillhub_skillhub_storage
