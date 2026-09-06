package com.example.contract.common;

import java.sql.DriverManager;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("db")
@RestController
@RequestMapping("/api/db")
public class DatabaseHealthController {

    private final DatabaseProperties databaseProperties;

    public DatabaseHealthController(DatabaseProperties databaseProperties) {
        this.databaseProperties = databaseProperties;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() throws Exception {
        try (var connection = DriverManager.getConnection(
                databaseProperties.getUrl(),
                databaseProperties.getUsername(),
                databaseProperties.getPassword())) {
            return ApiResponse.success(Map.of(
                    "status", "UP",
                    "database", connection.getCatalog()
            ));
        }
    }
}
