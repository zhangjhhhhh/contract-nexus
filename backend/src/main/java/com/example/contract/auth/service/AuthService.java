package com.example.contract.auth.service;

import com.example.contract.auth.dto.AuthResponse;
import com.example.contract.auth.dto.LoginRequest;
import com.example.contract.auth.dto.RegisterCodeRequest;
import com.example.contract.auth.dto.RegisterRequest;
import com.example.contract.auth.dto.RoleDto;
import com.example.contract.auth.dto.RoleRequest;
import com.example.contract.auth.dto.UserDto;
import com.example.contract.auth.dto.UserRequest;
import com.example.contract.auth.model.Role;
import com.example.contract.auth.model.User;
import com.example.contract.auth.repository.UserRepository;
import com.example.contract.auth.security.TokenService;
import com.example.contract.common.BusinessException;
import com.example.contract.log.service.LogService;
import com.example.contract.notification.RegisterVerificationCodeService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String DEFAULT_REGISTER_ROLE = "operator";

    private final UserRepository userRepository;
    private final LogService logService;
    private final RegisterVerificationCodeService verificationCodeService;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       LogService logService,
                       RegisterVerificationCodeService verificationCodeService,
                       TokenService tokenService,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.logService = logService;
        this.verificationCodeService = verificationCodeService;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findUserByUsername(request.getName())
                .orElseThrow(() -> new BusinessException("用户名或密码不正确"));
        if (!passwordMatches(user, request.getPassword())) {
            throw new BusinessException("用户名或密码不正确");
        }
        upgradePasswordIfNeeded(user, request.getPassword());
        List<String> permissions = userRepository.findPermissionsByUserId(user.getId());
        String token = tokenService.issue(user.getUsername());
        logService.record(user.getUsername(), "登录系统");
        return new AuthResponse(UserDto.from(user), permissions, landingPathFor(user), token);
    }

    public void logout(String token) {
        tokenService.revoke(token);
    }

    private boolean passwordMatches(User user, String rawPassword) {
        String stored = user.getPassword();
        if (stored == null) {
            return false;
        }
        if (isBcrypt(stored)) {
            return passwordEncoder.matches(rawPassword, stored);
        }
        return stored.equals(rawPassword);
    }

    private void upgradePasswordIfNeeded(User user, String rawPassword) {
        String stored = user.getPassword();
        if (stored == null || isBcrypt(stored)) {
            return;
        }
        if (stored.equals(rawPassword)) {
            user.setPassword(passwordEncoder.encode(rawPassword));
            userRepository.saveUser(user);
        }
    }

    private boolean isBcrypt(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }

    public UserDto register(RegisterRequest request) {
        if (userRepository.findUserByUsername(request.getName()).isPresent()) {
            throw new BusinessException("用户名已存在");
        }
        requireRole(DEFAULT_REGISTER_ROLE);
        verificationCodeService.verifyForRegistration(request.getEmail(), request.getVerificationCode());

        User user = new User();
        user.setId(userRepository.nextUserId());
        user.setUsername(request.getName());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(normalizeEmail(request.getEmail()));
        user.setCreatedAt(LocalDateTime.now());
        user.setRoleIds(List.of(DEFAULT_REGISTER_ROLE));
        User saved = userRepository.saveUser(user);
        logService.record(request.getName(), "注册新用户，角色：" + roleText(DEFAULT_REGISTER_ROLE));
        return UserDto.from(saved);
    }

    public void sendRegisterCode(RegisterCodeRequest request) {
        verificationCodeService.sendRegisterCode(request.getEmail());
    }

    public List<UserDto> listUsers() {
        return userRepository.findAllUsers().stream().map(UserDto::from).toList();
    }

    public UserDto createUser(UserRequest request) {
        if (userRepository.findUserByUsername(request.getName()).isPresent()) {
            throw new BusinessException("用户名已存在");
        }
        validateRoles(request.getRoleIds());

        User user = new User();
        user.setId(userRepository.nextUserId());
        user.setUsername(request.getName());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(normalizeEmail(request.getEmail()));
        user.setCreatedAt(LocalDateTime.now());
        user.setRoleIds(request.getRoleIds());
        User saved = userRepository.saveUser(user);
        logService.record("admin", "新增用户：" + saved.getUsername());
        return UserDto.from(saved);
    }

    public UserDto updateUser(String id, UserRequest request) {
        User user = userRepository.findUserById(id)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        validateRoles(request.getRoleIds());
        user.setUsername(request.getName());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(normalizeEmail(request.getEmail()));
        user.setRoleIds(request.getRoleIds());
        User saved = userRepository.saveUser(user);
        logService.record("admin", "修改用户：" + saved.getUsername());
        return UserDto.from(saved);
    }

    public void deleteUser(String id) {
        User user = userRepository.findUserById(id)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        userRepository.deleteUser(id);
        logService.record("admin", "删除用户：" + user.getUsername());
    }

    public List<RoleDto> listRoles() {
        return userRepository.findAllRoles().stream().map(RoleDto::from).toList();
    }

    public RoleDto createRole(RoleRequest request) {
        if (userRepository.findRoleById(request.getName()).isPresent()) {
            throw new BusinessException("角色已存在");
        }
        Role role = new Role();
        role.setId(userRepository.nextRoleId());
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        role.setPermissions(request.getPermissions());
        Role saved = userRepository.saveRole(role);
        logService.record("admin", "新增角色：" + saved.getName());
        return RoleDto.from(saved);
    }

    public RoleDto updateRole(String id, RoleRequest request) {
        Role role = userRepository.findRoleById(id)
                .orElseThrow(() -> new BusinessException(404, "角色不存在"));
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        role.setPermissions(request.getPermissions());
        Role saved = userRepository.saveRole(role);
        logService.record("admin", "修改角色权限：" + saved.getName());
        return RoleDto.from(saved);
    }

    public void deleteRole(String id) {
        if (List.of("admin", "operator", "new_user").contains(id)) {
            throw new BusinessException("内置角色不能删除");
        }
        Role role = userRepository.findRoleById(id)
                .orElseThrow(() -> new BusinessException(404, "角色不存在"));
        boolean assigned = userRepository.findAllUsers().stream().anyMatch(user -> user.getRoleIds().contains(id));
        if (assigned) {
            throw new BusinessException("该角色已分配给用户，不能删除");
        }
        userRepository.deleteRole(id);
        logService.record("admin", "删除角色：" + role.getName());
    }

    public UserDto setUserRoles(String userId, List<String> roleIds) {
        User user = userRepository.findUserById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        validateRoles(roleIds);
        user.setRoleIds(roleIds);
        User saved = userRepository.saveUser(user);
        logService.record("admin", "调整用户权限：" + saved.getUsername() + "，角色：" + String.join("、", roleIds));
        return UserDto.from(saved);
    }

    private void validateRoles(List<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            throw new BusinessException("至少选择一个角色");
        }
        roleIds.forEach(this::requireRole);
    }

    private void requireRole(String roleId) {
        userRepository.findRoleById(roleId)
                .orElseThrow(() -> new BusinessException("角色不存在：" + roleId));
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private String landingPathFor(User user) {
        if (user.getRoleIds().contains("admin")) return "/system/assign";
        if (user.getRoleIds().contains("operator")) return "/dashboard";
        return "/dashboard";
    }

    private String roleText(String roleId) {
        return switch (roleId) {
            case "admin" -> "合同管理员";
            case "operator" -> "合同操作员";
            default -> roleId;
        };
    }
}
