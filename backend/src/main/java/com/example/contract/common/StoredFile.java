package com.example.contract.common;

import java.time.LocalDateTime;

public record StoredFile(
        String storedName,
        String originalName,
        String type,
        String contentType,
        long size,
        byte[] content,
        LocalDateTime uploadTime) {

    public StoredFile {
        content = content == null ? new byte[0] : content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
