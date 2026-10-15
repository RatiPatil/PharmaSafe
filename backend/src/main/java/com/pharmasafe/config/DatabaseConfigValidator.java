package com.pharmasafe.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Validates database password configuration at application startup.
 *
 * Prevents obscure SCRAM-SHA-256 driver errors by providing a clear, actionable
 * explanation when the database password is missing from the environment or .env file.
 *
 * Ensures security: secret values are NEVER printed, logged, or exposed in error messages.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DatabaseConfigValidator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfigValidator.class);

    private final Environment environment;

    public DatabaseConfigValidator(Environment environment) {
        this.environment = environment;
        // Perform initial validation during bean construction
        validate(environment);
    }

    @Override
    public void run(String... args) {
        // Redundant verification on CommandLineRunner startup
        validate(environment);
    }

    /**
     * Verifies that a valid database password is configured for non-test profiles.
     *
     * @param env Spring Environment
     * @throws IllegalStateException if database password is required but missing
     */
    public static void validate(Environment env) {
        if (env == null) {
            return;
        }

        // 1. Skip validation if running under test environment
        if (Arrays.asList(env.getActiveProfiles()).contains("test") ||
            env.containsProperty("org.springframework.boot.test.context.SpringBootTestContextBootstrapper") ||
            "true".equalsIgnoreCase(env.getProperty("org.springframework.boot.test.context.SpringBootTestContextBootstrapper"))) {
            log.debug("Database password validation skipped: running in test environment.");
            return;
        }

        // 2. Skip validation if datasource URL is configured for in-memory H2
        String dsUrl = env.getProperty("spring.datasource.url");
        if (dsUrl == null) {
            dsUrl = env.getProperty("DB_URL");
        }
        if (dsUrl != null && dsUrl.contains(":h2:")) {
            log.debug("Database password validation skipped: datasource is in-memory H2.");
            return;
        }

        // 3. Check for configured password in standard Spring or project environment properties
        String password = env.getProperty("spring.datasource.password");
        if (password == null || password.trim().isEmpty()) {
            password = env.getProperty("DB_PASSWORD");
        }

        if (password == null || password.trim().isEmpty()) {
            String errorMessage = """

                ================================================================================
                PHARMASAFE STARTUP CONFIGURATION ERROR: PostgreSQL Password Missing
                ================================================================================
                The backend service cannot connect to PostgreSQL because no database password
                was provided. PostgreSQL 18 requires SCRAM-SHA-256 authenticated credentials.

                How to resolve this issue:
                1. Ensure a valid '.env' file exists in the repository root (D:\\PharmaSafeFresh\\.env)
                   containing the application password:
                     DB_PASSWORD=your_secure_password
                   Refer to '.env.example' for the expected configuration template.

                2. Alternatively, supply the environment variable in your current terminal session:
                   PowerShell:
                     $env:DB_PASSWORD = "your_secure_password"
                     mvn spring-boot:run

                3. Or use the automated backend startup script which loads '.env' automatically:
                     .\\run-backend.ps1

                Security Notice: Password values and secrets are never displayed or logged.
                ================================================================================
                """;

            System.err.println(errorMessage);
            throw new IllegalStateException(
                    "PostgreSQL database password is not configured. Please supply 'DB_PASSWORD' in .env or the process environment."
            );
        }

        log.debug("PostgreSQL database password configuration validated successfully (presence confirmed, value masked).");
    }
}
