package ru.telegramproject.reels.limit;

import org.springframework.stereotype.Component;
import ru.telegramproject.reels.config.properties.LimitProperties;

import java.util.concurrent.Semaphore;

/**
 * Общая очередь бота: сколько видео одновременно в работе (качаются и ждут свободного места).
 * Сверх лимита ссылка сразу отклоняется, а не ждёт без конца.
 */
@Component
public class DownloadQueueLimit {

    private final Semaphore places;

    public DownloadQueueLimit(LimitProperties properties) {
        this.places = new Semaphore(properties.queue().maxSize());
    }

    public boolean tryEnter() {
        return places.tryAcquire();
    }

    public void leave() {
        places.release();
    }
}
