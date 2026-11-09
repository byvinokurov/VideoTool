package ru.telegramproject.reels.limit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;
import ru.telegramproject.reels.config.properties.LimitProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;

/**
 * Антиспам: считает сообщения каждого человека и при превышении лимита заставляет бота молчать.
 */
@Component
public class AntispamService {

    private final LimitProperties.Antispam limits;
    private final Cache<Long, UserActivity> users;

    public AntispamService(LimitProperties properties) {
        this.limits = properties.antispam();
        this.users = Caffeine.newBuilder()
                .expireAfterAccess(memoryTime(limits))
                .build();
    }

    public AntispamResult register(Long userId) {
        return users.get(userId, id -> new UserActivity()).register(Instant.now(), limits);
    }

    /**
     * Человека можно забыть, только когда прошли и самое длинное наказание, и период прощения после него.
     */
    private static Duration memoryTime(LimitProperties.Antispam limits) {
        return Collections.max(limits.mutes()).plus(limits.forgiveAfter());
    }

    /**
     * Сообщения и наказания одного человека. Синхронизация — на случай нескольких сообщений одновременно.
     */
    private static final class UserActivity {

        private final Deque<Instant> recentMessages = new ArrayDeque<>();
        private Instant mutedUntil = Instant.EPOCH;
        private int strikes;

        synchronized AntispamResult register(Instant now, LimitProperties.Antispam limits) {
            if (now.isBefore(mutedUntil)) {
                return AntispamResult.ignored();
            }
            recentMessages.addLast(now);
            while (recentMessages.getFirst().isBefore(now.minus(limits.window()))) {
                recentMessages.removeFirst();
            }
            return recentMessages.size() > limits.maxMessages() ? mute(now, limits) : AntispamResult.allowed();
        }

        /**
         * Каждое следующее наказание длиннее. Спокойный период считается от конца прошлого наказания,
         * иначе после суточного наказания отсчёт сбрасывался бы сразу.
         */
        private AntispamResult mute(Instant now, LimitProperties.Antispam limits) {
            if (now.isAfter(mutedUntil.plus(limits.forgiveAfter()))) {
                strikes = 0;
            }
            Duration duration = limits.mutes().get(Math.min(strikes, limits.mutes().size() - 1));
            strikes++;
            mutedUntil = now.plus(duration);
            recentMessages.clear();
            return AntispamResult.muted(duration);
        }
    }
}
