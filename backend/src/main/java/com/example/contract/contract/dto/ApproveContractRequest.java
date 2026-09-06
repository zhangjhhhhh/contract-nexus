package com.example.contract.contract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class ApproveContractRequest {

    @NotBlank(message = "用户编号不能为空")
    private String userId;

    @NotBlank(message = "审批结果不能为空")
    @Pattern(regexp = "approved|rejected", message = "审批结果只能为 approved 或 rejected")
    private String result;

    @NotBlank(message = "审批意见不能为空")
    private String content;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
