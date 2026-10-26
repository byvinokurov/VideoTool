package ru.telegramproject.reels.downloader;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.telegramproject.reels.config.DownloaderProperties;
import ru.telegramproject.reels.downloader.dto.DownloadedVideo;
import ru.telegramproject.reels.exception.DownloadException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.util.function.Predicate.not;

@Component
@RequiredArgsConstructor
public class YtDlpDownloader {

    private static final String OUTPUT_TEMPLATE = "video.%(ext)s";
    private static final String RESULT_TEMPLATE = "after_move:%(.{filepath,width,height,duration})j";

    private final DownloaderProperties properties;
    private final JsonMapper jsonMapper;

    @PostConstruct
    void createWorkDir() throws IOException {
        Files.createDirectories(properties.workDir());
    }

    public DownloadedVideo download(String url) {
        Path directory = createDownloadDirectory();
        try {
            YtDlpResult result = runYtDlp(url, directory);
            return new DownloadedVideo(
                    directory,
                    Path.of(result.filepath()),
                    result.width(),
                    result.height(),
                    roundToSeconds(result.duration()));
        } catch (RuntimeException e) {
            DownloadedVideo.deleteDirectory(directory);
            throw e;
        }
    }

    private Path createDownloadDirectory() {
        try {
            return Files.createTempDirectory(properties.workDir(), "download-");
        } catch (IOException e) {
            throw new DownloadException("Failed to create download directory in " + properties.workDir(), e);
        }
    }

    private YtDlpResult runYtDlp(String url, Path directory) {
        Path stdout = directory.resolve("yt-dlp.out");
        Path stderr = directory.resolve("yt-dlp.err");
        Process process = start(buildCommand(url, directory), stdout, stderr);
        awaitSuccess(process, stderr);
        return parseResult(stdout);
    }

    private List<String> buildCommand(String url, Path directory) {
        return List.of(
                properties.ytDlpPath(),
                "--no-playlist",
                "--format-sort", "vcodec:h264,res:%d,acodec:aac,ext:mp4:m4a".formatted(properties.maxHeight()),
                "--merge-output-format", "mp4",
                "--remux-video", "mp4",
                "--paths", directory.toString(),
                "--output", OUTPUT_TEMPLATE,
                "--print", RESULT_TEMPLATE,
                "--",
                url);
    }

    private static Process start(List<String> command, Path stdout, Path stderr) {
        try {
            return new ProcessBuilder(command)
                    .redirectOutput(stdout.toFile())
                    .redirectError(stderr.toFile())
                    .start();
        } catch (IOException e) {
            throw new DownloadException("Failed to start " + command.getFirst(), e);
        }
    }

    private void awaitSuccess(Process process, Path stderr) {
        try {
            if (!process.waitFor(properties.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                kill(process);
                throw new DownloadException("yt-dlp did not finish within " + properties.timeout());
            }
        } catch (InterruptedException e) {
            kill(process);
            Thread.currentThread().interrupt();
            throw new DownloadException("Interrupted while waiting for yt-dlp", e);
        }
        if (process.exitValue() != 0) {
            throw new DownloadException("yt-dlp exited with code %d: %s"
                    .formatted(process.exitValue(), readString(stderr).strip()));
        }
    }

    private static void kill(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private YtDlpResult parseResult(Path stdout) {
        String json = readString(stdout).lines()
                .filter(not(String::isBlank))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new DownloadException("yt-dlp did not report a downloaded file"));
        try {
            return jsonMapper.readValue(json, YtDlpResult.class);
        } catch (JacksonException e) {
            throw new DownloadException("Unexpected yt-dlp output: " + json, e);
        }
    }

    private static String readString(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new DownloadException("Failed to read " + file, e);
        }
    }

    private static Integer roundToSeconds(Double duration) {
        return duration == null ? null : (int) Math.round(duration);
    }

    record YtDlpResult(String filepath, Integer width, Integer height, Double duration) {
    }
}
