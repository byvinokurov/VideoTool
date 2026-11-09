package ru.telegramproject.reels.download;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.telegramproject.reels.bot.ChatReplier;
import ru.telegramproject.reels.limit.BotQueueLimit;
import ru.telegramproject.reels.limit.LimitMessages;
import ru.telegramproject.reels.limit.UserQueueLimit;
import ru.telegramproject.reels.limit.UserQuotaLimit;
import ru.telegramproject.reels.limit.UserQuotaResult;

import java.util.Optional;

/**
 * Обработка ссылки: проверяет лимиты, запускает доставку видео, сообщает об ошибке.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkHandler {

    private static final String DOWNLOAD_FAILED_REPLY = "Не удалось скачать видео. Попробуй позже.";

    private final BotQueueLimit botQueueLimit;
    private final UserQueueLimit userQueueLimit;
    private final UserQuotaLimit userQuotaLimit;
    private final VideoDeliveryService videoDeliveryService;
    private final ChatReplier chatReplier;

    public void handle(Message message, String link) {
        Long chatId = message.getChatId();
        chatReplier.markSeen(chatId, message.getMessageId());
        if (!botQueueLimit.tryEnter()) {
            log.info("Download queue is full, rejected {} from chat {}", link, chatId);
            chatReplier.reply(chatId, LimitMessages.queueFull());
            return;
        }
        try {
            handleInUserQueue(message.getFrom().getId(), chatId, link);
        } finally {
            botQueueLimit.leave();
        }
    }

    private void handleInUserQueue(Long userId, Long chatId, String link) {
        Optional<UserQueueLimit.Turn> turn = userQueueLimit.tryEnter(userId);
        if (turn.isEmpty()) {
            log.info("User {} has too many links waiting, rejected {}", userId, link);
            chatReplier.reply(chatId, LimitMessages.tooManyWaiting());
            return;
        }
        try (UserQueueLimit.Turn userTurn = turn.get()) {
            if (allowedByQuota(userQuotaLimit.tryAcquire(userId), userId, chatId, link)) {
                deliverInTurn(userTurn, userId, chatId, link);
            }
        }
    }

    /**
     * Ждёт своей очереди и перед скачиванием ещё раз проверяет суточный объём.
     */
    private void deliverInTurn(UserQueueLimit.Turn turn, Long userId, Long chatId, String link) {
        try {
            turn.awaitDownload();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for a download turn of user {}", userId);
            return;
        }
        if (allowedByQuota(userQuotaLimit.checkDailyBytes(userId), userId, chatId, link)) {
            deliver(userId, chatId, link);
        }
    }

    /**
     * Если квота превышена — сообщает человеку, когда можно снова, и возвращает false.
     */
    private boolean allowedByQuota(UserQuotaResult quota, Long userId, Long chatId, String link) {
        if (quota.status() == UserQuotaResult.Status.ALLOWED) {
            return true;
        }
        log.info("User {} hit {}, rejected {}", userId, quota.status(), link);
        chatReplier.reply(chatId, LimitMessages.quotaExceeded(quota));
        return false;
    }

    private void deliver(Long userId, Long chatId, String link) {
        try {
            long bytes = videoDeliveryService.deliver(chatId, link);
            userQuotaLimit.addDeliveredBytes(userId, bytes);
        } catch (DownloadException | TelegramApiException e) {
            log.warn("Failed to deliver video {} to chat {}", link, chatId, e);
            chatReplier.reply(chatId, DOWNLOAD_FAILED_REPLY);
        }
    }
}
