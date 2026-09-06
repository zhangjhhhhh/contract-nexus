package com.example.contract.common;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://127.0.0.1:5173", "http://127.0.0.1:5174"})
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping
    public ApiResponse<AttachmentInfo> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(toAttachmentInfo(fileStorageService.store(file)));
    }

    @GetMapping("/{storedName}")
    public ResponseEntity<Resource> download(@PathVariable String storedName,
                                             @RequestParam(value = "name", required = false) String displayName) {
        return buildFileResponse(storedName, displayName, false);
    }

    @GetMapping("/preview/{storedName}")
    public ResponseEntity<?> preview(@PathVariable String storedName,
                                     @RequestParam(value = "name", required = false) String displayName) {
        if (storedName.toLowerCase().endsWith(".docx")) {
            return buildDocxPreviewResponse(storedName);
        }
        return buildFileResponse(storedName, displayName, true);
    }

    private ResponseEntity<Resource> buildFileResponse(String storedName, String displayName, boolean inline) {
        StoredFile file = fileStorageService.getByStoredName(storedName);
        InputStream inputStream = new ByteArrayInputStream(file.content());
        String mimeType = resolveContentType(file);

        String downloadName = (displayName != null && !displayName.isBlank()) ? displayName : file.originalName();
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(downloadName, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new InputStreamResource(inputStream));
    }

    private ResponseEntity<String> buildDocxPreviewResponse(String storedName) {
        StoredFile file = fileStorageService.getByStoredName(storedName);
        try (InputStream inputStream = new ByteArrayInputStream(file.content());
             XWPFDocument document = new XWPFDocument(inputStream)) {
            String html = buildDocxHtml(document);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .body(html);
        } catch (IOException exception) {
            throw new BusinessException(500, "DOCX 预览失败：" + exception.getMessage());
        }
    }

    private String buildDocxHtml(XWPFDocument document) throws IOException {
        StringWriter writer = new StringWriter();
        writer.write("""
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <style>
                    body { margin: 0; background: #f3f4f6; color: #111827; font-family: Arial, 'Microsoft YaHei', sans-serif; }
                    main { max-width: 900px; min-height: 100vh; margin: 0 auto; padding: 40px 48px; background: #fff; box-sizing: border-box; }
                    p { margin: 0 0 12px; line-height: 1.8; white-space: pre-wrap; }
                    table { width: 100%; border-collapse: collapse; margin: 16px 0; }
                    td, th { border: 1px solid #d1d5db; padding: 8px 10px; vertical-align: top; line-height: 1.7; }
                  </style>
                </head>
                <body><main>
                """);

        for (IBodyElement element : document.getBodyElements()) {
            switch (element.getElementType()) {
                case PARAGRAPH -> writeParagraph(writer, (XWPFParagraph) element);
                case TABLE -> writeTable(writer, (XWPFTable) element);
                default -> { }
            }
        }

        writer.write("</main></body></html>");
        return writer.toString();
    }

    private void writeParagraph(StringWriter writer, XWPFParagraph paragraph) {
        writer.write("<p>");
        if (paragraph.getRuns().isEmpty()) {
            writer.write("&nbsp;");
        } else {
            for (XWPFRun run : paragraph.getRuns()) {
                writeRun(writer, run);
            }
        }
        writer.write("</p>");
    }

    private void writeTable(StringWriter writer, XWPFTable table) {
        writer.write("<table>");
        for (XWPFTableRow row : table.getRows()) {
            writer.write("<tr>");
            for (XWPFTableCell cell : row.getTableCells()) {
                writer.write("<td>");
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    writeParagraph(writer, paragraph);
                }
                writer.write("</td>");
            }
            writer.write("</tr>");
        }
        writer.write("</table>");
    }

    private void writeRun(StringWriter writer, XWPFRun run) {
        String text = run.text();
        if (text == null || text.isEmpty()) {
            return;
        }
        if (run.isBold()) {
            writer.write("<strong>");
        }
        if (run.isItalic()) {
            writer.write("<em>");
        }
        if (run.getUnderline() != UnderlinePatterns.NONE) {
            writer.write("<u>");
        }

        writer.write(escapeHtml(text).replace("\n", "<br>"));

        if (run.getUnderline() != UnderlinePatterns.NONE) {
            writer.write("</u>");
        }
        if (run.isItalic()) {
            writer.write("</em>");
        }
        if (run.isBold()) {
            writer.write("</strong>");
        }
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String resolveContentType(StoredFile file) {
        String contentType = file.contentType();
        if (contentType != null
                && !contentType.isBlank()
                && !"application/octet-stream".equalsIgnoreCase(contentType)) {
            return contentType;
        }
        return MediaTypeFactory.getMediaType(file.originalName())
                .map(MediaType::toString)
                .orElseGet(() -> switch (safeType(file)) {
                    case "pdf" -> "application/pdf";
                    case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                    case "doc" -> "application/msword";
                    default -> "application/octet-stream";
                });
    }

    private String safeType(StoredFile file) {
        String type = file.type();
        if (type != null && !type.isBlank()) {
            return type.toLowerCase(Locale.ROOT);
        }
        String name = file.originalName();
        int dot = name == null ? -1 : name.lastIndexOf('.');
        if (dot < 0 || dot + 1 >= name.length()) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static AttachmentInfo toAttachmentInfo(StoredFile file) {
        AttachmentInfo info = new AttachmentInfo();
        info.setName(file.originalName());
        info.setStoredName(file.storedName());
        info.setType(file.type());
        info.setPath("/api/files/" + file.storedName());
        info.setUploadTime(file.uploadTime());
        return info;
    }

    public static class AttachmentInfo {
        private String name;
        private String storedName;
        private String type;
        private String path;
        private LocalDateTime uploadTime;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getStoredName() { return storedName; }
        public void setStoredName(String storedName) { this.storedName = storedName; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public LocalDateTime getUploadTime() { return uploadTime; }
        public void setUploadTime(LocalDateTime uploadTime) { this.uploadTime = uploadTime; }
    }
}
