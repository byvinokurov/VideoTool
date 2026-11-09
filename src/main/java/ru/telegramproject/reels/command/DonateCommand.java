package ru.telegramproject.reels.command;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.extensions.bots.commandbot.commands.IBotCommand;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Slf4j
@Order(3)
@Component
public class DonateCommand implements IBotCommand {

    @Override
    public String getCommandIdentifier() {
        return "donate";
    }

    @Override
    public String getDescription() {
        return "Поддержать канал звездами";
    }

    @Override
    public void processMessage(TelegramClient telegramClient, Message message, String[] arguments) {

    }
}
