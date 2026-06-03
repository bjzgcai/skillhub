FROM python:3.11-slim

ARG GITLEAKS_VERSION=8.28.0

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    SCANNER_WORKSPACE_ROOT=/tmp/skill-security-scans \
    SCANNER_SEMGREP_CONFIG=/app/rules/semgrep \
    HOME=/home/app \
    SEMGREP_SETTINGS_FILE=/home/app/.semgrep/settings.yml

RUN apt-get update && \
    apt-get install -y --no-install-recommends ca-certificates curl tar && \
    rm -rf /var/lib/apt/lists/* && \
    curl -fsSL "https://github.com/gitleaks/gitleaks/releases/download/v${GITLEAKS_VERSION}/gitleaks_${GITLEAKS_VERSION}_linux_x64.tar.gz" -o /tmp/gitleaks.tar.gz && \
    tar -xzf /tmp/gitleaks.tar.gz -C /usr/local/bin gitleaks && \
    rm -f /tmp/gitleaks.tar.gz && \
    addgroup --system app && \
    adduser --system --ingroup app app && \
    mkdir -p /tmp/skill-security-scans /home/app/.semgrep && \
    printf '{}\n' > /home/app/.semgrep/settings.yml && \
    chown -R app:app /tmp/skill-security-scans /home/app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt semgrep==1.99.0

COPY bin/osv-scanner /usr/local/bin/osv-scanner
RUN chmod +x /usr/local/bin/osv-scanner /usr/local/bin/gitleaks && \
    semgrep --version && \
    osv-scanner --version && \
    gitleaks version && \
    chown -R app:app /home/app

COPY app ./app
COPY rules ./rules

USER app

EXPOSE 8020

HEALTHCHECK --interval=10s --timeout=3s \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8020/health', timeout=2).read()" || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8020"]
