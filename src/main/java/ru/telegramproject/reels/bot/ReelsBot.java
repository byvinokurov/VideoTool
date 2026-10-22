package ru.telegramproject.reels.bot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.DefaultLongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Slf4j
@Component
public class ReelsBot extends DefaultLongPollingUpdateConsumer {

    private final TelegramClient telegramClient;

    public ReelsBot(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    @Override
    public void consume(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        Message message = update.getMessage();
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(message.getChatId())
                    .text("Вы написали: " + message.getText())
                    .build());
        } catch (TelegramApiException e) {
            log.error("Не удалось отправить ответ в чат {}", message.getChatId(), e);
        }
    }
}
