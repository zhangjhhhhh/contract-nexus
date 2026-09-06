package com.example.contract.contract.model;

import java.time.LocalDate;

public class SignRecord {

    private String id;
    private String contractId;
    private LocalDate signDate;
    private String method;
    private String remark;
    private String signerName;
    private String signatureDataUrl;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContractId() {
        return contractId;
    }

    public void setContractId(String contractId) {
        this.contractId = contractId;
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

