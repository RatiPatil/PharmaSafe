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
    @DisplayName("DotenvLoader finds root .env file in base directory or parent directory")
    void testFindEnvFileInHierarchy(@TempDir Path tempDir) throws IOException {
        Path mockEnv = tempDir.resolve(".env");
        Files.writeString(mockEnv, "SERVER_PORT=8080\n");

        Path subDir = tempDir.resolve("backend");
        Files.createDirectories(subDir);

        // Discovery from sub-directory traversing up to parent .env
        Path discoveredFromSub = DotenvLoader.findEnvFile(subDir);
        assertNotNull(discoveredFromSub, ".env file should be located in parent directory of subDir");
        assertTrue(Files.exists(discoveredFromSub), "Discovered path must exist");
        assertEquals(mockEnv.toAbsolutePath().normalize(), discoveredFromSub.toAbsolutePath().normalize());

        // Discovery directly in the base directory
        Path discoveredFromRoot = DotenvLoader.findEnvFile(tempDir);
        assertNotNull(discoveredFromRoot, ".env file should be located directly in tempDir");
        assertEquals(mockEnv.toAbsolutePath().normalize(), discoveredFromRoot.toAbsolutePath().normalize());
    }

    @Test
    @DisplayName("DotenvLoader returns null when no .env exists")
    void testFindEnvFileReturnsNullWhenMissing(@TempDir Path tempDir) {
        Path emptySubDir = tempDir.resolve("empty-project");
        Path discovered = DotenvLoader.findEnvFile(emptySubDir);
        assertNull(discovered, "Should return null when no .env exists in target directory hierarchy");
    }

    @Test
    @DisplayName("DotenvLoader finds custom path specified via system property")
    void testFindEnvFileCustomPath(@TempDir Path tempDir) throws IOException {
        Path customEnv = tempDir.resolve("custom.env");
        Files.writeString(customEnv, "SERVER_PORT=9090\n");

        System.setProperty("ENV_FILE", customEnv.toAbsolutePath().toString());
        try {
            Path found = DotenvLoader.findEnvFile();
            assertNotNull(found);
            assertEquals(customEnv.toAbsolutePath().normalize(), found.toAbsolutePath().normalize());
        } finally {
            System.clearProperty("ENV_FILE");
        }
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

        Map<String, Object> props = DotenvLoader.load(mockEnv);
        assertNotNull(props);
        assertEquals("8080", props.get("SERVER_PORT"));
        assertEquals("localhost", props.get("DB_HOST"));
        assertEquals("pharmasafe_db", props.get("DB_NAME"));
        assertEquals("TestPassword123!", props.get("DB_PASSWORD"));
        assertEquals("jdbc:postgresql://localhost:5432/pharmasafe_db?sslmode=disable", props.get("DB_URL"));
        assertFalse(props.containsKey("INVALID_LINE_NO_EQUALS"));
        assertFalse(props.containsKey(""));
    }
}
