package ru.telegramproject.reels.limit;

import java.time.Duration;

/**
 * Ответ квоты на новую ссылку человека.
 *
 * @param limit      превышенный лимит: число загрузок, а для {@link Status#DAILY_BYTES_LIMIT} — байты
 * @param retryAfter через сколько лимит освободится
 */
public record UserQuotaResult(Status status, long limit, Duration retryAfter) {

    public enum Status {
        ALLOWED,
        HOURLY_LIMIT,
        DAILY_LIMIT,
        DAILY_BYTES_LIMIT
    }

    static UserQuotaResult allowed() {
        return new UserQuotaResult(Status.ALLOWED, 0, Duration.ZERO);
    }

    static UserQuotaResult exceeded(Status status, long limit, Duration retryAfter) {
        return new UserQuotaResult(status, limit, retryAfter);
    }
}
