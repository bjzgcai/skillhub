package com.iflytek.skillhub.infra.registry.clawhub;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.registry.remote.RemoteRegistryClient;
import com.iflytek.skillhub.domain.registry.remote.RemoteRegistryException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/**
 * ClawHub-specific adapter for the remote registry domain contract.
 */
public class ClawHubRemoteRegistryClient implements RemoteRegistryClient {

    private static final Logger log = LoggerFactory.getLogger(ClawHubRemoteRegistryClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final int LOG_BODY_LIMIT = 2_000;

    private final WebClient webClient;
    private final URI registryBaseUri;
    private final String apiBasePath;
    private final String authorizationHeaderValue;
    private final long maxBundleBytes;

    public ClawHubRemoteRegistryClient(WebClient webClient,
                                       URI registryBaseUri,
                                       String apiBasePath,
                                       String authorizationHeaderValue,
                                       long maxBundleBytes) {
        this.webClient = webClient;
        this.registryBaseUri = ensureTrailingSlash(registryBaseUri);
        this.apiBasePath = normalizeApiBasePath(apiBasePath);
        this.authorizationHeaderValue = authorizationHeaderValue;
        this.maxBundleBytes = maxBundleBytes;
        log.info("ClawHub remote registry auth configured={} maxBundleBytes={}", hasAuthorizationConfigured(), maxBundleBytes);
    }

    @Override
    public SearchResult search(String query, int page, int limit) {
        URI uri = UriComponentsBuilder.fromUri(registryBaseUri)
                .path(apiPath("/search"))
                .queryParam("q", query)
                .queryParam("page", page)
                .queryParam("limit", limit)
                .build(true)
                .toUri();
        ClawHubSearchResponse response = fetchJson(uri, ClawHubSearchResponse.class, "search");

        List<SearchHit> hits = response.results() == null
                ? List.of()
                : response.results().stream()
                .map(result -> new SearchHit(
                        result.slug(),
                        result.displayName(),
                        result.summary(),
                        result.version(),
                        result.score() == null ? 0D : result.score(),
                        toInstant(result.updatedAt())
                ))
                .toList();
        return new SearchResult(hits);
    }

    @Override
    public SkillDetail getSkill(String canonicalSlug) {
        URI uri = UriComponentsBuilder.fromUri(registryBaseUri)
                .path(apiPath("/skills/{canonicalSlug}"))
                .buildAndExpand(canonicalSlug)
                .toUri();
        ClawHubSkillResponse response = fetchJson(uri, ClawHubSkillResponse.class, "getSkill");

        ClawHubSkillResponse.SkillInfo skill = response.skill();
        if (skill == null) {
            throw new RemoteRegistryException("ClawHub skill response missing skill payload for " + canonicalSlug);
        }

        return new SkillDetail(
                skill.slug(),
                skill.displayName(),
                skill.summary(),
                safeStringMap(skill.tags()),
                safeObjectMap(skill.stats()),
                toInstant(skill.createdAt()),
                toInstant(skill.updatedAt()),
                toVersionInfo(response.latestVersion()),
                toOwnerInfo(response.owner()),
                toModerationInfo(response.moderation()),
                safeObjectMap(response.metadata())
        );
    }

    @Override
    public ResolveResult resolve(String canonicalSlug, String version) {
        String requestedVersion = normalizeRequestedVersion(version);
        if ("latest".equals(requestedVersion)) {
            URI uri = UriComponentsBuilder.fromUri(registryBaseUri)
                    .path(apiPath("/skills/{canonicalSlug}"))
                    .buildAndExpand(canonicalSlug)
                    .toUri();
            ClawHubSkillResponse response = fetchJson(uri, ClawHubSkillResponse.class, "resolveLatest");
            String latestVersion = response.latestVersion() != null ? response.latestVersion().version() : null;
            return new ResolveResult(latestVersion, latestVersion);
        }

        URI versionUri = UriComponentsBuilder.fromUri(registryBaseUri)
                .path(apiPath("/skills/{canonicalSlug}/versions/{version}"))
                .buildAndExpand(canonicalSlug, requestedVersion)
                .toUri();
        URI skillUri = UriComponentsBuilder.fromUri(registryBaseUri)
                .path(apiPath("/skills/{canonicalSlug}"))
                .buildAndExpand(canonicalSlug)
                .toUri();

        ClawHubVersionResponse versionResponse = fetchJson(versionUri, ClawHubVersionResponse.class, "resolveVersion");
        ClawHubSkillResponse skillResponse = fetchJson(skillUri, ClawHubSkillResponse.class, "getSkillForResolve");
        String matchedVersion = versionResponse.version() != null ? versionResponse.version().version() : requestedVersion;
        String latestVersion = skillResponse.latestVersion() != null ? skillResponse.latestVersion().version() : matchedVersion;
        return new ResolveResult(matchedVersion, latestVersion);
    }

    @Override
    public DownloadInfo resolveDownload(String canonicalSlug, String version) {
        URI requestUri = buildDownloadUri(canonicalSlug, version);
        log.info("ClawHub resolveDownload request uri={} authConfigured={}", requestUri, hasAuthorizationConfigured());
        return applyAuthorization(webClient.get().uri(requestUri))
                .exchangeToMono(response -> toDownloadInfo(response, canonicalSlug, version, requestUri))
                .blockOptional()
                .orElseThrow(() -> new RemoteRegistryException("ClawHub download resolution returned no response for " + canonicalSlug));
    }

    @Override
    public byte[] downloadBundle(URI downloadUri) {
        URI resolvedUri = resolveAgainstBase(downloadUri);
        log.info("ClawHub downloadBundle request uri={} authConfigured={}", resolvedUri, hasAuthorizationConfigured());
        try {
            return applyAuthorization(webClient.get().uri(resolvedUri))
                    .exchangeToMono(response -> toBundleBytes(response, resolvedUri))
                    .blockOptional()
                    .orElseThrow(() -> new RemoteRegistryException("ClawHub bundle download returned empty body for " + resolvedUri));
        } catch (RemoteRegistryException e) {
            throw e;
        } catch (Exception e) {
            throw new RemoteRegistryException("ClawHub bundle download failed for " + resolvedUri, e);
        }
    }

    private <T> T fetchJson(URI uri, Class<T> responseType, String operation) {
        log.info("ClawHub {} request uri={} authConfigured={}", operation, uri, hasAuthorizationConfigured());
        try {
            return applyAuthorization(webClient.get().uri(uri))
                    .exchangeToMono(response -> response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> decodeJsonResponse(response, body, uri, responseType, operation)))
                    .blockOptional()
                    .orElseThrow(() -> new RemoteRegistryException("ClawHub " + operation + " returned no response body"));
        } catch (RemoteRegistryException e) {
            throw e;
        } catch (Exception e) {
            throw new RemoteRegistryException("ClawHub " + operation + " request failed", e);
        }
    }

    private <T> T decodeJsonResponse(ClientResponse response,
                                     String body,
                                     URI uri,
                                     Class<T> responseType,
                                     String operation) {
        HttpStatusCode status = response.statusCode();
        HttpHeaders headers = response.headers().asHttpHeaders();
        logResponse(operation, uri, status.value(), headers, body);
        if (status.isError()) {
            throw new RemoteRegistryException(
                    status.value(),
                    body,
                    "ClawHub " + operation + " failed: HTTP " + status.value()
            );
        }
        if (body == null || body.isBlank()) {
            throw new RemoteRegistryException("ClawHub " + operation + " returned empty response body");
        }
        try {
            return OBJECT_MAPPER.readValue(body, responseType);
        } catch (Exception e) {
            throw new RemoteRegistryException(
                    status.value(),
                    body,
                    "ClawHub " + operation + " returned unparsable JSON"
            );
        }
    }

    private Mono<DownloadInfo> toDownloadInfo(ClientResponse response,
                                              String canonicalSlug,
                                              String version,
                                              URI requestUri) {
        HttpStatusCode status = response.statusCode();
        HttpHeaders headers = response.headers().asHttpHeaders();
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> {
                    logResponse("resolveDownload", requestUri, status.value(), headers, body);
                    if (status.is3xxRedirection()) {
                        URI location = headers.getLocation();
                        if (location == null) {
                            throw new RemoteRegistryException("ClawHub download redirect missing Location header for " + canonicalSlug);
                        }
                        return new DownloadInfo(canonicalSlug, version, resolveAgainstBase(location));
                    }
                    if (status.is2xxSuccessful()) {
                        return new DownloadInfo(canonicalSlug, version, requestUri);
                    }
                    throw new RemoteRegistryException(
                            status.value(),
                            body,
                            "ClawHub download resolution failed: HTTP " + status.value() + " for " + canonicalSlug
                    );
                });
    }

    private Mono<byte[]> toBundleBytes(ClientResponse response, URI uri) {
        HttpStatusCode status = response.statusCode();
        HttpHeaders headers = response.headers().asHttpHeaders();
        if (status.isError()) {
            return response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .map(body -> {
                        logResponse("downloadBundle", uri, status.value(), headers, body);
                        throw new RemoteRegistryException(
                                status.value(),
                                body,
                                "ClawHub bundle download failed: HTTP " + status.value() + " for " + uri
                        );
                    });
        }
        long contentLength = headers.getContentLength();
        if (contentLength > maxBundleBytes) {
            throw new RemoteRegistryException(
                    413,
                    "content-length=" + contentLength,
                    "ClawHub bundle download exceeds configured max size of " + maxBundleBytes + " bytes"
            );
        }
        return response.bodyToMono(byte[].class)
                .onErrorMap(DataBufferLimitException.class, ex -> new RemoteRegistryException(
                        413,
                        "max-bytes=" + maxBundleBytes,
                        "ClawHub bundle download exceeds configured max size of " + maxBundleBytes + " bytes"
                ))
                .map(bytes -> {
                    if (bytes != null && bytes.length > maxBundleBytes) {
                        throw new RemoteRegistryException(
                                413,
                                "bytes=" + bytes.length,
                                "ClawHub bundle download exceeds configured max size of " + maxBundleBytes + " bytes"
                        );
                    }
                    log.info(
                            "ClawHub downloadBundle response uri={} status={} headers={} bytes={}",
                            uri,
                            status.value(),
                            summarizeHeaders(headers),
                            bytes == null ? 0 : bytes.length
                    );
                    return bytes;
                });
    }

    private boolean hasAuthorizationConfigured() {
        return authorizationHeaderValue != null && !authorizationHeaderValue.isBlank();
    }

    private WebClient.RequestHeadersSpec<?> applyAuthorization(WebClient.RequestHeadersSpec<?> requestSpec) {
        if (!hasAuthorizationConfigured()) {
            return requestSpec;
        }
        return requestSpec.header(HttpHeaders.AUTHORIZATION, authorizationHeaderValue);
    }

    private URI buildDownloadUri(String canonicalSlug, String version) {
        String requestedVersion = normalizeRequestedVersion(version);
        return UriComponentsBuilder
                .fromUri(registryBaseUri)
                .path(apiPath("/download"))
                .queryParam("slug", canonicalSlug)
                .queryParam("latest".equals(requestedVersion) ? "tag" : "version", requestedVersion)
                .build(true)
                .toUri();
    }

    private String normalizeRequestedVersion(String version) {
        return version == null || version.isBlank() ? "latest" : version;
    }

    private String apiPath(String path) {
        String suffix = path.startsWith("/") ? path : "/" + path;
        return apiBasePath + suffix;
    }

    private static String normalizeApiBasePath(String apiBasePath) {
        if (apiBasePath == null || apiBasePath.isBlank()) {
            return "/api/v1";
        }
        String normalized = apiBasePath.startsWith("/") ? apiBasePath : "/" + apiBasePath;
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static URI ensureTrailingSlash(URI uri) {
        String text = uri.toString();
        return URI.create(text.endsWith("/") ? text : text + "/");
    }

    private URI resolveAgainstBase(URI candidate) {
        return candidate.isAbsolute() ? candidate : registryBaseUri.resolve(candidate.toString());
    }

    private RemoteRegistryClient.VersionInfo toVersionInfo(ClawHubSkillResponse.VersionInfo versionInfo) {
        if (versionInfo == null) {
            return null;
        }
        return new RemoteRegistryClient.VersionInfo(
                versionInfo.version(),
                toInstant(versionInfo.createdAt()),
                versionInfo.changelog(),
                versionInfo.license()
        );
    }

    private RemoteRegistryClient.OwnerInfo toOwnerInfo(ClawHubSkillResponse.OwnerInfo ownerInfo) {
        if (ownerInfo == null) {
            return null;
        }
        return new RemoteRegistryClient.OwnerInfo(
                ownerInfo.handle(),
                ownerInfo.displayName(),
                ownerInfo.image()
        );
    }

    private RemoteRegistryClient.ModerationInfo toModerationInfo(ClawHubSkillResponse.ModerationInfo moderationInfo) {
        if (moderationInfo == null) {
            return null;
        }
        return new RemoteRegistryClient.ModerationInfo(
                Boolean.TRUE.equals(moderationInfo.isSuspicious()),
                Boolean.TRUE.equals(moderationInfo.isMalwareBlocked()),
                moderationInfo.verdict(),
                moderationInfo.reasonCodes() == null ? List.of() : moderationInfo.reasonCodes(),
                toInstant(moderationInfo.updatedAt()),
                moderationInfo.engineVersion(),
                moderationInfo.summary()
        );
    }

    private void logResponse(String operation, URI uri, int status, HttpHeaders headers, String body) {
        String levelMessage = String.format(
                "ClawHub %s response uri=%s status=%d headers=%s body=%s",
                operation,
                uri,
                status,
                summarizeHeaders(headers),
                abbreviate(body)
        );
        if (status >= 400) {
            log.warn(levelMessage);
        } else {
            log.info(levelMessage);
        }
    }

    private String summarizeHeaders(HttpHeaders headers) {
        List<String> interesting = List.of(
                HttpHeaders.LOCATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.CONTENT_LENGTH,
                "ratelimit-limit",
                "ratelimit-remaining",
                "ratelimit-reset",
                "retry-after",
                "x-ratelimit-limit",
                "x-ratelimit-remaining",
                "x-ratelimit-reset"
        );
        return interesting.stream()
                .filter(headers::containsKey)
                .map(name -> name + "=" + String.join("|", headers.getOrEmpty(name)))
                .collect(Collectors.joining(", "));
    }

    private String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        String normalized = body.replace('\n', ' ').replace('\r', ' ');
        if (normalized.length() <= LOG_BODY_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, LOG_BODY_LIMIT) + "...(truncated)";
    }

    private static Instant toInstant(Long epochMillis) {
        return epochMillis == null ? null : Instant.ofEpochMilli(epochMillis);
    }

    private static Map<String, String> safeStringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return map.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        entry -> String.valueOf(entry.getKey()),
                        entry -> String.valueOf(entry.getValue()),
                        (left, right) -> right
                ));
    }

    private static Map<String, Object> safeObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return map.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        entry -> String.valueOf(entry.getKey()),
                        entry -> entry.getValue(),
                        (left, right) -> right
                ));
    }

    private record ClawHubSearchResponse(List<ClawHubSearchResult> results) {
    }

    private record ClawHubSearchResult(
            String slug,
            String displayName,
            String summary,
            String version,
            Double score,
            Long updatedAt
    ) {
    }

    private record ClawHubVersionResponse(ClawHubVersionInfo version) {
    }

    private record ClawHubVersionInfo(String version) {
    }

    private record ClawHubSkillResponse(
            SkillInfo skill,
            VersionInfo latestVersion,
            OwnerInfo owner,
            ModerationInfo moderation,
            Object metadata
    ) {
        private record SkillInfo(
                String slug,
                String displayName,
                String summary,
                Object tags,
                Object stats,
                Long createdAt,
                Long updatedAt
        ) {
        }

        private record VersionInfo(
                String version,
                Long createdAt,
                String changelog,
                String license
        ) {
        }

        private record OwnerInfo(
                String handle,
                String displayName,
                String image
        ) {
        }

        private record ModerationInfo(
                Boolean isSuspicious,
                Boolean isMalwareBlocked,
                String verdict,
                List<String> reasonCodes,
                Long updatedAt,
                String engineVersion,
                String summary
        ) {
        }
    }
}
