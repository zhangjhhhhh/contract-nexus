package com.example.contract.legalai.dto;

import jakarta.validation.constraints.NotBlank;

public class LegalAiMessage {

    @NotBlank(message = "角色不能为空")
    private String role;

    @NotBlank(message = "消息内容不能为空")
    private String content;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
