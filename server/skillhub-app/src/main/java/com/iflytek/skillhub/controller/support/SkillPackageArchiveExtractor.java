package com.iflytek.skillhub.controller.support;

import com.iflytek.skillhub.config.SkillPublishProperties;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class SkillPackageArchiveExtractor {

    private final long maxArchiveSize;
    private final long maxTotalUncompressedSize;
    private final long maxSingleFileSize;
    private final int maxFileCount;

    public SkillPackageArchiveExtractor(SkillPublishProperties properties) {
        this.maxArchiveSize = properties.getMaxArchiveSize();
        this.maxTotalUncompressedSize = properties.getMaxTotalUncompressedSize();
        this.maxSingleFileSize = properties.getMaxSingleFileSize();
        this.maxFileCount = properties.getMaxFileCount();
    }

    public List<PackageEntry> extract(MultipartFile file) throws IOException {
        if (file.getSize() > maxArchiveSize) {
            throw new IllegalArgumentException(
                    "Archive too large: " + file.getSize() + " bytes (max: "
                            + maxArchiveSize + ")"
            );
        }

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            return extractFromZipStream(zis);
        }
    }

    public List<PackageEntry> extract(byte[] archiveBytes) throws IOException {
        if (archiveBytes.length > maxArchiveSize) {
            throw new IllegalArgumentException(
                    "Archive too large: " + archiveBytes.length + " bytes (max: "
                            + maxArchiveSize + ")"
            );
        }

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(archiveBytes))) {
            return extractFromZipStream(zis);
        }
    }

    private List<PackageEntry> extractFromZipStream(ZipInputStream zis) throws IOException {
        List<PackageEntry> entries = new ArrayList<>();
        long totalSize = 0;

        ZipEntry zipEntry;
        while ((zipEntry = zis.getNextEntry()) != null) {
            if (zipEntry.isDirectory()) {
                zis.closeEntry();
                continue;
            }

            String entryName = zipEntry.getName();
            // Skip macOS metadata: __MACOSX/ directory and ._ prefixed files
            if (entryName.startsWith("__MACOSX/") || entryName.contains("/__MACOSX/")
                    || entryName.startsWith("._") || entryName.contains("/._")) {
                zis.closeEntry();
                continue;
            }

            if (entries.size() >= maxFileCount) {
                throw new IllegalArgumentException(
                        "Too many files: more than " + maxFileCount
                );
            }

            String normalizedPath = SkillPackagePolicy.normalizeEntryPath(entryName);
            byte[] content = readEntry(zis, normalizedPath);
            totalSize += content.length;
            if (totalSize > maxTotalUncompressedSize) {
                throw new IllegalArgumentException(
                        "Uncompressed package too large: " + totalSize + " bytes (max: "
                                + maxTotalUncompressedSize + ")"
                );
            }

            entries.add(new PackageEntry(
                    normalizedPath,
                    content,
                    content.length,
                    SkillPackagePolicy.determineContentType(normalizedPath)
            ));
            zis.closeEntry();
        }

        return stripSingleRootDirectory(entries);
    }

    /**
     * If all file paths share a single root directory prefix (e.g., "my-skill/xxx"),
     * strip that prefix. Otherwise return entries unchanged.
     */
    static List<PackageEntry> stripSingleRootDirectory(List<PackageEntry> entries) {
        if (entries.isEmpty()) return entries;

        Set<String> rootSegments = new HashSet<>();
        for (PackageEntry entry : entries) {
            int slashIndex = entry.path().indexOf('/');
            if (slashIndex < 0) {
                // File at root level, no stripping
                return entries;
            }
            rootSegments.add(entry.path().substring(0, slashIndex));
        }

        if (rootSegments.size() != 1) {
            return entries;
        }

        String prefix = rootSegments.iterator().next() + "/";
        return entries.stream()
                .map(e -> new PackageEntry(
                        e.path().substring(prefix.length()),
                        e.content(),
                        e.size(),
                        e.contentType()))
                .toList();
    }

    private byte[] readEntry(ZipInputStream zis, String path) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long totalRead = 0;
        int read;
        while ((read = zis.read(buffer)) != -1) {
            totalRead += read;
            if (totalRead > maxSingleFileSize) {
                throw new IllegalArgumentException(
                        "File too large: " + path + " (" + totalRead + " bytes, max: "
                                + maxSingleFileSize + ")"
                );
            }
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }

}
