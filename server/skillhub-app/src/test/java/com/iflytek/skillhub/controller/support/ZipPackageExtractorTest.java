package com.iflytek.skillhub.controller.support;

import com.iflytek.skillhub.config.SkillPublishProperties;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipPackageExtractorTest {

    @Test
    void rejectsArchiveAboveCompressedLimitBeforeExtraction() throws Exception {
        byte[] zip = createZip("data.txt", "content".getBytes(StandardCharsets.UTF_8));
        SkillPublishProperties properties = new SkillPublishProperties();
        properties.setMaxArchiveSize(zip.length - 1L);
        ZipPackageExtractor extractor = new ZipPackageExtractor(properties);
        MockMultipartFile file = new MockMultipartFile("file", "package.zip", "application/zip", zip);

        DomainBadRequestException error = assertThrows(DomainBadRequestException.class,
                () -> extractor.extract(file));

        assertTrue(error.messageArgs()[0].toString().contains("Archive too large"));
    }

    @Test
    void acceptsUncompressedPackageAtExactLimit() throws Exception {
        byte[] content = "0123456789".getBytes(StandardCharsets.UTF_8);
        byte[] zip = createZip("data.txt", content);
        SkillPublishProperties properties = new SkillPublishProperties();
        properties.setMaxArchiveSize(zip.length);
        properties.setMaxTotalUncompressedSize(content.length);
        ZipPackageExtractor extractor = new ZipPackageExtractor(properties);
        MockMultipartFile file = new MockMultipartFile("file", "package.zip", "application/zip", zip);

        List<PackageEntry> entries = extractor.extract(file);

        assertEquals(1, entries.size());
        assertEquals(content.length, entries.get(0).size());
    }

    @Test
    void rejectsActualFileCountAboveLimit() throws Exception {
        byte[] zip = createZipWithTwoFiles();
        SkillPublishProperties properties = new SkillPublishProperties();
        properties.setMaxFileCount(1);
        properties.setMaxArchiveSize(zip.length);
        ZipPackageExtractor extractor = new ZipPackageExtractor(properties);
        MockMultipartFile file = new MockMultipartFile("file", "package.zip", "application/zip", zip);

        DomainBadRequestException error = assertThrows(DomainBadRequestException.class,
                () -> extractor.extract(file));

        assertTrue(error.messageArgs()[0].toString().contains("Too many files"));
    }

    private byte[] createZip(String path, byte[] content) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(path));
            zip.write(content);
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private byte[] createZipWithTwoFiles() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("first.txt"));
            zip.write('a');
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("second.txt"));
            zip.write('b');
            zip.closeEntry();
        }
        return output.toByteArray();
    }
}
