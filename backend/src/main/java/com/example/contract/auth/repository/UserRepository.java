package com.example.contract.auth.repository;

import com.example.contract.auth.model.Role;
import com.example.contract.auth.model.User;
import java.util.List;
import java.util.Optional;

public interface UserRepository {

    // 用户
    List<User> findAllUsers();

    Optional<User> findUserById(String id);

    Optional<User> findUserByUsername(String username);

    User saveUser(User user);

    void deleteUser(String id);

    // 角色
    List<Role> findAllRoles();

    Optional<Role> findRoleById(String id);

    Role saveRole(Role role);

    void deleteRole(String id);

    // 关联查询
    List<String> findRoleIdsByUserId(String userId);

    List<String> findPermissionsByUserId(String userId);

    // ID 生成
    String nextUserId();

    String nextRoleId();
}
