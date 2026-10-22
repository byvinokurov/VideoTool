package ru.telegramproject.reels;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ru.telegramproject.reels.config.TelegramBotProperties;

@SpringBootApplication
@EnableConfigurationProperties(TelegramBotProperties.class)
public class ReelsProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReelsProjectApplication.class, args);
    }
}
