package ru.telegramproject.reels.limit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;
import ru.telegramproject.reels.config.properties.LimitProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static ru.telegramproject.reels.limit.UserQuotaResult.Status.DAILY_BYTES_LIMIT;
import static ru.telegramproject.reels.limit.UserQuotaResult.Status.DAILY_LIMIT;
import static ru.telegramproject.reels.limit.UserQuotaResult.Status.HOURLY_LIMIT;

/**
 * Квоты одного человека: загрузки за час и за сутки, объём видео за сутки.
 */
@Component
public class UserQuotaLimit {

    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofDays(1);

    private final LimitProperties.PerUser limits;
    private final Cache<Long, UserDownloads> users = Caffeine.newBuilder()
            .expireAfterAccess(DAY)
            .build();

    public UserQuotaLimit(LimitProperties properties) {
        this.limits = properties.perUser();
    }

    /**
     * Проверяет квоты и, если они не превышены, сразу засчитывает новую загрузку.
     */
    public UserQuotaResult tryAcquire(Long userId) {
        return downloadsOf(userId).tryAcquire(Instant.now(), limits);
    }

    /**
     * Проверяет объём ещё раз перед самым скачиванием: пока ссылка ждала очереди,
     * могли отправиться другие видео этого человека.
     */
    public UserQuotaResult checkDailyBytes(Long userId) {
        return downloadsOf(userId).checkDailyBytes(Instant.now(), limits);
    }

    public void addDeliveredBytes(Long userId, long bytes) {
        downloadsOf(userId).addDeliveredBytes(Instant.now(), bytes);
    }

    private UserDownloads downloadsOf(Long userId) {
        return users.get(userId, id -> new UserDownloads());
    }

    /**
     * Загрузки одного человека за последние сутки. Синхронизация — на случай нескольких ссылок одновременно.
     */
    private static final class UserDownloads {

        private final Deque<Instant> starts = new ArrayDeque<>();
        private final Deque<DeliveredBytes> delivered = new ArrayDeque<>();

        synchronized UserQuotaResult tryAcquire(Instant now, LimitProperties.PerUser limits) {
            forgetOlderThanDay(now);
            UserQuotaResult result = check(now, limits);
            if (result.status() == UserQuotaResult.Status.ALLOWED) {
                starts.addLast(now);
            }
            return result;
        }

        synchronized UserQuotaResult checkDailyBytes(Instant now, LimitProperties.PerUser limits) {
            forgetOlderThanDay(now);
            return checkBytes(now, limits);
        }

        synchronized void addDeliveredBytes(Instant now, long bytes) {
            delivered.addLast(new DeliveredBytes(now, bytes));
        }

        private UserQuotaResult check(Instant now, LimitProperties.PerUser limits) {
            if (starts.size() >= limits.downloadsPerDay()) {
                return UserQuotaResult.exceeded(DAILY_LIMIT, limits.downloadsPerDay(), until(starts.getFirst().plus(DAY), now));
            }
            UserQuotaResult bytes = checkBytes(now, limits);
            if (bytes.status() != UserQuotaResult.Status.ALLOWED) {
                return bytes;
            }
            List<Instant> lastHour = starts.stream()
                    .filter(start -> start.isAfter(now.minus(HOUR)))
                    .toList();
            if (lastHour.size() >= limits.downloadsPerHour()) {
                return UserQuotaResult.exceeded(HOURLY_LIMIT, limits.downloadsPerHour(), until(lastHour.getFirst().plus(HOUR), now));
            }
            return UserQuotaResult.allowed();
        }

        private UserQuotaResult checkBytes(Instant now, LimitProperties.PerUser limits) {
            long bytesLimit = limits.bytesPerDay().toBytes();
            if (!delivered.isEmpty() && totalBytes() >= bytesLimit) {
                return UserQuotaResult.exceeded(DAILY_BYTES_LIMIT, bytesLimit, until(bytesFreedAt(bytesLimit), now));
            }
            return UserQuotaResult.allowed();
        }

        private void forgetOlderThanDay(Instant now) {
            Instant dayAgo = now.minus(DAY);
            while (!starts.isEmpty() && !starts.getFirst().isAfter(dayAgo)) {
                starts.removeFirst();
            }
            while (!delivered.isEmpty() && !delivered.getFirst().at().isAfter(dayAgo)) {
                delivered.removeFirst();
            }
        }

        private long totalBytes() {
            return delivered.stream().mapToLong(DeliveredBytes::bytes).sum();
        }

        /**
         * Когда из суточного окна уйдёт столько старых видео, что объём станет меньше лимита.
         */
        private Instant bytesFreedAt(long bytesLimit) {
            long remaining = totalBytes();
            for (DeliveredBytes entry : delivered) {
                remaining -= entry.bytes();
                if (remaining < bytesLimit) {
                    return entry.at().plus(DAY);
                }
            }
            return delivered.getLast().at().plus(DAY);
        }

        private static Duration until(Instant moment, Instant now) {
            return Duration.between(now, moment);
        }

        private record DeliveredBytes(Instant at, long bytes) {
        }
    }
}
