package ru.telegramproject.reels;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"telegram.bot.token=111111:TEST-TOKEN-NOT-A-REAL-ONE-0000000000"})
class ReelsProjectApplicationTests {

    @Test
    void contextLoads() {
    }
}
