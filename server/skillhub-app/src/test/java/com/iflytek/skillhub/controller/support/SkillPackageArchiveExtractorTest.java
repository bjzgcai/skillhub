package com.iflytek.skillhub.controller.support;

import com.iflytek.skillhub.config.SkillPublishProperties;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillPackageArchiveExtractorTest {

    private SkillPackageArchiveExtractor extractor;

    @BeforeEach
    void setUp() {
        SkillPublishProperties props = new SkillPublishProperties();
        extractor = new SkillPackageArchiveExtractor(props);
    }

    @Test
    void shouldRejectPathTraversalEntry() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "skill.zip",
            "application/zip",
            createZip("../secrets.txt", "hidden")
        );

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> extractor.extract(file));

        assertTrue(error.getMessage().contains("escapes package root"));
    }

    @Test
    void shouldRejectOversizedZipEntry() throws Exception {
        SkillPublishProperties props = new SkillPublishProperties();
        props.setMaxSingleFileSize(1024); // 1KB limit
        SkillPackageArchiveExtractor smallExtractor = new SkillPackageArchiveExtractor(props);

        byte[] content = new byte[1025]; // >1KB
        byte[] zip = createZip(Map.of("large.txt", content));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", zip);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> smallExtractor.extract(file));

        assertTrue(error.getMessage().contains("File too large: large.txt"));
    }

    @Test
    void respectsConfiguredSingleFileLimit() throws Exception {
        SkillPublishProperties props = new SkillPublishProperties();
        props.setMaxSingleFileSize(5 * 1024 * 1024); // 5MB
        SkillPackageArchiveExtractor customExtractor = new SkillPackageArchiveExtractor(props);

        byte[] content = new byte[3 * 1024 * 1024]; // 3MB — under 5MB limit
        byte[] zip = createZip(Map.of("data.md", content));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", zip);

        List<PackageEntry> entries = customExtractor.extract(file);
        assertEquals(1, entries.size());
    }

    @Test
    void enforcesCompressedArchiveLimitSeparatelyFromUncompressedLimit() throws Exception {
        byte[] zip = createZip("data.txt", "compressible content");
        SkillPublishProperties props = new SkillPublishProperties();
        props.setMaxArchiveSize(zip.length - 1L);
        props.setMaxTotalUncompressedSize(1024);
        SkillPackageArchiveExtractor smallExtractor = new SkillPackageArchiveExtractor(props);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> smallExtractor.extract(zip));

        assertTrue(error.getMessage().contains("Archive too large"));
    }

    @Test
    void acceptsArchiveAndUncompressedContentAtExactLimits() throws Exception {
        byte[] content = "0123456789".getBytes(StandardCharsets.UTF_8);
        byte[] zip = createZip("data.txt", content);
        SkillPublishProperties props = new SkillPublishProperties();
        props.setMaxArchiveSize(zip.length);
        props.setMaxTotalUncompressedSize(content.length);
        SkillPackageArchiveExtractor boundaryExtractor = new SkillPackageArchiveExtractor(props);

        List<PackageEntry> entries = boundaryExtractor.extract(zip);

        assertEquals(1, entries.size());
        assertEquals(content.length, entries.get(0).size());
    }

    @Test
    void rejectsUncompressedContentOneByteOverLimit() throws Exception {
        byte[] content = "0123456789".getBytes(StandardCharsets.UTF_8);
        byte[] zip = createZip("data.txt", content);
        SkillPublishProperties props = new SkillPublishProperties();
        props.setMaxArchiveSize(zip.length);
        props.setMaxTotalUncompressedSize(content.length - 1L);
        SkillPackageArchiveExtractor boundaryExtractor = new SkillPackageArchiveExtractor(props);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> boundaryExtractor.extract(zip));

        assertTrue(error.getMessage().contains("Uncompressed package too large"));
    }

    @Test
    void assignsMimeTypesForNewFormatsAndLicense() throws Exception {
        byte[] zip = createZip(Map.of(
                "LICENSE", "license text".getBytes(StandardCharsets.UTF_8),
                "config.in", "key=value".getBytes(StandardCharsets.UTF_8),
                "settings.example", "key=example".getBytes(StandardCharsets.UTF_8),
                "slides.pptx", createPptx(),
                "sound.wav", createWav()
        ));

        List<PackageEntry> entries = extractor.extract(zip);

        assertEquals("text/plain", contentType(entries, "LICENSE"));
        assertEquals("text/plain", contentType(entries, "config.in"));
        assertEquals("text/plain", contentType(entries, "settings.example"));
        assertEquals("application/vnd.openxmlformats-officedocument.presentationml.presentation",
                contentType(entries, "slides.pptx"));
        assertEquals("audio/wav", contentType(entries, "sound.wav"));
    }

    @Test
    void stripsRootDirectoryWhenSingleFolder() throws Exception {
        byte[] zipBytes = createZip(Map.of(
                "my-skill/SKILL.md", "---\nname: test\n---\n".getBytes(),
                "my-skill/config.json", "{}".getBytes()
        ));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", zipBytes);
        List<PackageEntry> entries = extractor.extract(file);

        assertTrue(entries.stream().anyMatch(e -> e.path().equals("SKILL.md")));
        assertTrue(entries.stream().anyMatch(e -> e.path().equals("config.json")));
    }

    @Test
    void doesNotStripWhenMultipleRootEntries() throws Exception {
        byte[] zipBytes = createZip(Map.of(
                "SKILL.md", "---\nname: test\n---\n".getBytes(),
                "config.json", "{}".getBytes()
        ));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", zipBytes);
        List<PackageEntry> entries = extractor.extract(file);

        assertTrue(entries.stream().anyMatch(e -> e.path().equals("SKILL.md")));
    }

    @Test
    void stripsRootDirectoryWhenZipHasExplicitDirEntry() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("my-skill/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("my-skill/SKILL.md"));
            zos.write("---\nname: test\n---".getBytes());
            zos.closeEntry();
        }
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", baos.toByteArray());
        List<PackageEntry> entries = extractor.extract(file);

        assertEquals(1, entries.size());
        assertEquals("SKILL.md", entries.get(0).path());
    }

    @Test
    void doesNotStripWhenMultipleRootDirectories() throws Exception {
        byte[] zipBytes = createZip(Map.of(
                "dir-a/SKILL.md", "---\nname: test\n---\n".getBytes(),
                "dir-b/other.md", "# other".getBytes()
        ));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", zipBytes);
        List<PackageEntry> entries = extractor.extract(file);

        assertTrue(entries.stream().anyMatch(e -> e.path().equals("dir-a/SKILL.md")));
        assertTrue(entries.stream().anyMatch(e -> e.path().equals("dir-b/other.md")));
    }

    @Test
    void skipsMacOSMetadataFiles() throws Exception {
        byte[] zipBytes = createZip(Map.of(
                "my-skill/SKILL.md", "---\nname: test\n---\n".getBytes(),
                "my-skill/references.md", "# References".getBytes(),
                "__MACOSX/._my-skill", new byte[]{0x00, 0x05},
                "__MACOSX/my-skill/._references", new byte[]{0x00, 0x05},
                "__MACOSX/my-skill/._SKILL.md", new byte[]{0x00, 0x05}
        ));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", zipBytes);
        List<PackageEntry> entries = extractor.extract(file);

        // Only real files should remain, __MACOSX and ._ files filtered out
        assertEquals(2, entries.size());
        assertTrue(entries.stream().anyMatch(e -> e.path().equals("SKILL.md")));
        assertTrue(entries.stream().anyMatch(e -> e.path().equals("references.md")));
        assertTrue(entries.stream().noneMatch(e -> e.path().contains("__MACOSX")));
        assertTrue(entries.stream().noneMatch(e -> e.path().startsWith("._") || e.path().contains("/._")));
    }

    private byte[] createZip(String entryName, String content) throws Exception {
        return createZip(entryName, content.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] createZip(String entryName, byte[] content) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            zos.write(content);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] createZip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                zos.putNextEntry(entry);
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private byte[] createPptx() throws Exception {
        return createZip(Map.of(
                "[Content_Types].xml", "<Types/>".getBytes(StandardCharsets.UTF_8),
                "ppt/presentation.xml", ("<p:presentation xmlns:p=\""
                        + "http://schemas.openxmlformats.org/presentationml/2006/main\"/>")
                        .getBytes(StandardCharsets.UTF_8)
        ));
    }

    private byte[] createWav() {
        return new byte[]{'R', 'I', 'F', 'F', 4, 0, 0, 0, 'W', 'A', 'V', 'E'};
    }

    private String contentType(List<PackageEntry> entries, String path) {
        return entries.stream()
                .filter(entry -> path.equals(entry.path()))
                .findFirst()
                .orElseThrow()
                .contentType();
    }
}
