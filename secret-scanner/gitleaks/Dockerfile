FROM node:22-alpine

ARG GITLEAKS_VERSION=8.28.0

USER root
RUN wget -qO /tmp/gitleaks.tar.gz "https://github.com/gitleaks/gitleaks/releases/download/v${GITLEAKS_VERSION}/gitleaks_${GITLEAKS_VERSION}_linux_x64.tar.gz" \
    && tar -xzf /tmp/gitleaks.tar.gz -C /usr/local/bin gitleaks \
    && rm -f /tmp/gitleaks.tar.gz \
    && addgroup -S scanner \
    && adduser -S scanner -G scanner

WORKDIR /app
COPY app ./app
COPY config/skillhub-gitleaks.toml /etc/skillhub/gitleaks.toml

USER scanner
EXPOSE 8015

HEALTHCHECK --interval=10s --timeout=5s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8015/health || exit 1

CMD ["node", "app/server.js"]
