package com.example.contract.contract.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.contract.common.DatabaseProperties;
import com.example.contract.common.FileStorageService;
import com.example.contract.common.StoredFile;
import com.example.contract.contract.model.Attachment;
import com.example.contract.contract.model.Contract;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContractTextExtractionServiceTest {

    @TempDir
    Path uploadDir;

    @Test
    void extractsDocxTextFromStoredFileContent() throws Exception {
        Path docx = uploadDir.resolve("stored-contract.docx");
        try (XWPFDocument document = new XWPFDocument();
             OutputStream outputStream = Files.newOutputStream(docx)) {
            document.createParagraph().createRun().setText("付款条款：30日内支付。");
            document.write(outputStream);
        }

        FileStorageService fileStorageService = new FileStorageService(new DatabaseProperties());
        StoredFile storedFile;
        try (InputStream inputStream = Files.newInputStream(docx)) {
            storedFile = fileStorageService.store(
                    "stored-contract.docx",
                    "合同.docx",
                    "docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    Files.size(docx),
                    inputStream);
        }

        Contract contract = new Contract();
        contract.setId("CON-1");
        contract.setNum("HT-001");
        contract.setAttachments(List.of(new Attachment(
                "合同.docx",
                "docx",
                "/api/files/" + storedFile.storedName(),
                null)));

        ContractTextExtractionService service = new ContractTextExtractionService(fileStorageService);

        List<ContractTextExtractionService.AttachmentText> result = service.extractReviewTexts(contract);

        assertEquals(1, result.size());
        assertEquals("合同.docx", result.get(0).name());
        assertEquals("docx", result.get(0).type());
        assertTrue(result.get(0).text().contains("付款条款：30日内支付。"));
    }
}
