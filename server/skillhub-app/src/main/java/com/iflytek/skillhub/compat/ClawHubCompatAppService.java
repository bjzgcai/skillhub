package com.iflytek.skillhub.compat;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.compat.dto.ClawHubDeleteResponse;
import com.iflytek.skillhub.compat.dto.ClawHubPublishResponse;
import com.iflytek.skillhub.compat.dto.ClawHubResolveResponse;
import com.iflytek.skillhub.compat.dto.ClawHubSearchResponse;
import com.iflytek.skillhub.compat.dto.ClawHubSkillListResponse;
import com.iflytek.skillhub.compat.dto.ClawHubSkillResponse;
import com.iflytek.skillhub.compat.dto.ClawHubSkillVersionListResponse;
import com.iflytek.skillhub.compat.dto.ClawHubSkillVersionResponse;
import com.iflytek.skillhub.compat.dto.ClawHubStarResponse;
import com.iflytek.skillhub.compat.dto.ClawHubUnstarResponse;
import com.iflytek.skillhub.compat.dto.ClawHubWhoamiResponse;
import com.iflytek.skillhub.controller.support.MultipartPackageExtractor;
import com.iflytek.skillhub.controller.support.ZipPackageExtractor;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.registry.remote.RemoteRegistryClient;
import com.iflytek.skillhub.domain.registry.remote.RemoteRegistryException;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.domain.social.SkillStarService;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.exception.ServiceUnavailableException;
import com.iflytek.skillhub.exception.TooManyRequestsException;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.RemoteMirrorIngestAppService;
import com.iflytek.skillhub.service.SkillLifecycleAppService;
import com.iflytek.skillhub.service.SkillSearchAppService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Compatibility-focused application service that keeps ClawHub transport logic
 * out of the controller while preserving the existing wire contract.
 */
@Service
public class ClawHubCompatAppService {

    private static final Logger log = LoggerFactory.getLogger(ClawHubCompatAppService.class);
    private static final Duration REMOTE_SEARCH_CACHE_TTL = Duration.ofMinutes(2);
    private static final Duration REMOTE_SEARCH_STALE_FALLBACK_TTL = Duration.ofMinutes(10);
    private static final int REMOTE_SEARCH_CACHE_MAX_ENTRIES = 256;
    private static final int MAX_COMPAT_FILE_PATH_LENGTH = 1024;
    private static final long MAX_COMPAT_TEXT_FILE_BYTES = 1024 * 1024;

    private final CanonicalSlugMapper mapper;
    private final SkillSearchAppService skillSearchAppService;
    private final SkillQueryService skillQueryService;
    private final SkillPublishService skillPublishService;
    private final ZipPackageExtractor zipPackageExtractor;
    private final MultipartPackageExtractor multipartPackageExtractor;
    private final AuditLogService auditLogService;
    private final CompatSkillLookupService compatSkillLookupService;
    private final SkillStarService skillStarService;
    private final ObjectProvider<RemoteRegistryClient> remoteRegistryClientProvider;
    private final RemoteMirrorIngestAppService remoteMirrorIngestAppService;
    private final SkillLifecycleAppService skillLifecycleAppService;
    private final Map<String, CachedRemoteSearchResults> remoteSearchCache = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedRemoteSearchResults> eldest) {
                    return size() > REMOTE_SEARCH_CACHE_MAX_ENTRIES;
                }
            }
    );

    public ClawHubCompatAppService(CanonicalSlugMapper mapper,
                                   SkillSearchAppService skillSearchAppService,
                                   SkillQueryService skillQueryService,
                                   SkillPublishService skillPublishService,
                                   ZipPackageExtractor zipPackageExtractor,
                                   MultipartPackageExtractor multipartPackageExtractor,
                                   AuditLogService auditLogService,
                                   CompatSkillLookupService compatSkillLookupService,
                                   SkillStarService skillStarService,
                                   ObjectProvider<RemoteRegistryClient> remoteRegistryClientProvider,
                                   RemoteMirrorIngestAppService remoteMirrorIngestAppService,
                                   SkillLifecycleAppService skillLifecycleAppService) {
        this.mapper = mapper;
        this.skillSearchAppService = skillSearchAppService;
        this.skillQueryService = skillQueryService;
        this.skillPublishService = skillPublishService;
        this.zipPackageExtractor = zipPackageExtractor;
        this.multipartPackageExtractor = multipartPackageExtractor;
        this.auditLogService = auditLogService;
        this.compatSkillLookupService = compatSkillLookupService;
        this.skillStarService = skillStarService;
        this.remoteRegistryClientProvider = remoteRegistryClientProvider;
        this.remoteMirrorIngestAppService = remoteMirrorIngestAppService;
        this.skillLifecycleAppService = skillLifecycleAppService;
    }

    public ClawHubSearchResponse search(String q,
                                        int page,
                                        int limit,
                                        String userId,
                                        Map<Long, NamespaceRole> userNsRoles) {
        SkillSearchAppService.SearchResponse response = skillSearchAppService.search(
                q,
                null,
                q == null || q.isBlank() ? "newest" : "relevance",
                page,
                limit,
                userId,
                userNsRoles
        );

        List<ClawHubSearchResponse.ClawHubSearchResult> localResults = response.items().stream()
                .map(this::toSearchResult)
                .toList();

        List<ClawHubSearchResponse.ClawHubSearchResult> results = mergeSearchResults(
                localResults,
                fetchRemoteSearchResults(q, page, limit),
                limit
        );

        return new ClawHubSearchResponse(results);
    }

    public ClawHubResolveResponse resolveByQuery(String slug,
                                                 String version,
                                                 String hash,
                                                 String userId,
                                                 Map<Long, NamespaceRole> userNsRoles) {
        try {
            CompatSkillLookupService.CompatSkillContext context = compatSkillLookupService.findByLegacySlug(slug);

            SkillQueryService.ResolvedVersionDTO resolved = skillQueryService.resolveVersion(
                    context.namespace().getSlug(),
                    context.skill().getSlug(),
                    "latest".equals(version) ? null : version,
                    "latest".equals(version) ? "latest" : null,
                    hash,
                    userId,
                    userNsRoles != null ? userNsRoles : Map.of()
            );
            return toResolveResponse(resolved);
        } catch (DomainNotFoundException e) {
            return resolveFromRemote(slug, version, e);
        }
    }

    public ClawHubResolveResponse resolve(String canonicalSlug,
                                          String version,
                                          String userId,
                                          Map<Long, NamespaceRole> userNsRoles) {
        SkillCoordinate coord = mapper.fromCanonical(canonicalSlug);
        try {
            compatSkillLookupService.resolveVisible(coord.namespace(), coord.slug(), userId);
            SkillQueryService.ResolvedVersionDTO resolved = skillQueryService.resolveVersion(
                    coord.namespace(),
                    coord.slug(),
                    "latest".equals(version) ? null : version,
                    "latest".equals(version) ? "latest" : null,
                    null,
                    userId,
                    userNsRoles != null ? userNsRoles : Map.of()
            );
            return toResolveResponse(resolved);
        } catch (DomainNotFoundException e) {
            return resolveFromRemote(canonicalSlug, version, e);
        }
    }

    public String downloadLocationByPath(String canonicalSlug, String version) {
        SkillCoordinate coord = mapper.fromCanonical(canonicalSlug);
        try {
            compatSkillLookupService.resolveVisible(coord.namespace(), coord.slug(), null);
            return "latest".equals(version)
                    ? "/api/v1/skills/" + coord.namespace() + "/" + coord.slug() + "/download"
                    : "/api/v1/skills/" + coord.namespace() + "/" + coord.slug() + "/versions/" + version + "/download";
        } catch (DomainNotFoundException e) {
            return mirrorThenResolveLocalDownloadLocation(canonicalSlug, version, e);
        }
    }

    public String downloadLocationByQuery(String slug, String version) {
        try {
            CompatSkillLookupService.CompatSkillContext context = compatSkillLookupService.findByLegacySlug(slug);
            return "latest".equals(version)
                    ? "/api/v1/skills/" + context.namespace().getSlug() + "/" + context.skill().getSlug() + "/download"
                    : "/api/v1/skills/" + context.namespace().getSlug() + "/" + context.skill().getSlug() + "/versions/" + version + "/download";
        } catch (DomainNotFoundException e) {
            return mirrorThenResolveLocalDownloadLocation(slug, version, e);
        }
    }

    public ClawHubSkillListResponse listSkills(int page,
                                               int limit,
                                               String sort,
                                               String userId,
                                               Map<Long, NamespaceRole> userNsRoles) {
        String sortBy = sort != null ? sort : "newest";
        SkillSearchAppService.SearchResponse response = skillSearchAppService.search(
                "",
                null,
                sortBy,
                page,
                limit,
                userId,
                userNsRoles
        );

        List<ClawHubSkillListResponse.SkillListItem> items = response.items().stream()
                .map(this::toSkillListItem)
                .toList();

        String nextCursor = null;
        long totalResults = response.total();
        long currentOffset = (long) page * limit;
        if (currentOffset + items.size() < totalResults) {
            nextCursor = String.valueOf(page + 1);
        }

        return new ClawHubSkillListResponse(items, nextCursor);
    }

    public ClawHubSkillResponse getSkill(String canonicalSlug, String userId) {
        SkillCoordinate coord = mapper.fromCanonical(canonicalSlug);
        try {
            CompatSkillLookupService.CompatSkillContext context = compatSkillLookupService.resolveVisible(
                    coord.namespace(),
                    coord.slug(),
                    userId
            );
            SkillVersion latestVersionEntity = context.latestVersion().orElse(null);

            ClawHubSkillResponse.SkillInfo skillInfo = null;
            ClawHubSkillResponse.VersionInfo versionInfo = null;

            if (context.skill().getId() != null) {
                long createdAt = context.skill().getCreatedAt() != null ? context.skill().getCreatedAt().toEpochMilli() : 0;
                long updatedAt = context.skill().getUpdatedAt() != null ? context.skill().getUpdatedAt().toEpochMilli() : 0;
                skillInfo = new ClawHubSkillResponse.SkillInfo(
                        mapper.toCanonical(coord.namespace(), coord.slug()),
                        context.skill().getDisplayName(),
                        context.skill().getSummary(),
                        Map.of(),
                        Map.of(),
                        createdAt,
                        updatedAt
                );

                if (latestVersionEntity != null) {
                    long versionCreatedAt = latestVersionEntity.getPublishedAt() != null
                            ? latestVersionEntity.getPublishedAt().toEpochMilli()
                            : 0;
                    versionInfo = new ClawHubSkillResponse.VersionInfo(
                            latestVersionEntity.getVersion(),
                            versionCreatedAt,
                            latestVersionEntity.getChangelog() == null ? "" : latestVersionEntity.getChangelog(),
                            null
                    );
                }
            }

            return new ClawHubSkillResponse(
                    skillInfo,
                    versionInfo,
                    null,
                    new ClawHubSkillResponse.ModerationInfo(false, false, "clean", new String[0], null, null, null)
            );
        } catch (DomainNotFoundException e) {
            return getRemoteSkill(canonicalSlug, e);
        }
    }

    public ClawHubDeleteResponse deleteSkill(String canonicalSlug,
                                             String userId,
                                             Map<Long, NamespaceRole> userNsRoles,
                                             AuditRequestContext auditRequestContext) {
        SkillCoordinate coord = mapper.fromCanonical(canonicalSlug);
        skillLifecycleAppService.archiveSkill(
                coord.namespace(),
                coord.slug(),
                null,
                userId,
                userNsRoles,
                auditRequestContext
        );
        return new ClawHubDeleteResponse();
    }

    public ClawHubDeleteResponse undeleteSkill(String canonicalSlug,
                                               String userId,
                                               Map<Long, NamespaceRole> userNsRoles,
                                               AuditRequestContext auditRequestContext) {
        SkillCoordinate coord = mapper.fromCanonical(canonicalSlug);
        skillLifecycleAppService.unarchiveSkill(
                coord.namespace(),
                coord.slug(),
                userId,
                userNsRoles,
                auditRequestContext
        );
        return new ClawHubDeleteResponse();
    }

    public ClawHubStarResponse starSkill(String canonicalSlug, PlatformPrincipal principal) {
        SkillCoordinate coord = mapper.fromCanonical(canonicalSlug);
        CompatSkillLookupService.CompatSkillContext context = compatSkillLookupService.resolveVisible(
                coord.namespace(),
                coord.slug(),
                principal.userId()
        );

        boolean alreadyStarred = skillStarService.isStarred(context.skill().getId(), principal.userId());
        skillStarService.star(context.skill().getId(), principal.userId());
        return new ClawHubStarResponse(true, alreadyStarred);
    }

    public ClawHubUnstarResponse unstarSkill(String canonicalSlug, PlatformPrincipal principal) {
        SkillCoordinate coord = mapper.fromCanonical(canonicalSlug);
        CompatSkillLookupService.CompatSkillContext context = compatSkillLookupService.resolveVisible(
                coord.namespace(),
                coord.slug(),
                principal.userId()
        );

        boolean alreadyUnstarred = !skillStarService.isStarred(context.skill().getId(), principal.userId());
        skillStarService.unstar(context.skill().getId(), principal.userId());
        return new ClawHubUnstarResponse(true, alreadyUnstarred);
    }

    public ClawHubPublishResponse publishSkill(String payloadJson,
                                               MultipartFile[] files,
                                               PlatformPrincipal principal,
                                               String clientIp,
                                               String userAgent) throws IOException {
        MultipartPackageExtractor.ExtractedPackage extracted = multipartPackageExtractor.extract(files, payloadJson);
        String namespace = determineNamespace(principal, extracted.payload());
        SkillPublishService.PublishResult result = skillPublishService.publishFromEntries(
                namespace,
                extracted.entries(),
                principal.userId(),
                SkillVisibility.PUBLIC,
                principal.platformRoles()
        );
        recordCompatPublishAudit(principal.userId(), result.version().getId(), clientIp, userAgent,
                "{\"namespace\":\"" + namespace + "\",\"slug\":\"" + extracted.payload().slug() + "\"}");
        return new ClawHubPublishResponse(result.skillId().toString(), result.version().getId().toString());
    }

    public ClawHubPublishResponse publish(MultipartFile file,
                                          String namespace,
                                          PlatformPrincipal principal,
                                          String clientIp,
                                          String userAgent) throws IOException {
        SkillPublishService.PublishResult result = skillPublishService.publishFromEntries(
                namespace,
                zipPackageExtractor.extract(file),
                principal.userId(),
                SkillVisibility.PUBLIC,
                principal.platformRoles()
        );
        recordCompatPublishAudit(principal.userId(), result.version().getId(), clientIp, userAgent,
                "{\"namespace\":\"" + namespace + "\"}");
        return new ClawHubPublishResponse(result.skillId().toString(), result.version().getId().toString());
    }

    public ClawHubWhoamiResponse whoami(PlatformPrincipal principal) {
        return new ClawHubWhoamiResponse(
                principal.userId(),
                principal.displayName(),
                principal.avatarUrl()
        );
    }

    public ClawHubSkillVersionListResponse listVersions(String canonicalSlug,
                                                        int limit,
                                                        String userId,
                                                        Map<Long, NamespaceRole> userNsRoles) {
        SkillCoordinate coord = mapper.fromCanonical(canonicalSlug);
        int resolvedLimit = Math.max(1, Math.min(limit, 200));
        var page = skillQueryService.listVersions(
                coord.namespace(),
                coord.slug(),
                userId,
                userNsRoles != null ? userNsRoles : Map.of(),
                org.springframework.data.domain.PageRequest.of(0, resolvedLimit)
        );
        List<ClawHubSkillVersionListResponse.Item> items = page.getContent().stream()
                .map(v -> new ClawHubSkillVersionListResponse.Item(
                        v.getVersion(),
                        v.getPublishedAt() != null ? v.getPublishedAt().toEpochMilli() : 0,
                        v.getChangelog() != null ? v.getChangelog() : "",
                        v.getChangelog() != null && !v.getChangelog().isBlank() ? "user" : null
                ))
                .toList();
        return new ClawHubSkillVersionListResponse(items, null);
    }

    public ClawHubSkillVersionResponse getVersion(String canonicalSlug,
                                                  String version,
                                                  String userId,
                                                  Map<Long, NamespaceRole> userNsRoles) {
        SkillCoordinate coord = mapper.fromCanonical(canonicalSlug);
        CompatSkillLookupService.CompatSkillContext context = compatSkillLookupService.resolveVisible(
                coord.namespace(),
                coord.slug(),
                userId
        );
        SkillQueryService.SkillVersionDetailDTO detail = skillQueryService.getVersionDetail(
                coord.namespace(),
                coord.slug(),
                version,
                userId,
                userNsRoles != null ? userNsRoles : Map.of()
        );
        List<ClawHubSkillVersionResponse.FileEntry> files = skillQueryService.listFiles(
                        coord.namespace(),
                        coord.slug(),
                        version,
                        userId,
                        userNsRoles != null ? userNsRoles : Map.of()
                ).stream()
                .map(f -> new ClawHubSkillVersionResponse.FileEntry(
                        f.getFilePath(),
                        f.getFileSize() != null ? f.getFileSize() : 0,
                        f.getSha256(),
                        f.getContentType()
                ))
                .toList();
        return new ClawHubSkillVersionResponse(
                new ClawHubSkillVersionResponse.Version(
                        detail.version(),
                        detail.publishedAt() != null ? detail.publishedAt().toEpochMilli() : 0,
                        detail.changelog() != null ? detail.changelog() : "",
                        detail.changelog() != null && !detail.changelog().isBlank() ? "user" : null,
                        null,
                        files
                ),
                new ClawHubSkillVersionResponse.Skill(
                        mapper.toCanonical(coord.namespace(), context.skill().getSlug()),
                        context.skill().getDisplayName()
                )
        );
    }

    public String getFileContent(String canonicalSlug,
                                 String path,
                                 String version,
                                 String tag,
                                 String userId,
                                 Map<Long, NamespaceRole> userNsRoles) throws IOException {
        SkillCoordinate coord = mapper.fromCanonical(canonicalSlug);
        Map<Long, NamespaceRole> roles = userNsRoles != null ? userNsRoles : Map.of();
        String validatedPath = validateCompatFilePath(path);
        FileContentLookup lookup = resolveFileLookup(coord, validatedPath, version, tag, userId, roles);
        enforceCompatTextFilePolicy(lookup.file());
        try (InputStream content = openResolvedFileContent(coord, lookup, userId, roles)) {
            return new String(content.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private InputStream openResolvedFileContent(SkillCoordinate coord,
                                                FileContentLookup lookup,
                                                String userId,
                                                Map<Long, NamespaceRole> userNsRoles) {
        if (lookup.tag() != null && !lookup.tag().isBlank()) {
            return skillQueryService.getFileContentByTag(coord.namespace(), coord.slug(), lookup.tag(), lookup.path(), userId, userNsRoles);
        }
        return skillQueryService.getFileContent(coord.namespace(), coord.slug(), lookup.version(), lookup.path(), userId, userNsRoles);
    }

    private String validateCompatFilePath(String path) {
        if (path == null || path.isBlank()) {
            throw new DomainBadRequestException("error.skill.file.path.invalid", "path is blank");
        }
        String normalized = path.trim().replace('\\', '/');
        if (normalized.length() > MAX_COMPAT_FILE_PATH_LENGTH) {
            throw new DomainBadRequestException("error.skill.file.path.invalid", "path is too long");
        }
        if (normalized.startsWith("/")) {
            throw new DomainBadRequestException("error.skill.file.path.invalid", "absolute path is not allowed");
        }
        if (normalized.contains("../") || normalized.equals("..") || normalized.contains("/..")) {
            throw new DomainBadRequestException("error.skill.file.path.invalid", "parent path traversal is not allowed");
        }
        if (normalized.contains("//")) {
            throw new DomainBadRequestException("error.skill.file.path.invalid", "path contains empty segments");
        }
        return normalized;
    }

    private FileContentLookup resolveFileLookup(SkillCoordinate coord,
                                                String path,
                                                String version,
                                                String tag,
                                                String userId,
                                                Map<Long, NamespaceRole> userNsRoles) {
        if (tag != null && !tag.isBlank()) {
            return skillQueryService.listFilesByTag(coord.namespace(), coord.slug(), tag, userId, userNsRoles).stream()
                    .filter(f -> path.equals(f.getFilePath()))
                    .findFirst()
                    .map(file -> new FileContentLookup(path, null, tag, file))
                    .orElseThrow(() -> new DomainNotFoundException("error.skill.file.notFound", path));
        }
        String resolvedVersion = version;
        if (resolvedVersion == null || resolvedVersion.isBlank()) {
            CompatSkillLookupService.CompatSkillContext context = compatSkillLookupService.resolveVisible(
                    coord.namespace(),
                    coord.slug(),
                    userId
            );
            resolvedVersion = context.latestVersion().map(SkillVersion::getVersion).orElseThrow(
                    () -> new DomainNotFoundException("error.skill.version.notFound", coord.slug())
            );
        }
        String finalVersion = resolvedVersion;
        return skillQueryService.listFiles(coord.namespace(), coord.slug(), finalVersion, userId, userNsRoles).stream()
                .filter(f -> path.equals(f.getFilePath()))
                .findFirst()
                .map(file -> new FileContentLookup(path, finalVersion, null, file))
                .orElseThrow(() -> new DomainNotFoundException("error.skill.file.notFound", path));
    }

    private void enforceCompatTextFilePolicy(com.iflytek.skillhub.domain.skill.SkillFile file) {
        Long fileSize = file.getFileSize();
        if (fileSize != null && fileSize > MAX_COMPAT_TEXT_FILE_BYTES) {
            throw new DomainBadRequestException("error.skill.file.unsupported", "file is too large for text preview");
        }
        if (!isCompatTextFile(file.getFilePath(), file.getContentType())) {
            throw new DomainBadRequestException("error.skill.file.unsupported", "only text-like files are supported");
        }
    }

    private boolean isCompatTextFile(String filePath, String contentType) {
        if (contentType != null) {
            String normalizedType = contentType.toLowerCase();
            if (normalizedType.startsWith("text/")) {
                return true;
            }
            if (normalizedType.equals("application/json")
                    || normalizedType.equals("application/xml")
                    || normalizedType.equals("application/yaml")
                    || normalizedType.equals("application/x-yaml")
                    || normalizedType.equals("application/toml")
                    || normalizedType.equals("application/javascript")) {
                return true;
            }
        }
        String lowerPath = filePath != null ? filePath.toLowerCase() : "";
        return lowerPath.endsWith(".md")
                || lowerPath.endsWith(".txt")
                || lowerPath.endsWith(".json")
                || lowerPath.endsWith(".yaml")
                || lowerPath.endsWith(".yml")
                || lowerPath.endsWith(".xml")
                || lowerPath.endsWith(".toml")
                || lowerPath.endsWith(".java")
                || lowerPath.endsWith(".kt")
                || lowerPath.endsWith(".groovy")
                || lowerPath.endsWith(".js")
                || lowerPath.endsWith(".ts")
                || lowerPath.endsWith(".py")
                || lowerPath.endsWith(".sh")
                || lowerPath.endsWith(".properties")
                || lowerPath.endsWith(".sql")
                || lowerPath.endsWith(".csv")
                || lowerPath.endsWith(".html")
                || lowerPath.endsWith(".css")
                || lowerPath.endsWith(".svg");
    }

    private record FileContentLookup(
            String path,
            String version,
            String tag,
            com.iflytek.skillhub.domain.skill.SkillFile file
    ) {}

    private ClawHubSearchResponse.ClawHubSearchResult toSearchResult(SkillSummaryResponse item) {
        Long updatedAtEpoch = item.updatedAt() != null ? item.updatedAt().toEpochMilli() : null;
        return new ClawHubSearchResponse.ClawHubSearchResult(
                mapper.toCanonical(item.namespace(), item.slug()),
                item.displayName(),
                item.summary(),
                item.publishedVersion() != null ? item.publishedVersion().version() : null,
                calculateScore(item),
                updatedAtEpoch
        );
    }

    private double calculateScore(SkillSummaryResponse item) {
        int starScore = item.starCount() != null ? item.starCount() * 10 : 0;
        long downloadScore = item.downloadCount() != null ? item.downloadCount() : 0;
        return (starScore + downloadScore) / 100.0;
    }

    private List<ClawHubSearchResponse.ClawHubSearchResult> fetchRemoteSearchResults(String q, int page, int limit) {
        if (q == null || q.isBlank() || limit <= 0) {
            return List.of();
        }

        String cacheKey = buildRemoteSearchCacheKey(q, page, limit);
        CachedRemoteSearchResults cached = remoteSearchCache.get(cacheKey);
        if (cached != null && cached.isFresh()) {
            return cached.results();
        }

        RemoteRegistryClient remoteRegistryClient = remoteRegistryClientProvider.getIfAvailable();
        if (remoteRegistryClient == null) {
            return cached != null ? cached.results() : List.of();
        }

        try {
            List<ClawHubSearchResponse.ClawHubSearchResult> remoteResults = executeRemoteSearch(remoteRegistryClient, q, page, limit);
            remoteSearchCache.put(cacheKey, new CachedRemoteSearchResults(remoteResults, System.currentTimeMillis()));
            return remoteResults;
        } catch (RemoteRegistryException e) {
            if (cached != null && cached.isUsableAsStaleFallback()) {
                log.warn("Remote registry search failed for query '{}': {}. Using cached search results.", q, e.getMessage());
                return cached.results();
            }
            log.warn("Remote registry search failed for query '{}': {}", q, e.getMessage());
            return List.of();
        }
    }

    private List<ClawHubSearchResponse.ClawHubSearchResult> executeRemoteSearch(RemoteRegistryClient remoteRegistryClient,
                                                                                String q,
                                                                                int page,
                                                                                int limit) {
        RemoteRegistryException lastError = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                RemoteRegistryClient.SearchResult searchResult = remoteRegistryClient.search(q, page, limit);
                return mapRemoteSearchResults(searchResult);
            } catch (RemoteRegistryException e) {
                lastError = e;
                if (!isRetryableRemoteSearchError(e) || attempt == 2) {
                    throw e;
                }
                sleepQuietly(250L * attempt);
            }
        }
        throw lastError == null ? new RemoteRegistryException("Remote registry search failed") : lastError;
    }

    private List<ClawHubSearchResponse.ClawHubSearchResult> mapRemoteSearchResults(RemoteRegistryClient.SearchResult searchResult) {
        if (searchResult == null || searchResult.results() == null) {
            return List.of();
        }
        return searchResult.results().stream()
                .filter(hit -> hit != null && hit.canonicalSlug() != null && !hit.canonicalSlug().isBlank())
                .map(hit -> new ClawHubSearchResponse.ClawHubSearchResult(
                        hit.canonicalSlug(),
                        hit.displayName(),
                        hit.summary(),
                        hit.version(),
                        hit.score(),
                        hit.updatedAt() != null ? hit.updatedAt().toEpochMilli() : null
                ))
                .toList();
    }

    private boolean isRetryableRemoteSearchError(RemoteRegistryException e) {
        int statusCode = e.getStatusCode();
        return statusCode == 0 || statusCode == 429 || statusCode >= 500;
    }

    private String buildRemoteSearchCacheKey(String q, int page, int limit) {
        return q.strip().toLowerCase() + "::" + page + "::" + limit;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private List<ClawHubSearchResponse.ClawHubSearchResult> mergeSearchResults(
            List<ClawHubSearchResponse.ClawHubSearchResult> localResults,
            List<ClawHubSearchResponse.ClawHubSearchResult> remoteResults,
            int limit) {
        Map<String, ClawHubSearchResponse.ClawHubSearchResult> merged = new LinkedHashMap<>();
        List<ClawHubSearchResponse.ClawHubSearchResult> ordered = new ArrayList<>();

        appendUniqueResults(ordered, merged, localResults);
        appendUniqueResults(ordered, merged, remoteResults);

        if (limit <= 0 || ordered.size() <= limit) {
            return List.copyOf(ordered);
        }
        return List.copyOf(ordered.subList(0, limit));
    }

    private void appendUniqueResults(
            List<ClawHubSearchResponse.ClawHubSearchResult> ordered,
            Map<String, ClawHubSearchResponse.ClawHubSearchResult> merged,
            List<ClawHubSearchResponse.ClawHubSearchResult> candidates) {
        for (ClawHubSearchResponse.ClawHubSearchResult candidate : candidates) {
            if (candidate == null || candidate.slug() == null || candidate.slug().isBlank()) {
                continue;
            }
            if (merged.putIfAbsent(candidate.slug(), candidate) == null) {
                ordered.add(candidate);
            }
        }
    }

    private record CachedRemoteSearchResults(
            List<ClawHubSearchResponse.ClawHubSearchResult> results,
            long fetchedAtEpochMs
    ) {
        private boolean isFresh() {
            return System.currentTimeMillis() - fetchedAtEpochMs <= REMOTE_SEARCH_CACHE_TTL.toMillis();
        }

        private boolean isUsableAsStaleFallback() {
            return System.currentTimeMillis() - fetchedAtEpochMs <= REMOTE_SEARCH_STALE_FALLBACK_TTL.toMillis();
        }
    }

    private ClawHubResolveResponse resolveFromRemote(String canonicalSlug, String version, DomainNotFoundException original) {
        RemoteRegistryClient remoteRegistryClient = remoteRegistryClientProvider.getIfAvailable();
        if (remoteRegistryClient == null) {
            throw original;
        }
        try {
            RemoteRegistryClient.ResolveResult resolved = remoteRegistryClient.resolve(canonicalSlug, version);
            ClawHubResolveResponse.VersionInfo matchVersion = resolved.matchedVersion() != null
                    ? new ClawHubResolveResponse.VersionInfo(resolved.matchedVersion())
                    : null;
            ClawHubResolveResponse.VersionInfo latestVersion = resolved.latestVersion() != null
                    ? new ClawHubResolveResponse.VersionInfo(resolved.latestVersion())
                    : matchVersion;
            return new ClawHubResolveResponse(matchVersion, latestVersion);
        } catch (RemoteRegistryException remoteException) {
            log.warn("Remote registry resolve failed for slug '{}': {}", canonicalSlug, remoteException.getMessage());
            throw original;
        }
    }

    private String mirrorThenResolveLocalDownloadLocation(String canonicalSlug, String version, DomainNotFoundException original) {
        try {
            RemoteMirrorIngestAppService.MirrorIngestResult ingestResult = remoteMirrorIngestAppService.ingestCanonical(canonicalSlug, version);
            String localVersion = ingestResult.publishResult().version().getVersion();
            return "/api/v1/skills/"
                    + ingestResult.target().namespaceSlug()
                    + "/"
                    + ingestResult.target().skillSlug()
                    + "/versions/"
                    + localVersion
                    + "/download";
        } catch (RuntimeException mirrorException) {
            log.warn("On-demand mirror failed for slug '{}': {}", canonicalSlug, mirrorException.getMessage());
            RemoteRegistryException remoteException = findRemoteRegistryException(mirrorException);
            if (remoteException != null) {
                throw translateRemoteRegistryFailure(remoteException, original, canonicalSlug);
            }
            throw new ServiceUnavailableException("error.remoteMirror.ingestFailed", canonicalSlug);
        }
    }

    private ClawHubSkillResponse getRemoteSkill(String canonicalSlug, DomainNotFoundException original) {
        RemoteRegistryClient remoteRegistryClient = remoteRegistryClientProvider.getIfAvailable();
        if (remoteRegistryClient == null) {
            throw original;
        }
        try {
            RemoteRegistryClient.SkillDetail detail = remoteRegistryClient.getSkill(canonicalSlug);
            ClawHubSkillResponse.SkillInfo skillInfo = new ClawHubSkillResponse.SkillInfo(
                    detail.canonicalSlug(),
                    detail.displayName(),
                    detail.summary(),
                    detail.tags(),
                    detail.stats(),
                    detail.createdAt() != null ? detail.createdAt().toEpochMilli() : 0,
                    detail.updatedAt() != null ? detail.updatedAt().toEpochMilli() : 0
            );
            ClawHubSkillResponse.VersionInfo versionInfo = detail.latestVersion() != null
                    ? new ClawHubSkillResponse.VersionInfo(
                            detail.latestVersion().version(),
                            detail.latestVersion().createdAt() != null ? detail.latestVersion().createdAt().toEpochMilli() : 0,
                            detail.latestVersion().changelog(),
                            detail.latestVersion().license()
                    )
                    : null;
            ClawHubSkillResponse.OwnerInfo ownerInfo = detail.owner() != null
                    ? new ClawHubSkillResponse.OwnerInfo(
                            detail.owner().handle(),
                            detail.owner().displayName(),
                            detail.owner().image()
                    )
                    : null;
            ClawHubSkillResponse.ModerationInfo moderationInfo = detail.moderation() != null
                    ? new ClawHubSkillResponse.ModerationInfo(
                            detail.moderation().suspicious(),
                            detail.moderation().malwareBlocked(),
                            detail.moderation().verdict(),
                            detail.moderation().reasonCodes().toArray(String[]::new),
                            detail.moderation().updatedAt() != null ? detail.moderation().updatedAt().toEpochMilli() : null,
                            detail.moderation().engineVersion(),
                            detail.moderation().summary()
                    )
                    : new ClawHubSkillResponse.ModerationInfo(false, false, "clean", new String[0], null, null, null);
            return new ClawHubSkillResponse(skillInfo, versionInfo, ownerInfo, moderationInfo);
        } catch (RemoteRegistryException remoteException) {
            log.warn("Remote registry getSkill failed for slug '{}': {}", canonicalSlug, remoteException.getMessage());
            throw translateRemoteRegistryFailure(remoteException, original, canonicalSlug);
        }
    }

    private RuntimeException translateRemoteRegistryFailure(RemoteRegistryException remoteException,
                                                            RuntimeException notFoundFallback,
                                                            String canonicalSlug) {
        int statusCode = remoteException.getStatusCode();
        if (statusCode == 404) {
            return notFoundFallback;
        }
        if (statusCode == 429) {
            return new TooManyRequestsException("error.remoteRegistry.rateLimited", canonicalSlug);
        }
        return new ServiceUnavailableException("error.remoteRegistry.unavailable", canonicalSlug);
    }

    private RemoteRegistryException findRemoteRegistryException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof RemoteRegistryException remoteRegistryException) {
                return remoteRegistryException;
            }
            current = current.getCause();
        }
        return null;
    }

    private ClawHubResolveResponse toResolveResponse(SkillQueryService.ResolvedVersionDTO resolved) {
        ClawHubResolveResponse.VersionInfo matchVersion = Boolean.TRUE.equals(resolved.matched()) && resolved.version() != null
                ? new ClawHubResolveResponse.VersionInfo(resolved.version())
                : null;
        ClawHubResolveResponse.VersionInfo latestVersion = resolved.version() != null
                ? new ClawHubResolveResponse.VersionInfo(resolved.version())
                : null;
        return new ClawHubResolveResponse(matchVersion, latestVersion);
    }

    private ClawHubSkillListResponse.SkillListItem toSkillListItem(SkillSummaryResponse item) {
        long createdAt = 0;
        long updatedAt = item.updatedAt() != null ? item.updatedAt().toEpochMilli() : 0;

        ClawHubSkillListResponse.SkillListItem.LatestVersion latestVersion = null;
        if (item.publishedVersion() != null) {
            latestVersion = new ClawHubSkillListResponse.SkillListItem.LatestVersion(
                    item.publishedVersion().version(),
                    updatedAt,
                    "",
                    null
            );
        }

        Map<String, Object> stats = new HashMap<>();
        if (item.downloadCount() != null) {
            stats.put("downloads", item.downloadCount());
        }
        if (item.starCount() != null) {
            stats.put("stars", item.starCount());
        }

        return new ClawHubSkillListResponse.SkillListItem(
                mapper.toCanonical(item.namespace(), item.slug()),
                item.displayName(),
                item.summary(),
                Map.of(),
                stats,
                createdAt,
                updatedAt,
                latestVersion
        );
    }

    private String determineNamespace(PlatformPrincipal principal, MultipartPackageExtractor.PublishPayload payload) {
        return "global";
    }

    private void recordCompatPublishAudit(String userId,
                                          Long versionId,
                                          String clientIp,
                                          String userAgent,
                                          String detailJson) {
        auditLogService.record(
                userId,
                "COMPAT_PUBLISH",
                "SKILL_VERSION",
                versionId,
                MDC.get("requestId"),
                clientIp,
                userAgent,
                detailJson
        );
    }

}
