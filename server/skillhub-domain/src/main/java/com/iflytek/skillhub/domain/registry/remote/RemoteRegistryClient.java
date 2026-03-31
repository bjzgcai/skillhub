package com.iflytek.skillhub.domain.registry.remote;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Domain contract for querying external skill registries without leaking
 * transport-specific details into application services.
 */
public interface RemoteRegistryClient {

    SearchResult search(String query, int page, int limit);

    SkillDetail getSkill(String canonicalSlug);

    ResolveResult resolve(String canonicalSlug, String version);

    DownloadInfo resolveDownload(String canonicalSlug, String version);

    byte[] downloadBundle(URI downloadUri);

    record SearchResult(List<SearchHit> results) {
    }

    record SearchHit(
            String canonicalSlug,
            String displayName,
            String summary,
            String version,
            double score,
            Instant updatedAt
    ) {
    }

    record SkillDetail(
            String canonicalSlug,
            String displayName,
            String summary,
            Map<String, String> tags,
            Map<String, Object> stats,
            Instant createdAt,
            Instant updatedAt,
            VersionInfo latestVersion,
            OwnerInfo owner,
            ModerationInfo moderation,
            Map<String, Object> metadata
    ) {
    }

    record VersionInfo(
            String version,
            Instant createdAt,
            String changelog,
            String license
    ) {
    }

    record OwnerInfo(
            String handle,
            String displayName,
            String image
    ) {
    }

    record ModerationInfo(
            boolean suspicious,
            boolean malwareBlocked,
            String verdict,
            List<String> reasonCodes,
            Instant updatedAt,
            String engineVersion,
            String summary
    ) {
    }

    record ResolveResult(
            String matchedVersion,
            String latestVersion
    ) {
    }

    record DownloadInfo(
            String canonicalSlug,
            String version,
            URI downloadUri
    ) {
    }
}
