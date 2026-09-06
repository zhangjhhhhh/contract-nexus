package com.example.contract.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(DatabaseProperties.class)
            .withBean(FileStorageService.class);

    @Test
    void springContextCreatesFileStorageService() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(FileStorageService.class));
    }

    @Test
    void storesAndReadsFileContent() {
        FileStorageService service = new FileStorageService(new DatabaseProperties());
        byte[] content = "contract bytes".getBytes(StandardCharsets.UTF_8);

        StoredFile stored = service.store(
                "contract.pdf",
                "application/pdf",
                content.length,
                new ByteArrayInputStream(content));

        StoredFile loaded = service.getByStoredName(stored.storedName());

        assertEquals("contract.pdf", loaded.originalName());
        assertEquals("pdf", loaded.type());
        assertArrayEquals(content, loaded.content());
    }

    @Test
    void migratesLegacyUploadsIntoStorageWithoutDeletingFiles() throws Exception {
        Path uploads = tempDir.resolve("uploads");
        Files.createDirectories(uploads);
        Path legacyFile = uploads.resolve("legacy.pdf");
        byte[] content = "legacy bytes".getBytes(StandardCharsets.UTF_8);
        Files.write(legacyFile, content);

        FileStorageService service = new FileStorageService(new DatabaseProperties(), java.util.List.of(uploads));
        service.migrateLegacyUploads();

        StoredFile migrated = service.getByStoredName("legacy.pdf");
        assertArrayEquals(content, migrated.content());
        assertTrue(Files.exists(legacyFile));
    }
}
