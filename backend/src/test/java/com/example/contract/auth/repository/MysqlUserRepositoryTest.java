package com.example.contract.auth.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.contract.common.DatabaseProperties;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MysqlUserRepositoryTest {

    @Test
    void syncDefaultUserEmailsOnlyFillsBlankEmails() throws Exception {
        SqlCapture capture = new SqlCapture();
        MysqlUserRepository repository = new MysqlUserRepository(new DatabaseProperties());

        repository.syncDefaultUserEmails(capture.connection());

        String normalizedSql = capture.sql.replaceAll("\\s+", " ").trim().toLowerCase();
        assertTrue(normalizedSql.contains("where email is null or trim(email) = ''"));
        assertFalse(normalizedSql.matches("update `user` set email = \\?"));
        assertEquals("user@example.com", capture.stringValues.get(1));
    }

    private static class SqlCapture {
        private String sql = "";
        private final Map<Integer, String> stringValues = new HashMap<>();

        Connection connection() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] { Connection.class },
                    (proxy, method, args) -> {
                        if ("prepareStatement".equals(method.getName()) && args != null && args.length > 0) {
                            sql = (String) args[0];
                            return preparedStatement();
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private PreparedStatement preparedStatement() {
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[] { PreparedStatement.class },
                    (proxy, method, args) -> {
                        if ("setString".equals(method.getName()) && args != null && args.length == 2) {
                            stringValues.put((Integer) args[0], (String) args[1]);
                            return null;
                        }
                        if ("executeUpdate".equals(method.getName())) {
                            return 1;
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private Object defaultValue(Class<?> returnType) {
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            return null;
        }
    }
}
