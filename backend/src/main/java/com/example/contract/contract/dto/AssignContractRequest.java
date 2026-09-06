package com.example.contract.contract.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;

public class AssignContractRequest {

    @NotEmpty(message = "会签人员不能为空")
    private List<String> countersignUserIds = new ArrayList<>();

    @NotEmpty(message = "审批人员不能为空")
    private List<String> approveUserIds = new ArrayList<>();

    @NotEmpty(message = "签订人员不能为空")
    private List<String> signUserIds = new ArrayList<>();

    public List<String> getCountersignUserIds() {
        return countersignUserIds;
    }

    public void setCountersignUserIds(List<String> countersignUserIds) {
        this.countersignUserIds = countersignUserIds == null ? new ArrayList<>() : countersignUserIds;
    }

    public List<String> getApproveUserIds() {
        return approveUserIds;
    }

    public void setApproveUserIds(List<String> approveUserIds) {
        this.approveUserIds = approveUserIds == null ? new ArrayList<>() : approveUserIds;
    }

    public List<String> getSignUserIds() {
        return signUserIds;
    }

    public void setSignUserIds(List<String> signUserIds) {
        this.signUserIds = signUserIds == null ? new ArrayList<>() : signUserIds;
    }
}

