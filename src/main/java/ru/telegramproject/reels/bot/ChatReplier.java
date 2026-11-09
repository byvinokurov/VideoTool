package ru.telegramproject.reels.bot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Текстовые ответы в чат. Ошибка отправки только пишется в лог: ответить об ответе всё равно некому.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatReplier {

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
}
