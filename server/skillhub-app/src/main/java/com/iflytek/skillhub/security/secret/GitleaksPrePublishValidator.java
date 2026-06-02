package com.iflytek.skillhub.security.secret;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.SecretScanProperties;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.PrePublishValidator;
import com.iflytek.skillhub.domain.skill.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
@Primary
@ConditionalOnExpression("${skillhub.security.secret-scan.enabled:false} && !${skillhub.security.unified-scan.enabled:false}")
public class GitleaksPrePublishValidator implements PrePublishValidator {

    private static final Logger log = LoggerFactory.getLogger(GitleaksPrePublishValidator.class);

    private final SecretScanProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI baseUri;

    @Autowired
    public GitleaksPrePublishValidator(SecretScanProperties properties) {
        this(properties, new ObjectMapper());
    }

    GitleaksPrePublishValidator(SecretScanProperties properties, ObjectMapper objectMapper) {
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
            tempDir = Files.createTempDirectory("skillhub-secret-scan-");
            Path packageZip = tempDir.resolve("skill-" + UUID.randomUUID() + ".zip");
            writePackageZip(context.entries(), packageZip);
            GitleaksScanResponse response = scan(packageZip);
            List<GitleaksFinding> findings = response.safeFindings();
            if (findings.isEmpty()) {
                return ValidationResult.pass();
            }
            logSafeFindings(findings, response.truncated());
            return ValidationResult.fail(formatErrors(findings, response.truncated()));
        } catch (Exception e) {
            log.warn("Secret scan failed during pre-publish validation: {}", e.getMessage());
            if (properties.isFailClosed()) {
                return ValidationResult.fail("Secret scan failed before publishing. Please retry later or contact an administrator.");
            }
            return ValidationResult.pass();
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private GitleaksScanResponse scan(Path packageZip) {
        String path = properties.getScanPath().startsWith("/") ? properties.getScanPath() : "/" + properties.getScanPath();
        try {
            byte[] packageBytes = Files.readAllBytes(packageZip);
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                    .timeout(Duration.ofMillis(Math.max(1, properties.getReadTimeoutMs())))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(packageBytes))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Gitleaks scanner API returned " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), GitleaksScanResponse.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to call secret scanner", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Secret scanner call was interrupted", e);
        }
    }

    private void writePackageZip(List<PackageEntry> entries, Path packageZip) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(packageZip))) {
            for (PackageEntry entry : entries) {
                String safeName = safeZipEntryName(entry.path());
                ZipEntry zipEntry = new ZipEntry(safeName);
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

    private List<String> formatErrors(List<GitleaksFinding> findings, boolean truncated) {
        List<String> errors = new ArrayList<>();
        int limit = Math.max(1, properties.getMaxFindings());
        findings.stream().limit(limit).forEach(finding -> errors.add(formatFinding(finding)));
        if (truncated || findings.size() > limit) {
            errors.add("Secret scan found additional findings; showing first " + limit + ".");
        }
        return errors;
    }

    private void logSafeFindings(List<GitleaksFinding> findings, boolean truncated) {
        int limit = Math.max(1, properties.getMaxFindings());
        findings.stream().limit(limit).forEach(finding -> log.warn(
                "Secret scan finding [ruleId={}, file={}, line={}, description={}]",
                safeRule(finding),
                safeFile(finding),
                safeLine(finding),
                safeDescription(finding)
        ));
        if (truncated || findings.size() > limit) {
            log.warn("Secret scan findings truncated [shown={}, total={}, scannerTruncated={}]",
                    limit, findings.size(), truncated);
        }
    }

    private String formatFinding(GitleaksFinding finding) {
        String file = safeFile(finding);
        String line = safeLine(finding);
        String rule = safeRule(finding);
        String description = safeDescription(finding);
        return file + " line " + line + " matched Gitleaks rule " + rule + ": " + description
                + ". Remove real credentials or replace them with placeholders before publishing.";
    }

    private String safeFile(GitleaksFinding finding) {
        return finding.file() == null || finding.file().isBlank() ? "<unknown>" : finding.file();
    }

    private String safeLine(GitleaksFinding finding) {
        return finding.startLine() == null ? "?" : String.valueOf(finding.startLine());
    }

    private String safeRule(GitleaksFinding finding) {
        return finding.ruleId() == null || finding.ruleId().isBlank() ? "unknown" : finding.ruleId();
    }

    private String safeDescription(GitleaksFinding finding) {
        return finding.description() == null || finding.description().isBlank()
                ? "potential secret detected"
                : finding.description();
    }

    private URI normalizeBaseUrl(String baseUrl) {
        URI uri = URI.create(baseUrl);
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("Secret scanner base URL must include scheme and host");
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
