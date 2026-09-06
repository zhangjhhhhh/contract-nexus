package com.example.contract.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DotenvLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesBackendEnvWhenStartedFromRepositoryRoot() throws IOException {
        Path backendEnv = tempDir.resolve("backend").resolve(".env");
        Files.createDirectories(backendEnv.getParent());
        Files.writeString(backendEnv, "DB_USERNAME=tester%n", StandardCharsets.UTF_8);

        Optional<Path> resolved = DotenvLoader.resolveEnvFile(tempDir);

        assertTrue(resolved.isPresent());
        assertEquals(backendEnv.normalize(), resolved.get());
    }

    @Test
    void loadsValuesFromEnvFile() throws IOException {
        String key = uniqueKey("LOADS_VALUE");
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "# comment%n%s=\"value=with=equals\"%n".formatted(key), StandardCharsets.UTF_8);

        try {
            DotenvLoader.loadFrom(envFile);

            assertEquals("value=with=equals", System.getProperty(key));
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    void keepsExistingSystemProperty() throws IOException {
        String key = uniqueKey("KEEPS_EXISTING");
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "%s=from-file%n".formatted(key), StandardCharsets.UTF_8);

        try {
            System.setProperty(key, "existing");

            DotenvLoader.loadFrom(envFile);

            assertEquals("existing", System.getProperty(key));
        } finally {
            System.clearProperty(key);
        }
    }

    private static String uniqueKey(String name) {
        return "DOTENV_LOADER_TEST_" + name + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
