package ru.telegramproject.reels.bot;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.extensions.bots.commandbot.commands.CommandRegistry;
import org.telegram.telegrambots.extensions.bots.commandbot.commands.IBotCommand;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.EntityType;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.telegramproject.reels.limit.AntispamLimit;
import ru.telegramproject.reels.limit.AntispamResult;
import ru.telegramproject.reels.limit.LimitMessages;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReelsBot implements LongPollingUpdateConsumer {

    private static final String SEND_LINK_REPLY = "Пришли ссылку на видео из YouTube, TikTok, Instagram или VK.";

    private final TelegramClient telegramClient;
    private final CommandRegistry commandRegistry;
    private final List<IBotCommand> commands;
    private final AntispamLimit antispamLimit;
    private final LinkHandler linkHandler;
    private final ChatReplier chatReplier;
    private final ExecutorService updateHandlers =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("update-", 1).factory());

    @PostConstruct
    public void initCommands() {
        List<BotCommand> menu = commands.stream()
                .map(c -> BotCommand.builder().command(c.getCommandIdentifier()).description(c.getDescription()).build())
                .collect(Collectors.toList());
        try {
            telegramClient.execute(SetMyCommands.builder()
                    .commands(menu)
                    .scope(new BotCommandScopeDefault())
                    .build());
        } catch (TelegramApiException e) {
            log.warn("Failed to update bot commands menu", e);
        }
    }

    @Override
    public void consume(List<Update> updates) {
        updates.forEach(update -> updateHandlers.execute(() -> handleUpdate(update)));
    }

    @Override
    public void close() {
        updateHandlers.close();
    }

    private void handleUpdate(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        Message message = update.getMessage();
        AntispamResult antispam = antispamLimit.register(message.getFrom().getId());
        switch (antispam.status()) {
            case ALLOWED -> handleMessage(message);
            case MUTED -> {
                log.info("User {} muted for {}", message.getFrom().getId(), antispam.muteDuration());
                chatReplier.reply(message.getChatId(), LimitMessages.muted(antispam.muteDuration()));
            }
            case IGNORED -> {
            }
        }
    }

    private void handleMessage(Message message) {
        if (message.isCommand()) {
            commandRegistry.executeCommand(message);
            return;
        }
        findLink(message).ifPresentOrElse(
                link -> linkHandler.handle(message, link),
                () -> chatReplier.reply(message.getChatId(), SEND_LINK_REPLY));
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
}
