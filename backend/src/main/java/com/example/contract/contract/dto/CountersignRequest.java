package com.example.contract.contract.dto;

import jakarta.validation.constraints.NotBlank;

public class CountersignRequest {

    @NotBlank(message = "用户编号不能为空")
    private String userId;

    @NotBlank(message = "会签意见不能为空")
    private String content;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}

