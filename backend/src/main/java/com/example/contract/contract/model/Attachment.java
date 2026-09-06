package com.example.contract.contract.model;

import java.time.LocalDateTime;

public class Attachment {

    private String name;
    private String type;
    private String path;
    private LocalDateTime uploadTime;

    public Attachment() {
    }

    public Attachment(String name, String type, String path, LocalDateTime uploadTime) {
        this.name = name;
        this.type = type;
        this.path = path;
        this.uploadTime = uploadTime;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public LocalDateTime getUploadTime() {
        return uploadTime;
    }

    public void setUploadTime(LocalDateTime uploadTime) {
        this.uploadTime = uploadTime;
    }
}

