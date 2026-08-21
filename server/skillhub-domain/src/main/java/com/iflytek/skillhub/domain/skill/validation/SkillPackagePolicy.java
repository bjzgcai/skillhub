package com.iflytek.skillhub.domain.skill.validation;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Shared package-policy rules for path normalization, extension allowlists, and lightweight file
 * signature validation.
 */
public final class SkillPackagePolicy {

    private static final int MAX_PPTX_CORE_XML_SIZE = 4 * 1024 * 1024;

    public static final int MAX_FILE_COUNT = 20_000;
    public static final long MAX_SINGLE_FILE_SIZE = 20L * 1024 * 1024;
    public static final long MAX_ARCHIVE_SIZE = 200L * 1024 * 1024;
    public static final long MAX_TOTAL_UNCOMPRESSED_SIZE = 300L * 1024 * 1024;
    public static final String SKILL_MD_PATH = "SKILL.md";
    public static final String LICENSE_BASENAME = "LICENSE";
    public static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            // Documentation
            ".md", ".txt", ".json", ".yaml", ".yml", ".html", ".css", ".csv", ".pdf",
            // Configuration
            ".toml", ".xml", ".ini", ".cfg", ".env", ".in", ".example",
            // Scripts and source code
            ".js", ".mjs", ".ts", ".py", ".sh", ".rb", ".go", ".rs", ".java", ".kt",
            ".lua", ".sql", ".r", ".bat", ".ps1", ".zsh", ".bash",
            // Images
            ".png", ".jpg", ".jpeg", ".svg", ".gif", ".webp", ".ico",
            // Documents and audio
            ".pptx", ".wav"
    );

    private SkillPackagePolicy() {
    }

    public static String normalizeEntryPath(String rawPath) {
        if (rawPath == null) {
            throw new IllegalArgumentException("Package entry path is missing");
        }

        String sanitized = rawPath.replace('\\', '/').trim();
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Package entry path is empty");
        }
        if (sanitized.startsWith("/") || sanitized.startsWith("\\")) {
            throw new IllegalArgumentException("Package entry path must be relative: " + rawPath);
        }
        if (sanitized.contains(":")) {
            throw new IllegalArgumentException("Package entry path contains an invalid drive or scheme prefix: " + rawPath);
        }

        Path normalized = Paths.get(sanitized).normalize();
        String canonical = normalized.toString().replace('\\', '/');
        if (normalized.isAbsolute() || canonical.isBlank()) {
            throw new IllegalArgumentException("Package entry path is invalid: " + rawPath);
        }
        if (canonical.equals(".") || canonical.equals("..") || canonical.startsWith("../")) {
            throw new IllegalArgumentException("Package entry path escapes package root: " + rawPath);
        }
        if (!sanitized.equals(canonical)) {
            throw new IllegalArgumentException("Package entry path must be normalized: " + rawPath);
        }

        return canonical;
    }

    public static boolean hasAllowedExtension(String path) {
        return hasExactLicenseBasename(path) || ALLOWED_EXTENSIONS.stream().anyMatch(path::endsWith);
    }

    public static String validateContentMatchesExtension(String path, byte[] content) {
        String lowerPath = path.toLowerCase(Locale.ROOT);
        if (lowerPath.endsWith(".png")) {
            return hasPrefix(content, (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
                    ? null
                    : "File content does not match extension: " + path;
        }
        if (lowerPath.endsWith(".jpg")) {
            return hasPrefix(content, (byte) 0xff, (byte) 0xd8, (byte) 0xff)
                    ? null
                    : "File content does not match extension: " + path;
        }
        if (lowerPath.endsWith(".svg")) {
            if (!isUtf8Text(content)) {
                return "File content does not match extension: " + path;
            }
            String text = new String(content, StandardCharsets.UTF_8).trim().toLowerCase();
            return text.contains("<svg") ? null : "File content does not match extension: " + path;
        }
        if (lowerPath.endsWith(".jpeg")) {
            return hasPrefix(content, (byte) 0xff, (byte) 0xd8, (byte) 0xff)
                    ? null
                    : "File content does not match extension: " + path;
        }
        if (lowerPath.endsWith(".gif")) {
            return hasPrefix(content, 'G', 'I', 'F', '8')
                    ? null
                    : "File content does not match extension: " + path;
        }
        if (lowerPath.endsWith(".webp")) {
            return (content.length >= 12
                    && hasPrefix(content, 'R', 'I', 'F', 'F')
                    && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P')
                    ? null
                    : "File content does not match extension: " + path;
        }
        if (lowerPath.endsWith(".ico")) {
            return hasPrefix(content, 0x00, 0x00, 0x01, 0x00)
                    ? null
                    : "File content does not match extension: " + path;
        }
        if (lowerPath.endsWith(".pdf")) {
            return hasPrefix(content, '%', 'P', 'D', 'F')
                    ? null
                    : "File content does not match extension: " + path;
        }
        if (lowerPath.endsWith(".pptx")) {
            return isPptx(content) ? null : "File content does not match extension: " + path;
        }
        if (lowerPath.endsWith(".wav")) {
            return isWav(content) ? null : "File content does not match extension: " + path;
        }
        if (isTextFilePath(path)) {
            return isUtf8Text(content) ? null : "File content does not match extension: " + path;
        }
        return null;
    }

    public static boolean isTextFilePath(String path) {
        if (hasExactLicenseBasename(path)) {
            return true;
        }
        String lowerPath = path.toLowerCase(Locale.ROOT);
        return lowerPath.endsWith(".md") || lowerPath.endsWith(".txt")
                || lowerPath.endsWith(".json") || lowerPath.endsWith(".yaml") || lowerPath.endsWith(".yml")
                || lowerPath.endsWith(".js") || lowerPath.endsWith(".mjs") || lowerPath.endsWith(".ts")
                || lowerPath.endsWith(".py") || lowerPath.endsWith(".sh")
                || lowerPath.endsWith(".html") || lowerPath.endsWith(".css") || lowerPath.endsWith(".csv")
                || lowerPath.endsWith(".toml") || lowerPath.endsWith(".xml") || lowerPath.endsWith(".ini")
                || lowerPath.endsWith(".cfg") || lowerPath.endsWith(".env")
                || lowerPath.endsWith(".in") || lowerPath.endsWith(".example")
                || lowerPath.endsWith(".rb") || lowerPath.endsWith(".go") || lowerPath.endsWith(".rs")
                || lowerPath.endsWith(".java") || lowerPath.endsWith(".kt") || lowerPath.endsWith(".lua")
                || lowerPath.endsWith(".sql") || lowerPath.endsWith(".r")
                || lowerPath.endsWith(".bat") || lowerPath.endsWith(".ps1")
                || lowerPath.endsWith(".zsh") || lowerPath.endsWith(".bash");
    }

    private static boolean isUtf8Text(byte[] content) {
        for (byte value : content) {
            if (value == 0) {
                return false;
            }
        }
        try {
            CharBuffer ignored = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            return true;
        } catch (CharacterCodingException ex) {
            return false;
        }
    }

    private static boolean hasPrefix(byte[] content, int... prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if ((content[index] & 0xff) != (prefix[index] & 0xff)) {
                return false;
            }
        }
        return true;
    }

    public static String determineContentType(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (hasExactLicenseBasename(filename) || lower.endsWith(".txt")
                || lower.endsWith(".in") || lower.endsWith(".example")) return "text/plain";
        if (lower.endsWith(".py")) return "text/x-python";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return "application/x-yaml";
        if (lower.endsWith(".md")) return "text/markdown";
        if (lower.endsWith(".html")) return "text/html";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".xml")) return "application/xml";
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) return "text/javascript";
        if (lower.endsWith(".ts")) return "text/typescript";
        if (lower.endsWith(".sh") || lower.endsWith(".bash") || lower.endsWith(".zsh")) return "text/x-shellscript";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".toml")) return "application/toml";
        if (lower.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        }
        if (lower.endsWith(".wav")) return "audio/wav";
        return "application/octet-stream";
    }

    private static boolean hasExactLicenseBasename(String path) {
        int separator = path.lastIndexOf('/');
        String basename = separator >= 0 ? path.substring(separator + 1) : path;
        return LICENSE_BASENAME.equals(basename);
    }

    private static boolean isPptx(byte[] content) {
        int eocdOffset = findEndOfCentralDirectory(content);
        if (eocdOffset < 0) {
            return false;
        }

        long centralDirectorySize = readUnsignedIntLittleEndian(content, eocdOffset + 12);
        long centralDirectoryOffset = readUnsignedIntLittleEndian(content, eocdOffset + 16);
        if (centralDirectoryOffset > Integer.MAX_VALUE || centralDirectorySize > Integer.MAX_VALUE
                || centralDirectoryOffset + centralDirectorySize > eocdOffset) {
            return false;
        }

        boolean hasContentTypes = false;
        boolean hasPresentation = false;
        int position = (int) centralDirectoryOffset;
        int end = position + (int) centralDirectorySize;
        while (position + 46 <= end && position + 46 <= content.length) {
            if (!hasPrefixAt(content, position, 0x50, 0x4b, 0x01, 0x02)) {
                return false;
            }
            int nameLength = readUnsignedShortLittleEndian(content, position + 28);
            int extraLength = readUnsignedShortLittleEndian(content, position + 30);
            int commentLength = readUnsignedShortLittleEndian(content, position + 32);
            long uncompressedSize = readUnsignedIntLittleEndian(content, position + 24);
            int nextPosition = position + 46 + nameLength + extraLength + commentLength;
            if (nextPosition > end || position + 46 + nameLength > content.length) {
                return false;
            }
            String entryName = new String(content, position + 46, nameLength, StandardCharsets.UTF_8);
            if ("[Content_Types].xml".equals(entryName)) {
                byte[] xml = readZipEntryContent(content, position, uncompressedSize);
                hasContentTypes = hasXmlRoot(xml, "Types");
            } else if ("ppt/presentation.xml".equals(entryName)) {
                byte[] xml = readZipEntryContent(content, position, uncompressedSize);
                hasPresentation = hasXmlRoot(xml, "presentation");
            }
            if (hasContentTypes && hasPresentation) {
                return true;
            }
            position = nextPosition;
        }
        return false;
    }

    private static byte[] readZipEntryContent(byte[] archive, int centralHeaderOffset, long uncompressedSize) {
        if (uncompressedSize <= 0 || uncompressedSize > MAX_PPTX_CORE_XML_SIZE) {
            return null;
        }
        int flags = readUnsignedShortLittleEndian(archive, centralHeaderOffset + 8);
        int compressionMethod = readUnsignedShortLittleEndian(archive, centralHeaderOffset + 10);
        long compressedSize = readUnsignedIntLittleEndian(archive, centralHeaderOffset + 20);
        long localHeaderOffset = readUnsignedIntLittleEndian(archive, centralHeaderOffset + 42);
        if ((flags & 1) != 0 || compressedSize > Integer.MAX_VALUE || localHeaderOffset > Integer.MAX_VALUE) {
            return null;
        }

        int localOffset = (int) localHeaderOffset;
        if (!hasPrefixAt(archive, localOffset, 0x50, 0x4b, 0x03, 0x04)
                || readUnsignedShortLittleEndian(archive, localOffset + 8) != compressionMethod) {
            return null;
        }
        int nameLength = readUnsignedShortLittleEndian(archive, localOffset + 26);
        int extraLength = readUnsignedShortLittleEndian(archive, localOffset + 28);
        long dataOffset = localOffset + 30L + nameLength + extraLength;
        if (nameLength < 0 || extraLength < 0 || dataOffset < 0
                || dataOffset + compressedSize > archive.length) {
            return null;
        }

        if (compressionMethod == 0) {
            if (compressedSize != uncompressedSize) {
                return null;
            }
            return java.util.Arrays.copyOfRange(
                    archive,
                    (int) dataOffset,
                    (int) (dataOffset + compressedSize)
            );
        }
        if (compressionMethod != 8) {
            return null;
        }

        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(archive, (int) dataOffset, (int) compressedSize);
            ByteArrayOutputStream output = new ByteArrayOutputStream((int) uncompressedSize);
            byte[] buffer = new byte[8192];
            while (!inflater.finished()) {
                int read = inflater.inflate(buffer);
                if (read > 0) {
                    if (output.size() + read > uncompressedSize) {
                        return null;
                    }
                    output.write(buffer, 0, read);
                } else if (inflater.needsInput() || inflater.needsDictionary()) {
                    return null;
                }
            }
            return output.size() == uncompressedSize ? output.toByteArray() : null;
        } catch (DataFormatException e) {
            return null;
        } finally {
            inflater.end();
        }
    }

    private static boolean hasXmlRoot(byte[] content, String expectedRoot) {
        if (content == null) {
            return false;
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
            XMLStreamReader reader = factory.createXMLStreamReader(input, StandardCharsets.UTF_8.name());
            try {
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                        return expectedRoot.equals(reader.getLocalName());
                    }
                }
                return false;
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isWav(byte[] content) {
        if (content.length < 12
                || !hasPrefix(content, 'R', 'I', 'F', 'F')
                || content[8] != 'W' || content[9] != 'A' || content[10] != 'V' || content[11] != 'E') {
            return false;
        }

        boolean hasFormatChunk = false;
        boolean hasDataChunk = false;
        int position = 12;
        while (position + 8 <= content.length) {
            long chunkSize = readUnsignedIntLittleEndian(content, position + 4);
            if (chunkSize > Integer.MAX_VALUE || chunkSize > content.length - position - 8L) {
                return false;
            }
            hasFormatChunk |= hasPrefixAt(content, position, 'f', 'm', 't', ' ') && chunkSize >= 16;
            hasDataChunk |= hasPrefixAt(content, position, 'd', 'a', 't', 'a');
            long nextPosition = position + 8L + chunkSize + (chunkSize & 1L);
            if (nextPosition > content.length) {
                return false;
            }
            position = (int) nextPosition;
        }
        return hasFormatChunk && hasDataChunk;
    }

    private static int findEndOfCentralDirectory(byte[] content) {
        int minimumOffset = Math.max(0, content.length - 65_557);
        for (int offset = content.length - 22; offset >= minimumOffset; offset--) {
            if (hasPrefixAt(content, offset, 0x50, 0x4b, 0x05, 0x06)) {
                return offset;
            }
        }
        return -1;
    }

    private static int readUnsignedShortLittleEndian(byte[] content, int offset) {
        if (offset < 0 || offset + 2 > content.length) {
            return -1;
        }
        return (content[offset] & 0xff) | ((content[offset + 1] & 0xff) << 8);
    }

    private static long readUnsignedIntLittleEndian(byte[] content, int offset) {
        if (offset < 0 || offset + 4 > content.length) {
            return Long.MAX_VALUE;
        }
        return (content[offset] & 0xffL)
                | ((content[offset + 1] & 0xffL) << 8)
                | ((content[offset + 2] & 0xffL) << 16)
                | ((content[offset + 3] & 0xffL) << 24);
    }

    private static boolean hasPrefixAt(byte[] content, int offset, int... prefix) {
        if (offset < 0 || offset + prefix.length > content.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if ((content[offset + index] & 0xff) != (prefix[index] & 0xff)) {
                return false;
            }
        }
        return true;
    }
}
