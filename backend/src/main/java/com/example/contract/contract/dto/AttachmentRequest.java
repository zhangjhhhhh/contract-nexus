package com.example.contract.contract.dto;

import jakarta.validation.constraints.NotBlank;

public class AttachmentRequest {

    @NotBlank(message = "附件名称不能为空")
    private String name;

    @NotBlank(message = "附件类型不能为空")
    private String type;

    @NotBlank(message = "附件路径不能为空")
    private String path;

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
}

