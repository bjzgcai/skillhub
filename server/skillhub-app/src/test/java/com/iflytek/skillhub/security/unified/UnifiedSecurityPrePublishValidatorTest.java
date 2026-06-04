package com.iflytek.skillhub.security.unified;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.SecretScanProperties;
import com.iflytek.skillhub.config.UnifiedSecurityScanProperties;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;
import com.iflytek.skillhub.domain.skill.validation.BasicPrePublishValidator;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.PrePublishValidator;
import com.iflytek.skillhub.domain.skill.validation.ValidationResult;
import com.iflytek.skillhub.security.secret.GitleaksPrePublishValidator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

class UnifiedSecurityPrePublishValidatorTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ValidatorConfiguration.class)
            .withPropertyValues(
                    "skillhub.security.secret-scan.base-url=http://127.0.0.1:65535",
                    "skillhub.security.unified-scan.base-url=http://127.0.0.1:65535"
            );

    @Test
    void unifiedScanEnabledMakesUnifiedValidatorPrimary() {
        contextRunner
                .withPropertyValues(
                        "skillhub.security.secret-scan.enabled=true",
                        "skillhub.security.unified-scan.enabled=true"
                )
                .run(context -> {
                    assertThat(context).hasBean("com.iflytek.skillhub.security.unified.UnifiedSecurityPrePublishValidator");
                    assertThat(context).doesNotHaveBean("com.iflytek.skillhub.security.secret.GitleaksPrePublishValidator");
                    assertThat(context.getBean(PrePublishValidator.class))
                            .isInstanceOf(UnifiedSecurityPrePublishValidator.class);
                });
    }

    @Test
    void validatorPassesWhenScannerVerdictPasses() throws Exception {
        try (StubScanner scanner = StubScanner.respondingWith("""
                {
                  "scan_id": "scan_test",
                  "status": "completed",
                  "verdict": "PASS",
                  "risk_level": "NONE",
                  "policy_version": "test-policy",
                  "findings": [],
                  "duration_seconds": 0.1
                }
                """)) {
            UnifiedSecurityPrePublishValidator validator = new UnifiedSecurityPrePublishValidator(
                    properties(scanner.baseUrl()), new ObjectMapper());

            ValidationResult result = validator.validate(contextWithEntry("main.py", "print('safe')"));

            assertThat(result.passed()).isTrue();
            assertThat(scanner.requestCount()).isEqualTo(1);
            assertThat(scanner.lastRequestBody()).contains("name=\"namespace\"").contains("name=\"file\"");
        }
    }

    @Test
    void validatorFailsWhenScannerVerdictFails() throws Exception {
        try (StubScanner scanner = StubScanner.respondingWith("""
                {
                  "scan_id": "scan_test",
                  "status": "completed",
                  "verdict": "FAIL",
                  "risk_level": "HIGH",
                  "policy_version": "test-policy",
                  "findings": [{
                    "scanner": "skill-vetter",
                    "rule_id": "credential-file-access",
                    "severity": "HIGH",
                    "category": "credential_access",
                    "file": "main.py",
                    "line": 3,
                    "message": "Skill references sensitive credential or system files."
                  }],
                  "duration_seconds": 0.1
                }
                """)) {
            UnifiedSecurityPrePublishValidator validator = new UnifiedSecurityPrePublishValidator(
                    properties(scanner.baseUrl()), new ObjectMapper());

            ValidationResult result = validator.validate(contextWithEntry("main.py", "open('~/.ssh/id_rsa')"));

            assertThat(result.passed()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> assertThat(error)
                    .contains("Unified security scan verdict FAIL")
                    .contains("HIGH"));
            assertThat(result.errors()).anySatisfy(error -> assertThat(error)
                    .contains("main.py line 3")
                    .contains("credential-file-access"));
        }
    }


    @Test
    void validatorRoutesWarnToManualReviewAndCarriesFindingsForAuditDisplay() throws Exception {
        try (StubScanner scanner = StubScanner.respondingWith("""
                {
                  "scan_id": "scan_warn",
                  "status": "completed",
                  "verdict": "WARN",
                  "risk_level": "MEDIUM",
                  "policy_version": "test-policy",
                  "scanner_versions": {"skill-vetter":"1.0.0"},
                  "findings": [{
                    "scanner": "skill-vetter",
                    "rule_id": "dynamic-code-execution",
                    "severity": "MEDIUM",
                    "category": "dynamic_execution",
                    "file": "main.py",
                    "line": 2,
                    "message": "Skill uses dynamic code execution."
                  }],
                  "duration_seconds": 0.2
                }
                """)) {
            UnifiedSecurityPrePublishValidator validator = new UnifiedSecurityPrePublishValidator(
                    properties(scanner.baseUrl()), new ObjectMapper());

            ValidationResult result = validator.validate(contextWithEntry("main.py", "eval(user_input)"));

            assertThat(result.passed()).isTrue();
            assertThat(result.manualReviewRequired()).isTrue();
            assertThat(result.securityAudit()).isPresent();
            assertThat(result.securityAudit().orElseThrow().response().verdict().name()).isEqualTo("SUSPICIOUS");
            assertThat(result.securityAudit().orElseThrow().response().findings()).hasSize(1);
            assertThat(result.securityAudit().orElseThrow().response().findings().get(0).metadata())
                    .containsEntry("unifiedVerdict", "WARN")
                    .containsEntry("riskLevel", "MEDIUM")
                    .containsEntry("policyVersion", "test-policy");
        }
    }

    @Test
    void validatorRoutesManualReviewWithoutBlockingUpload() throws Exception {
        try (StubScanner scanner = StubScanner.respondingWith("""
                {
                  "scan_id": "scan_manual",
                  "status": "completed",
                  "verdict": "MANUAL_REVIEW",
                  "risk_level": "HIGH",
                  "policy_version": "test-policy",
                  "findings": [{
                    "scanner": "semgrep",
                    "rule_id": "risky-import",
                    "severity": "HIGH",
                    "category": "static_analysis",
                    "file": "main.py",
                    "line": 5,
                    "message": "Needs administrator review."
                  }],
                  "duration_seconds": 0.3
                }
                """)) {
            UnifiedSecurityPrePublishValidator validator = new UnifiedSecurityPrePublishValidator(
                    properties(scanner.baseUrl()), new ObjectMapper());

            ValidationResult result = validator.validate(contextWithEntry("main.py", "import risky"));

            assertThat(result.passed()).isTrue();
            assertThat(result.manualReviewRequired()).isTrue();
            assertThat(result.securityAudit()).isPresent();
            assertThat(result.securityAudit().orElseThrow().response().verdict().name()).isEqualTo("SUSPICIOUS");
            assertThat(result.securityAudit().orElseThrow().response().findings().get(0).metadata())
                    .containsEntry("unifiedVerdict", "MANUAL_REVIEW");
        }
    }

    private UnifiedSecurityScanProperties properties(String baseUrl) {
        UnifiedSecurityScanProperties properties = new UnifiedSecurityScanProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(baseUrl);
        properties.setReadTimeoutMs(5000);
        properties.setFailClosed(true);
        return properties;
    }

    private PrePublishValidator.SkillPackageContext contextWithEntry(String path, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new PrePublishValidator.SkillPackageContext(
                List.of(new PackageEntry(path, bytes, bytes.length, "text/plain")),
                new SkillMetadata("test-skill", "Test skill", "1.0.0", "# Test skill", Map.of()),
                "usr_test",
                1L
        );
    }

    @Configuration
    @EnableConfigurationProperties({SecretScanProperties.class, UnifiedSecurityScanProperties.class})
    @Import({BasicPrePublishValidator.class, GitleaksPrePublishValidator.class, UnifiedSecurityPrePublishValidator.class})
    static class ValidatorConfiguration {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private static final class StubScanner implements AutoCloseable {
        private final HttpServer server;
        private int requestCount;
        private String lastRequestBody;

        private StubScanner(HttpServer server) {
            this.server = server;
        }

        static StubScanner respondingWith(String responseBody) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            StubScanner scanner = new StubScanner(server);
            server.createContext("/v1/scans:sync", exchange -> scanner.handleScan(exchange, responseBody));
            server.start();
            return scanner;
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        int requestCount() {
            return requestCount;
        }

        String lastRequestBody() {
            return lastRequestBody;
        }

        private void handleScan(HttpExchange exchange, String responseBody) throws IOException {
            requestCount++;
            lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
