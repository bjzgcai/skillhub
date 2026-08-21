package com.iflytek.skillhub.controller.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.SkillPublishProperties;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultipartPackageExtractorTest {

    private static final String PAYLOAD_JSON = """
            {
              "slug": "test-skill",
              "displayName": "Test Skill",
              "version": "1.0.0",
              "acceptLicenseTerms": true,
              "tags": ["test"]
            }
            """;

    @Test
    void acceptsFilesAtExactCountSingleFileAndTotalSizeLimits() throws Exception {
        SkillPublishProperties properties = properties(2, 3, 5);
        MultipartPackageExtractor extractor = extractor(properties);
        MultipartFile[] files = {
                file("first.txt", "abc"),
                file("second.txt", "de")
        };

        MultipartPackageExtractor.ExtractedPackage extracted = extractor.extract(files, PAYLOAD_JSON);

        assertEquals("test-skill", extracted.payload().slug());
        assertEquals(2, extracted.entries().size());
        assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), extracted.entries().get(0).content());
        assertArrayEquals("de".getBytes(StandardCharsets.UTF_8), extracted.entries().get(1).content());
    }

    @Test
    void rejectsTotalContentOneByteAboveLimit() {
        SkillPublishProperties properties = properties(2, 10, 4);
        MultipartPackageExtractor extractor = extractor(properties);
        MultipartFile[] files = {
                file("first.txt", "abc"),
                file("second.txt", "de")
        };

        DomainBadRequestException error = assertThrows(DomainBadRequestException.class,
                () -> extractor.extract(files, PAYLOAD_JSON));

        assertTrue(message(error).contains("Package too large: max 4 bytes"));
    }

    @Test
    void rejectsSingleFileOneByteAboveLimit() {
        SkillPublishProperties properties = properties(1, 3, 10);
        MultipartPackageExtractor extractor = extractor(properties);

        DomainBadRequestException error = assertThrows(DomainBadRequestException.class,
                () -> extractor.extract(new MultipartFile[]{file("large.txt", "abcd")}, PAYLOAD_JSON));

        assertTrue(message(error).contains("File too large: large.txt (max 3 bytes)"));
    }

    @Test
    void rejectsReportedOversizedFileWithoutReadingItsBytes() throws Exception {
        SkillPublishProperties properties = properties(1, 3, 10);
        MultipartPackageExtractor extractor = extractor(properties);
        MultipartFile oversized = mock(MultipartFile.class);
        when(oversized.getOriginalFilename()).thenReturn("large.txt");
        when(oversized.getSize()).thenReturn(4L);

        DomainBadRequestException error = assertThrows(DomainBadRequestException.class,
                () -> extractor.extract(new MultipartFile[]{oversized}, PAYLOAD_JSON));

        assertTrue(message(error).contains("File too large: large.txt (max 3 bytes)"));
        verify(oversized, never()).getBytes();
    }

    @Test
    void rejectsActualFileCountOneAboveLimit() {
        SkillPublishProperties properties = properties(1, 10, 20);
        MultipartPackageExtractor extractor = extractor(properties);
        MultipartFile[] files = {
                file("first.txt", "a"),
                file("second.txt", "b")
        };

        DomainBadRequestException error = assertThrows(DomainBadRequestException.class,
                () -> extractor.extract(files, PAYLOAD_JSON));

        assertTrue(message(error).contains("Too many files: max 1"));
    }

    @Test
    void assignsMimeTypesForNewFormatsAndLicense() throws Exception {
        SkillPublishProperties properties = properties(5, 10, 50);
        MultipartPackageExtractor extractor = extractor(properties);
        MultipartFile[] files = {
                file("LICENSE", "license"),
                file("config.in", "value"),
                file("config.example", "value"),
                file("slides.pptx", "pptx"),
                file("sound.wav", "wav")
        };

        List<PackageEntry> entries = extractor.extract(files, PAYLOAD_JSON).entries();

        assertEquals("text/plain", contentType(entries, "LICENSE"));
        assertEquals("text/plain", contentType(entries, "config.in"));
        assertEquals("text/plain", contentType(entries, "config.example"));
        assertEquals("application/vnd.openxmlformats-officedocument.presentationml.presentation",
                contentType(entries, "slides.pptx"));
        assertEquals("audio/wav", contentType(entries, "sound.wav"));
    }

    private MultipartPackageExtractor extractor(SkillPublishProperties properties) {
        return new MultipartPackageExtractor(properties, new ObjectMapper());
    }

    private SkillPublishProperties properties(int maxFileCount, long maxSingleFileSize,
                                               long maxTotalUncompressedSize) {
        SkillPublishProperties properties = new SkillPublishProperties();
        properties.setMaxFileCount(maxFileCount);
        properties.setMaxSingleFileSize(maxSingleFileSize);
        properties.setMaxTotalUncompressedSize(maxTotalUncompressedSize);
        return properties;
    }

    private MockMultipartFile file(String path, String content) {
        return new MockMultipartFile(
                "files",
                path,
                "application/octet-stream",
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String contentType(List<PackageEntry> entries, String path) {
        return entries.stream()
                .filter(entry -> path.equals(entry.path()))
                .findFirst()
                .orElseThrow()
                .contentType();
    }

    private String message(DomainBadRequestException error) {
        return error.messageArgs()[0].toString();
    }
}
