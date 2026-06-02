'use strict';

const http = require('http');
const fs = require('fs');
const os = require('os');
const path = require('path');
const crypto = require('crypto');
const { execFileSync, spawnSync } = require('child_process');

const PORT = Number(process.env.PORT || 8015);
const GITLEAKS_BIN = process.env.GITLEAKS_BIN || '/usr/local/bin/gitleaks';
const GITLEAKS_CONFIG = process.env.GITLEAKS_CONFIG || '/etc/skillhub/gitleaks.toml';
const GITLEAKS_TIMEOUT_SECONDS = Number(process.env.GITLEAKS_TIMEOUT_SECONDS || 30);
const MAX_FINDINGS = Number(process.env.GITLEAKS_MAX_FINDINGS || 50);
const MAX_UPLOAD_BYTES = Number(process.env.GITLEAKS_MAX_UPLOAD_BYTES || 25 * 1024 * 1024);

function json(res, status, body) {
  const payload = Buffer.from(JSON.stringify(body));
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': payload.length,
  });
  res.end(payload);
}

function gitleaksVersion() {
  try {
    return execFileSync(GITLEAKS_BIN, ['version'], { encoding: 'utf8', timeout: 5000 }).trim() || null;
  } catch (_) {
    return null;
  }
}

function collectRequest(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > MAX_UPLOAD_BYTES) {
        reject(Object.assign(new Error('Upload exceeds secret scanner size limit'), { status: 413 }));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

function extractMultipartFile(contentType, body) {
  const match = /boundary=(?:(?:"([^"]+)")|([^;]+))/i.exec(contentType || '');
  if (!match) {
    throw Object.assign(new Error('Missing multipart boundary'), { status: 400 });
  }
  const boundary = Buffer.from(`--${match[1] || match[2]}`);
  const start = body.indexOf(boundary);
  if (start < 0) {
    throw Object.assign(new Error('Multipart boundary not found'), { status: 400 });
  }
  const headerStart = start + boundary.length + 2; // CRLF
  const headerEnd = body.indexOf(Buffer.from('\r\n\r\n'), headerStart);
  if (headerEnd < 0) {
    throw Object.assign(new Error('Multipart headers not found'), { status: 400 });
  }
  const headers = body.slice(headerStart, headerEnd).toString('utf8');
  if (!/name="file"/i.test(headers)) {
    throw Object.assign(new Error('Multipart file field not found'), { status: 400 });
  }
  const dataStart = headerEnd + 4;
  const nextBoundary = body.indexOf(Buffer.from(`\r\n--${match[1] || match[2]}`), dataStart);
  if (nextBoundary < 0) {
    throw Object.assign(new Error('Multipart file terminator not found'), { status: 400 });
  }
  return body.slice(dataStart, nextBoundary);
}

function normalizeFileName(fileName, uploadPath) {
  if (!fileName) return fileName;
  if (fileName.startsWith(uploadPath)) return fileName.slice(uploadPath.length + 1);
  return fileName;
}

function toFinding(item, uploadPath) {
  return {
    rule_id: String(item.RuleID || 'unknown'),
    description: item.Description || null,
    file: normalizeFileName(item.File || null, uploadPath),
    start_line: item.StartLine || item.Line || null,
    end_line: item.EndLine || null,
    start_column: item.StartColumn || null,
    end_column: item.EndColumn || null,
    entropy: item.Entropy || null,
    fingerprint: item.Fingerprint || null,
    redacted_secret: item.Secret || null,
  };
}

function validateZip(uploadPath) {
  const result = spawnSync('unzip', ['-l', uploadPath], { encoding: 'utf8', timeout: 5000 });
  if (result.status !== 0) {
    throw Object.assign(new Error('Invalid zip package'), { status: 400 });
  }
  for (const line of result.stdout.split(/\r?\n/)) {
    const match = /^\s*\d+\s+\d{2}-\d{2}-\d{4}\s+\d{2}:\d{2}\s+(.+)$/.exec(line);
    if (!match) continue;
    const entry = match[1];
    if (!entry || entry.endsWith('/')) continue;
    const normalized = path.posix.normalize(entry.replace(/\\/g, '/'));
    if (normalized.startsWith('../') || normalized === '..' || normalized.startsWith('/') || path.isAbsolute(entry)) {
      throw Object.assign(new Error(`Unsafe zip entry: ${entry}`), { status: 400 });
    }
  }
}

function extractZip(uploadPath, extractDir) {
  validateZip(uploadPath);
  fs.mkdirSync(extractDir, { recursive: true });
  const result = spawnSync('unzip', ['-q', uploadPath, '-d', extractDir], { encoding: 'utf8', timeout: 10000 });
  if (result.status !== 0) {
    const message = (result.stderr || result.stdout || 'Failed to extract zip package').replace(/\s+/g, ' ').trim();
    throw Object.assign(new Error(message.slice(0, 500)), { status: 400 });
  }
}

function runGitleaks(scanPath, reportPath) {
  const result = spawnSync(GITLEAKS_BIN, [
    'dir', scanPath,
    '--config', GITLEAKS_CONFIG,
    '--report-format', 'json',
    '--report-path', reportPath,
    '--redact=100',
    '--ignore-gitleaks-allow',
    '--max-archive-depth', '1',
    '--no-banner',
    '--exit-code', '0',
  ], {
    encoding: 'utf8',
    timeout: GITLEAKS_TIMEOUT_SECONDS * 1000,
  });
  if (result.error) {
    const status = result.error.code === 'ETIMEDOUT' ? 504 : 500;
    throw Object.assign(new Error(status === 504 ? 'Gitleaks scan timed out' : result.error.message), { status });
  }
  if (result.status !== 0) {
    const message = (result.stderr || result.stdout || 'gitleaks failed').replace(/\s+/g, ' ').trim();
    throw Object.assign(new Error(message.slice(0, 500)), { status: 500 });
  }
  if (!fs.existsSync(reportPath) || fs.statSync(reportPath).size === 0) return [];
  const raw = JSON.parse(fs.readFileSync(reportPath, 'utf8'));
  return Array.isArray(raw) ? raw.map((item) => toFinding(item, scanPath)) : [];
}

async function handleScan(req, res) {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'skillhub-gitleaks-'));
  try {
    const body = await collectRequest(req);
    const contentType = req.headers['content-type'] || '';
    const zipBytes = contentType.toLowerCase().startsWith('multipart/form-data')
      ? extractMultipartFile(contentType, body)
      : body;
    const uploadPath = path.join(tmpDir, `package-${crypto.randomUUID()}.zip`);
    const extractDir = path.join(tmpDir, 'extract');
    const reportPath = path.join(tmpDir, 'gitleaks-report.json');
    fs.writeFileSync(uploadPath, zipBytes);
    extractZip(uploadPath, extractDir);
    const findings = runGitleaks(extractDir, reportPath);
    const truncated = findings.length > MAX_FINDINGS;
    json(res, 200, {
      passed: findings.length === 0,
      scanner: 'gitleaks',
      scanner_version: gitleaksVersion(),
      findings: findings.slice(0, MAX_FINDINGS),
      truncated,
    });
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
}

const server = http.createServer((req, res) => {
  if (req.method === 'GET' && req.url === '/health') {
    json(res, 200, { ok: true, scanner: 'gitleaks', version: gitleaksVersion() });
    return;
  }
  if (req.method === 'POST' && req.url === '/scan-upload') {
    handleScan(req, res).catch((err) => json(res, err.status || 500, { detail: err.message || 'scan failed' }));
    return;
  }
  json(res, 404, { detail: 'not found' });
});

server.listen(PORT, '0.0.0.0');
