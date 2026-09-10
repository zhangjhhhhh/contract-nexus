package com.example.contract.auth.controller;

import com.example.contract.auth.dto.AuthResponse;
import com.example.contract.auth.dto.LoginRequest;
import com.example.contract.auth.dto.RegisterCodeRequest;
import com.example.contract.auth.dto.RegisterRequest;
import com.example.contract.auth.dto.UserDto;
import com.example.contract.auth.security.PublicApi;
import com.example.contract.auth.service.AuthService;
import com.example.contract.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@PublicApi
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/register")
    public ApiResponse<UserDto> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/register/code")
    public ApiResponse<Void> sendRegisterCode(@Valid @RequestBody RegisterCodeRequest request) {
        authService.sendRegisterCode(request);
        return ApiResponse.success();
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            authService.logout(authorization.substring("Bearer ".length()));
        }
        return ApiResponse.success();
    }
}
