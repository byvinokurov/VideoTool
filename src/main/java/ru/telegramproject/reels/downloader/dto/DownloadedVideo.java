package ru.telegramproject.reels.downloader.dto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Скачанный файл во временном каталоге загрузки; {@link #close()} удаляет весь каталог.
 */
@Slf4j
public record DownloadedVideo(
        Path directory,
        Path file,
        Integer width,
        Integer height,
        Integer durationSeconds) implements AutoCloseable {

    @Override
    public void close() {
        deleteDirectory(directory);
    }

    public static void deleteDirectory(Path directory) {
        try {
            FileSystemUtils.deleteRecursively(directory);
        } catch (IOException e) {
            log.warn("Failed to delete download directory {}", directory, e);
        }
    }
}
