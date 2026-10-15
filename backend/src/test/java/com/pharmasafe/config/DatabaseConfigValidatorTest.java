package com.pharmasafe.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseConfigValidatorTest {

    @Test
    @DisplayName("Validation fails cleanly with clear message when DB password is empty in production/default profile")
    void testValidationFailsWhenPasswordMissing() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("default");
        env.setProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/pharmasafe_db");
        env.setProperty("spring.datasource.password", "");
        env.setProperty("DB_PASSWORD", "");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                DatabaseConfigValidator.validate(env)
        );

        assertTrue(ex.getMessage().contains("PostgreSQL database password is not configured"));
        assertTrue(ex.getMessage().contains("DB_PASSWORD"));
    }

    @Test
    @DisplayName("Validation fails when password property is completely absent")
    void testValidationFailsWhenPasswordNull() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("production");
        env.setProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/pharmasafe_db");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                DatabaseConfigValidator.validate(env)
        );

        assertTrue(ex.getMessage().contains("PostgreSQL database password is not configured"));
    }

    @Test
    @DisplayName("Validation succeeds when valid DB_PASSWORD is provided")
    void testValidationSucceedsWithPassword() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("default");
        env.setProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/pharmasafe_db");
        env.setProperty("DB_PASSWORD", "AValidSecurePassword123!");

        assertDoesNotThrow(() -> DatabaseConfigValidator.validate(env));
    }

    @Test
    @DisplayName("Validation succeeds when spring.datasource.password is provided")
    void testValidationSucceedsWithSpringDatasourcePassword() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("default");
        env.setProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/pharmasafe_db");
        env.setProperty("spring.datasource.password", "AValidSecurePassword123!");

        assertDoesNotThrow(() -> DatabaseConfigValidator.validate(env));
    }

    @Test
    @DisplayName("Validation is skipped when active profile is 'test'")
    void testValidationSkippedInTestProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");
        env.setProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/pharmasafe_db");
        // No password provided, but test profile skips validation
        assertDoesNotThrow(() -> DatabaseConfigValidator.validate(env));
    }

    @Test
    @DisplayName("Validation is skipped when datasource is in-memory H2 database")
    void testValidationSkippedForH2() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("default");
        env.setProperty("spring.datasource.url", "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        // No password provided, but H2 skips validation
        assertDoesNotThrow(() -> DatabaseConfigValidator.validate(env));
    }
}
