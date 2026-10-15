package com.pharmasafe.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Arrays;
import java.util.Map;

/**
 * Spring Boot EnvironmentPostProcessor that automatically discovers and registers
 * configuration from the root .env file into the Spring Environment, and validates
 * that required database credentials are present before database connection pooling starts.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // Skip completely if running under test context
        if (isTestEnvironment(environment)) {
            return;
        }

        // 1. Load .env properties if available
        Map<String, Object> dotenvProps = DotenvLoader.load();
        if (!dotenvProps.isEmpty()) {
            if (environment.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
                environment.getPropertySources().addAfter(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new MapPropertySource("dotenvProperties", dotenvProps)
                );
            } else {
                environment.getPropertySources().addLast(
                        new MapPropertySource("dotenvProperties", dotenvProps)
                );
            }
        }

        // 2. Perform early database configuration validation
        DatabaseConfigValidator.validate(environment);
    }

    private boolean isTestEnvironment(ConfigurableEnvironment environment) {
        if (Arrays.asList(environment.getActiveProfiles()).contains("test")) {
            return true;
        }
        if (environment.containsProperty("org.springframework.boot.test.context.SpringBootTestContextBootstrapper")) {
            return true;
        }
        if (environment.getPropertySources().contains("Inlined Test Properties")) {
            return true;
        }
        return false;
    }
}
