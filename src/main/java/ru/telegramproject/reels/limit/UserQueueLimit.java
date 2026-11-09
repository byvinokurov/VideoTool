package ru.telegramproject.reels.limit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;
import ru.telegramproject.reels.config.properties.LimitProperties;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Semaphore;

/**
 * Очередь одного человека: сколько его ссылок качаются одновременно и сколько ждут.
 */
@Component
public class UserQueueLimit {

    private final LimitProperties.PerUser limits;
    /**
     * Человек забывается через сутки без обращений; загрузка и ожидание длятся минуты.
     */
    private final Cache<Long, UserQueue> users = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofDays(1))
            .build();

    public UserQueueLimit(LimitProperties properties) {
        this.limits = properties.perUser();
    }

    /**
     * Место в очереди человека; пусто, если у него уже качаются и ждут столько ссылок, сколько разрешено.
     */
    public Optional<Turn> tryEnter(Long userId) {
        UserQueue queue = users.get(userId, id -> new UserQueue(
                new Semaphore(limits.maxConcurrentDownloads() + limits.maxWaitingDownloads()),
                new Semaphore(limits.maxConcurrentDownloads(), true)));
        return queue.places().tryAcquire() ? Optional.of(new Turn(queue)) : Optional.empty();
    }

    /**
     * @param places    все ссылки человека в работе: качаются и ждут
     * @param downloads ссылки, которые качаются прямо сейчас
     */
    private record UserQueue(Semaphore places, Semaphore downloads) {
    }

    /**
     * Место человека в очереди. Закрытие освобождает ровно те семафоры, которые были заняты.
     */
    public static final class Turn implements AutoCloseable {

        private final UserQueue queue;
        private boolean downloading;

        private Turn(UserQueue queue) {
            this.queue = queue;
        }

        /**
         * Ждёт, пока закончатся предыдущие загрузки этого человека.
         */
        public void awaitDownload() throws InterruptedException {
            queue.downloads().acquire();
            downloading = true;
        }

        @Override
        public void close() {
            if (downloading) {
                queue.downloads().release();
            }
            queue.places().release();
        }
    }
}
