package com.iflytek.skillhub.domain.skill.validation;

import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class SkillPackageValidatorTest {

    private SkillPackageValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SkillPackageValidator(new SkillMetadataParser());
    }

    @Test
    void testValidPackage() {
        String skillMdContent = """
            ---
            name: test-skill
            description: A test skill
            version: 1.0.0
            ---
            # Test Skill
            """;

        List<PackageEntry> entries = List.of(
            new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown"),
            new PackageEntry("README.md", "readme".getBytes(), 6, "text/markdown")
        );

        ValidationResult result = validator.validate(entries);

        assertTrue(result.passed());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void testMissingSkillMd() {
        List<PackageEntry> entries = List.of(
            new PackageEntry("README.md", "readme".getBytes(), 6, "text/markdown")
        );

        ValidationResult result = validator.validate(entries);

        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Missing required file: SKILL.md")));
    }

    @Test
    void testDisallowedExtension() {
        String skillMdContent = """
            ---
            name: test-skill
            description: A test skill
            version: 1.0.0
            ---
            Body
            """;

        List<PackageEntry> entries = List.of(
            new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown"),
            new PackageEntry("malware.exe", "bad".getBytes(), 3, "application/octet-stream")
        );

        ValidationResult result = validator.validate(entries);

        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Disallowed file extension") && e.contains("malware.exe")));
    }

    @Test
    void testFileTooLarge() {
        // Use a custom validator with 1KB single file limit to test the logic
        SkillPackageValidator smallValidator = new SkillPackageValidator(
                new SkillMetadataParser(), 100, 1024, 100 * 1024 * 1024,
                SkillPackagePolicy.ALLOWED_EXTENSIONS);
        byte[] bigContent = new byte[1025]; // >1KB
        List<PackageEntry> entries = List.of(
                skillMdEntry(),
                new PackageEntry("big.txt", bigContent, bigContent.length, "text/plain")
        );
        ValidationResult result = smallValidator.validate(entries);
        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("File too large")));
    }

    @Test
    void testTooManyFiles() {
        SkillPackageValidator smallValidator = new SkillPackageValidator(
                new SkillMetadataParser(), 3, SkillPackagePolicy.MAX_SINGLE_FILE_SIZE,
                SkillPackagePolicy.MAX_TOTAL_UNCOMPRESSED_SIZE, SkillPackagePolicy.ALLOWED_EXTENSIONS);
        String skillMdContent = """
            ---
            name: test-skill
            description: A test skill
            version: 1.0.0
            ---
            Body
            """;

        List<PackageEntry> entries = new ArrayList<>();
        entries.add(new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown"));

        for (int i = 0; i < 3; i++) {
            entries.add(new PackageEntry("file" + i + ".txt", "content".getBytes(), 7, "text/plain"));
        }

        ValidationResult result = smallValidator.validate(entries);

        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Too many files")));
    }

    @Test
    void testMissingFrontmatterName() {
        String skillMdContent = """
            ---
            description: A test skill
            version: 1.0.0
            ---
            Body
            """;

        List<PackageEntry> entries = List.of(
            new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown")
        );

        ValidationResult result = validator.validate(entries);

        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Invalid SKILL.md frontmatter") && e.contains("name")));
    }

    @Test
    void testInvalidYamlFrontmatterWithColonInValueShouldStillPass() {
        String skillMdContent = """
            ---
            name: clawdbot
            description: Send messages from Clawdbot via the discord tool: send messages, react, post or edit
            version: 1.0.0
            ---
            Body
            """;

        List<PackageEntry> entries = List.of(
                new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown")
        );

        ValidationResult result = validator.validate(entries);

        assertTrue(result.passed());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void testPackageTooLarge() {
        // Use a custom validator with 2KB total limit to test the logic
        SkillPackageValidator smallValidator = new SkillPackageValidator(
                new SkillMetadataParser(), 100, 10 * 1024 * 1024, 2048,
                SkillPackagePolicy.ALLOWED_EXTENSIONS);
        byte[] content = new byte[2000]; // 2KB
        List<PackageEntry> entries = List.of(
                skillMdEntry(),  // ~50 bytes
                new PackageEntry("data.txt", content, content.length, "text/plain")
        );
        ValidationResult result = smallValidator.validate(entries);
        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Package too large")));
    }

    @Test
    void testPathTraversalEntryRejected() {
        String skillMdContent = """
            ---
            name: test-skill
            description: A test skill
            version: 1.0.0
            ---
            Body
            """;

        List<PackageEntry> entries = List.of(
            new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown"),
            new PackageEntry("../secrets.txt", "hidden".getBytes(), 6, "text/plain")
        );

        ValidationResult result = validator.validate(entries);

        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("escapes package root")));
    }

    @Test
    void testDuplicateNormalizedPathRejected() {
        String skillMdContent = """
            ---
            name: test-skill
            description: A test skill
            version: 1.0.0
            ---
            Body
            """;

        List<PackageEntry> entries = List.of(
            new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown"),
            new PackageEntry("docs\\guide.md", "first".getBytes(), 5, "text/markdown"),
            new PackageEntry("docs/guide.md", "second".getBytes(), 6, "text/markdown")
        );

        ValidationResult result = validator.validate(entries);

        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Duplicate package entry path: docs/guide.md")));
    }

    @Test
    void testSpoofedBinaryTextFileRejected() {
        String skillMdContent = """
            ---
            name: test-skill
            description: A test skill
            version: 1.0.0
            ---
            Body
            """;

        byte[] binaryPayload = new byte[] {0x4d, 0x5a, 0x00, 0x02};

        List<PackageEntry> entries = List.of(
            new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown"),
            new PackageEntry("notes.md", binaryPayload, binaryPayload.length, "text/markdown")
        );

        ValidationResult result = validator.validate(entries);

        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("File content does not match extension")));
    }

    @Test
    void testInvalidSvgPayloadRejected() {
        String skillMdContent = """
            ---
            name: test-skill
            description: A test skill
            version: 1.0.0
            ---
            Body
            """;

        List<PackageEntry> entries = List.of(
            new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown"),
            new PackageEntry("icon.svg", "not actually svg".getBytes(), 16, "image/svg+xml")
        );

        ValidationResult result = validator.validate(entries);

        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("File content does not match extension")));
    }

    @Test
    void rejectsJpegWithWrongMagicBytes() {
        List<PackageEntry> entries = List.of(
                skillMdEntry(),
                new PackageEntry("photo.jpeg", new byte[]{0x00, 0x00}, 2, "image/jpeg")
        );
        ValidationResult result = validator.validate(entries);
        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("photo.jpeg")));
    }

    @Test
    void acceptsValidGif() {
        byte[] gifHeader = "GIF89a".getBytes();
        byte[] content = new byte[20];
        System.arraycopy(gifHeader, 0, content, 0, gifHeader.length);
        List<PackageEntry> entries = List.of(
                skillMdEntry(),
                new PackageEntry("anim.gif", content, content.length, "image/gif")
        );
        ValidationResult result = validator.validate(entries);
        assertTrue(result.passed());
    }

    @Test
    void acceptsNewFormatsAndExactLicenseBasename() throws Exception {
        List<PackageEntry> entries = List.of(
                skillMdEntry(),
                entry("LICENSE", "license".getBytes(StandardCharsets.UTF_8)),
                entry("templates/config.in", "key=value".getBytes(StandardCharsets.UTF_8)),
                entry("templates/config.example", "key=example".getBytes(StandardCharsets.UTF_8)),
                entry("slides.pptx", createPptx()),
                entry("audio.wav", validWav())
        );

        ValidationResult result = validator.validate(entries);

        assertTrue(result.passed(), () -> String.join("\n", result.errors()));
    }

    @Test
    void rejectsLowercaseLicenseWithoutExtension() {
        List<PackageEntry> entries = List.of(
                skillMdEntry(),
                entry("license", "license".getBytes(StandardCharsets.UTF_8))
        );

        ValidationResult result = validator.validate(entries);

        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(error ->
                error.equals("Disallowed file extension: license")));
    }

    @Test
    void rejectsInvalidUtf8ForLicenseInAndExampleFiles() {
        byte[] invalidUtf8 = new byte[]{(byte) 0xc3, 0x28};
        List<PackageEntry> entries = List.of(
                skillMdEntry(),
                entry("LICENSE", invalidUtf8),
                entry("config.in", invalidUtf8),
                entry("config.example", invalidUtf8)
        );

        ValidationResult result = validator.validate(entries);

        assertFalse(result.passed());
        assertEquals(3, result.errors().stream()
                .filter(error -> error.startsWith("File content does not match extension:"))
                .count());
    }

    @Test
    void rejectsPptxThatIsOnlyAnUnrelatedZip() throws Exception {
        List<PackageEntry> entries = List.of(
                skillMdEntry(),
                entry("slides.pptx", createZip("readme.txt", "not a presentation"))
        );

        ValidationResult result = validator.validate(entries);

        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("slides.pptx")));
    }

    @Test
    void rejectsWavWithSpoofedRiffHeader() {
        byte[] spoofedWav = new byte[]{'R', 'I', 'F', 'F', 4, 0, 0, 0, 'N', 'O', 'P', 'E'};
        List<PackageEntry> entries = List.of(
                skillMdEntry(),
                entry("audio.wav", spoofedWav)
        );

        ValidationResult result = validator.validate(entries);

        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("audio.wav")));
    }

    @Test
    void rejectsWavWithoutFormatAndDataChunks() {
        byte[] headerOnly = new byte[]{'R', 'I', 'F', 'F', 4, 0, 0, 0, 'W', 'A', 'V', 'E'};

        ValidationResult result = validator.validate(List.of(
                skillMdEntry(),
                entry("audio.wav", headerOnly)
        ));

        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("audio.wav")));
    }

    @Test
    void rejectsPptxWithEmptyCoreXmlEntries() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            writeZipEntry(zip, "[Content_Types].xml", "");
            writeZipEntry(zip, "ppt/presentation.xml", "");
        }

        ValidationResult result = validator.validate(List.of(
                skillMdEntry(),
                entry("slides.pptx", output.toByteArray())
        ));

        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("slides.pptx")));
    }

    @Test
    void rejectsPptxWithWrongCoreXmlRoots() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            writeZipEntry(zip, "[Content_Types].xml", "<NotTypes/>");
            writeZipEntry(zip, "ppt/presentation.xml", "<notPresentation/>");
        }

        ValidationResult result = validator.validate(List.of(
                skillMdEntry(),
                entry("slides.pptx", output.toByteArray())
        ));

        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("slides.pptx")));
    }

    @Test
    void defaultPolicyUsesRequestedPackageLimits() {
        assertEquals(20_000, SkillPackagePolicy.MAX_FILE_COUNT);
        assertEquals(20L * 1024 * 1024, SkillPackagePolicy.MAX_SINGLE_FILE_SIZE);
        assertEquals(200L * 1024 * 1024, SkillPackagePolicy.MAX_ARCHIVE_SIZE);
        assertEquals(300L * 1024 * 1024, SkillPackagePolicy.MAX_TOTAL_UNCOMPRESSED_SIZE);
    }

    private PackageEntry skillMdEntry() {
        String skillMdContent = """
            ---
            name: test-skill
            description: A test skill
            version: 1.0.0
            ---
            Body
            """;
        return new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown");
    }

    private PackageEntry entry(String path, byte[] content) {
        return new PackageEntry(path, content, content.length, SkillPackagePolicy.determineContentType(path));
    }

    private byte[] createPptx() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            writeZipEntry(zip, "[Content_Types].xml", "<Types/>");
            writeZipEntry(zip, "ppt/presentation.xml",
                    "<p:presentation xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\"/>");
        }
        return output.toByteArray();
    }

    private byte[] createZip(String path, String content) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            writeZipEntry(zip, path, content);
        }
        return output.toByteArray();
    }

    private void writeZipEntry(ZipOutputStream zip, String path, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private byte[] validWav() {
        return new byte[]{
                'R', 'I', 'F', 'F', 36, 0, 0, 0, 'W', 'A', 'V', 'E',
                'f', 'm', 't', ' ', 16, 0, 0, 0,
                1, 0, 1, 0, 0x44, (byte) 0xac, 0, 0,
                (byte) 0x88, 0x58, 1, 0, 2, 0, 16, 0,
                'd', 'a', 't', 'a', 0, 0, 0, 0
        };
    }
}
