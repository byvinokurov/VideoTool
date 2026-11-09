package ru.telegramproject.reels.limit;

import org.springframework.util.unit.DataSize;

import java.time.Duration;

/**
 * Тексты ответов бота о лимитах.
 */
public final class LimitMessages {

    private static final String MUTED = "Слишком много сообщений подряд. Бот не будет отвечать тебе %s.";
    private static final String QUEUE_FULL = "Сервис перегружен, пожалуйста, подожди.";
    private static final String TOO_MANY_WAITING = "Погоди, сначала докачаю прошлые ссылки — эту пришли потом.";
    private static final String PER_HOUR = "Лимит — %s в час. Следующая — через %s.";
    private static final String PER_DAY = "Лимит — %s в сутки. Следующая — через %s.";
    private static final String BYTES_PER_DAY = "Лимит — %s видео в сутки. Следующая загрузка — через %s.";

    private LimitMessages() {
    }

    public static String muted(Duration muteDuration) {
        return MUTED.formatted(duration(muteDuration));
    }

    public static String queueFull() {
        return QUEUE_FULL;
    }

    public static String tooManyWaiting() {
        return TOO_MANY_WAITING;
    }

    public static String quotaExceeded(UserQuotaResult quota) {
        return switch (quota.status()) {
            case HOURLY_LIMIT -> PER_HOUR.formatted(downloads(quota.limit()), duration(quota.retryAfter()));
            case DAILY_LIMIT -> PER_DAY.formatted(downloads(quota.limit()), duration(quota.retryAfter()));
            case DAILY_BYTES_LIMIT -> BYTES_PER_DAY.formatted(size(quota.limit()), duration(quota.retryAfter()));
            case ALLOWED -> throw new IllegalArgumentException("Quota is not exceeded");
        };
    }

    /**
     * «минуту», «15 минут», «час», «2 часа», «сутки» — в форме после «на» и «через».
     */
    static String duration(Duration duration) {
        long seconds = Math.max(duration.toSeconds(), 1);
        if (seconds < 60) {
            return amount(seconds, "секунду", "секунды", "секунд");
        }
        long minutes = ceil(seconds, 60);
        if (minutes < 60) {
            return amount(minutes, "минуту", "минуты", "минут");
        }
        if (duration.toDays() > 0 && duration.toHoursPart() == 0 && duration.toMinutesPart() == 0) {
            return amount(duration.toDays(), "сутки", "суток", "суток");
        }
        return amount(ceil(minutes, 60), "час", "часа", "часов");
    }

    /**
     * «25 загрузок», «21 загрузка».
     */
    private static String downloads(long count) {
        return count(count, "загрузка", "загрузки", "загрузок");
    }

    /**
     * «3 ГБ», «500 МБ».
     */
    private static String size(long bytes) {
        DataSize size = DataSize.ofBytes(bytes);
        return bytes % DataSize.ofGigabytes(1).toBytes() == 0
                ? size.toGigabytes() + " ГБ"
                : size.toMegabytes() + " МБ";
    }

    /**
     * Как count, но единицу пишет без числа: «час», а не «1 час».
     */
    private static String amount(long value, String one, String few, String many) {
        return value == 1 ? one : count(value, one, few, many);
    }

    private static String count(long value, String one, String few, String many) {
        long lastTwo = value % 100;
        long last = value % 10;
        String word = last == 1 && lastTwo != 11 ? one
                : last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14) ? few
                : many;
        return value + " " + word;
    }

    private static long ceil(long value, long unit) {
        return (value + unit - 1) / unit;
    }
}
