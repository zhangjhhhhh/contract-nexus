package com.example.contract.log.controller;

import com.example.contract.common.ApiResponse;
import com.example.contract.log.model.OperationLog;
import com.example.contract.log.service.LogService;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    @GetMapping
    public ApiResponse<List<OperationLog>> list() {
        return ApiResponse.success(logService.list());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        StringBuilder csv = new StringBuilder("\uFEFF操作人,操作内容,操作时间\n");
        for (OperationLog log : logService.list()) {
            csv.append(escape(log.getUserName())).append(",")
                    .append(escape(log.getContent())).append(",")
                    .append(log.getTime() == null ? "" : FORMATTER.format(log.getTime()))
                    .append("\n");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=contract-system-logs.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
