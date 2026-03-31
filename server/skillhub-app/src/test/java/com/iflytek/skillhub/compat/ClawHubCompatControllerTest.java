package com.iflytek.skillhub.compat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.registry.remote.RemoteRegistryClient;
import com.iflytek.skillhub.domain.registry.remote.RemoteRegistryException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.domain.skill.validation.ValidationResult;
import com.iflytek.skillhub.dto.SkillLifecycleVersionResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.service.RemoteMirrorIngestAppService;
import com.iflytek.skillhub.service.SkillLifecycleAppService;
import com.iflytek.skillhub.service.SkillSearchAppService;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClawHubCompatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @MockBean
    private SkillSearchAppService skillSearchAppService;

    @MockBean
    private SkillQueryService skillQueryService;

    @MockBean
    private CompatSkillLookupService compatSkillLookupService;

    @MockBean
    private RemoteRegistryClient remoteRegistryClient;

    @MockBean
    private RemoteMirrorIngestAppService remoteMirrorIngestAppService;

    @MockBean
    private SkillLifecycleAppService skillLifecycleAppService;

    @Test
    void search_returns_mapped_results() throws Exception {
        when(skillSearchAppService.search("test", null, "relevance", 0, 20, null, null))
                .thenReturn(new SkillSearchAppService.SearchResponse(
                        List.of(new SkillSummaryResponse(
                                1L,
                                "my-skill",
                                "My Skill",
                                "test summary",
                                "ACTIVE",
                                10L,
                                5,
                                BigDecimal.valueOf(4.5),
                                2,
                                "global",
                                Instant.parse("2026-03-13T09:00:00Z"),
                                false,
                                new SkillLifecycleVersionResponse(11L, "1.2.0", "PUBLISHED"),
                                new SkillLifecycleVersionResponse(11L, "1.2.0", "PUBLISHED"),
                                null,
                                "PUBLISHED")),
                        1,
                        0,
                        20
                ));

        mockMvc.perform(get("/api/v1/search").param("q", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results[0].slug").value("my-skill"))
                .andExpect(jsonPath("$.results[0].summary").value("test summary"))
                .andExpect(jsonPath("$.results[0].version").value("1.2.0"));
    }

    @Test
    void deleteSkill_requiresAuthentication() throws Exception {
        mockMvc.perform(delete("/api/v1/skills/global--demo-skill").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteSkill_archivesSkillWhenAuthenticated() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "usr_1",
                "tester",
                "tester@example.com",
                null,
                "local",
                Set.of("USER")
        );
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, Set.of(new SimpleGrantedAuthority("ROLE_USER")));

        mockMvc.perform(delete("/api/v1/skills/global--demo-skill")
                        .with(csrf())
                        .with(authentication(authentication))
                        .requestAttr("userId", "usr_1")
                        .requestAttr("userNsRoles", Map.of(1L, com.iflytek.skillhub.domain.namespace.NamespaceRole.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        verify(skillLifecycleAppService).archiveSkill(eq("global"), eq("demo-skill"), eq(null), eq("usr_1"), anyMap(), any());
    }

    @Test
    void undeleteSkill_unarchivesSkillWhenAuthenticated() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "usr_1",
                "tester",
                "tester@example.com",
                null,
                "local",
                Set.of("USER")
        );
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, Set.of(new SimpleGrantedAuthority("ROLE_USER")));

        mockMvc.perform(post("/api/v1/skills/global--demo-skill/undelete")
                        .with(csrf())
                        .with(authentication(authentication))
                        .requestAttr("userId", "usr_1")
                        .requestAttr("userNsRoles", Map.of(1L, com.iflytek.skillhub.domain.namespace.NamespaceRole.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        verify(skillLifecycleAppService).unarchiveSkill(eq("global"), eq("demo-skill"), eq("usr_1"), anyMap(), any());
    }

    @Test
    void resolve_returns_correct_downloadUrl() throws Exception {
        when(skillQueryService.resolveVersion("global", "my-skill", null, "latest", null, null, java.util.Map.of()))
                .thenReturn(new SkillQueryService.ResolvedVersionDTO(
                        1L, "global", "my-skill", "latest", 2L, "sha", true, "/api/v1/skills/global/my-skill/download"));
        mockMvc.perform(get("/api/v1/resolve/my-skill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.match.version").value("latest"))
                .andExpect(jsonPath("$.latestVersion.version").value("latest"));
    }

    @Test
    void resolve_with_namespace_returns_correct_downloadUrl() throws Exception {
        when(skillQueryService.resolveVersion("team-ai", "my-skill", null, "latest", null, null, java.util.Map.of()))
                .thenReturn(new SkillQueryService.ResolvedVersionDTO(
                        1L, "team-ai", "my-skill", "latest", 2L, "sha", true, "/api/v1/skills/team-ai/my-skill/download"));
        mockMvc.perform(get("/api/v1/resolve/team-ai--my-skill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.match.version").value("latest"))
                .andExpect(jsonPath("$.latestVersion.version").value("latest"));
    }

    @Test
    void resolve_with_version_returns_specified_version() throws Exception {
        when(skillQueryService.resolveVersion("global", "my-skill", "1.0.0", null, null, null, java.util.Map.of()))
                .thenReturn(new SkillQueryService.ResolvedVersionDTO(
                        1L, "global", "my-skill", "1.0.0", 2L, "sha", true, "/api/v1/skills/global/my-skill/download"));
        mockMvc.perform(get("/api/v1/resolve/my-skill").param("version", "1.0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.match.version").value("1.0.0"))
                .andExpect(jsonPath("$.latestVersion.version").value("1.0.0"));
    }

    @Test
    void getSkill_fallsBackToRemoteWhenLocalSkillIsMissing() throws Exception {
        when(compatSkillLookupService.resolveVisible("global", "calendar", null))
                .thenThrow(new DomainNotFoundException("error.skill.notFound", "calendar"));
        when(remoteRegistryClient.getSkill("calendar"))
                .thenReturn(new RemoteRegistryClient.SkillDetail(
                        "calendar",
                        "Calendar",
                        "Remote calendar skill",
                        Map.of("latest", "2.0.0"),
                        Map.of("downloads", 321),
                        Instant.parse("2026-03-24T08:00:00Z"),
                        Instant.parse("2026-03-24T09:00:00Z"),
                        new RemoteRegistryClient.VersionInfo("2.0.0", Instant.parse("2026-03-24T08:30:00Z"), "Remote release", "MIT"),
                        new RemoteRegistryClient.OwnerInfo("robot", "Robot", "https://example.com/avatar.png"),
                        new RemoteRegistryClient.ModerationInfo(false, false, "clean", List.of(), Instant.parse("2026-03-24T09:10:00Z"), "1.0", "clean"),
                        Map.of()
                ));

        mockMvc.perform(get("/api/v1/skills/calendar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skill.slug").value("calendar"))
                .andExpect(jsonPath("$.skill.summary").value("Remote calendar skill"))
                .andExpect(jsonPath("$.latestVersion.version").value("2.0.0"))
                .andExpect(jsonPath("$.owner.handle").value("robot"));
    }

    @Test
    void resolve_fallsBackToRemoteWhenLocalSkillIsMissing() throws Exception {
        when(compatSkillLookupService.resolveVisible("global", "calendar", null))
                .thenThrow(new DomainNotFoundException("error.skill.notFound", "calendar"));
        when(remoteRegistryClient.resolve("calendar", "latest"))
                .thenReturn(new RemoteRegistryClient.ResolveResult("1.4.0", "1.4.0"));

        mockMvc.perform(get("/api/v1/resolve/calendar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.match.version").value("1.4.0"))
                .andExpect(jsonPath("$.latestVersion.version").value("1.4.0"));
    }

    @Test
    void download_usesOnDemandMirrorWhenLocalSkillIsMissing() throws Exception {
        when(compatSkillLookupService.resolveVisible("global", "calendar", null))
                .thenThrow(new DomainNotFoundException("error.skill.notFound", "calendar"));
        when(remoteMirrorIngestAppService.ingestCanonical("calendar", "latest"))
                .thenReturn(mirrorIngestResult("global", "calendar", "1.4.0"));

        mockMvc.perform(get("/api/v1/download/calendar"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/api/v1/skills/global/calendar/versions/1.4.0/download"));
    }

    @Test
    void download_returnsTooManyRequestsWhenMirrorHitsUpstreamRateLimit() throws Exception {
        when(compatSkillLookupService.resolveVisible("global", "calendar", null))
                .thenThrow(new DomainNotFoundException("error.skill.notFound", "calendar"));
        when(remoteMirrorIngestAppService.ingestCanonical("calendar", "latest"))
                .thenThrow(new RemoteRegistryException(429, "", "rate limited"));

        mockMvc.perform(get("/api/v1/download/calendar"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.msg").value("error.remoteRegistry.rateLimited"));
    }

    @Test
    void whoami_with_auth_returns_user_info() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-42",
                "tester",
                "tester@example.com",
                "https://example.com/avatar.png",
                "github",
                Set.of("SUPER_ADMIN")
        );
        var auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );

        mockMvc.perform(get("/api/v1/whoami")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.handle").value("user-42"))
                .andExpect(jsonPath("$.user.displayName").value("tester"))
                .andExpect(jsonPath("$.user.image").value("https://example.com/avatar.png"));
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
                new com.iflytek.skillhub.domain.skill.service.SkillPublishService.PublishResult(1L, skillSlug, publishedVersion)
        );
    }
}
