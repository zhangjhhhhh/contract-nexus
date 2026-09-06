package com.example.contract.common;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class ChunkController {

    private static final Path CHUNK_DIR = Paths.get("uploads", "chunks").toAbsolutePath().normalize();

    private final FileStorageService fileStorageService;

    public ChunkController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/chunk")
    public ApiResponse<ChunkResult> uploadChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam("fileId") String fileId,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("totalChunks") int totalChunks,
            @RequestParam("fileName") String fileName) {

        Path chunkDir = CHUNK_DIR.resolve(fileId).normalize();
        if (!chunkDir.startsWith(CHUNK_DIR)) {
            throw new BusinessException(400, "分片标识不合法");
        }
        Path chunkFile = chunkDir.resolve("chunk_" + chunkIndex);

        try {
            Files.createDirectories(chunkDir);
            file.transferTo(chunkFile.toFile());
        } catch (IOException exception) {
            throw new BusinessException(500, "分片保存失败：" + exception.getMessage());
        }

        ChunkResult result = new ChunkResult();
        result.setFileId(fileId);
        result.setChunkIndex(chunkIndex);
        result.setTotalChunks(totalChunks);
        result.setReceived(true);
        return ApiResponse.success(result);
    }

    @GetMapping("/chunks/{fileId}")
    public ApiResponse<ChunkStatus> getChunkStatus(@PathVariable String fileId) {
        Path chunkDir = CHUNK_DIR.resolve(fileId).normalize();
        if (!chunkDir.startsWith(CHUNK_DIR)) {
            throw new BusinessException(400, "分片标识不合法");
        }
        File dir = chunkDir.toFile();
        List<Integer> uploaded = new ArrayList<>();

        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    if (name.startsWith("chunk_")) {
                        try {
                            uploaded.add(Integer.parseInt(name.substring(6)));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }

        ChunkStatus status = new ChunkStatus();
        status.setFileId(fileId);
        status.setUploadedChunks(uploaded);
        return ApiResponse.success(status);
    }

    @PostMapping("/merge")
    public ApiResponse<FileController.AttachmentInfo> mergeChunks(@RequestBody MergeRequest request) {
        String fileId = request.getFileId();
        String fileName = request.getFileName();
        int totalChunks = request.getTotalChunks();

        Path chunkDir = CHUNK_DIR.resolve(fileId).normalize();
        if (!chunkDir.startsWith(CHUNK_DIR)) {
            throw new BusinessException(400, "分片标识不合法");
        }
        if (!Files.isDirectory(chunkDir)) {
            throw new BusinessException("没有找到分片数据");
        }

        Path mergedFile = chunkDir.resolve("merged");
        StoredFile storedFile;
        try {
            try (OutputStream out = new FileOutputStream(mergedFile.toFile())) {
                for (int i = 0; i < totalChunks; i++) {
                    Path chunkFile = chunkDir.resolve("chunk_" + i);
                    if (!Files.isRegularFile(chunkFile)) {
                        throw new BusinessException("分片 " + i + " 缺失，合并失败");
                    }
                    Files.copy(chunkFile, out);
                }
            }

            try (InputStream inputStream = Files.newInputStream(mergedFile)) {
                storedFile = fileStorageService.store(fileName, Files.probeContentType(mergedFile), Files.size(mergedFile), inputStream);
            }

            File[] files = chunkDir.toFile().listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            chunkDir.toFile().delete();
        } catch (IOException exception) {
            throw new BusinessException(500, "文件合并失败：" + exception.getMessage());
        }

        return ApiResponse.success(FileController.toAttachmentInfo(storedFile));
    }

    public static class ChunkResult {
        private String fileId;
        private int chunkIndex;
        private int totalChunks;
        private boolean received;
        public String getFileId() { return fileId; }
        public void setFileId(String fileId) { this.fileId = fileId; }
        public int getChunkIndex() { return chunkIndex; }
        public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }
        public int getTotalChunks() { return totalChunks; }
        public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }
        public boolean isReceived() { return received; }
        public void setReceived(boolean received) { this.received = received; }
    }

    public static class ChunkStatus {
        private String fileId;
        private List<Integer> uploadedChunks;
        public String getFileId() { return fileId; }
        public void setFileId(String fileId) { this.fileId = fileId; }
        public List<Integer> getUploadedChunks() { return uploadedChunks; }
        public void setUploadedChunks(List<Integer> uploadedChunks) { this.uploadedChunks = uploadedChunks; }
    }

    public static class MergeRequest {
        private String fileId;
        private String fileName;
        private int totalChunks;
        public String getFileId() { return fileId; }
        public void setFileId(String fileId) { this.fileId = fileId; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public int getTotalChunks() { return totalChunks; }
        public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }
    }
}
