package com.example.contract.contract.service;

import com.example.contract.common.FileStorageService;
import com.example.contract.common.StoredFile;
import com.example.contract.contract.model.Attachment;
import com.example.contract.contract.model.Contract;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ContractTextExtractionService {

    private static final Logger LOG = LoggerFactory.getLogger(ContractTextExtractionService.class);
    private static final Set<String> REVIEW_ATTACHMENT_TYPES = Set.of("docx", "pdf");

    private final FileStorageService fileStorageService;

    public ContractTextExtractionService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public List<AttachmentText> extractReviewTexts(Contract contract) {
        List<AttachmentText> texts = new ArrayList<>();
        if (contract.getAttachments() == null || contract.getAttachments().isEmpty()) {
            LOG.warn("[AI审查] 合同没有附件，无法提取合同文字：contractId={}，contractNum={}",
                    contract.getId(), contract.getNum());
            return texts;
        }

        for (Attachment attachment : contract.getAttachments()) {
            String type = normalizeType(attachment);
            if (!REVIEW_ATTACHMENT_TYPES.contains(type)) {
                LOG.info("[AI审查] 跳过非文字提取附件：name={}，type={}", attachment.getName(), attachment.getType());
                continue;
            }
            try {
                StoredFile storedFile = resolveStoredFile(attachment);
                String text = switch (type) {
                    case "docx" -> extractDocxText(storedFile.content());
                    case "pdf" -> extractPdfText(storedFile.content());
                    default -> "";
                };
                String normalized = normalizeText(text);
                if (normalized.isBlank()) {
                    LOG.warn("[AI审查] 附件未提取到文字：name={}，type={}，path={}",
                            attachment.getName(), attachment.getType(), attachment.getPath());
                    continue;
                }
                texts.add(new AttachmentText(attachment.getName(), type, normalized));
                LOG.info("[AI审查] 附件文字提取完成：name={}，type={}，textLength={}",
                        attachment.getName(), type, normalized.length());
            } catch (IOException | RuntimeException | LinkageError exception) {
                LOG.error("[AI审查] 附件文字提取失败：name={}，type={}，path={}，reason={}",
                        attachment.getName(), attachment.getType(), attachment.getPath(), exception.toString(), exception);
            }
        }
        return texts;
    }

    private StoredFile resolveStoredFile(Attachment attachment) {
        if (fileStorageService == null) {
            throw new IllegalStateException("File storage service is not configured");
        }
        String storedName = storedNameFromPath(attachment.getPath());
        Optional<StoredFile> storedFile = fileStorageService.findByStoredName(storedName);
        return storedFile.orElseThrow(() -> new IllegalArgumentException("Attachment file does not exist"));
    }

    private static String extractDocxText(byte[] content) throws IOException {
        try {
            try (InputStream inputStream = new ByteArrayInputStream(content);
                 XWPFDocument document = new XWPFDocument(inputStream);
                 XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                return extractor.getText();
            }
        } catch (LinkageError exception) {
            LOG.warn("[AI审查] POI docx 提取不可用，改用内置 XML 提取：reason={}", exception.toString());
            return extractDocxTextFromXml(content);
        }
    }

    private static String extractDocxTextFromXml(byte[] content) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!"word/document.xml".equals(entry.getName())) {
                    continue;
                }
                String xml = new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
                return unescapeXml(xml
                        .replaceAll("<w:tab[^>]*/>", " ")
                        .replaceAll("</w:p>", "\n")
                        .replaceAll("<[^>]+>", ""));
            }
        }
        return "";
    }

    private static String unescapeXml(String value) {
        return value.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    private static String extractPdfText(byte[] content) throws IOException {
        try (InputStream inputStream = new ByteArrayInputStream(content);
             PDDocument document = PDDocument.load(inputStream)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static String storedNameFromPath(String apiPath) {
        if (apiPath == null || apiPath.isBlank()) {
            throw new IllegalArgumentException("Attachment path is empty");
        }
        int slash = Math.max(apiPath.lastIndexOf('/'), apiPath.lastIndexOf('\\'));
        String storedName = slash >= 0 ? apiPath.substring(slash + 1) : apiPath;
        if (storedName.isBlank()) {
            throw new IllegalArgumentException("Attachment path is invalid");
        }
        return storedName;
    }

    private static String normalizeType(Attachment attachment) {
        String type = attachment.getType();
        if (type == null || type.isBlank()) {
            String name = attachment.getName() == null ? "" : attachment.getName();
            int dot = name.lastIndexOf('.');
            type = dot >= 0 ? name.substring(dot + 1) : "";
        }
        return type.toLowerCase(Locale.ROOT);
    }

    private static String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace("\u0000", "")
                .replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n+ *", "\n")
                .replaceAll(" {2,}", " ")
                .trim();
    }

    public record AttachmentText(String name, String type, String text) {
    }
}
