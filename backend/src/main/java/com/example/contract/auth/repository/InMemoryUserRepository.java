package com.example.contract.auth.repository;

import com.example.contract.auth.model.Role;
import com.example.contract.auth.model.User;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("memory")
public class InMemoryUserRepository implements UserRepository {

    private static final String DEFAULT_USER_EMAIL = "user@example.com";

    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<String, Role> roles = new LinkedHashMap<>();
    private final AtomicInteger userSequence = new AtomicInteger();
    private final AtomicInteger roleSequence = new AtomicInteger();

    @PostConstruct
    public void init() {
        seedRoles();
        seedUsers();
    }

    // ==================== 用户 CRUD ====================

    @Override
    public synchronized List<User> findAllUsers() {
        return users.values().stream().map(this::copyUser).toList();
    }

    @Override
    public synchronized Optional<User> findUserById(String id) {
        return Optional.ofNullable(users.get(id)).map(this::copyUser);
    }

    @Override
    public synchronized Optional<User> findUserByUsername(String username) {
        return users.values().stream()
                .filter(user -> user.getUsername().equals(username))
                .findFirst()
                .map(this::copyUser);
    }

    @Override
    public synchronized User saveUser(User user) {
        users.put(user.getId(), copyUser(user));
        return copyUser(user);
    }

    @Override
    public synchronized void deleteUser(String id) {
        users.remove(id);
    }

    // ==================== 角色 CRUD ====================

    @Override
    public synchronized List<Role> findAllRoles() {
        return roles.values().stream().map(this::copyRole).toList();
    }

    @Override
    public synchronized Optional<Role> findRoleById(String id) {
        return Optional.ofNullable(roles.get(id)).map(this::copyRole);
    }

    @Override
    public synchronized Role saveRole(Role role) {
        roles.put(role.getId(), copyRole(role));
        return copyRole(role);
    }

    @Override
    public synchronized void deleteRole(String id) {
        roles.remove(id);
    }

    // ==================== 关联查询 ====================

    @Override
    public List<String> findRoleIdsByUserId(String userId) {
        User user = users.get(userId);
        if (user == null) {
            return List.of();
        }
        return new ArrayList<>(user.getRoleIds());
    }

    @Override
    public synchronized List<String> findPermissionsByUserId(String userId) {
        User user = users.get(userId);
        if (user == null) {
            return List.of();
        }
        List<String> permissions = new ArrayList<>();
        for (String roleId : user.getRoleIds()) {
            Role role = roles.get(roleId);
            if (role != null) {
                permissions.addAll(role.getPermissions());
            }
        }
        return permissions.stream().distinct().toList();
    }

    // ==================== ID 生成 ====================

    @Override
    public String nextUserId() {
        return "U" + String.format("%03d", userSequence.incrementAndGet());
    }

    @Override
    public String nextRoleId() {
        return "R" + String.format("%03d", roleSequence.incrementAndGet());
    }

    // ==================== 种子数据 ====================

    private void seedRoles() {
        saveRole(buildRole("admin", "管理员",
                "分配合同流转人员，维护用户、角色、权限与日志。",
                List.of("dashboard:view", "contract:draft", "contract:countersign",
                        "contract:finalize", "contract:approve", "contract:sign",
                        "query:info", "query:process", "base:contract", "base:customer",
                        "system:assign", "system:user", "system:role", "system:permission",
                        "system:log")));
        saveRole(buildRole("operator", "合同操作员",
                "负责合同起草、会签、定稿、审批与签订。",
                List.of("dashboard:view", "contract:draft", "contract:countersign",
                        "contract:finalize", "contract:approve", "contract:sign",
                        "query:info", "query:process", "base:contract", "base:customer")));
        saveRole(buildRole("new_user", "新用户", "注册后等待管理员授权。", List.of()));
        saveRole(buildRole("finance", "财务复核",
                "参与审批、签订与合同查询。",
                List.of("dashboard:view", "contract:approve", "contract:sign",
                        "query:info", "query:process")));
        roleSequence.set(4);
    }

    private void seedUsers() {
        saveUser(buildUser("U001", "admin", "admin123", List.of("admin")));
        saveUser(buildUser("U002", "operator", "operator123", List.of("operator")));
        saveUser(buildUser("U003", "zhangmin", "operator123", List.of("operator")));
        saveUser(buildUser("U004", "liwei", "operator123", List.of("operator")));
        saveUser(buildUser("U005", "wangyan", "finance", List.of("finance")));
        saveUser(buildUser("U006", "chenhao", "operator123", List.of("operator", "finance")));
        saveUser(buildUser("U007", "newuser", "newuser123", List.of("new_user")));
        saveUser(buildUser("U008", "sunqi", "operator123", List.of("operator")));
        userSequence.set(8);
    }

    private Role buildRole(String id, String name, String description, List<String> permissions) {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        role.setDescription(description);
        role.setPermissions(permissions);
        return role;
    }

    private User buildUser(String id, String username, String rawPassword, List<String> roleIds) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(rawPassword);
        user.setEmail(DEFAULT_USER_EMAIL);
        user.setCreatedAt(LocalDateTime.now());
        user.setRoleIds(roleIds);
        return user;
    }

    // ==================== 深拷贝（防止外部修改内部数据） ====================

    private User copyUser(User source) {
        User user = new User();
        user.setId(source.getId());
        user.setUsername(source.getUsername());
        user.setPassword(source.getPassword());
        user.setEmail(source.getEmail());
        user.setCreatedAt(source.getCreatedAt());
        user.setRoleIds(new ArrayList<>(source.getRoleIds()));
        return user;
    }

    private Role copyRole(Role source) {
        Role role = new Role();
        role.setId(source.getId());
        role.setName(source.getName());
        role.setDescription(source.getDescription());
        role.setPermissions(new ArrayList<>(source.getPermissions()));
        return role;
    }
}
