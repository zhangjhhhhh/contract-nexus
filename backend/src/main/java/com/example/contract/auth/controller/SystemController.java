package com.example.contract.auth.controller;

import com.example.contract.auth.dto.RoleDto;
import com.example.contract.auth.dto.RoleRequest;
import com.example.contract.auth.dto.UserDto;
import com.example.contract.auth.dto.UserRequest;
import com.example.contract.auth.dto.UserRolesRequest;
import com.example.contract.auth.service.AuthService;
import com.example.contract.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SystemController {

    private final AuthService authService;

    public SystemController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/users")
    public ApiResponse<List<UserDto>> users() {
        return ApiResponse.success(authService.listUsers());
    }

    @PostMapping("/users")
    public ApiResponse<UserDto> createUser(@Valid @RequestBody UserRequest request) {
        return ApiResponse.success(authService.createUser(request));
    }

    @PutMapping("/users/{id}")
    public ApiResponse<UserDto> updateUser(@PathVariable String id, @Valid @RequestBody UserRequest request) {
        return ApiResponse.success(authService.updateUser(id, request));
    }

    @DeleteMapping("/users/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable String id) {
        authService.deleteUser(id);
        return ApiResponse.success();
    }

    @PutMapping("/users/{id}/roles")
    public ApiResponse<UserDto> setUserRoles(@PathVariable String id, @Valid @RequestBody UserRolesRequest request) {
        return ApiResponse.success(authService.setUserRoles(id, request.getRoleIds()));
    }

    @GetMapping("/roles")
    public ApiResponse<List<RoleDto>> roles() {
        return ApiResponse.success(authService.listRoles());
    }

    @PostMapping("/roles")
    public ApiResponse<RoleDto> createRole(@Valid @RequestBody RoleRequest request) {
        return ApiResponse.success(authService.createRole(request));
    }

    @PutMapping("/roles/{id}")
    public ApiResponse<RoleDto> updateRole(@PathVariable String id, @Valid @RequestBody RoleRequest request) {
        return ApiResponse.success(authService.updateRole(id, request));
    }

    @DeleteMapping("/roles/{id}")
    public ApiResponse<Void> deleteRole(@PathVariable String id) {
        authService.deleteRole(id);
        return ApiResponse.success();
    }
}
