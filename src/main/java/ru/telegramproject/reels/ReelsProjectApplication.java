package ru.telegramproject.reels;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ru.telegramproject.reels.config.properties.DownloaderProperties;
import ru.telegramproject.reels.config.properties.TelegramBotProperties;

@SpringBootApplication
@EnableConfigurationProperties({TelegramBotProperties.class, DownloaderProperties.class})
public class ReelsProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReelsProjectApplication.class, args);
    }
}
