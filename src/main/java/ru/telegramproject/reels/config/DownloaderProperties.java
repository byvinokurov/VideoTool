package ru.telegramproject.reels.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "downloader")
public record DownloaderProperties(
        @NotBlank String ytDlpPath,
        @NotNull Path workDir,
        @NotNull Duration timeout,
        @Positive int maxHeight) {
}
