package ru.telegramproject.reels.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.telegramproject.reels.config.DownloaderProperties;
import ru.telegramproject.reels.downloader.dto.DownloadedVideo;
import ru.telegramproject.reels.downloader.YtDlpDownloader;
import ru.telegramproject.reels.exception.DownloadException;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class VideoDeliveryService {

    /**
     * Telegram показывает статус 5 секунд, поэтому он обновляется чуть чаще.
     */
    private static final Duration UPLOAD_STATUS_INTERVAL = Duration.ofSeconds(4);

    private final YtDlpDownloader downloader;
    private final TelegramClient telegramClient;
    private final Semaphore downloadSlots;

    public VideoDeliveryService(YtDlpDownloader downloader, TelegramClient telegramClient, DownloaderProperties properties) {
        this.downloader = downloader;
        this.telegramClient = telegramClient;
        this.downloadSlots = new Semaphore(properties.maxConcurrentDownloads(), true);
    }

    public void deliver(Long chatId, String url) throws TelegramApiException {
        Thread uploadStatus = Thread.ofVirtual().start(() -> showUploadStatus(chatId));
        try {
            deliverInTurn(chatId, url);
        } finally {
            uploadStatus.interrupt();
        }
    }

    private void deliverInTurn(Long chatId, String url) throws TelegramApiException {
        long queuedAt = System.nanoTime();
        acquireDownloadSlot();
        try {
            downloadAndSend(chatId, url, queuedAt);
        } finally {
            downloadSlots.release();
        }
    }

    private void downloadAndSend(Long chatId, String url, long queuedAt) throws TelegramApiException {
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
            log.info("Delivered {} to chat {}: queue {} ms, download {} ms, send {} ms",
                    url, chatId,
                    millisBetween(queuedAt, downloadStartedAt),
                    millisBetween(downloadStartedAt, sendStartedAt),
                    millisBetween(sendStartedAt, System.nanoTime()));
        }
    }

    private void acquireDownloadSlot() {
        try {
            downloadSlots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DownloadException("Interrupted while waiting for a free download slot", e);
        }
    }

    private void showUploadStatus(Long chatId) {
        SendChatAction uploadingVideo = SendChatAction.builder()
                .chatId(chatId)
                .action(ActionType.UPLOAD_VIDEO.toString())
                .build();
        try {
            while (true) {
                telegramClient.executeAsync(uploadingVideo);
                Thread.sleep(UPLOAD_STATUS_INTERVAL);
            }
        } catch (InterruptedException e) {
            // Доставка закончилась, статус больше не нужен
        } catch (TelegramApiException e) {
            log.debug("Failed to show upload status in chat {}", chatId, e);
        }
    }

    private static long millisBetween(long startNanos, long endNanos) {
        return TimeUnit.NANOSECONDS.toMillis(endNanos - startNanos);
    }
}
