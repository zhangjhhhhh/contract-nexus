package com.example.contract.common;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FileControllerTest {

    @Test
    void pdfPreviewUsesInlinePdfResponse() throws Exception {
        FileStorageService fileStorageService = new FileStorageService(new DatabaseProperties());
        StoredFile storedFile = fileStorageService.store(
                "preview.pdf",
                "application/octet-stream",
                4,
                new ByteArrayInputStream("%PDF".getBytes(StandardCharsets.UTF_8)));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new FileController(fileStorageService))
                .build();

        mockMvc.perform(get("/api/files/preview/{storedName}", storedFile.storedName())
                        .param("name", "合同预览.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("inline")));
    }
}
