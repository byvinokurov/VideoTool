package ru.telegramproject.reels.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "telegram.bot")
public record TelegramBotProperties(
        @NotBlank(message = "Токен бота не задан: проверьте переменную окружения.")
        String token) {

    @Override
    public String toString() {
        return "TelegramBotProperties[token=***]";
    }
}
