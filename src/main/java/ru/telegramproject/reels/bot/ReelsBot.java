package ru.telegramproject.reels.bot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.EntityType;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.telegramproject.reels.exception.DownloadException;
import ru.telegramproject.reels.service.VideoDeliveryService;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReelsBot implements LongPollingUpdateConsumer {

    private static final String SEND_LINK_REPLY = "Пришлите ссылку на видео из YouTube, TikTok, Instagram или VK.";
    private static final String DOWNLOAD_FAILED_REPLY = "Не удалось скачать видео. Попробуйте позже.";

    private final TelegramClient telegramClient;
    private final VideoDeliveryService videoDeliveryService;
    private final ExecutorService updateHandlers =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("update-", 1).factory());

    @Override
    public void consume(List<Update> updates) {
        updates.forEach(update -> updateHandlers.execute(() -> handle(update)));
    }

    /**
     * Дожидается уже начатых доставок: Telegram считает эти обновления полученными и повторно их не пришлёт.
     */
    @Override
    public void close() {
        updateHandlers.close();
    }

    private void handle(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        Message message = update.getMessage();
        findLink(message).ifPresentOrElse(
                link -> deliverVideo(message.getChatId(), link),
                () -> defaultReply(message.getChatId(), SEND_LINK_REPLY));
    }

    private void deliverVideo(Long chatId, String link) {
        try {
            videoDeliveryService.deliver(chatId, link);
        } catch (DownloadException | TelegramApiException e) {
            log.warn("Failed to deliver video {} to chat {}", link, chatId, e);
            defaultReply(chatId, DOWNLOAD_FAILED_REPLY);
        }
    }

    private Optional<String> findLink(Message message) {
        List<MessageEntity> entities = Objects.requireNonNullElse(message.getEntities(), List.of());
        return entities.stream()
                .map(this::linkOf)
                .flatMap(Optional::stream)
                .findFirst()
                .map(this::withScheme);
    }

    private Optional<String> linkOf(MessageEntity entity) {
        return switch (entity.getType()) {
            case EntityType.URL -> Optional.of(entity.getText());
            case EntityType.TEXTLINK -> Optional.of(entity.getUrl());
            default -> Optional.empty();
        };
    }

    private String withScheme(String link) {
        return link.contains("://") ? link : "https://" + link;
    }

    private void defaultReply(Long chatId, String text) {
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
