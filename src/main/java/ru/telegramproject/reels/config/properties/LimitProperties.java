package ru.telegramproject.reels.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "limits")
public record LimitProperties(
        @Valid @NotNull Antispam antispam,
        @Valid @NotNull Queue queue,
        @Valid @NotNull PerUser perUser) {

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

    /**
     * Ограничения одного человека: сколько его видео качаются и ждут одновременно и сколько он может скачать.
     */
    public record PerUser(
            @Positive int maxConcurrentDownloads,
            @PositiveOrZero int maxWaitingDownloads,
            @Positive int downloadsPerHour,
            @Positive int downloadsPerDay,
            @NotNull DataSize bytesPerDay) {

        /**
         * Для DataSize нет стандартной аннотации, а 0 заблокировал бы человека после первого же видео.
         */
        public PerUser {
            if (bytesPerDay != null && bytesPerDay.toBytes() <= 0) {
                throw new IllegalArgumentException("limits.per-user.bytes-per-day must be positive");
            }
        }
    }
}
