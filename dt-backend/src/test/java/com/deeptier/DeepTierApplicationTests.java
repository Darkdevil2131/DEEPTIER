package com.deeptier;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DeepTierApplicationTests {

    @Test
    void contextLoads() {
        // Spring context must start cleanly with H2 in-memory DB
    }
}
