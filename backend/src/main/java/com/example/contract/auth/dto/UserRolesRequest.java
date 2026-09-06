package com.example.contract.auth.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;

public class UserRolesRequest {

    @NotEmpty
    private List<String> roleIds = new ArrayList<>();

    public List<String> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<String> roleIds) {
        this.roleIds = roleIds == null ? new ArrayList<>() : roleIds;
    }
}
