package com.example.contract.auth.dto;

import com.example.contract.auth.model.Role;
import java.util.List;

public record RoleDto(String id, String name, String description, List<String> permissions) {

    public static RoleDto from(Role role) {
        return new RoleDto(role.getId(), role.getName(), role.getDescription(), role.getPermissions());
    }
}
