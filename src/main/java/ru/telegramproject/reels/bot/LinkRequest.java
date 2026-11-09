package ru.telegramproject.reels.bot;

/**
 * Ссылка от человека: кто прислал, в какой чат и на какое сообщение отвечать видео или ошибкой.
 */
public record LinkRequest(Long userId, Long chatId, Integer messageId, String link) {
}
