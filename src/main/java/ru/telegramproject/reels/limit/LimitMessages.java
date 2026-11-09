package ru.telegramproject.reels.limit;

import java.time.Duration;

public final class LimitMessages {

    private static final String MUTED = "Слишком много сообщений подряд. Бот не будет отвечать тебе %s.";
    private static final String QUEUE_FULL = "Сервис перегружен, пожалуйста, подожди.";

    private LimitMessages() {
    }

    public static String muted(Duration muteDuration) {
        return MUTED.formatted(duration(muteDuration));
    }

    public static String queueFull() {
        return QUEUE_FULL;
    }

    /**
     * «минуту», «15 минут», «час», «2 часа», «сутки» — в форме после «на» и «через».
     */
    static String duration(Duration duration) {
        if (duration.toDays() > 0 && duration.toHoursPart() == 0 && duration.toMinutesPart() == 0) {
            return amount(duration.toDays(), "сутки", "суток", "суток");
        }
        if (duration.toHours() > 0) {
            return amount(ceil(duration.toMinutes(), 60), "час", "часа", "часов");
        }
        if (duration.toMinutes() > 0) {
            return amount(ceil(duration.toSeconds(), 60), "минуту", "минуты", "минут");
        }
        return amount(Math.max(duration.toSeconds(), 1), "секунду", "секунды", "секунд");
    }

    private static String amount(long value, String one, String few, String many) {
        if (value == 1) {
            return one;
        }
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
