package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.compat.CanonicalSlugMapper;
import com.iflytek.skillhub.config.SkillPublishProperties;
import com.iflytek.skillhub.controller.support.SkillPackageArchiveExtractor;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.registry.RemoteMirrorRecordRepository;
import com.iflytek.skillhub.domain.registry.remote.RemoteRegistryClient;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class RemoteMirrorIngestAppServiceTest {

    @Test
    void prepareMirror_downloadsExtractsAndParsesMetadata() throws Exception {
        RemoteRegistryClient remoteRegistryClient = mock(RemoteRegistryClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteRegistryClient> remoteRegistryClientProvider = mock(ObjectProvider.class);
        SkillPublishService skillPublishService = mock(SkillPublishService.class);
        NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
        NamespaceService namespaceService = mock(NamespaceService.class);
        RemoteMirrorRecordRepository remoteMirrorRecordRepository = mock(RemoteMirrorRecordRepository.class);

        when(remoteRegistryClientProvider.getIfAvailable()).thenReturn(remoteRegistryClient);
        when(remoteRegistryClient.resolveDownload("calendar", "latest"))
                .thenReturn(new RemoteRegistryClient.DownloadInfo(
                        "calendar",
                        "latest",
                        URI.create("https://clawhub.ai/bundles/calendar-latest.zip")
                ));
        when(remoteRegistryClient.downloadBundle(URI.create("https://clawhub.ai/bundles/calendar-latest.zip")))
                .thenReturn(zipOf(
                        entry("calendar/SKILL.md", """
                                ---
                                name: Calendar
                                description: Remote calendar skill
                                version: 2.0.0
                                ---
                                # Calendar
                                """),
                        entry("calendar/README.md", "hello")
                ));

        RemoteMirrorIngestAppService service = newService(
                remoteRegistryClientProvider,
                skillPublishService,
                namespaceRepository,
                namespaceService,
                remoteMirrorRecordRepository
        );

        var preview = service.prepareMirror("calendar", "latest");

        assertThat(preview.canonicalSlug()).isEqualTo("calendar");
        assertThat(preview.requestedVersion()).isEqualTo("latest");
        assertThat(preview.resolvedVersion()).isEqualTo("2.0.0");
        assertThat(preview.downloadUrl()).isEqualTo("https://clawhub.ai/bundles/calendar-latest.zip");
        assertThat(preview.entries()).hasSize(2);
        assertThat(preview.entries().getFirst().path()).isEqualTo("SKILL.md");
        assertThat(preview.metadata()).isNotNull();
        assertThat(preview.metadata().name()).isEqualTo("Calendar");
        assertThat(preview.validation().passed()).isTrue();
        assertThat(preview.bundleSha256()).hasSize(64);
    }

    @Test
    void ingest_publishesValidatedMirroredEntriesWithExplicitSlugAndPersistsMirrorRecord() throws Exception {
        RemoteRegistryClient remoteRegistryClient = mock(RemoteRegistryClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteRegistryClient> remoteRegistryClientProvider = mock(ObjectProvider.class);
        SkillPublishService skillPublishService = mock(SkillPublishService.class);
        NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
        NamespaceService namespaceService = mock(NamespaceService.class);
        RemoteMirrorRecordRepository remoteMirrorRecordRepository = mock(RemoteMirrorRecordRepository.class);

        when(remoteRegistryClientProvider.getIfAvailable()).thenReturn(remoteRegistryClient);
        when(remoteRegistryClient.resolveDownload("calendar", "latest"))
                .thenReturn(new RemoteRegistryClient.DownloadInfo(
                        "calendar",
                        "latest",
                        URI.create("https://clawhub.ai/bundles/calendar-latest.zip")
                ));
        when(remoteRegistryClient.downloadBundle(URI.create("https://clawhub.ai/bundles/calendar-latest.zip")))
                .thenReturn(zipOf(
                        entry("calendar/SKILL.md", """
                                ---
                                name: Calendar
                                description: Remote calendar skill
                                version: 2.0.0
                                ---
                                # Calendar
                                """),
                        entry("calendar/README.md", "hello")
                ));
        SkillVersion publishedVersion = new SkillVersion(1L, "2.0.0", "mirror-bot");
        when(skillPublishService.publishMirroredEntries(eq("global"), anyList(), eq("mirror-bot"), eq(SkillVisibility.PUBLIC), eq("calendar")))
                .thenReturn(new SkillPublishService.PublishResult(1L, "calendar", publishedVersion));
        when(remoteMirrorRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RemoteMirrorIngestAppService service = newService(
                remoteRegistryClientProvider,
                skillPublishService,
                namespaceRepository,
                namespaceService,
                remoteMirrorRecordRepository
        );

        var result = service.ingest("calendar", "latest", "global", "mirror-bot", SkillVisibility.PUBLIC, "calendar");

        assertThat(result.preview().metadata()).isNotNull();
        assertThat(result.target().namespaceSlug()).isEqualTo("global");
        assertThat(result.target().skillSlug()).isEqualTo("calendar");
        assertThat(result.publishResult().slug()).isEqualTo("calendar");
        verify(skillPublishService).publishMirroredEntries("global", result.preview().entries(), "mirror-bot", SkillVisibility.PUBLIC, "calendar");
        verify(remoteMirrorRecordRepository).save(any());
    }

    @Test
    void ingestCanonical_ensuresNamespaceAndPreservesCanonicalSlug() throws Exception {
        RemoteRegistryClient remoteRegistryClient = mock(RemoteRegistryClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteRegistryClient> remoteRegistryClientProvider = mock(ObjectProvider.class);
        SkillPublishService skillPublishService = mock(SkillPublishService.class);
        NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
        NamespaceService namespaceService = mock(NamespaceService.class);
        RemoteMirrorRecordRepository remoteMirrorRecordRepository = mock(RemoteMirrorRecordRepository.class);

        when(remoteRegistryClientProvider.getIfAvailable()).thenReturn(remoteRegistryClient);
        when(namespaceRepository.findBySlug("team-ai")).thenReturn(Optional.empty());
        when(namespaceService.createNamespace("team-ai", "team-ai", "Mirrored from ClawHub", "remote-mirror-bot"))
                .thenReturn(new Namespace("team-ai", "team-ai", "remote-mirror-bot"));
        when(remoteRegistryClient.resolveDownload("team-ai--calendar", "latest"))
                .thenReturn(new RemoteRegistryClient.DownloadInfo(
                        "team-ai--calendar",
                        "latest",
                        URI.create("https://clawhub.ai/bundles/team-ai--calendar-latest.zip")
                ));
        when(remoteRegistryClient.downloadBundle(URI.create("https://clawhub.ai/bundles/team-ai--calendar-latest.zip")))
                .thenReturn(zipOf(
                        entry("team-ai--calendar/SKILL.md", """
                                ---
                                name: Calendar
                                description: Remote calendar skill
                                version: 2.0.0
                                ---
                                # Calendar
                                """),
                        entry("team-ai--calendar/README.md", "hello")
                ));
        SkillVersion publishedVersion = new SkillVersion(1L, "2.0.0", "remote-mirror-bot");
        when(skillPublishService.publishMirroredEntries(eq("team-ai"), anyList(), eq("remote-mirror-bot"), eq(SkillVisibility.PUBLIC), eq("calendar")))
                .thenReturn(new SkillPublishService.PublishResult(1L, "calendar", publishedVersion));
        when(remoteMirrorRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RemoteMirrorIngestAppService service = newService(
                remoteRegistryClientProvider,
                skillPublishService,
                namespaceRepository,
                namespaceService,
                remoteMirrorRecordRepository
        );

        var result = service.ingestCanonical("team-ai--calendar", "latest");

        assertThat(result.target().namespaceSlug()).isEqualTo("team-ai");
        assertThat(result.target().skillSlug()).isEqualTo("calendar");
        verify(namespaceService).createNamespace("team-ai", "team-ai", "Mirrored from ClawHub", "remote-mirror-bot");
        verify(skillPublishService).publishMirroredEntries("team-ai", result.preview().entries(), "remote-mirror-bot", SkillVisibility.PUBLIC, "calendar");
        verify(remoteMirrorRecordRepository).save(any());
    }

    @Test
    void ingest_rejectsInvalidRemotePackageBeforePublish() throws Exception {
        RemoteRegistryClient remoteRegistryClient = mock(RemoteRegistryClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteRegistryClient> remoteRegistryClientProvider = mock(ObjectProvider.class);
        SkillPublishService skillPublishService = mock(SkillPublishService.class);
        NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
        NamespaceService namespaceService = mock(NamespaceService.class);
        RemoteMirrorRecordRepository remoteMirrorRecordRepository = mock(RemoteMirrorRecordRepository.class);

        when(remoteRegistryClientProvider.getIfAvailable()).thenReturn(remoteRegistryClient);
        when(remoteRegistryClient.resolveDownload("calendar", "latest"))
                .thenReturn(new RemoteRegistryClient.DownloadInfo(
                        "calendar",
                        "latest",
                        URI.create("https://clawhub.ai/bundles/calendar-latest.zip")
                ));
        when(remoteRegistryClient.downloadBundle(URI.create("https://clawhub.ai/bundles/calendar-latest.zip")))
                .thenReturn(zipOf(entry("calendar/README.md", "hello")));

        RemoteMirrorIngestAppService service = newService(
                remoteRegistryClientProvider,
                skillPublishService,
                namespaceRepository,
                namespaceService,
                remoteMirrorRecordRepository
        );

        assertThatThrownBy(() -> service.ingest("calendar", "latest", "global", "mirror-bot", SkillVisibility.PUBLIC))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.skill.publish.package.invalid");
    }

    private RemoteMirrorIngestAppService newService(ObjectProvider<RemoteRegistryClient> remoteRegistryClientProvider,
                                                    SkillPublishService skillPublishService,
                                                    NamespaceRepository namespaceRepository,
                                                    NamespaceService namespaceService,
                                                    RemoteMirrorRecordRepository remoteMirrorRecordRepository) {
        return new RemoteMirrorIngestAppService(
                remoteRegistryClientProvider,
                new SkillPackageArchiveExtractor(new SkillPublishProperties()),
                new SkillPackageValidator(new SkillMetadataParser()),
                new SkillMetadataParser(),
                skillPublishService,
                new CanonicalSlugMapper(),
                namespaceRepository,
                namespaceService,
                remoteMirrorRecordRepository
        );
    }

    private static byte[] zipOf(ZipFileEntry... entries) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (ZipFileEntry entry : entries) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.path()));
                zipOutputStream.write(entry.content().getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
        }
        return outputStream.toByteArray();
    }

    private static ZipFileEntry entry(String path, String content) {
        return new ZipFileEntry(path, content);
    }

    private record ZipFileEntry(String path, String content) {
    }
}
