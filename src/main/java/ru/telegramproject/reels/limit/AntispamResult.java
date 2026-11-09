package ru.telegramproject.reels.limit;

import java.time.Duration;

/**
 * Ответ антиспама на сообщение человека.
 *
 * @param muteDuration на сколько наказан; заполнено только для {@link Status#MUTED}
 */
public record AntispamResult(Status status, Duration muteDuration) {

    public enum Status {
        /**
         * Лимит не превышен — сообщение обрабатывается.
         */
        ALLOWED,
        /**
         * Лимит превышен только что — бот один раз сообщает, на сколько замолкает.
         */
        MUTED,
        /**
         * Наказание уже идёт — бот молча пропускает сообщение.
         */
        IGNORED
    }

    static AntispamResult allowed() {
        return new AntispamResult(Status.ALLOWED, null);
    }

    static AntispamResult muted(Duration muteDuration) {
        return new AntispamResult(Status.MUTED, muteDuration);
    }

    static AntispamResult ignored() {
        return new AntispamResult(Status.IGNORED, null);
    }
}
