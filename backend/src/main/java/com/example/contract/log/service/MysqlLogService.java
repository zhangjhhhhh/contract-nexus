package com.example.contract.log.service;

import com.example.contract.common.DatabaseProperties;
import com.example.contract.log.model.OperationLog;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("db")
public class MysqlLogService implements LogService {

    private final DatabaseProperties databaseProperties;

    public MysqlLogService(DatabaseProperties databaseProperties) {
        this.databaseProperties = databaseProperties;
    }

    @Override
    public void record(String userName, String content) {
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into `log` (userName, content, time) values (?, ?, ?)")) {
                statement.setString(1, userName);
                statement.setString(2, content);
                statement.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
                statement.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public List<OperationLog> list() {
        return withConnection(connection -> {
            List<OperationLog> logs = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "select id, userName, content, time from `log` order by time desc, id desc");
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Timestamp time = resultSet.getTimestamp("time");
                    logs.add(new OperationLog(
                            String.valueOf(resultSet.getInt("id")),
                            resultSet.getString("userName"),
                            resultSet.getString("content"),
                            time == null ? null : time.toLocalDateTime()));
                }
            }
            return logs;
        });
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
}
