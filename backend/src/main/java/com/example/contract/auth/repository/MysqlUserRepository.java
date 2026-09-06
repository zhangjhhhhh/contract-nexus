package com.example.contract.auth.repository;

import com.example.contract.auth.model.Role;
import com.example.contract.auth.model.User;
import com.example.contract.common.DatabaseProperties;
import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("db")
public class MysqlUserRepository implements UserRepository {

    private static final String DEFAULT_USER_EMAIL = "user@example.com";

    private final DatabaseProperties databaseProperties;
    private final AtomicInteger userSequence = new AtomicInteger();
    private final AtomicInteger roleSequence = new AtomicInteger();

    public MysqlUserRepository(DatabaseProperties databaseProperties) {
        this.databaseProperties = databaseProperties;
    }

    @PostConstruct
    public void init() {
        withConnection(connection -> {
            // The cloud database already owns these tables:
            // user, role, function, right. Do not create users/roles/user_roles/role_permissions here.
            ensureUserEmailColumn(connection);
            syncDefaultUserEmails(connection);
            ensureDefaultRoles(connection);
            userSequence.set(maxId(connection, "`user`"));
            roleSequence.set(maxId(connection, "`role`"));
            return null;
        });
    }

    @Override
    public List<User> findAllUsers() {
        return withConnection(connection -> {
            List<User> users = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "select id, userName, password, email from `user` order by id");
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    User user = mapUser(resultSet);
                    user.setRoleIds(findRoleIdsByUsername(connection, user.getUsername()));
                    users.add(user);
                }
            }
            return users;
        });
    }

    @Override
    public Optional<User> findUserById(String id) {
        return withConnection(connection -> findUserById(connection, id));
    }

    @Override
    public Optional<User> findUserByUsername(String username) {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "select id, userName, password, email from `user` where userName = ?")) {
                statement.setString(1, username);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    User user = mapUser(resultSet);
                    user.setRoleIds(findRoleIdsByUsername(connection, user.getUsername()));
                    return Optional.of(user);
                }
            }
        });
    }

    @Override
    public User saveUser(User user) {
        return withConnection(connection -> {
            try {
                connection.setAutoCommit(false);

                String oldUsername = findUsernameById(connection, user.getId()).orElse(null);
                if (oldUsername != null && !oldUsername.equals(user.getUsername())) {
                    deleteUserRoles(connection, oldUsername);
                }
                String savedUsername = saveUserRow(connection, user);
                saveUserRoles(connection, savedUsername, user.getRoleIds(), oldUsername);

                connection.commit();
                return findUserByUsername(savedUsername).orElse(user);
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    @Override
    public void deleteUser(String id) {
        withConnection(connection -> {
            Optional<String> username = findUsernameById(connection, id);
            if (username.isPresent()) {
                try (PreparedStatement deleteRight = connection.prepareStatement(
                        "delete from `right` where userName = ?")) {
                    deleteRight.setString(1, username.get());
                    deleteRight.executeUpdate();
                }
            }
            try (PreparedStatement deleteUser = connection.prepareStatement(
                    "delete from `user` where cast(id as char) = ? or userName = ?")) {
                deleteUser.setString(1, normalizeId(id));
                deleteUser.setString(2, id);
                deleteUser.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public List<Role> findAllRoles() {
        return withConnection(connection -> {
            List<Role> roles = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "select id, name, description, functions from `role` order by id");
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    roles.add(mapRole(connection, resultSet));
                }
            }
            return roles;
        });
    }

    @Override
    public Optional<Role> findRoleById(String id) {
        return withConnection(connection -> findRoleByName(connection, id));
    }

    @Override
    public Role saveRole(Role role) {
        return withConnection(connection -> {
            try {
                connection.setAutoCommit(false);
                String oldName = blankToNull(role.getId());
                String roleName = saveRoleRow(connection, oldName, role);
                connection.commit();
                return findRoleByName(connection, roleName).orElse(role);
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    @Override
    public void deleteRole(String id) {
        withConnection(connection -> {
            try (PreparedStatement deleteRight = connection.prepareStatement(
                    "delete from `right` where roleName = ?")) {
                deleteRight.setString(1, id);
                deleteRight.executeUpdate();
            }
            try (PreparedStatement deleteRole = connection.prepareStatement(
                    "delete from `role` where name = ?")) {
                deleteRole.setString(1, id);
                deleteRole.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public List<String> findRoleIdsByUserId(String userId) {
        return withConnection(connection -> {
            Optional<User> user = findUserById(connection, userId);
            if (user.isEmpty()) {
                return List.of();
            }
            return findRoleIdsByUsername(connection, user.get().getUsername());
        });
    }

    @Override
    public List<String> findPermissionsByUserId(String userId) {
        return withConnection(connection -> {
            Optional<User> user = findUserById(connection, userId);
            if (user.isEmpty()) {
                return List.of();
            }

            Set<String> permissions = new LinkedHashSet<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    select r.functions
                    from `role` r
                    join `right` ur on ur.roleName = r.name
                    where ur.userName = ?
                    """)) {
                statement.setString(1, user.get().getUsername());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        permissions.addAll(parsePermissions(connection, resultSet.getString("functions")));
                    }
                }
            }
            return new ArrayList<>(permissions);
        });
    }

    @Override
    public String nextUserId() {
        return String.valueOf(userSequence.incrementAndGet());
    }

    @Override
    public String nextRoleId() {
        return String.valueOf(roleSequence.incrementAndGet());
    }

    private Optional<User> findUserById(Connection connection, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select id, userName, password, email from `user` where cast(id as char) = ? or userName = ?")) {
            statement.setString(1, normalizeId(id));
            statement.setString(2, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                User user = mapUser(resultSet);
                user.setRoleIds(findRoleIdsByUsername(connection, user.getUsername()));
                return Optional.of(user);
            }
        }
    }

    private void ensureDefaultRoles(Connection connection) throws SQLException {
        ensureRole(connection, "admin", "合同管理员",
                "负责分配合同、维护用户角色权限和查看日志",
                "dashboard:view,contract:draft,contract:countersign,contract:finalize,contract:approve,contract:sign,query:info,query:process,base:contract,base:customer,system:assign,system:user,system:role,system:permission,system:log");
        ensureRole(connection, "operator", "合同操作员",
                "负责合同起草、会签、定稿、审批、签订和查询",
                "dashboard:view,contract:draft,contract:countersign,contract:finalize,contract:approve,contract:sign,query:info,query:process,base:contract,base:customer");
        ensureRole(connection, "new_user", "新用户", "注册后等待管理员授权", "");
    }

    private void ensureRole(Connection connection, String name, String description, String fallbackDescription,
                            String functions) throws SQLException {
        if (roleExists(connection, name)) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into `role` (name, description, functions) values (?, ?, ?)")) {
            statement.setString(1, name);
            statement.setString(2, description == null || description.isBlank() ? fallbackDescription : description);
            statement.setString(3, functions);
            statement.executeUpdate();
        }
    }

    private String saveUserRow(Connection connection, User user) throws SQLException {
        String id = blankToNull(user.getId());
        String password = requirePasswordForLegacySchema(user.getPassword());
        String email = blankToNull(user.getEmail()) == null ? DEFAULT_USER_EMAIL : user.getEmail();
        if (id != null && userExists(connection, id)) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "update `user` set userName = ?, password = ?, email = ? where cast(id as char) = ? or userName = ?")) {
                statement.setString(1, user.getUsername());
                statement.setString(2, password);
                statement.setString(3, email);
                statement.setString(4, normalizeId(id));
                statement.setString(5, id);
                statement.executeUpdate();
            }
            return user.getUsername();
        }

        if (id == null) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into `user` (userName, password, email) values (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, user.getUsername());
                statement.setString(2, password);
                statement.setString(3, email);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        user.setId(String.valueOf(keys.getInt(1)));
                    }
                }
            }
            return user.getUsername();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "insert into `user` (id, userName, password, email) values (?, ?, ?, ?)")) {
            statement.setInt(1, Integer.parseInt(normalizeId(id)));
            statement.setString(2, user.getUsername());
            statement.setString(3, password);
            statement.setString(4, email);
            statement.executeUpdate();
        }
        return user.getUsername();
    }

    private void saveUserRoles(Connection connection, String username, List<String> roleNames,
                               String oldUsername) throws SQLException {
        String deleteName = oldUsername == null ? username : oldUsername;
        deleteUserRoles(connection, deleteName);
        if (oldUsername != null && !oldUsername.equals(username)) {
            deleteUserRoles(connection, username);
        }

        for (String roleName : roleNames) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into `right` (userName, roleName, description) values (?, ?, ?)")) {
                statement.setString(1, username);
                statement.setString(2, roleName);
                statement.setString(3, "");
                statement.executeUpdate();
            }
        }
    }

    private void deleteUserRoles(Connection connection, String username) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "delete from `right` where userName = ?")) {
            statement.setString(1, username);
            statement.executeUpdate();
        }
    }

    private Optional<Role> findRoleByName(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select id, name, description, functions from `role` where name = ?")) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRole(connection, resultSet));
            }
        }
    }

    private String saveRoleRow(Connection connection, String oldName, Role role) throws SQLException {
        String roleName = role.getName();
        String functions = serializePermissions(connection, role.getPermissions());
        if (oldName != null && roleExists(connection, oldName)) {
            if (!oldName.equals(roleName)) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "insert into `role` (name, description, functions) values (?, ?, ?)")) {
                    insert.setString(1, roleName);
                    insert.setString(2, role.getDescription());
                    insert.setString(3, functions);
                    insert.executeUpdate();
                }
                try (PreparedStatement updateRight = connection.prepareStatement(
                        "update `right` set roleName = ? where roleName = ?")) {
                    updateRight.setString(1, roleName);
                    updateRight.setString(2, oldName);
                    updateRight.executeUpdate();
                }
                try (PreparedStatement deleteOld = connection.prepareStatement(
                        "delete from `role` where name = ?")) {
                    deleteOld.setString(1, oldName);
                    deleteOld.executeUpdate();
                }
                return roleName;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "update `role` set name = ?, description = ?, functions = ? where name = ?")) {
                statement.setString(1, roleName);
                statement.setString(2, role.getDescription());
                statement.setString(3, functions);
                statement.setString(4, oldName);
                statement.executeUpdate();
            }
            return roleName;
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "insert into `role` (name, description, functions) values (?, ?, ?)")) {
            statement.setString(1, roleName);
            statement.setString(2, role.getDescription());
            statement.setString(3, functions);
            statement.executeUpdate();
        }
        return roleName;
    }

    private List<String> findRoleIdsByUsername(Connection connection, String username) throws SQLException {
        List<String> roleIds = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "select roleName from `right` where userName = ? order by id")) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    roleIds.add(resultSet.getString("roleName"));
                }
            }
        }
        return roleIds;
    }

    private List<String> parsePermissions(Connection connection, String functions) throws SQLException {
        if (functions == null || functions.isBlank()) {
            return List.of();
        }

        List<FunctionRecord> functionRecords = findAllFunctions(connection);
        Set<String> permissions = new LinkedHashSet<>();
        for (String value : functions.split(",")) {
            String raw = value.trim();
            if (raw.isEmpty()) {
                continue;
            }
            if (isPermissionKey(raw)) {
                permissions.add(raw);
                continue;
            }
            functionRecords.stream()
                    .filter(record -> record.num().equals(raw))
                    .findFirst()
                    .map(this::permissionFromFunction)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .ifPresent(permissions::add);
        }
        return new ArrayList<>(permissions);
    }

    private String serializePermissions(Connection connection, List<String> permissions) throws SQLException {
        List<FunctionRecord> functionRecords = findAllFunctions(connection);
        List<String> values = new ArrayList<>();
        for (String permission : permissions) {
            Optional<String> functionNum = functionRecords.stream()
                    .filter(record -> permissionFromFunction(record)
                            .map(permission::equals)
                            .orElse(false))
                    .map(FunctionRecord::num)
                    .findFirst();
            values.add(functionNum.orElse(permission));
        }
        return String.join(",", values);
    }

    private List<FunctionRecord> findAllFunctions(Connection connection) throws SQLException {
        List<FunctionRecord> records = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "select num, name, URL, description from `function` order by num");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                records.add(new FunctionRecord(
                        resultSet.getString("num"),
                        resultSet.getString("name"),
                        resultSet.getString("URL"),
                        resultSet.getString("description")));
            }
        }
        return records;
    }

    private Optional<String> permissionFromFunction(FunctionRecord record) {
        String text = String.join(" ",
                nullToEmpty(record.name()),
                nullToEmpty(record.url()),
                nullToEmpty(record.description())).toLowerCase();

        if (text.contains("dashboard") || containsAny(text, "\u5de5\u4f5c\u53f0")) return Optional.of("dashboard:view");
        if (text.contains("contract/draft") || containsAny(text, "\u8d77\u8349")) return Optional.of("contract:draft");
        if (text.contains("contract/countersign") || containsAny(text, "\u4f1a\u7b7e")) return Optional.of("contract:countersign");
        if (text.contains("contract/finalize") || containsAny(text, "\u5b9a\u7a3f")) return Optional.of("contract:finalize");
        if (text.contains("contract/approve") || containsAny(text, "\u5ba1\u6279")) return Optional.of("contract:approve");
        if (text.contains("contract/sign") || containsAny(text, "\u7b7e\u8ba2")) return Optional.of("contract:sign");
        if (text.contains("query/info") || containsAny(text, "\u5408\u540c\u4fe1\u606f\u67e5\u8be2")) return Optional.of("query:info");
        if (text.contains("query/process") || containsAny(text, "\u5408\u540c\u6d41\u7a0b\u67e5\u8be2")) return Optional.of("query:process");
        if (text.contains("base/contract") || containsAny(text, "\u5408\u540c\u4fe1\u606f\u7ba1\u7406")) return Optional.of("base:contract");
        if (text.contains("base/customer") || containsAny(text, "\u5ba2\u6237")) return Optional.of("base:customer");
        if (text.contains("system/assign") || containsAny(text, "\u5206\u914d\u5408\u540c")) return Optional.of("system:assign");
        if (text.contains("system/user") || containsAny(text, "\u7528\u6237\u7ba1\u7406")) return Optional.of("system:user");
        if (text.contains("system/role") || containsAny(text, "\u89d2\u8272\u7ba1\u7406")) return Optional.of("system:role");
        if (text.contains("system/permission") || containsAny(text, "\u6743\u9650")) return Optional.of("system:permission");
        if (text.contains("system/log") || containsAny(text, "\u65e5\u5fd7")) return Optional.of("system:log");
        return Optional.empty();
    }

    private boolean containsAny(String text, String value) {
        return text.contains(value.toLowerCase());
    }

    private Role mapRole(Connection connection, ResultSet resultSet) throws SQLException {
        Role role = new Role();
        // The legacy right table references role.name, so expose role.name as the stable role id.
        role.setId(resultSet.getString("name"));
        role.setName(resultSet.getString("name"));
        role.setDescription(resultSet.getString("description"));
        role.setPermissions(parsePermissions(connection, resultSet.getString("functions")));
        return role;
    }

    private User mapUser(ResultSet resultSet) throws SQLException {
        User user = new User();
        user.setUsername(resultSet.getString("userName"));
        user.setId(user.getUsername());
        user.setPassword(resultSet.getString("password"));
        user.setEmail(resultSet.getString("email"));
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }

    private Optional<String> findUsernameById(Connection connection, String id) throws SQLException {
        if (blankToNull(id) == null) {
            return Optional.empty();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "select userName from `user` where cast(id as char) = ? or userName = ?")) {
            statement.setString(1, normalizeId(id));
            statement.setString(2, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(resultSet.getString("userName")) : Optional.empty();
            }
        }
    }

    private boolean userExists(Connection connection, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select 1 from `user` where cast(id as char) = ? or userName = ?")) {
            statement.setString(1, normalizeId(id));
            statement.setString(2, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean roleExists(Connection connection, String roleName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select 1 from `role` where name = ?")) {
            statement.setString(1, roleName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private int maxId(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select coalesce(max(id), 0) from " + tableName)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private boolean isPermissionKey(String value) {
        return value.contains(":");
    }

    private void ensureUserEmailColumn(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("alter table `user` add column email varchar(120) null");
        } catch (SQLException exception) {
            if (!isDuplicateColumn(exception)) {
                throw exception;
            }
        }
    }

    void syncDefaultUserEmails(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "update `user` set email = ? where email is null or trim(email) = ''")) {
            statement.setString(1, DEFAULT_USER_EMAIL);
            statement.executeUpdate();
        }
    }

    private boolean isDuplicateColumn(SQLException exception) {
        return exception.getErrorCode() == 1060;
    }

    private String requirePasswordForLegacySchema(String password) {
        if (password != null && password.length() > 20) {
            throw new IllegalArgumentException("Legacy `user.password` supports at most 20 characters. Expand the column before storing encrypted passwords.");
        }
        return password;
    }

    private String normalizeId(String id) {
        String value = id == null ? "" : id.trim();
        if (value.matches("[Uu]\\d+")) {
            return String.valueOf(Integer.parseInt(value.substring(1)));
        }
        return value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private <T> T withConnection(SqlCallback<T> callback) {
        try (Connection connection = DriverManager.getConnection(
                databaseProperties.getUrl(),
                databaseProperties.getUsername(),
                databaseProperties.getPassword())) {
            return callback.execute(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Database operation failed: " + exception.getMessage(), exception);
        }
    }

    @FunctionalInterface
    private interface SqlCallback<T> {
        T execute(Connection connection) throws SQLException;
    }

    private record FunctionRecord(String num, String name, String url, String description) {
    }
}
