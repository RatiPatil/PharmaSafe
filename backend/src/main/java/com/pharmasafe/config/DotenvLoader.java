package com.pharmasafe.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Utility for discovering and parsing local .env files into property maps.
 *
 * Automatically locates the root .env file regardless of whether the application is executed
 * from the repository root or the backend subdirectory.
 *
 * Strictly enforces security: secret values (passwords, tokens) are NEVER logged or printed.
 */
public final class DotenvLoader {

    private static final Logger log = LoggerFactory.getLogger(DotenvLoader.class);
    private static volatile boolean logged = false;

    private DotenvLoader() {
        // Utility class
    }

    /**
     * Finds and parses the default .env file if present.
     *
     * @return Map of properties loaded from .env
     */
    public static Map<String, Object> load() {
        return load(findEnvFile());
    }

    /**
     * Parses the specified .env file if present.
     *
     * @param envPath Path to the .env file
     * @return Map of properties loaded from the file, or empty map if null
     */
    public static Map<String, Object> load(Path envPath) {
        Map<String, Object> properties = new LinkedHashMap<>();

        if (envPath == null) {
            log.debug("No .env file found in default locations. Proceeding with system environment.");
            return properties;
        }

        try {
            List<String> lines = Files.readAllLines(envPath, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line == null) continue;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                int eqIdx = trimmed.indexOf('=');
                if (eqIdx <= 0) {
                    continue;
                }

                String key = trimmed.substring(0, eqIdx).trim();
                String value = trimmed.substring(eqIdx + 1).trim();

                // Strip outer quotes if present
                if ((value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) ||
                    (value.startsWith("'") && value.endsWith("'") && value.length() >= 2)) {
                    value = value.substring(1, value.length() - 1);
                }

                properties.put(key, value);
            }

            if (!logged) {
                log.info("Loaded {} environment configuration properties from: {}", properties.size(), envPath.toAbsolutePath().normalize());
                logged = true;
            }
        } catch (IOException e) {
            log.warn("Failed to read .env file at {}: {}", envPath, e.getMessage());
        }

        return properties;
    }

    /**
     * Searches standard locations for .env file starting from the current working directory.
     *
     * @return Path to .env if found and readable, otherwise null
     */
    public static Path findEnvFile() {
        String customPath = System.getProperty("ENV_FILE");
        if (customPath == null || customPath.isBlank()) {
            customPath = System.getenv("ENV_FILE");
        }
        if (customPath != null && !customPath.isBlank()) {
            Path p = Paths.get(customPath);
            if (Files.isRegularFile(p)) {
                return p;
            }
        }

        String userDir = System.getProperty("user.dir", ".");
        List<Path> candidates = Arrays.asList(
                Paths.get(".env"),
                Paths.get("..", ".env"),
                Paths.get(userDir, ".env"),
                Paths.get(userDir, "..", ".env")
        );

        for (Path candidate : candidates) {
            try {
                if (Files.isRegularFile(candidate) && Files.isReadable(candidate)) {
                    return candidate;
                }
            } catch (Exception ignored) {
                // Skip unreadable paths
            }
        }

        return null;
    }

    /**
     * Searches standard locations for .env file starting from a given base directory.
     *
     * @param baseDir Base directory to search from
     * @return Path to .env if found and readable, otherwise null
     */
    public static Path findEnvFile(Path baseDir) {
        if (baseDir == null) {
            return findEnvFile();
        }

        List<Path> candidates = Arrays.asList(
                baseDir.resolve(".env"),
                baseDir.resolve("..").resolve(".env")
        );

        for (Path candidate : candidates) {
            try {
                Path normalized = candidate.normalize();
                if (Files.isRegularFile(normalized) && Files.isReadable(normalized)) {
                    return normalized;
                }
            } catch (Exception ignored) {
                // Skip unreadable paths
            }
        }

        return null;
    }
}
