package com.example.contract.auth.dto;

import com.example.contract.auth.model.User;
import java.util.List;

public record UserDto(String id, String name, String password, String email, List<String> roleIds) {

    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getUsername(), user.getPassword(), user.getEmail(), user.getRoleIds());
    }
}
