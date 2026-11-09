package ru.telegramproject.reels.limit;

import org.springframework.stereotype.Component;
import ru.telegramproject.reels.config.properties.LimitProperties;

import java.util.concurrent.Semaphore;

/**
 * Общая очередь бота: сколько ссылок всех людей одновременно в работе.
 */
@Component
public class BotQueueLimit {

    private final Semaphore places;

    public BotQueueLimit(LimitProperties properties) {
        this.places = new Semaphore(properties.queue().maxSize());
    }

    public boolean tryEnter() {
        return places.tryAcquire();
    }

    public void leave() {
        places.release();
    }
}
