package ru.telegramproject.reels.command;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.extensions.bots.commandbot.commands.IBotCommand;
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Order(1)
@Component
public class StartCommand implements IBotCommand {

    private static final String WELCOME_MESSAGE = "Привет! Кидай ссылку на видео — пришлю его прямо сюда.";
    private static final Resource ANIMATION = new ClassPathResource("welcome.gif");

    /**
     * Гифка загружается в Telegram один раз, дальше отправляется по file_id из ответа.
     */
    private final AtomicReference<String> uploadedAnimationId = new AtomicReference<>();

    @Override
    public String getCommandIdentifier() {
        return "start";
    }

    @Override
    public String getDescription() {
        return "Как использовать?";
    }

    @SneakyThrows
    @Override
    public void processMessage(TelegramClient telegramClient, Message message, String[] arguments) {
        Long chatId = message.getChatId();
        try {
            sendAnimation(telegramClient, chatId);
        } catch (TelegramApiException | IOException e) {
            log.warn("Failed to send welcome animation to chat {}, sending text only", chatId, e);
            sendText(telegramClient, chatId);
        }
    }

    private void sendAnimation(TelegramClient telegramClient, Long chatId) throws TelegramApiException, IOException {
        String animationId = uploadedAnimationId.get();
        if (animationId != null) {
            postAnimation(telegramClient, chatId, new InputFile(animationId));
            return;
        }
        try (InputStream gif = ANIMATION.getInputStream()) {
            Message sent = postAnimation(telegramClient, chatId, new InputFile(gif, ANIMATION.getFilename()));
            if (sent.hasAnimation()) {
                uploadedAnimationId.set(sent.getAnimation().getFileId());
            }
        }
    }

    private Message postAnimation(TelegramClient telegramClient, Long chatId, InputFile animation) throws TelegramApiException {
        return telegramClient.execute(SendAnimation.builder()
                .chatId(chatId)
                .animation(animation)
                .caption(WELCOME_MESSAGE)
                .build());
    }

    private void sendText(TelegramClient telegramClient, Long chatId) throws TelegramApiException {
        telegramClient.execute(SendMessage.builder()
                .chatId(chatId)
                .text(WELCOME_MESSAGE)
                .build());
    }
}
