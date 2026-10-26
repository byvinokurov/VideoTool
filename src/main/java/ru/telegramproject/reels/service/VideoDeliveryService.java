package ru.telegramproject.reels.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.telegramproject.reels.downloader.dto.DownloadedVideo;
import ru.telegramproject.reels.downloader.YtDlpDownloader;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class VideoDeliveryService {

    private final YtDlpDownloader downloader;
    private final TelegramClient telegramClient;

    public VideoDeliveryService(YtDlpDownloader downloader, TelegramClient telegramClient) {
        this.downloader = downloader;
        this.telegramClient = telegramClient;
    }

    public void deliver(Long chatId, String url) throws TelegramApiException {
        long downloadStartedAt = System.nanoTime();
        try (DownloadedVideo video = downloader.download(url)) {
            long sendStartedAt = System.nanoTime();
            telegramClient.execute(SendVideo.builder()
                    .chatId(chatId)
                    .video(new InputFile(video.file().toFile()))
                    .width(video.width())
                    .height(video.height())
                    .duration(video.durationSeconds())
                    .supportsStreaming(true)
                    .build());
            log.info("Delivered {} to chat {}: download {} ms, send {} ms",
                    url, chatId, millisBetween(downloadStartedAt, sendStartedAt), millisBetween(sendStartedAt, System.nanoTime()));
        }
    }

    private static long millisBetween(long startNanos, long endNanos) {
        return TimeUnit.NANOSECONDS.toMillis(endNanos - startNanos);
    }
}
