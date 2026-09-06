package com.example.contract.auth.dto;

import java.util.List;

public record AuthResponse(UserDto user, List<String> permissions, String redirectTo) {
}
