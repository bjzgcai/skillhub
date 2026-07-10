package com.iflytek.skillhub.security.unified;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.UnifiedSecurityScanProperties;
import com.iflytek.skillhub.domain.security.ScannerType;
import com.iflytek.skillhub.domain.security.SecurityFinding;
import com.iflytek.skillhub.domain.security.SecurityScanResponse;
import com.iflytek.skillhub.domain.security.SecurityVerdict;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.PrePublishValidator;
import com.iflytek.skillhub.domain.skill.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
@Primary
@ConditionalOnProperty(prefix = "skillhub.security.unified-scan", name = "enabled", havingValue = "true")
public class UnifiedSecurityPrePublishValidator implements PrePublishValidator {

    private static final Logger log = LoggerFactory.getLogger(UnifiedSecurityPrePublishValidator.class);

    private final UnifiedSecurityScanProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI baseUri;

    @Autowired
    public UnifiedSecurityPrePublishValidator(UnifiedSecurityScanProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.baseUri = normalizeBaseUrl(properties.getBaseUrl());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1, properties.getConnectTimeoutMs())))
                .build();
    }

    @Override
    public ValidationResult validate(SkillPackageContext context) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("skillhub-unified-scan-");
            Path packageZip = tempDir.resolve("skill-" + UUID.randomUUID() + ".zip");
            writePackageZip(context.entries(), packageZip);
            UnifiedSecurityScanResponse response = scan(context, packageZip);
            return evaluate(response);
        } catch (Exception e) {
            log.warn("Unified security scan failed during pre-publish validation: {}", e.getMessage());
            if (properties.isFailClosed()) {
                return ValidationResult.fail("Unified security scan failed before publishing. Please retry later or contact an administrator.");
            }
            return ValidationResult.pass();
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private UnifiedSecurityScanResponse scan(SkillPackageContext context, Path packageZip) {
        String path = properties.getSyncScanPath().startsWith("/") ? properties.getSyncScanPath() : "/" + properties.getSyncScanPath();
        try {
            byte[] body = multipartBody(context, packageZip);
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofMillis(Math.max(1, properties.getReadTimeoutMs())))
                    .header("Accept", "application/json")
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary(context))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Unified scanner API returned " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), UnifiedSecurityScanResponse.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to call unified security scanner", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Unified security scanner call was interrupted", e);
        }
    }

    private ValidationResult evaluate(UnifiedSecurityScanResponse response) {
        String verdict = response.verdict() == null ? "MANUAL_REVIEW" : response.verdict();
        ValidationResult.SecurityAuditSnapshot audit = toAuditSnapshot(response, verdict);
        if ("PASS".equalsIgnoreCase(verdict)) {
            return ValidationResult.pass(audit);
        }
        // Log full scan findings at WARN level for non-PASS verdicts so they are searchable in Loki
        log.warn("Unified security scan returned non-PASS verdict: scanId={}, verdict={}, riskLevel={}, findingsCount={}, policyVersion={}",
                response.scanId(), verdict, response.riskLevel(),
                response.safeFindings().size(), response.policyVersion());
        for (UnifiedSecurityScanResponse.Finding finding : response.safeFindings()) {
            log.warn("Scan finding: scanner={}, ruleId={}, severity={}, category={}, file={}, line={}, message={}",
                    finding.scanner(), finding.ruleId(), finding.severity(), finding.category(),
                    finding.file(), finding.line(), finding.message());
        }
        if ("WARN".equalsIgnoreCase(verdict) && !properties.isBlockWarn()) {
            log.warn("Unified security scan returned WARN; publishing will be routed to administrator review");
            return ValidationResult.manualReview(audit);
        }
        if ("MANUAL_REVIEW".equalsIgnoreCase(verdict) && !properties.isBlockManualReview()) {
            log.warn("Unified security scan returned MANUAL_REVIEW; publishing will be routed to administrator review");
            return ValidationResult.manualReview(audit);
        }
        return ValidationResult.fail(formatErrors(response), audit);
    }

    private ValidationResult.SecurityAuditSnapshot toAuditSnapshot(UnifiedSecurityScanResponse response, String scannerVerdict) {
        List<SecurityFinding> findings = response.safeFindings().stream()
                .map(finding -> toSecurityFinding(finding, response, scannerVerdict))
                .toList();
        SecurityScanResponse scanResponse = new SecurityScanResponse(
                response.scanId(),
                toSecurityVerdict(scannerVerdict),
                findings.size(),
                normalizeSeverity(response.riskLevel()),
                findings,
                response.durationSeconds() == null ? 0.0 : response.durationSeconds()
        );
        return new ValidationResult.SecurityAuditSnapshot(ScannerType.CUSTOM, scanResponse);
    }

    private SecurityVerdict toSecurityVerdict(String scannerVerdict) {
        if ("PASS".equalsIgnoreCase(scannerVerdict)) {
            return SecurityVerdict.SAFE;
        }
        if ("FAIL".equalsIgnoreCase(scannerVerdict)) {
            return SecurityVerdict.BLOCKED;
        }
        return SecurityVerdict.SUSPICIOUS;
    }

    private SecurityFinding toSecurityFinding(
            UnifiedSecurityScanResponse.Finding finding,
            UnifiedSecurityScanResponse response,
            String scannerVerdict) {
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        if (finding.metadata() != null) {
            metadata.putAll(finding.metadata());
        }
        metadata.put("scanner", finding.scanner());
        metadata.put("unifiedVerdict", scannerVerdict);
        metadata.put("riskLevel", response.riskLevel());
        metadata.put("policyVersion", response.policyVersion());
        metadata.put("scannerVersions", response.scannerVersions() == null ? Map.of() : response.scannerVersions());
        return new SecurityFinding(
                nullToDefault(finding.ruleId(), "unified-security"),
                normalizeSeverity(finding.severity()),
                nullToDefault(finding.category(), "security"),
                nullToDefault(finding.ruleId(), "Unified security finding"),
                finding.message(),
                sanitizeFilePath(finding.file()),
                finding.line(),
                null,
                null,
                finding.scanner(),
                metadata
        );
    }

    private String normalizeSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return "INFO";
        }
        return severity.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * Strips scanner temporary extraction paths from file paths so findings show
     * clean relative paths (e.g. "references/service-ops.md" instead of
     * "/tmp/skill-security-scans/scan_xxx/extracted/references/service-ops.md").
     */
    private String sanitizeFilePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return filePath;
        }
        // Strip common extraction temp path prefixes
        int extractedIdx = filePath.indexOf("/extracted/");
        if (extractedIdx >= 0) {
            return filePath.substring(extractedIdx + "/extracted/".length());
        }
        return filePath;
    }

    private String nullToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private List<String> formatErrors(UnifiedSecurityScanResponse response) {
        List<String> errors = new ArrayList<>();
        String verdict = response.verdict() == null ? "UNKNOWN" : response.verdict();
        String risk = response.riskLevel() == null ? "UNKNOWN" : response.riskLevel();
        errors.add("Unified security scan verdict " + verdict + " with risk level " + risk + ".");
        int limit = Math.max(1, properties.getMaxFindings());
        List<UnifiedSecurityScanResponse.Finding> findings = response.safeFindings();
        findings.stream().limit(limit).forEach(finding -> errors.add(formatFinding(finding)));
        if (findings.size() > limit) {
            errors.add("Unified security scan found additional findings; showing first " + limit + ".");
        }
        return errors;
    }

    private String formatFinding(UnifiedSecurityScanResponse.Finding finding) {
        String file = finding.file() == null || finding.file().isBlank() ? "<unknown>" : finding.file();
        String line = finding.line() == null ? "?" : String.valueOf(finding.line());
        String scanner = finding.scanner() == null || finding.scanner().isBlank() ? "scanner" : finding.scanner();
        String rule = finding.ruleId() == null || finding.ruleId().isBlank() ? "unknown" : finding.ruleId();
        String severity = finding.severity() == null || finding.severity().isBlank() ? "UNKNOWN" : finding.severity();
        String message = finding.message() == null || finding.message().isBlank() ? "security finding" : finding.message();
        return file + " line " + line + " matched " + scanner + " rule " + rule + " [" + severity + "]: " + message;
    }

    private byte[] multipartBody(SkillPackageContext context, Path packageZip) throws IOException {
        String boundary = boundary(context);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeFormField(out, boundary, "namespace", String.valueOf(context.namespaceId()));
        writeFormField(out, boundary, "slug", context.metadata().name());
        writeFormField(out, boundary, "version", context.metadata().version());
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write("Content-Disposition: form-data; name=\"file\"; filename=\"bundle.zip\"\r\n".getBytes(StandardCharsets.UTF_8));
        out.write("Content-Type: application/zip\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        Files.copy(packageZip, out);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private void writeFormField(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private String boundary(SkillPackageContext context) {
        return "skillhub-unified-scan-" + Math.abs(context.hashCode());
    }

    private void writePackageZip(List<PackageEntry> entries, Path packageZip) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(packageZip))) {
            for (PackageEntry entry : entries) {
                ZipEntry zipEntry = new ZipEntry(safeZipEntryName(entry.path()));
                out.putNextEntry(zipEntry);
                out.write(entry.content());
                out.closeEntry();
            }
        }
    }

    private String safeZipEntryName(String entryPath) {
        Path normalized = Path.of(entryPath).normalize();
        if (normalized.isAbsolute() || normalized.startsWith("..")) {
            throw new IllegalArgumentException("Unsafe package entry: " + entryPath);
        }
        String safePath = normalized.toString().replace('\\', '/');
        if (safePath.isBlank() || safePath.startsWith("../")) {
            throw new IllegalArgumentException("Unsafe package entry: " + entryPath);
        }
        return safePath;
    }

    private URI normalizeBaseUrl(String baseUrl) {
        URI uri = URI.create(baseUrl);
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("Unified security scanner base URL must include scheme and host");
        }
        String normalized = uri.toString();
        return URI.create(normalized.endsWith("/") ? normalized : normalized + "/");
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(candidate -> {
                try {
                    Files.deleteIfExists(candidate);
                } catch (IOException e) {
                    log.debug("Failed to delete temp path {}", candidate, e);
                }
            });
        } catch (IOException e) {
            log.debug("Failed to walk temp path {}", path, e);
        }
    }
}
