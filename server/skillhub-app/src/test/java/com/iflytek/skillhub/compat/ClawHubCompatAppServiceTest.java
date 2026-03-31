package com.iflytek.skillhub.compat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.controller.support.MultipartPackageExtractor;
import com.iflytek.skillhub.controller.support.ZipPackageExtractor;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.registry.remote.RemoteRegistryClient;
import com.iflytek.skillhub.domain.registry.remote.RemoteRegistryException;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.domain.skill.validation.ValidationResult;
import com.iflytek.skillhub.domain.social.SkillStarService;
import com.iflytek.skillhub.dto.SkillLifecycleVersionResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.exception.TooManyRequestsException;
import com.iflytek.skillhub.service.RemoteMirrorIngestAppService;
import com.iflytek.skillhub.service.SkillLifecycleAppService;
import com.iflytek.skillhub.service.SkillSearchAppService;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ClawHubCompatAppServiceTest {

    @Test
    void search_mergesRemoteResultsAfterLocalAndDeduplicatesBySlug() {
        SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        SkillPublishService skillPublishService = mock(SkillPublishService.class);
        ZipPackageExtractor zipPackageExtractor = mock(ZipPackageExtractor.class);
        MultipartPackageExtractor multipartPackageExtractor = mock(MultipartPackageExtractor.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        SkillStarService skillStarService = mock(SkillStarService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteRegistryClient> remoteRegistryClientProvider = mock(ObjectProvider.class);
        RemoteRegistryClient remoteRegistryClient = mock(RemoteRegistryClient.class);
        RemoteMirrorIngestAppService remoteMirrorIngestAppService = mock(RemoteMirrorIngestAppService.class);

        when(remoteRegistryClientProvider.getIfAvailable()).thenReturn(remoteRegistryClient);
        when(skillSearchAppService.search("calendar", null, "relevance", 0, 5, null, Map.of()))
                .thenReturn(new SkillSearchAppService.SearchResponse(
                        List.of(new SkillSummaryResponse(
                                1L,
                                "calendar",
                                "Calendar",
                                "Local calendar skill",
                                "ACTIVE",
                                100L,
                                5,
                                BigDecimal.valueOf(4.5),
                                2,
                                "global",
                                Instant.parse("2026-03-24T09:00:00Z"),
                                false,
                                new SkillLifecycleVersionResponse(11L, "1.0.0", "PUBLISHED"),
                                new SkillLifecycleVersionResponse(11L, "1.0.0", "PUBLISHED"),
                                null,
                                "PUBLISHED"
                        )),
                        1,
                        0,
                        5
                ));
        when(remoteRegistryClient.search("calendar", 0, 5))
                .thenReturn(new RemoteRegistryClient.SearchResult(List.of(
                        new RemoteRegistryClient.SearchHit(
                                "calendar",
                                "Calendar",
                                "Remote duplicate",
                                "1.1.0",
                                9.5,
                                Instant.parse("2026-03-24T09:05:00Z")
                        ),
                        new RemoteRegistryClient.SearchHit(
                                "remote-calendar",
                                "Remote Calendar",
                                "Remote-only calendar skill",
                                "2.0.0",
                                8.2,
                                Instant.parse("2026-03-24T09:06:00Z")
                        )
                )));

        ClawHubCompatAppService service = newService(
                skillSearchAppService,
                skillQueryService,
                skillPublishService,
                zipPackageExtractor,
                multipartPackageExtractor,
                auditLogService,
                compatSkillLookupService,
                skillStarService,
                remoteRegistryClientProvider,
                remoteMirrorIngestAppService
        );

        var response = service.search("calendar", 0, 5, null, Map.of());

        assertThat(response.results()).hasSize(2);
        assertThat(response.results().get(0).slug()).isEqualTo("calendar");
        assertThat(response.results().get(0).summary()).isEqualTo("Local calendar skill");
        assertThat(response.results().get(1).slug()).isEqualTo("remote-calendar");
        assertThat(response.results().get(1).summary()).isEqualTo("Remote-only calendar skill");
    }

    @Test
    void search_fallsBackToLocalResultsWhenRemoteSearchFails() {
        SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        SkillPublishService skillPublishService = mock(SkillPublishService.class);
        ZipPackageExtractor zipPackageExtractor = mock(ZipPackageExtractor.class);
        MultipartPackageExtractor multipartPackageExtractor = mock(MultipartPackageExtractor.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        SkillStarService skillStarService = mock(SkillStarService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteRegistryClient> remoteRegistryClientProvider = mock(ObjectProvider.class);
        RemoteRegistryClient remoteRegistryClient = mock(RemoteRegistryClient.class);
        RemoteMirrorIngestAppService remoteMirrorIngestAppService = mock(RemoteMirrorIngestAppService.class);

        when(remoteRegistryClientProvider.getIfAvailable()).thenReturn(remoteRegistryClient);
        when(skillSearchAppService.search("calendar", null, "relevance", 0, 5, null, Map.of()))
                .thenReturn(new SkillSearchAppService.SearchResponse(
                        List.of(new SkillSummaryResponse(
                                1L,
                                "calendar",
                                "Calendar",
                                "Local calendar skill",
                                "ACTIVE",
                                100L,
                                5,
                                BigDecimal.valueOf(4.5),
                                2,
                                "global",
                                Instant.parse("2026-03-24T09:00:00Z"),
                                false,
                                new SkillLifecycleVersionResponse(11L, "1.0.0", "PUBLISHED"),
                                new SkillLifecycleVersionResponse(11L, "1.0.0", "PUBLISHED"),
                                null,
                                "PUBLISHED"
                        )),
                        1,
                        0,
                        5
                ));
        when(remoteRegistryClient.search("calendar", 0, 5))
                .thenThrow(new RemoteRegistryException("boom"));

        ClawHubCompatAppService service = newService(
                skillSearchAppService,
                skillQueryService,
                skillPublishService,
                zipPackageExtractor,
                multipartPackageExtractor,
                auditLogService,
                compatSkillLookupService,
                skillStarService,
                remoteRegistryClientProvider,
                remoteMirrorIngestAppService
        );

        var response = service.search("calendar", 0, 5, null, Map.of());

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().getFirst().slug()).isEqualTo("calendar");
    }

    @Test
    void search_usesCachedRemoteResultsForRepeatedQueries() {
        SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        SkillPublishService skillPublishService = mock(SkillPublishService.class);
        ZipPackageExtractor zipPackageExtractor = mock(ZipPackageExtractor.class);
        MultipartPackageExtractor multipartPackageExtractor = mock(MultipartPackageExtractor.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        SkillStarService skillStarService = mock(SkillStarService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteRegistryClient> remoteRegistryClientProvider = mock(ObjectProvider.class);
        RemoteRegistryClient remoteRegistryClient = mock(RemoteRegistryClient.class);
        RemoteMirrorIngestAppService remoteMirrorIngestAppService = mock(RemoteMirrorIngestAppService.class);

        when(remoteRegistryClientProvider.getIfAvailable()).thenReturn(remoteRegistryClient);
        when(skillSearchAppService.search("git", null, "relevance", 0, 10, null, Map.of()))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(), 0, 0, 10));
        when(remoteRegistryClient.search("git", 0, 10))
                .thenReturn(new RemoteRegistryClient.SearchResult(List.of(
                        new RemoteRegistryClient.SearchHit(
                                "git",
                                "Git",
                                "Git workflow helper",
                                null,
                                3.5,
                                Instant.parse("2026-03-25T01:00:00Z")
                        )
                )));

        ClawHubCompatAppService service = newService(
                skillSearchAppService,
                skillQueryService,
                skillPublishService,
                zipPackageExtractor,
                multipartPackageExtractor,
                auditLogService,
                compatSkillLookupService,
                skillStarService,
                remoteRegistryClientProvider,
                remoteMirrorIngestAppService
        );

        var first = service.search("git", 0, 10, null, Map.of());
        var second = service.search("git", 0, 10, null, Map.of());

        assertThat(first.results()).hasSize(1);
        assertThat(second.results()).hasSize(1);
        assertThat(second.results().getFirst().slug()).isEqualTo("git");
        verify(remoteRegistryClient, times(1)).search("git", 0, 10);
    }

    @Test
    void search_retriesOnceForRetryableRemoteErrors() {
        SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        SkillPublishService skillPublishService = mock(SkillPublishService.class);
        ZipPackageExtractor zipPackageExtractor = mock(ZipPackageExtractor.class);
        MultipartPackageExtractor multipartPackageExtractor = mock(MultipartPackageExtractor.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        SkillStarService skillStarService = mock(SkillStarService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteRegistryClient> remoteRegistryClientProvider = mock(ObjectProvider.class);
        RemoteRegistryClient remoteRegistryClient = mock(RemoteRegistryClient.class);
        RemoteMirrorIngestAppService remoteMirrorIngestAppService = mock(RemoteMirrorIngestAppService.class);

        when(remoteRegistryClientProvider.getIfAvailable()).thenReturn(remoteRegistryClient);
        when(skillSearchAppService.search("git", null, "relevance", 0, 10, null, Map.of()))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(), 0, 0, 10));
        when(remoteRegistryClient.search("git", 0, 10))
                .thenThrow(new RemoteRegistryException(429, "", "rate limited"))
                .thenReturn(new RemoteRegistryClient.SearchResult(List.of(
                        new RemoteRegistryClient.SearchHit(
                                "git",
                                "Git",
                                "Git workflow helper",
                                null,
                                3.5,
                                Instant.parse("2026-03-25T01:00:00Z")
                        )
                )));

        ClawHubCompatAppService service = newService(
                skillSearchAppService,
                skillQueryService,
                skillPublishService,
                zipPackageExtractor,
                multipartPackageExtractor,
                auditLogService,
                compatSkillLookupService,
                skillStarService,
                remoteRegistryClientProvider,
                remoteMirrorIngestAppService
        );

        var response = service.search("git", 0, 10, null, Map.of());

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().getFirst().slug()).isEqualTo("git");
        verify(remoteRegistryClient, times(2)).search("git", 0, 10);
    }

    @Test
    void getSkill_fallsBackToRemoteWhenLocalSkillIsMissing() {
        SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        SkillPublishService skillPublishService = mock(SkillPublishService.class);
        ZipPackageExtractor zipPackageExtractor = mock(ZipPackageExtractor.class);
        MultipartPackageExtractor multipartPackageExtractor = mock(MultipartPackageExtractor.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        SkillStarService skillStarService = mock(SkillStarService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteRegistryClient> remoteRegistryClientProvider = mock(ObjectProvider.class);
        RemoteRegistryClient remoteRegistryClient = mock(RemoteRegistryClient.class);
        RemoteMirrorIngestAppService remoteMirrorIngestAppService = mock(RemoteMirrorIngestAppService.class);

        when(remoteRegistryClientProvider.getIfAvailable()).thenReturn(remoteRegistryClient);
        when(compatSkillLookupService.resolveVisible("global", "calendar", null))
                .thenThrow(new DomainNotFoundException("error.skill.notFound", "calendar"));
        when(remoteRegistryClient.getSkill("calendar"))
                .thenReturn(new RemoteRegistryClient.SkillDetail(
                        "calendar",
                        "Calendar",
                        "Remote calendar skill",
                        Map.of("latest", "2.0.0"),
                        Map.of("downloads", 321L),
                        Instant.parse("2026-03-24T08:00:00Z"),
                        Instant.parse("2026-03-24T09:00:00Z"),
                        new RemoteRegistryClient.VersionInfo("2.0.0", Instant.parse("2026-03-24T08:30:00Z"), "Remote release", "MIT"),
                        new RemoteRegistryClient.OwnerInfo("robot", "Robot", "https://example.com/avatar.png"),
                        new RemoteRegistryClient.ModerationInfo(false, false, "clean", List.of(), Instant.parse("2026-03-24T09:10:00Z"), "1.0", "clean"),
                        Map.of()
                ));

        ClawHubCompatAppService service = newService(
                skillSearchAppService,
                skillQueryService,
                skillPublishService,
                zipPackageExtractor,
                multipartPackageExtractor,
                auditLogService,
                compatSkillLookupService,
                skillStarService,
                remoteRegistryClientProvider,
                remoteMirrorIngestAppService
        );

        var response = service.getSkill("calendar", null);

        assertThat(response.skill()).isNotNull();
        assertThat(response.skill().slug()).isEqualTo("calendar");
        assertThat(response.skill().summary()).isEqualTo("Remote calendar skill");
        assertThat(response.latestVersion()).isNotNull();
        assertThat(response.latestVersion().version()).isEqualTo("2.0.0");
        assertThat(response.owner()).isNotNull();
        assertThat(response.owner().handle()).isEqualTo("robot");
    }

    @Test
    void getSkill_fallsBackToCleanModerationWhenRemoteModerationIsMissing() {
        SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        SkillPublishService skillPublishService = mock(SkillPublishService.class);
        ZipPackageExtractor zipPackageExtractor = mock(ZipPackageExtractor.class);
        MultipartPackageExtractor multipartPackageExtractor = mock(MultipartPackageExtractor.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        SkillStarService skillStarService = mock(SkillStarService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteRegistryClient> remoteRegistryClientProvider = mock(ObjectProvider.class);
        RemoteRegistryClient remoteRegistryClient = mock(RemoteRegistryClient.class);
        RemoteMirrorIngestAppService remoteMirrorIngestAppService = mock(RemoteMirrorIngestAppService.class);

        when(remoteRegistryClientProvider.getIfAvailable()).thenReturn(remoteRegistryClient);
        when(compatSkillLookupService.resolveVisible("global", "obsidian-sync", null))
                .thenThrow(new DomainNotFoundException("error.skill.notFound", "obsidian-sync"));
        when(remoteRegistryClient.getSkill("obsidian-sync"))
                .thenReturn(new RemoteRegistryClient.SkillDetail(
                        "obsidian-sync",
                        "Obsidian Sync",
                        "Remote obsidian sync skill",
                        Map.of("latest", "1.0.0"),
                        Map.of("downloads", 123L),
                        Instant.parse("2026-03-24T08:00:00Z"),
                        Instant.parse("2026-03-24T09:00:00Z"),
                        new RemoteRegistryClient.VersionInfo("1.0.0", Instant.parse("2026-03-24T08:30:00Z"), "Remote release", "MIT"),
                        new RemoteRegistryClient.OwnerInfo("andybold", "AndyBold", "https://example.com/avatar.png"),
                        null,
                        Map.of()
                ));

        ClawHubCompatAppService service = newService(
                skillSearchAppService,
                skillQueryService,
                skillPublishService,
                zipPackageExtractor,
                multipartPackageExtractor,
                auditLogService,
                compatSkillLookupService,
                skillStarService,
                remoteRegistryClientProvider,
                remoteMirrorIngestAppService
        );

        var response = service.getSkill("obsidian-sync", null);

        assertThat(response.moderation()).isNotNull();
        assertThat(response.moderation().verdict()).isEqualTo("clean");
        assertThat(response.moderation().isSuspicious()).isFalse();
        assertThat(response.moderation().isMalwareBlocked()).isFalse();
    }

    @Test
    void resolve_fallsBackToRemoteWhenLocalSkillIsMissing() {
        SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        SkillPublishService skillPublishService = mock(SkillPublishService.class);
        ZipPackageExtractor zipPackageExtractor = mock(ZipPackageExtractor.class);
        MultipartPackageExtractor multipartPackageExtractor = mock(MultipartPackageExtractor.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        SkillStarService skillStarService = mock(SkillStarService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteRegistryClient> remoteRegistryClientProvider = mock(ObjectProvider.class);
        RemoteRegistryClient remoteRegistryClient = mock(RemoteRegistryClient.class);
        RemoteMirrorIngestAppService remoteMirrorIngestAppService = mock(RemoteMirrorIngestAppService.class);

        when(remoteRegistryClientProvider.getIfAvailable()).thenReturn(remoteRegistryClient);
        when(compatSkillLookupService.resolveVisible("global", "calendar", null))
                .thenThrow(new DomainNotFoundException("error.skill.notFound", "calendar"));
        when(remoteRegistryClient.resolve("calendar", "latest"))
                .thenReturn(new RemoteRegistryClient.ResolveResult("1.4.0", "1.4.0"));

        ClawHubCompatAppService service = newService(
                skillSearchAppService,
                skillQueryService,
                skillPublishService,
                zipPackageExtractor,
                multipartPackageExtractor,
                auditLogService,
                compatSkillLookupService,
                skillStarService,
                remoteRegistryClientProvider,
                remoteMirrorIngestAppService
        );

        var resolveResponse = service.resolve("calendar", "latest", null, Map.of());

        assertThat(resolveResponse.match()).isNotNull();
        assertThat(resolveResponse.match().version()).isEqualTo("1.4.0");
        assertThat(resolveResponse.latestVersion()).isNotNull();
        assertThat(resolveResponse.latestVersion().version()).isEqualTo("1.4.0");
    }

    @Test
    void download_usesOnDemandMirrorWhenLocalSkillIsMissing() {
        SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        SkillPublishService skillPublishService = mock(SkillPublishService.class);
        ZipPackageExtractor zipPackageExtractor = mock(ZipPackageExtractor.class);
        MultipartPackageExtractor multipartPackageExtractor = mock(MultipartPackageExtractor.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        SkillStarService skillStarService = mock(SkillStarService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteRegistryClient> remoteRegistryClientProvider = mock(ObjectProvider.class);
        RemoteRegistryClient remoteRegistryClient = mock(RemoteRegistryClient.class);
        RemoteMirrorIngestAppService remoteMirrorIngestAppService = mock(RemoteMirrorIngestAppService.class);

        when(remoteRegistryClientProvider.getIfAvailable()).thenReturn(remoteRegistryClient);
        when(compatSkillLookupService.resolveVisible("global", "calendar", null))
                .thenThrow(new DomainNotFoundException("error.skill.notFound", "calendar"));
        when(remoteMirrorIngestAppService.ingestCanonical("calendar", "latest"))
                .thenReturn(mirrorIngestResult("global", "calendar", "1.4.0"));

        ClawHubCompatAppService service = newService(
                skillSearchAppService,
                skillQueryService,
                skillPublishService,
                zipPackageExtractor,
                multipartPackageExtractor,
                auditLogService,
                compatSkillLookupService,
                skillStarService,
                remoteRegistryClientProvider,
                remoteMirrorIngestAppService
        );

        var downloadLocation = service.downloadLocationByPath("calendar", "latest");

        assertThat(downloadLocation).isEqualTo("/api/v1/skills/global/calendar/versions/1.4.0/download");
    }

    @Test
    void download_returnsTooManyRequestsWhenMirrorHitsUpstreamRateLimit() {
        SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        SkillPublishService skillPublishService = mock(SkillPublishService.class);
        ZipPackageExtractor zipPackageExtractor = mock(ZipPackageExtractor.class);
        MultipartPackageExtractor multipartPackageExtractor = mock(MultipartPackageExtractor.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        SkillStarService skillStarService = mock(SkillStarService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteRegistryClient> remoteRegistryClientProvider = mock(ObjectProvider.class);
        RemoteRegistryClient remoteRegistryClient = mock(RemoteRegistryClient.class);
        RemoteMirrorIngestAppService remoteMirrorIngestAppService = mock(RemoteMirrorIngestAppService.class);

        when(remoteRegistryClientProvider.getIfAvailable()).thenReturn(remoteRegistryClient);
        when(compatSkillLookupService.resolveVisible("global", "calendar", null))
                .thenThrow(new DomainNotFoundException("error.skill.notFound", "calendar"));
        when(remoteMirrorIngestAppService.ingestCanonical("calendar", "latest"))
                .thenThrow(new RemoteRegistryException(429, "", "rate limited"));

        ClawHubCompatAppService service = newService(
                skillSearchAppService,
                skillQueryService,
                skillPublishService,
                zipPackageExtractor,
                multipartPackageExtractor,
                auditLogService,
                compatSkillLookupService,
                skillStarService,
                remoteRegistryClientProvider,
                remoteMirrorIngestAppService
        );

        assertThatThrownBy(() -> service.downloadLocationByPath("calendar", "latest"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void getFileContent_rejectsBlankPath() {
        SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        SkillPublishService skillPublishService = mock(SkillPublishService.class);
        ZipPackageExtractor zipPackageExtractor = mock(ZipPackageExtractor.class);
        MultipartPackageExtractor multipartPackageExtractor = mock(MultipartPackageExtractor.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        SkillStarService skillStarService = mock(SkillStarService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteRegistryClient> remoteRegistryClientProvider = mock(ObjectProvider.class);
        RemoteMirrorIngestAppService remoteMirrorIngestAppService = mock(RemoteMirrorIngestAppService.class);

        ClawHubCompatAppService service = newService(
                skillSearchAppService,
                skillQueryService,
                skillPublishService,
                zipPackageExtractor,
                multipartPackageExtractor,
                auditLogService,
                compatSkillLookupService,
                skillStarService,
                remoteRegistryClientProvider,
                remoteMirrorIngestAppService
        );

        assertThatThrownBy(() -> service.getFileContent("calendar", "   ", "1.0.0", null, null, Map.of()))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.skill.file.path.invalid");
    }

    @Test
    void getFileContent_rejectsParentTraversalPath() {
        SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        SkillPublishService skillPublishService = mock(SkillPublishService.class);
        ZipPackageExtractor zipPackageExtractor = mock(ZipPackageExtractor.class);
        MultipartPackageExtractor multipartPackageExtractor = mock(MultipartPackageExtractor.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        SkillStarService skillStarService = mock(SkillStarService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteRegistryClient> remoteRegistryClientProvider = mock(ObjectProvider.class);
        RemoteMirrorIngestAppService remoteMirrorIngestAppService = mock(RemoteMirrorIngestAppService.class);

        ClawHubCompatAppService service = newService(
                skillSearchAppService,
                skillQueryService,
                skillPublishService,
                zipPackageExtractor,
                multipartPackageExtractor,
                auditLogService,
                compatSkillLookupService,
                skillStarService,
                remoteRegistryClientProvider,
                remoteMirrorIngestAppService
        );

        assertThatThrownBy(() -> service.getFileContent("calendar", "../secret.txt", "1.0.0", null, null, Map.of()))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.skill.file.path.invalid");
    }

    @Test
    void getFileContent_rejectsNonTextFile() {
        SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        SkillPublishService skillPublishService = mock(SkillPublishService.class);
        ZipPackageExtractor zipPackageExtractor = mock(ZipPackageExtractor.class);
        MultipartPackageExtractor multipartPackageExtractor = mock(MultipartPackageExtractor.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        SkillStarService skillStarService = mock(SkillStarService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteRegistryClient> remoteRegistryClientProvider = mock(ObjectProvider.class);
        RemoteMirrorIngestAppService remoteMirrorIngestAppService = mock(RemoteMirrorIngestAppService.class);

        when(skillQueryService.listFiles("global", "calendar", "1.0.0", null, Map.of()))
                .thenReturn(List.of(new SkillFile(1L, "logo.png", 128L, "image/png", "sha", "storage-key")));

        ClawHubCompatAppService service = newService(
                skillSearchAppService,
                skillQueryService,
                skillPublishService,
                zipPackageExtractor,
                multipartPackageExtractor,
                auditLogService,
                compatSkillLookupService,
                skillStarService,
                remoteRegistryClientProvider,
                remoteMirrorIngestAppService
        );

        assertThatThrownBy(() -> service.getFileContent("calendar", "logo.png", "1.0.0", null, null, Map.of()))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.skill.file.unsupported");
    }

    @Test
    void getFileContent_rejectsOversizedTextFile() {
        SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        SkillPublishService skillPublishService = mock(SkillPublishService.class);
        ZipPackageExtractor zipPackageExtractor = mock(ZipPackageExtractor.class);
        MultipartPackageExtractor multipartPackageExtractor = mock(MultipartPackageExtractor.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        SkillStarService skillStarService = mock(SkillStarService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteRegistryClient> remoteRegistryClientProvider = mock(ObjectProvider.class);
        RemoteMirrorIngestAppService remoteMirrorIngestAppService = mock(RemoteMirrorIngestAppService.class);

        when(skillQueryService.listFiles("global", "calendar", "1.0.0", null, Map.of()))
                .thenReturn(List.of(new SkillFile(1L, "README.md", 2_000_000L, "text/markdown", "sha", "storage-key")));

        ClawHubCompatAppService service = newService(
                skillSearchAppService,
                skillQueryService,
                skillPublishService,
                zipPackageExtractor,
                multipartPackageExtractor,
                auditLogService,
                compatSkillLookupService,
                skillStarService,
                remoteRegistryClientProvider,
                remoteMirrorIngestAppService
        );

        assertThatThrownBy(() -> service.getFileContent("calendar", "README.md", "1.0.0", null, null, Map.of()))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.skill.file.unsupported");
    }

    @Test
    void getFileContent_returnsTextFileContent() throws Exception {
        SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        SkillPublishService skillPublishService = mock(SkillPublishService.class);
        ZipPackageExtractor zipPackageExtractor = mock(ZipPackageExtractor.class);
        MultipartPackageExtractor multipartPackageExtractor = mock(MultipartPackageExtractor.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        SkillStarService skillStarService = mock(SkillStarService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteRegistryClient> remoteRegistryClientProvider = mock(ObjectProvider.class);
        RemoteMirrorIngestAppService remoteMirrorIngestAppService = mock(RemoteMirrorIngestAppService.class);

        when(skillQueryService.listFiles("global", "calendar", "1.0.0", null, Map.of()))
                .thenReturn(List.of(new SkillFile(1L, "README.md", 32L, "text/markdown", "sha", "storage-key")));
        when(skillQueryService.getFileContent("global", "calendar", "1.0.0", "README.md", null, Map.of()))
                .thenReturn(new ByteArrayInputStream("# Calendar".getBytes()));

        ClawHubCompatAppService service = newService(
                skillSearchAppService,
                skillQueryService,
                skillPublishService,
                zipPackageExtractor,
                multipartPackageExtractor,
                auditLogService,
                compatSkillLookupService,
                skillStarService,
                remoteRegistryClientProvider,
                remoteMirrorIngestAppService
        );

        String content = service.getFileContent("calendar", "README.md", "1.0.0", null, null, Map.of());

        assertThat(content).isEqualTo("# Calendar");
    }

    private ClawHubCompatAppService newService(SkillSearchAppService skillSearchAppService,
                                               SkillQueryService skillQueryService,
                                               SkillPublishService skillPublishService,
                                               ZipPackageExtractor zipPackageExtractor,
                                               MultipartPackageExtractor multipartPackageExtractor,
                                               AuditLogService auditLogService,
                                               CompatSkillLookupService compatSkillLookupService,
                                               SkillStarService skillStarService,
                                               ObjectProvider<RemoteRegistryClient> remoteRegistryClientProvider,
                                               RemoteMirrorIngestAppService remoteMirrorIngestAppService) {
        return new ClawHubCompatAppService(
                new CanonicalSlugMapper(),
                skillSearchAppService,
                skillQueryService,
                skillPublishService,
                zipPackageExtractor,
                multipartPackageExtractor,
                auditLogService,
                compatSkillLookupService,
                skillStarService,
                remoteRegistryClientProvider,
                remoteMirrorIngestAppService,
                mock(SkillLifecycleAppService.class)
        );
    }

    private RemoteMirrorIngestAppService.MirrorIngestResult mirrorIngestResult(String namespace, String skillSlug, String version) {
        SkillVersion publishedVersion = new SkillVersion(1L, version, "remote-mirror-bot");
        return new RemoteMirrorIngestAppService.MirrorIngestResult(
                new RemoteMirrorIngestAppService.MirrorPreview(
                        skillSlug,
                        "latest",
                        version,
                        "https://clawhub.ai/bundles/" + skillSlug + "-" + version + ".zip",
                        "abc",
                        12,
                        12,
                        List.of(),
                        new SkillMetadata("Calendar", "Remote calendar skill", version, "# Calendar", Map.of()),
                        ValidationResult.pass()
                ),
                new RemoteMirrorIngestAppService.MirrorTarget(namespace, skillSlug, "remote-mirror-bot"),
                new SkillPublishService.PublishResult(1L, skillSlug, publishedVersion)
        );
    }
}
