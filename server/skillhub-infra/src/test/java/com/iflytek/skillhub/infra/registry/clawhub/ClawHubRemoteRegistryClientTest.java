package com.iflytek.skillhub.infra.registry.clawhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.domain.registry.remote.RemoteRegistryException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class ClawHubRemoteRegistryClientTest {

    private HttpServer server;
    private ClawHubRemoteRegistryClient client;
    private URI baseUri;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        client = new ClawHubRemoteRegistryClient(
                WebClient.builder().baseUrl(baseUri.toString()).build(),
                baseUri,
                "/api/v1",
                null,
                1024 * 1024
        );
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }


    @Test
    void search_includesAuthorizationHeaderWhenTokenConfigured() {
        client = new ClawHubRemoteRegistryClient(
                WebClient.builder().baseUrl(baseUri.toString()).build(),
                baseUri,
                "/api/v1",
                "Bearer test-token",
                1024 * 1024
        );
        server.createContext("/api/v1/search", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-token");
            respondJson(exchange, 200, "{\"results\":[]}");
        });

        var result = client.search("git", 0, 10);

        assertThat(result.results()).isEmpty();
    }

    @Test
    void search_mapsResults() {
        server.createContext("/api/v1/search", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            assertThat(exchange.getRequestURI().getQuery()).contains("q=calendar");
            assertThat(exchange.getRequestURI().getQuery()).contains("page=0");
            assertThat(exchange.getRequestURI().getQuery()).contains("limit=2");
            respondJson(exchange, 200, """
                    {
                      "results": [
                        {
                          "slug": "calendar",
                          "displayName": "Calendar",
                          "summary": "Calendar management",
                          "version": "1.0.0",
                          "score": 3.75,
                          "updatedAt": 1773439064678
                        }
                      ]
                    }
                    """);
        });

        var result = client.search("calendar", 0, 2);

        assertThat(result.results()).hasSize(1);
        var hit = result.results().getFirst();
        assertThat(hit.canonicalSlug()).isEqualTo("calendar");
        assertThat(hit.displayName()).isEqualTo("Calendar");
        assertThat(hit.version()).isEqualTo("1.0.0");
        assertThat(hit.score()).isEqualTo(3.75);
        assertThat(hit.updatedAt()).isEqualTo(Instant.ofEpochMilli(1773439064678L));
    }

    @Test
    void getSkill_mapsDetailPayload() {
        server.createContext("/api/v1/skills/calendar", exchange -> respondJson(exchange, 200, """
                {
                  "skill": {
                    "slug": "calendar",
                    "displayName": "Calendar",
                    "summary": "Calendar management and scheduling.",
                    "tags": {"latest": "1.0.0"},
                    "stats": {"downloads": 10242, "stars": 2},
                    "createdAt": 1771427803561,
                    "updatedAt": 1773439064678
                  },
                  "latestVersion": {
                    "version": "1.0.0",
                    "createdAt": 1771427803561,
                    "changelog": "Initial release",
                    "license": null
                  },
                  "metadata": {
                    "systems": ["linux"]
                  },
                  "owner": {
                    "handle": "ndcccccc",
                    "displayName": "NDCCCCCC",
                    "image": "https://example.com/avatar.png"
                  },
                  "moderation": {
                    "isSuspicious": false,
                    "isMalwareBlocked": false,
                    "verdict": "clean",
                    "reasonCodes": [],
                    "updatedAt": 1773439064678,
                    "engineVersion": "1.2.3",
                    "summary": "clean"
                  }
                }
                """));

        var detail = client.getSkill("calendar");

        assertThat(detail.canonicalSlug()).isEqualTo("calendar");
        assertThat(detail.displayName()).isEqualTo("Calendar");
        assertThat(detail.tags()).containsEntry("latest", "1.0.0");
        assertThat(detail.stats()).containsEntry("downloads", 10242);
        assertThat(detail.latestVersion()).isNotNull();
        assertThat(detail.latestVersion().version()).isEqualTo("1.0.0");
        assertThat(detail.owner()).isNotNull();
        assertThat(detail.owner().handle()).isEqualTo("ndcccccc");
        assertThat(detail.metadata()).containsKey("systems");
        assertThat(detail.moderation()).isNotNull();
        assertThat(detail.moderation().verdict()).isEqualTo("clean");
    }

    @Test
    void resolve_latestVersion_usesSkillDetailEndpoint() {
        server.createContext("/api/v1/skills/calendar", exchange -> respondJson(exchange, 200, """
                {
                  "skill": {"slug": "calendar", "displayName": "Calendar", "summary": "Calendar"},
                  "latestVersion": {"version": "1.0.0", "createdAt": 1771427803561, "changelog": "Initial release", "license": null}
                }
                """));

        var result = client.resolve("calendar", "latest");

        assertThat(result.matchedVersion()).isEqualTo("1.0.0");
        assertThat(result.latestVersion()).isEqualTo("1.0.0");
    }

    @Test
    void resolve_specificVersion_usesVersionEndpoint() {
        server.createContext("/api/v1/skills/calendar", exchange -> respondJson(exchange, 200, """
                {
                  "skill": {"slug": "calendar", "displayName": "Calendar", "summary": "Calendar"},
                  "latestVersion": {"version": "1.1.0", "createdAt": 1771427803561, "changelog": "Latest release", "license": null}
                }
                """));
        server.createContext("/api/v1/skills/calendar/versions/1.0.0", exchange -> respondJson(exchange, 200, """
                {
                  "skill": {"slug": "calendar", "displayName": "Calendar"},
                  "version": {"version": "1.0.0", "createdAt": 1771427803561, "changelog": "Initial release", "license": null}
                }
                """));

        var result = client.resolve("calendar", "1.0.0");

        assertThat(result.matchedVersion()).isEqualTo("1.0.0");
        assertThat(result.latestVersion()).isEqualTo("1.1.0");
    }

    @Test
    void resolveDownload_usesQueryEndpointAndRedirectLocation() {
        server.createContext("/api/v1/download", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            assertThat(exchange.getRequestURI().getQuery()).contains("slug=calendar");
            assertThat(exchange.getRequestURI().getQuery()).contains("tag=latest");
            exchange.getResponseHeaders().add("Location", "/bundles/calendar-1.0.0.zip");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        var downloadInfo = client.resolveDownload("calendar", "latest");

        assertThat(downloadInfo.canonicalSlug()).isEqualTo("calendar");
        assertThat(downloadInfo.version()).isEqualTo("latest");
        assertThat(downloadInfo.downloadUri()).isEqualTo(baseUri.resolve("/bundles/calendar-1.0.0.zip"));
    }

    @Test
    void resolveDownload_specificVersion_returnsDirectRequestUriOnSuccess() {
        server.createContext("/api/v1/download", exchange -> {
            assertThat(exchange.getRequestURI().getQuery()).contains("slug=calendar");
            assertThat(exchange.getRequestURI().getQuery()).contains("version=1.0.0");
            respondBytes(exchange, 200, "application/zip", "zip".getBytes(StandardCharsets.UTF_8));
        });

        var downloadInfo = client.resolveDownload("calendar", "1.0.0");

        assertThat(downloadInfo.downloadUri().toString()).contains("/api/v1/download");
        assertThat(downloadInfo.downloadUri().toString()).contains("slug=calendar");
        assertThat(downloadInfo.downloadUri().toString()).contains("version=1.0.0");
    }

    @Test
    void downloadBundle_readsBinaryPayload() {
        byte[] bundle = "fake-bundle".getBytes(StandardCharsets.UTF_8);
        server.createContext("/bundles/calendar-1.0.0.zip", exchange -> respondBytes(exchange, 200, "application/zip", bundle));

        byte[] result = client.downloadBundle(URI.create("/bundles/calendar-1.0.0.zip"));

        assertThat(result).isEqualTo(bundle);
    }

    @Test
    void downloadBundle_rejectsPayloadsLargerThanConfiguredLimit() {
        client = new ClawHubRemoteRegistryClient(
                WebClient.builder().baseUrl(baseUri.toString()).build(),
                baseUri,
                "/api/v1",
                null,
                4
        );
        server.createContext("/bundles/too-large.zip", exchange -> respondBytes(exchange, 200, "application/zip", "12345".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> client.downloadBundle(URI.create("/bundles/too-large.zip")))
                .isInstanceOf(RemoteRegistryException.class)
                .hasMessageContaining("exceeds configured max size")
                .satisfies(throwable -> {
                    RemoteRegistryException exception = (RemoteRegistryException) throwable;
                    assertThat(exception.getStatusCode()).isEqualTo(413);
                });
    }

    @Test
    void getSkill_wrapsHttpErrors() {
        server.createContext("/api/v1/skills/missing", exchange -> respondJson(exchange, 404, "{\"error\":\"not found\"}"));

        assertThatThrownBy(() -> client.getSkill("missing"))
                .isInstanceOf(RemoteRegistryException.class)
                .hasMessageContaining("ClawHub getSkill failed: HTTP 404")
                .satisfies(throwable -> {
                    RemoteRegistryException exception = (RemoteRegistryException) throwable;
                    assertThat(exception.getStatusCode()).isEqualTo(404);
                    assertThat(exception.getResponseBody()).contains("not found");
                });
    }

    private static void respondJson(HttpExchange exchange, int status, String body) throws IOException {
        respondBytes(exchange, status, "application/json", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void respondBytes(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }
}
