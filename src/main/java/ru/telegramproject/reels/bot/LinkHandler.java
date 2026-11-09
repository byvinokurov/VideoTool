package ru.telegramproject.reels.bot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.telegramproject.reels.download.DownloadException;
import ru.telegramproject.reels.download.VideoDeliveryService;
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
        LinkRequest request = new LinkRequest(message.getFrom().getId(), message.getChatId(), message.getMessageId(), link);
        chatReplier.markSeen(request.chatId(), request.messageId());
        if (!botQueueLimit.tryEnter()) {
            log.info("Download queue is full, rejected {} from chat {}", link, request.chatId());
            replyToLink(request, LimitMessages.queueFull());
            return;
        }
        try {
            handleInUserQueue(request);
        } finally {
            botQueueLimit.leave();
        }
    }

    private void handleInUserQueue(LinkRequest request) {
        Optional<UserQueueLimit.Turn> turn = userQueueLimit.tryEnter(request.userId());
        if (turn.isEmpty()) {
            log.info("User {} has too many links waiting, rejected {}", request.userId(), request.link());
            replyToLink(request, LimitMessages.tooManyWaiting());
            return;
        }
        try (UserQueueLimit.Turn userTurn = turn.get()) {
            if (allowedByQuota(userQuotaLimit.tryAcquire(request.userId()), request)) {
                deliverInTurn(userTurn, request);
            }
        }
    }

    /**
     * Ждёт своей очереди и перед скачиванием ещё раз проверяет суточный объём.
     */
    private void deliverInTurn(UserQueueLimit.Turn turn, LinkRequest request) {
        try {
            turn.awaitDownload();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for a download turn of user {}", request.userId());
            return;
        }
        if (allowedByQuota(userQuotaLimit.checkDailyBytes(request.userId()), request)) {
            deliver(request);
        }
    }

    /**
     * Если квота превышена — сообщает человеку, когда можно снова, и возвращает false.
     */
    private boolean allowedByQuota(UserQuotaResult quota, LinkRequest request) {
        if (quota.status() == UserQuotaResult.Status.ALLOWED) {
            return true;
        }
        log.info("User {} hit {}, rejected {}", request.userId(), quota.status(), request.link());
        replyToLink(request, LimitMessages.quotaExceeded(quota));
        return false;
    }

    private void deliver(LinkRequest request) {
        try {
            long bytes = videoDeliveryService.deliver(request);
            userQuotaLimit.addDeliveredBytes(request.userId(), bytes);
        } catch (DownloadException | TelegramApiException e) {
            log.warn("Failed to deliver video {} to chat {}", request.link(), request.chatId(), e);
            replyToLink(request, DOWNLOAD_FAILED_REPLY);
        }
    }

    /**
     * Ответ цитатой на сообщение со ссылкой: видно, к какой ссылке он относится.
     */
    private void replyToLink(LinkRequest request, String text) {
        chatReplier.replyTo(request.chatId(), request.messageId(), text);
    }
}
