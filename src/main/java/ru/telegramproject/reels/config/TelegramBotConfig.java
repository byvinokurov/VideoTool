package ru.telegramproject.reels.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.telegramproject.reels.bot.ReelsBot;

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

    @Bean
    public TelegramBotsLongPollingApplication telegramBotsApplication(
            TelegramBotProperties properties,
            TelegramClient telegramClient,
            ReelsBot reelsBot) throws TelegramApiException {

        User me = telegramClient.execute(new GetMe());
        log.info("Запуск бота @{}", me.getUserName());

        TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication();
        botsApplication.registerBot(properties.token(), reelsBot);
        return botsApplication;
    }
}
