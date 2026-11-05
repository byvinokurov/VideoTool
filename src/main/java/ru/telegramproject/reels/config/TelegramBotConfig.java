package ru.telegramproject.reels.config;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.extensions.bots.commandbot.commands.CommandRegistry;
import org.telegram.telegrambots.extensions.bots.commandbot.commands.IBotCommand;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.telegramproject.reels.bot.ReelsBot;
import ru.telegramproject.reels.config.properties.TelegramBotProperties;

import java.util.List;

/**
 * Единственное место в приложении, где токен попадает в код.
 * Дальше по коду передаётся уже готовый TelegramClient, а не строка с токеном.
 */
@Slf4j
@Configuration
public class TelegramBotConfig {

    @Bean
    public TelegramClient telegramClient(TelegramBotProperties properties) {
        return new OkHttpTelegramClient(properties.token());
    }

    @SneakyThrows
    @Bean
    public User botUser(TelegramClient telegramClient) {
        return telegramClient.execute(new GetMe());
    }

    @Bean
    public CommandRegistry commandRegistry(TelegramClient telegramClient, List<IBotCommand> commands, User botUser) {
        CommandRegistry registry = new CommandRegistry(telegramClient, true, botUser::getUserName);
        commands.forEach(command -> {
            if (!registry.register(command)) {
                throw new IllegalStateException("Duplicate bot command /" + command.getCommandIdentifier());
            }
        });
        return registry;
    }

    @SneakyThrows
    @Bean
    public TelegramBotsLongPollingApplication telegramBotsApplication(
            TelegramBotProperties properties,
            User botUser,
            ReelsBot reelsBot) {

        log.info("Запуск бота @{}", botUser.getUserName());
        TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication();
        botsApplication.registerBot(properties.token(), reelsBot);
        return botsApplication;
    }
}
