package com.example.contract.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.contract.contract.service.ContractTextExtractionService;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ContractTextExtractionServiceContextTest {

    @TempDir
    Path uploadDir;

    @Test
    void springContextCreatesExtractionServiceWithFileStorageDependency() {
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withBean(DatabaseProperties.class)
                .withBean(FileStorageService.class, () -> new FileStorageService(new DatabaseProperties(), java.util.List.of(uploadDir)))
                .withBean(ContractTextExtractionService.class);

        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ContractTextExtractionService.class);
            assertThat(context).getBean(ContractTextExtractionService.class);
        });
    }
}
