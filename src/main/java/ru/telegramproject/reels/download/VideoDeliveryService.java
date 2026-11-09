package ru.telegramproject.reels.download;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.ReplyParameters;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.telegramproject.reels.bot.LinkRequest;
import ru.telegramproject.reels.config.properties.DownloaderProperties;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Доставка видео: скачивает по ссылке и отправляет в чат.
 */
@Slf4j
@Service
public class VideoDeliveryService {

    /**
     * Telegram показывает статус 5 секунд, поэтому он обновляется чуть чаще.
     */
    private static final Duration UPLOAD_STATUS_INTERVAL = Duration.ofSeconds(4);

    private final VideoDownloader downloader;
    private final TelegramClient telegramClient;
    private final Semaphore downloadSlots;

    public VideoDeliveryService(VideoDownloader downloader, TelegramClient telegramClient, DownloaderProperties properties) {
        this.downloader = downloader;
        this.telegramClient = telegramClient;
        this.downloadSlots = new Semaphore(properties.maxConcurrentDownloads(), true);
    }

    /**
     * Скачивает и отправляет видео; возвращает размер отправленного файла в байтах (для суточной квоты).
     */
    public long deliver(LinkRequest request) throws TelegramApiException {
        Thread uploadStatus = Thread.ofVirtual().start(() -> showUploadStatus(request.chatId()));
        try {
            return deliverInDownloadSlot(request);
        } finally {
            uploadStatus.interrupt();
        }
    }

    private long deliverInDownloadSlot(LinkRequest request) throws TelegramApiException {
        long queuedAt = System.nanoTime();
        acquireDownloadSlot();
        try {
            return downloadAndSend(request, queuedAt);
        } finally {
            downloadSlots.release();
        }
    }

    private long downloadAndSend(LinkRequest request, long queuedAt) throws TelegramApiException {
        long downloadStartedAt = System.nanoTime();
        try (DownloadedVideo video = downloader.download(request.link())) {
            long sendStartedAt = System.nanoTime();
            telegramClient.execute(SendVideo.builder()
                    .chatId(request.chatId())
                    .replyParameters(ReplyParameters.builder()
                            .messageId(request.messageId())
                            .allowSendingWithoutReply(true)
                            .build())
                    .video(new InputFile(video.file().toFile()))
                    .width(video.width())
                    .height(video.height())
                    .duration(video.durationSeconds())
                    .supportsStreaming(true)
                    .build());
            log.info("Delivered {} to chat {}: queue {} ms, download {} ms, send {} ms",
                    request.link(), request.chatId(),
                    millisBetween(queuedAt, downloadStartedAt),
                    millisBetween(downloadStartedAt, sendStartedAt),
                    millisBetween(sendStartedAt, System.nanoTime()));
            return video.file().toFile().length();
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
