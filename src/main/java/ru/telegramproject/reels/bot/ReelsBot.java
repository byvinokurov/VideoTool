package ru.telegramproject.reels.bot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.DefaultLongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.EntityType;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.telegramproject.reels.download.DownloadException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
public class ReelsBot extends DefaultLongPollingUpdateConsumer {

    private static final String SEND_LINK_REPLY = "Пришлите ссылку на видео из YouTube, TikTok, Instagram или VK.";
    private static final String DOWNLOAD_FAILED_REPLY = "Не удалось скачать видео. Попробуйте позже.";

    private final TelegramClient telegramClient;
    private final VideoDeliveryService videoDeliveryService;

    public ReelsBot(TelegramClient telegramClient, VideoDeliveryService videoDeliveryService) {
        this.telegramClient = telegramClient;
        this.videoDeliveryService = videoDeliveryService;
    }

    @Override
    public void consume(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        Message message = update.getMessage();
        findLink(message).ifPresentOrElse(
                link -> deliverVideo(message.getChatId(), link),
                () -> reply(message.getChatId(), SEND_LINK_REPLY));
    }

    private void deliverVideo(Long chatId, String link) {
        try {
            videoDeliveryService.deliver(chatId, link);
        } catch (DownloadException | TelegramApiException e) {
            log.warn("Failed to deliver video {} to chat {}", link, chatId, e);
            reply(chatId, DOWNLOAD_FAILED_REPLY);
        }
    }

    private static Optional<String> findLink(Message message) {
        List<MessageEntity> entities = Objects.requireNonNullElse(message.getEntities(), List.of());
        return entities.stream()
                .filter(entity -> EntityType.URL.equals(entity.getType()) || EntityType.TEXTLINK.equals(entity.getType()))
                .findFirst()
                .map(entity -> EntityType.TEXTLINK.equals(entity.getType())
                        ? entity.getUrl()
                        : message.getText().substring(entity.getOffset(), entity.getOffset() + entity.getLength()))
                .map(ReelsBot::withScheme);
    }

    private static String withScheme(String link) {
        return link.contains("://") ? link : "https://" + link;
    }

    private void reply(Long chatId, String text) {
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
