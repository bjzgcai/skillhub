package com.iflytek.skillhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "skillhub.publish")
public class SkillPublishProperties {

    private int maxFileCount = 20_000;
    private long maxSingleFileSize = 20L * 1024 * 1024;
    private long maxArchiveSize = 200L * 1024 * 1024;
    private long maxTotalUncompressedSize = 300L * 1024 * 1024;
    private Set<String> allowedFileExtensions = new LinkedHashSet<>(Set.of(
            ".md", ".txt", ".json", ".json.gz", ".yaml", ".yml", ".html", ".css", ".csv", ".pdf",
            ".toml", ".xml", ".ini", ".cfg", ".env", ".in", ".example",
            ".js", ".ts", ".py", ".sh", ".rb", ".go", ".rs", ".java", ".kt",
            ".lua", ".sql", ".r", ".bat", ".ps1", ".zsh", ".bash",
            ".png", ".jpg", ".jpeg", ".svg", ".gif", ".webp", ".ico",
            ".pptx", ".wav"
    ));

    public int getMaxFileCount() {
        return maxFileCount;
    }

    public void setMaxFileCount(int maxFileCount) {
        this.maxFileCount = maxFileCount;
    }

    public long getMaxSingleFileSize() {
        return maxSingleFileSize;
    }

    public void setMaxSingleFileSize(long maxSingleFileSize) {
        this.maxSingleFileSize = maxSingleFileSize;
    }

    public long getMaxArchiveSize() {
        return maxArchiveSize;
    }

    public void setMaxArchiveSize(long maxArchiveSize) {
        this.maxArchiveSize = maxArchiveSize;
    }

    public long getMaxTotalUncompressedSize() {
        return maxTotalUncompressedSize;
    }

    public void setMaxTotalUncompressedSize(long maxTotalUncompressedSize) {
        this.maxTotalUncompressedSize = maxTotalUncompressedSize;
    }

    public Set<String> getAllowedFileExtensions() {
        return allowedFileExtensions;
    }

    public void setAllowedFileExtensions(Set<String> allowedFileExtensions) {
        this.allowedFileExtensions = new LinkedHashSet<>(allowedFileExtensions);
    }
}
