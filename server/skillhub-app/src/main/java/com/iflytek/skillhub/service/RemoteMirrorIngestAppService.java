package com.iflytek.skillhub.service;

import com.iflytek.skillhub.compat.CanonicalSlugMapper;
import com.iflytek.skillhub.compat.SkillCoordinate;
import com.iflytek.skillhub.controller.support.SkillPackageArchiveExtractor;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.registry.RemoteMirrorRecord;
import com.iflytek.skillhub.domain.registry.RemoteMirrorRecordRepository;
import com.iflytek.skillhub.domain.registry.remote.RemoteRegistryClient;
import com.iflytek.skillhub.domain.registry.remote.RemoteRegistryException;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator;
import com.iflytek.skillhub.domain.skill.validation.ValidationResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Prepares and ingests remote skill packages from an upstream registry into SkillHub.
 */
@Service
public class RemoteMirrorIngestAppService {

    private static final String DEFAULT_MIRROR_PUBLISHER_ID = "remote-mirror-bot";
    private static final String DEFAULT_MIRROR_NAMESPACE_DESCRIPTION = "Mirrored from ClawHub";
    private static final String DEFAULT_SOURCE_REGISTRY = "clawhub";

    public record MirrorPreview(
            String canonicalSlug,
            String requestedVersion,
            String resolvedVersion,
            String downloadUrl,
            String bundleSha256,
            long bundleSize,
            long extractedTotalSize,
            List<PackageEntry> entries,
            SkillMetadata metadata,
            ValidationResult validation
    ) {
    }

    public record MirrorTarget(
            String namespaceSlug,
            String skillSlug,
            String publisherId
    ) {
    }

    public record MirrorIngestResult(
            MirrorPreview preview,
            MirrorTarget target,
            SkillPublishService.PublishResult publishResult
    ) {
    }

    private final ObjectProvider<RemoteRegistryClient> remoteRegistryClientProvider;
    private final SkillPackageArchiveExtractor skillPackageArchiveExtractor;
    private final SkillPackageValidator skillPackageValidator;
    private final SkillMetadataParser skillMetadataParser;
    private final SkillPublishService skillPublishService;
    private final CanonicalSlugMapper canonicalSlugMapper;
    private final NamespaceRepository namespaceRepository;
    private final NamespaceService namespaceService;
    private final RemoteMirrorRecordRepository remoteMirrorRecordRepository;

    public RemoteMirrorIngestAppService(ObjectProvider<RemoteRegistryClient> remoteRegistryClientProvider,
                                        SkillPackageArchiveExtractor skillPackageArchiveExtractor,
                                        SkillPackageValidator skillPackageValidator,
                                        SkillMetadataParser skillMetadataParser,
                                        SkillPublishService skillPublishService,
                                        CanonicalSlugMapper canonicalSlugMapper,
                                        NamespaceRepository namespaceRepository,
                                        NamespaceService namespaceService,
                                        RemoteMirrorRecordRepository remoteMirrorRecordRepository) {
        this.remoteRegistryClientProvider = remoteRegistryClientProvider;
        this.skillPackageArchiveExtractor = skillPackageArchiveExtractor;
        this.skillPackageValidator = skillPackageValidator;
        this.skillMetadataParser = skillMetadataParser;
        this.skillPublishService = skillPublishService;
        this.canonicalSlugMapper = canonicalSlugMapper;
        this.namespaceRepository = namespaceRepository;
        this.namespaceService = namespaceService;
        this.remoteMirrorRecordRepository = remoteMirrorRecordRepository;
    }

    public MirrorPreview prepareMirror(String canonicalSlug, String version) {
        String requestedVersion = normalizeVersion(version);
        RemoteRegistryClient remoteRegistryClient = requireRemoteRegistryClient();

        try {
            RemoteRegistryClient.DownloadInfo downloadInfo = remoteRegistryClient.resolveDownload(canonicalSlug, requestedVersion);
            byte[] bundleBytes = remoteRegistryClient.downloadBundle(downloadInfo.downloadUri());
            List<PackageEntry> entries = extractEntries(bundleBytes);
            ValidationResult validation = skillPackageValidator.validate(entries);
            SkillMetadata metadata = tryParseMetadata(entries);
            long extractedTotalSize = entries.stream().mapToLong(PackageEntry::size).sum();
            String resolvedVersion = metadata != null && metadata.version() != null && !metadata.version().isBlank()
                    ? metadata.version()
                    : requestedVersion;

            return new MirrorPreview(
                    canonicalSlug,
                    requestedVersion,
                    resolvedVersion,
                    downloadInfo.downloadUri().toString(),
                    sha256Hex(bundleBytes),
                    bundleBytes.length,
                    extractedTotalSize,
                    List.copyOf(entries),
                    metadata,
                    validation
            );
        } catch (RemoteRegistryException e) {
            throw e;
        } catch (DomainBadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to prepare remote mirror for " + canonicalSlug, e);
        }
    }

    public MirrorIngestResult ingest(String canonicalSlug,
                                     String version,
                                     String namespaceSlug,
                                     String publisherId,
                                     SkillVisibility visibility) {
        return ingest(canonicalSlug, version, namespaceSlug, publisherId, visibility, null);
    }

    public MirrorIngestResult ingest(String canonicalSlug,
                                     String version,
                                     String namespaceSlug,
                                     String publisherId,
                                     SkillVisibility visibility,
                                     String explicitSkillSlug) {
        MirrorPreview preview = prepareMirror(canonicalSlug, version);
        if (!preview.validation().passed()) {
            throw new DomainBadRequestException(
                    "error.skill.publish.package.invalid",
                    String.join(", ", preview.validation().errors())
            );
        }

        MirrorTarget target = new MirrorTarget(namespaceSlug, explicitSkillSlug, publisherId);
        SkillPublishService.PublishResult publishResult = skillPublishService.publishMirroredEntries(
                target.namespaceSlug(),
                preview.entries(),
                target.publisherId(),
                visibility,
                target.skillSlug()
        );
        persistMirrorRecord(preview, publishResult, canonicalSlug);
        return new MirrorIngestResult(preview, target, publishResult);
    }

    public MirrorIngestResult ingestCanonical(String canonicalSlug, String version) {
        SkillCoordinate coordinate = canonicalSlugMapper.fromCanonical(canonicalSlug);
        String publisherId = DEFAULT_MIRROR_PUBLISHER_ID;
        Namespace namespace = ensureMirrorNamespace(coordinate.namespace(), publisherId);
        return ingest(canonicalSlug, version, namespace.getSlug(), publisherId, SkillVisibility.PUBLIC, coordinate.slug());
    }

    private List<PackageEntry> extractEntries(byte[] bundleBytes) {
        try {
            return skillPackageArchiveExtractor.extract(bundleBytes);
        } catch (IllegalArgumentException | IOException e) {
            throw new DomainBadRequestException("error.skill.publish.package.invalid", e.getMessage());
        }
    }

    private SkillMetadata tryParseMetadata(List<PackageEntry> entries) {
        return entries.stream()
                .filter(entry -> "SKILL.md".equals(entry.path()))
                .findFirst()
                .map(entry -> {
                    try {
                        return skillMetadataParser.parse(new String(entry.content(), StandardCharsets.UTF_8));
                    } catch (RuntimeException e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    private void persistMirrorRecord(MirrorPreview preview,
                                     SkillPublishService.PublishResult publishResult,
                                     String canonicalSlug) {
        SkillCoordinate coordinate = canonicalSlugMapper.fromCanonical(canonicalSlug);
        RemoteMirrorRecord record = new RemoteMirrorRecord(
                publishResult.skillId(),
                publishResult.version().getId(),
                DEFAULT_SOURCE_REGISTRY,
                canonicalSlug,
                coordinate.namespace(),
                coordinate.slug()
        );
        record.setRequestedVersion(preview.requestedVersion());
        record.setRemoteVersion(preview.resolvedVersion());
        record.setBundleSha256(preview.bundleSha256());
        record.setDownloadUrl(preview.downloadUrl());
        remoteMirrorRecordRepository.save(record);
    }

    private Namespace ensureMirrorNamespace(String namespaceSlug, String publisherId) {
        return namespaceRepository.findBySlug(namespaceSlug)
                .orElseGet(() -> namespaceService.createNamespace(
                        namespaceSlug,
                        namespaceSlug,
                        DEFAULT_MIRROR_NAMESPACE_DESCRIPTION,
                        publisherId
                ));
    }

    private RemoteRegistryClient requireRemoteRegistryClient() {
        RemoteRegistryClient remoteRegistryClient = remoteRegistryClientProvider.getIfAvailable();
        if (remoteRegistryClient == null) {
            throw new IllegalStateException("Remote registry client is not configured");
        }
        return remoteRegistryClient;
    }

    private String normalizeVersion(String version) {
        return version == null || version.isBlank() ? "latest" : version;
    }

    private String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate remote bundle SHA-256", e);
        }
    }
}
