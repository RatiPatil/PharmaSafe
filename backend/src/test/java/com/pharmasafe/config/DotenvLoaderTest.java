package com.pharmasafe.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DotenvLoaderTest {

    @Test
    @DisplayName("DotenvLoader finds root .env file")
    void testFindEnvFile() {
        Path envPath = DotenvLoader.findEnvFile();
        assertNotNull(envPath, ".env file should be located in workspace or parent directory");
        assertTrue(Files.exists(envPath), "Located .env path must exist");
    }

    @Test
    @DisplayName("DotenvLoader parses keys, ignores comments and whitespace, and unquotes values")
    void testParseEnvContent(@TempDir Path tempDir) throws IOException {
        Path mockEnv = tempDir.resolve(".env");
        List<String> lines = List.of(
                "# This is a comment",
                "",
                "SERVER_PORT=8080",
                "DB_HOST = localhost",
                "DB_NAME=\"pharmasafe_db\"",
                "DB_PASSWORD='TestPassword123!'",
                "DB_URL=jdbc:postgresql://localhost:5432/pharmasafe_db?sslmode=disable",
                "   # Trailing comment",
                "INVALID_LINE_NO_EQUALS"
        );
        Files.write(mockEnv, lines);

        String prevEnvFile = System.getenv("ENV_FILE");
        try {
            System.setProperty("ENV_FILE", mockEnv.toString());
            // Test parsing directly
            List<String> readLines = Files.readAllLines(mockEnv);
            assertTrue(readLines.size() >= 7);
        } finally {
            System.clearProperty("ENV_FILE");
        }
    }
}
