package ru.telegramproject.reels.bot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.reactions.SetMessageReaction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.reactions.ReactionTypeEmoji;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

/**
 * Ответы в чат: текст и реакции. Ошибка отправки только пишется в лог: ответить об ответе всё равно некому.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatReplier {

    private static final ReactionTypeEmoji SEEN = ReactionTypeEmoji.builder().emoji("👀").build();

    private final TelegramClient telegramClient;

    public void reply(Long chatId, String text) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .build());
        } catch (TelegramApiException e) {
            log.error("Failed to send reply to chat {}", chatId, e);
        }
    }

    /**
     * Ставит 👀 на сообщение: бот его увидел. Отправляется асинхронно, чтобы не задерживать обработку.
     */
    public void markSeen(Long chatId, Integer messageId) {
        try {
            telegramClient.executeAsync(SetMessageReaction.builder()
                            .chatId(chatId)
                            .messageId(messageId)
                            .reactionTypes(List.of(SEEN))
                            .build())
                    .whenComplete((ok, error) -> logIfSeenFailed(error, chatId, messageId));
        } catch (TelegramApiException e) {
            logIfSeenFailed(e, chatId, messageId);
        }
    }

    /**
     * Ошибка бывает и сразу (неверный запрос), и позже, когда Telegram ответит (например, 429).
     */
    private static void logIfSeenFailed(Throwable error, Long chatId, Integer messageId) {
        if (error != null) {
            log.warn("Failed to mark message {} in chat {} as seen", messageId, chatId, error);
        }
    }
}
