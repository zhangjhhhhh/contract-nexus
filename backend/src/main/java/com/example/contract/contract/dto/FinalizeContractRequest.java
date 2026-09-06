package com.example.contract.contract.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

public class FinalizeContractRequest {

    @NotBlank(message = "用户编号不能为空")
    private String userId;

    @NotBlank(message = "合同内容不能为空")
    private String content;

    private List<AttachmentRequest> attachments = new ArrayList<>();

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public List<AttachmentRequest> getAttachments() { return attachments; }
    public void setAttachments(List<AttachmentRequest> attachments) { this.attachments = attachments; }
}

