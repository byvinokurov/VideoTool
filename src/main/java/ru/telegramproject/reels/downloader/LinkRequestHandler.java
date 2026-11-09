package ru.telegramproject.reels.downloader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.telegramproject.reels.bot.ChatReplier;
import ru.telegramproject.reels.limit.LimitMessages;
import ru.telegramproject.reels.limit.DownloadQueueLimit;

/**
 * Обработка ссылки: проверить лимиты, скачать и отправить видео, сообщить об ошибке.
 * Новые лимиты добавляются сюда отдельными проверками перед доставкой.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkRequestHandler {

    private static final String DOWNLOAD_FAILED_REPLY = "Не удалось скачать видео. Попробуй позже.";

    private final DownloadQueueLimit downloadQueue;
    private final VideoDeliveryService videoDeliveryService;
    private final ChatReplier chatReplier;

    public void handle(Long chatId, String link) {
        if (!downloadQueue.tryEnter()) {
            log.info("Download queue is full, rejected {} from chat {}", link, chatId);
            chatReplier.reply(chatId, LimitMessages.queueFull());
            return;
        }
        try {
            videoDeliveryService.deliver(chatId, link);
        } catch (DownloadException | TelegramApiException e) {
            log.warn("Failed to deliver video {} to chat {}", link, chatId, e);
            chatReplier.reply(chatId, DOWNLOAD_FAILED_REPLY);
        } finally {
            downloadQueue.leave();
        }
    }
}
