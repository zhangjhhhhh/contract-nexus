package com.example.contract.contract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public class SignContractRequest {

    @NotBlank(message = "用户编号不能为空")
    private String userId;

    @NotNull(message = "签订日期不能为空")
    private LocalDate signDate;

    @NotBlank(message = "签订方式不能为空")
    private String method;

    private String remark;
    private String signerName;
    private String signatureDataUrl;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public LocalDate getSignDate() {
        return signDate;
    }

    public void setSignDate(LocalDate signDate) {
        this.signDate = signDate;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getSignerName() {
        return signerName;
    }

    public void setSignerName(String signerName) {
        this.signerName = signerName;
    }

    public String getSignatureDataUrl() {
        return signatureDataUrl;
    }

    public void setSignatureDataUrl(String signatureDataUrl) {
        this.signatureDataUrl = signatureDataUrl;
    }
}

