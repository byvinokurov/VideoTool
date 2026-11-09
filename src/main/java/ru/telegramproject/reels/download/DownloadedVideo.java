package ru.telegramproject.reels.download;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Скачанное видео во временном каталоге; {@link #close()} удаляет каталог.
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
