package com.example.contract.contract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public class UpdateContractRequest {

    @NotBlank(message = "合同名称不能为空")
    private String name;

    @NotBlank(message = "客户不能为空")
    private String customerId;

    @NotNull(message = "开始时间不能为空")
    private LocalDate beginTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDate endTime;

    @NotBlank(message = "合同内容不能为空")
    private String content;

    private List<AttachmentRequest> attachments;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public LocalDate getBeginTime() { return beginTime; }
    public void setBeginTime(LocalDate beginTime) { this.beginTime = beginTime; }
    public LocalDate getEndTime() { return endTime; }
    public void setEndTime(LocalDate endTime) { this.endTime = endTime; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public List<AttachmentRequest> getAttachments() { return attachments; }
    public void setAttachments(List<AttachmentRequest> attachments) { this.attachments = attachments; }
}
