package com.pharmasafe;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PharmaSafeApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the Spring Boot application context bootstraps successfully
    }
}
