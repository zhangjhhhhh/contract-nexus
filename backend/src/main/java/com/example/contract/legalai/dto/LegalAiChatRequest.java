package com.example.contract.legalai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

public class LegalAiChatRequest {

    @NotBlank(message = "问题不能为空")
    private String message;

    @Valid
    private List<LegalAiMessage> history = new ArrayList<>();

    private String userId;

    private String contractId;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<LegalAiMessage> getHistory() {
        return history;
    }

    public void setHistory(List<LegalAiMessage> history) {
        this.history = history == null ? new ArrayList<>() : history;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getContractId() {
        return contractId;
    }

    public void setContractId(String contractId) {
        this.contractId = contractId;
    }
}
