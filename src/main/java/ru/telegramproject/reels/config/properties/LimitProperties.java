package ru.telegramproject.reels.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "limits")
public record LimitProperties(
        @Valid @NotNull Antispam antispam,
        @Valid @NotNull Queue queue) {

    /**
     * Антиспам: больше maxMessages сообщений за window — бот молчит, каждый следующий раз дольше.
     */
    public record Antispam(
            @Positive int maxMessages,
            @NotNull @DurationMin(seconds = 1) Duration window,
            @NotEmpty List<@NotNull @DurationMin(seconds = 1) Duration> mutes,
            @NotNull @DurationMin(seconds = 1) Duration forgiveAfter) {
    }

    /**
     * Общая очередь бота: сколько видео одновременно в работе (качаются и ждут).
     */
    public record Queue(@Positive int maxSize) {
    }
}
