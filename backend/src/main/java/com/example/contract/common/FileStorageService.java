package com.example.contract.common;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    private static final Logger LOG = LoggerFactory.getLogger(FileStorageService.class);
    private static final Path UPLOAD_DIR = Paths.get("uploads").toAbsolutePath().normalize();
    private static final List<Path> DEFAULT_LEGACY_UPLOAD_DIRS = List.of(
            UPLOAD_DIR,
            Paths.get("..", "uploads").toAbsolutePath().normalize()
    );

    private final DatabaseProperties databaseProperties;
    private final List<Path> legacyUploadDirs;
    private final Map<String, StoredFile> memoryFiles = new ConcurrentHashMap<>();

    @Autowired
    public FileStorageService(DatabaseProperties databaseProperties) {
        this(databaseProperties, DEFAULT_LEGACY_UPLOAD_DIRS);
    }

    FileStorageService(DatabaseProperties databaseProperties, List<Path> legacyUploadDirs) {
        this.databaseProperties = databaseProperties;
        this.legacyUploadDirs = legacyUploadDirs == null ? List.of() : legacyUploadDirs;
    }

    @PostConstruct
    public void init() {
        if (usesDatabase()) {
            ensureTable();
        }
        migrateLegacyUploads();
    }

    public StoredFile store(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new BusinessException("文件名不能为空");
        }

        try {
            return store(
                    originalName,
                    file.getContentType(),
                    file.getSize(),
                    file.getInputStream());
        } catch (IOException exception) {
            throw new BusinessException(500, "文件读取失败：" + exception.getMessage());
        }
    }

    public StoredFile store(String originalName, String contentType, long size, InputStream inputStream) {
        String type = extensionOf(originalName);
        String storedName = UUID.randomUUID() + (type.isBlank() ? "" : "." + type);
        return store(storedName, originalName, type, contentType, size, inputStream);
    }

    public StoredFile store(String storedName, String originalName, String type, String contentType, long size, InputStream inputStream) {
        if (storedName == null || storedName.isBlank()) {
            throw new BusinessException("文件标识不能为空");
        }
        if (originalName == null || originalName.isBlank()) {
            throw new BusinessException("文件名不能为空");
        }

        try {
            byte[] content = inputStream.readAllBytes();
            long safeSize = size > 0 ? size : content.length;
            LocalDateTime uploadTime = LocalDateTime.now();
            StoredFile storedFile = new StoredFile(
                    storedName,
                    originalName,
                    type == null || type.isBlank() ? extensionOf(originalName) : type.toLowerCase(Locale.ROOT),
                    resolveContentType(originalName, contentType),
                    safeSize,
                    content,
                    uploadTime);
            save(storedFile);
            return storedFile;
        } catch (IOException exception) {
            throw new BusinessException(500, "文件读取失败：" + exception.getMessage());
        }
    }

    public Optional<StoredFile> findByStoredName(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            return Optional.empty();
        }
        String safeStoredName = sanitizeStoredName(storedName);
        if (!usesDatabase()) {
            return Optional.ofNullable(memoryFiles.get(safeStoredName));
        }
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    select storedName, originalName, type, contentType, size, content, uploadTime
                    from file_storage
                    where storedName = ?
                    """)) {
                statement.setString(1, safeStoredName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(mapStoredFile(resultSet));
                }
            }
        });
    }

    public StoredFile getByStoredName(String storedName) {
        return findByStoredName(storedName)
                .orElseThrow(() -> new BusinessException(404, "文件不存在"));
    }

    public boolean exists(String storedName) {
        String safeStoredName = sanitizeStoredName(storedName);
        if (!usesDatabase()) {
            return memoryFiles.containsKey(safeStoredName);
        }
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("select 1 from file_storage where storedName = ?")) {
                statement.setString(1, safeStoredName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            }
        });
    }

    public void migrateLegacyUploads() {
        for (Path uploadDir : legacyUploadDirs) {
            migrateLegacyUploadDir(uploadDir);
        }
    }

    private void migrateLegacyUploadDir(Path uploadDir) {
        if (!Files.isDirectory(uploadDir)) {
            return;
        }
        try (var stream = Files.walk(uploadDir, 1)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !path.getParent().endsWith("chunks"))
                    .forEach(this::migrateLegacyFile);
        } catch (IOException exception) {
            LOG.warn("[文件入库] 扫描旧上传目录失败：dir={}，reason={}", uploadDir, exception.toString());
        }
    }

    private void migrateLegacyFile(Path filePath) {
        String storedName = filePath.getFileName().toString();
        if (exists(storedName)) {
            return;
        }
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            String contentType = Files.probeContentType(filePath);
            store(storedName, storedName, extensionOf(storedName), contentType, Files.size(filePath), inputStream);
            LOG.info("[文件入库] 已迁移旧上传文件：{}", storedName);
        } catch (RuntimeException | IOException exception) {
            LOG.warn("[文件入库] 旧上传文件迁移失败：file={}，reason={}", filePath, exception.toString());
        }
    }

    private void save(StoredFile file) {
        if (!usesDatabase()) {
            memoryFiles.put(file.storedName(), file);
            return;
        }
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    insert into file_storage
                      (storedName, originalName, type, contentType, size, content, uploadTime)
                    values (?, ?, ?, ?, ?, ?, ?)
                    on duplicate key update
                      originalName = values(originalName),
                      type = values(type),
                      contentType = values(contentType),
                      size = values(size),
                      content = values(content),
                      uploadTime = values(uploadTime)
                    """)) {
                statement.setString(1, file.storedName());
                statement.setString(2, file.originalName());
                statement.setString(3, file.type());
                statement.setString(4, file.contentType());
                statement.setLong(5, file.size());
                statement.setBytes(6, file.content());
                statement.setTimestamp(7, Timestamp.valueOf(file.uploadTime()));
                statement.executeUpdate();
            }
            return null;
        });
    }

    private void ensureTable() {
        withConnection(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        create table if not exists file_storage (
                          storedName varchar(255) primary key,
                          originalName varchar(255) not null,
                          type varchar(30) not null,
                          contentType varchar(120) not null,
                          size bigint not null,
                          content longblob not null,
                          uploadTime datetime not null
                        ) engine=InnoDB default charset=utf8mb4
                        """);
            }
            return null;
        });
    }

    private StoredFile mapStoredFile(ResultSet resultSet) throws SQLException {
        Timestamp uploadTime = resultSet.getTimestamp("uploadTime");
        return new StoredFile(
                resultSet.getString("storedName"),
                resultSet.getString("originalName"),
                resultSet.getString("type"),
                resultSet.getString("contentType"),
                resultSet.getLong("size"),
                resultSet.getBytes("content"),
                uploadTime == null ? LocalDateTime.now() : uploadTime.toLocalDateTime());
    }

    private String sanitizeStoredName(String storedName) {
        String value = Paths.get(storedName).getFileName().toString();
        if (value.isBlank() || value.contains("/") || value.contains("\\")) {
            throw new BusinessException(400, "文件路径不合法");
        }
        return value;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dot < 0 || dot + 1 >= fileName.length()) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String resolveContentType(String fileName, String contentType) {
        if (contentType != null
                && !contentType.isBlank()
                && !"application/octet-stream".equalsIgnoreCase(contentType)) {
            return contentType;
        }
        return MediaTypeFactory.getMediaType(fileName)
                .map(MediaType::toString)
                .orElseGet(() -> switch (extensionOf(fileName)) {
                    case "pdf" -> "application/pdf";
                    case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                    case "doc" -> "application/msword";
                    default -> "application/octet-stream";
                });
    }

    private boolean usesDatabase() {
        return databaseProperties != null
                && databaseProperties.getUrl() != null
                && !databaseProperties.getUrl().isBlank()
                && databaseProperties.getUsername() != null;
    }

    private <T> T withConnection(SqlCallback<T> callback) {
        try (Connection connection = DriverManager.getConnection(
                databaseProperties.getUrl(),
                databaseProperties.getUsername(),
                databaseProperties.getPassword())) {
            return callback.execute(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Database operation failed: " + exception.getMessage(), exception);
        }
    }

    @FunctionalInterface
    private interface SqlCallback<T> {
        T execute(Connection connection) throws SQLException;
    }
}
