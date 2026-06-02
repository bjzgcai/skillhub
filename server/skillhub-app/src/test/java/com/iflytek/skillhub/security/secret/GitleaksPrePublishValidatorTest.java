package com.iflytek.skillhub.security.secret;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.config.SecretScanProperties;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;
import com.iflytek.skillhub.domain.skill.validation.BasicPrePublishValidator;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.PrePublishValidator;
import com.iflytek.skillhub.domain.skill.validation.ValidationResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class GitleaksPrePublishValidatorTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ValidatorConfiguration.class)
            .withPropertyValues("skillhub.security.secret-scan.base-url=http://127.0.0.1:65535");

    @Test
    void secretScanEnabledMakesGitleaksThePrePublishValidator() {
        contextRunner
                .withPropertyValues("skillhub.security.secret-scan.enabled=true")
                .run(context -> {
                    assertThat(context).hasBean("com.iflytek.skillhub.domain.skill.validation.BasicPrePublishValidator");
                    assertThat(context).hasBean("com.iflytek.skillhub.security.secret.GitleaksPrePublishValidator");
                    assertThat(context.getBean(PrePublishValidator.class))
                            .isInstanceOf(GitleaksPrePublishValidator.class);
                });
    }

    @Test
    void secretScanDisabledKeepsBasicPrePublishValidator() {
        contextRunner
                .withPropertyValues("skillhub.security.secret-scan.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(PrePublishValidator.class);
                    assertThat(context.getBean(PrePublishValidator.class))
                            .isInstanceOf(BasicPrePublishValidator.class);
                });
    }

    @Test
    void validatorFailsWhenScannerReportsFinding() throws Exception {
        try (StubScanner scanner = StubScanner.respondingWith("""
                {
                  "passed": false,
                  "scanner": "gitleaks",
                  "scanner_version": "8.28.0",
                  "findings": [{
                    "rule_id": "private-key",
                    "description": "Identified a Private Key.",
                    "file": "notes.txt",
                    "start_line": 1,
                    "end_line": 4,
                    "start_column": 1,
                    "end_column": 34,
                    "entropy": 4.9,
                    "fingerprint": "notes.txt:private-key:1",
                    "redacted_secret": "REDACTED"
                  }],
                  "truncated": false
                }
                """)) {
            GitleaksPrePublishValidator validator = new GitleaksPrePublishValidator(properties(scanner.baseUrl()));

            ValidationResult result = validator.validate(contextWithEntry("notes.txt", "safe content"));

            assertThat(result.passed()).isFalse();
            assertThat(result.errors()).singleElement().satisfies(error -> assertThat(error)
                    .contains("notes.txt line 1")
                    .contains("Gitleaks rule private-key"));
            assertThat(scanner.requestCount()).isEqualTo(1);
        }
    }

    private SecretScanProperties properties(String baseUrl) {
        SecretScanProperties properties = new SecretScanProperties();
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
                new SkillMetadata("test-skill", "Test skill", null, "# Test skill", Map.of()),
                "usr_test",
                1L
        );
    }

    @Configuration
    @EnableConfigurationProperties(SecretScanProperties.class)
    @Import({BasicPrePublishValidator.class, GitleaksPrePublishValidator.class})
    static class ValidatorConfiguration {
    }

    private static final class StubScanner implements AutoCloseable {
        private final HttpServer server;
        private int requestCount;

        private StubScanner(HttpServer server) {
            this.server = server;
        }

        static StubScanner respondingWith(String responseBody) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            StubScanner scanner = new StubScanner(server);
            server.createContext("/scan-upload", exchange -> scanner.handleScan(exchange, responseBody));
            server.start();
            return scanner;
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        int requestCount() {
            return requestCount;
        }

        private void handleScan(HttpExchange exchange, String responseBody) throws IOException {
            requestCount++;
            exchange.getRequestBody().readAllBytes();
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
