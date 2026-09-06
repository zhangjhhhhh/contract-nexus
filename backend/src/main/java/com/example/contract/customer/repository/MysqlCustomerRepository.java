package com.example.contract.customer.repository;

import com.example.contract.common.DatabaseProperties;
import com.example.contract.customer.model.Customer;
import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("db")
public class MysqlCustomerRepository implements CustomerRepository {

    private final DatabaseProperties databaseProperties;
    private final AtomicInteger sequence = new AtomicInteger();

    public MysqlCustomerRepository(DatabaseProperties databaseProperties) {
        this.databaseProperties = databaseProperties;
    }

    @PostConstruct
    public void init() {
        withConnection(connection -> {
            ensureColumns(connection);
            syncFromCustomersTable(connection);
            sequence.set(maxNumber(connection));
            return null;
        });
    }

    @Override
    public List<Customer> findAll() {
        return withConnection(connection -> {
            List<Customer> customers = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "select num, name, tel, address, fax, email, bank, account, remark from customer order by num desc");
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    customers.add(mapCustomer(resultSet));
                }
            }
            return customers;
        });
    }

    @Override
    public Optional<Customer> findById(String id) {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "select num, name, tel, address, fax, email, bank, account, remark from customer where num = ?")) {
                statement.setString(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(mapCustomer(resultSet));
                }
            }
        });
    }

    @Override
    public Customer save(Customer customer) {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into customer (num, name, tel, address, fax, email, bank, account, remark) " +
                    "values (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "on duplicate key update " +
                    "  name = values(name), " +
                    "  tel = values(tel), " +
                    "  address = values(address), " +
                    "  fax = values(fax), " +
                    "  email = values(email), " +
                    "  bank = values(bank), " +
                    "  account = values(account), " +
                    "  remark = values(remark)")) {
                statement.setString(1, customer.getId());
                statement.setString(2, customer.getName());
                statement.setString(3, customer.getTel());
                statement.setString(4, customer.getAddress());
                statement.setString(5, customer.getFax());
                statement.setString(6, customer.getEmail());
                statement.setString(7, customer.getBank());
                statement.setString(8, customer.getAccount());
                statement.setString(9, customer.getRemark());
                statement.executeUpdate();
            }
            return customer;
        });
    }

    @Override
    public void deleteById(String id) {
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "delete from customer where num = ?")) {
                statement.setString(1, id);
                statement.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public String nextId() {
        return "C" + String.format("%03d", sequence.incrementAndGet());
    }

    @Override
    public boolean existsByFieldsExcludingId(String name, String tel, String excludeId) {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "select count(*) from customer where name = ? and tel = ? and num <> ?")) {
                statement.setString(1, name);
                statement.setString(2, tel);
                statement.setString(3, excludeId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getLong(1) > 0;
                }
            }
        });
    }

    private void ensureColumns(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("alter table customer add column email varchar(60)");
        } catch (SQLException ignored) {}
        try (Statement statement = connection.createStatement()) {
            statement.execute("alter table customer add column remark text");
        } catch (SQLException ignored) {}
    }

    private void syncFromCustomersTable(Connection connection) throws SQLException {
        // Check if stale 'customers' table exists with data that 'customer' is missing
        boolean hasCustomersTable;
        try {
            connection.createStatement().executeQuery("select 1 from customers limit 1");
            hasCustomersTable = true;
        } catch (SQLException ignored) {
            hasCustomersTable = false;
        }
        if (!hasCustomersTable) return;

        try (PreparedStatement select = connection.prepareStatement(
                "select id, name, tel, address, fax, email, bank, account, remark from customers");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                String id = rs.getString("id");
                // Only insert if not already in customer table
                try (PreparedStatement check = connection.prepareStatement(
                        "select 1 from customer where num = ?")) {
                    check.setString(1, id);
                    ResultSet cr = check.executeQuery();
                    if (cr.next()) continue; // already exists
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "insert into customer (num, name, tel, address, fax, email, bank, account, remark) " +
                        "values (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    insert.setString(1, id);
                    insert.setString(2, rs.getString("name"));
                    insert.setString(3, rs.getString("tel"));
                    insert.setString(4, rs.getString("address"));
                    insert.setString(5, rs.getString("fax"));
                    insert.setString(6, rs.getString("email"));
                    insert.setString(7, rs.getString("bank"));
                    insert.setString(8, rs.getString("account"));
                    insert.setString(9, rs.getString("remark"));
                    insert.executeUpdate();
                }
            }
        }

        // Drop stale customers table — all data now lives in customer
        try (Statement statement = connection.createStatement()) {
            statement.execute("drop table customers");
        } catch (SQLException ignored) {}
    }

    private int maxNumber(Connection connection) throws SQLException {
        List<Integer> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "select num from customer where num like ? order by num")) {
            statement.setString(1, "C%");
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String value = resultSet.getString(1);
                    if (value != null && value.length() > 1) {
                        try {
                            values.add(Integer.parseInt(value.substring(1)));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }
        return values.stream().max(Comparator.naturalOrder()).orElse(0);
    }

    private Customer mapCustomer(ResultSet resultSet) throws SQLException {
        Customer customer = new Customer();
        customer.setId(resultSet.getString("num"));
        customer.setName(resultSet.getString("name"));
        customer.setTel(resultSet.getString("tel"));
        customer.setAddress(resultSet.getString("address"));
        customer.setFax(resultSet.getString("fax"));
        customer.setEmail(resultSet.getString("email"));
        customer.setBank(resultSet.getString("bank"));
        customer.setAccount(resultSet.getString("account"));
        customer.setRemark(resultSet.getString("remark"));
        return customer;
    }

    private <T> T withConnection(SqlCallback<T> callback) {
        try (Connection connection = DriverManager.getConnection(
                databaseProperties.getUrl(),
                databaseProperties.getUsername(),
                databaseProperties.getPassword())) {
            return callback.execute(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("数据库操作失败：" + exception.getMessage(), exception);
        }
    }

    @FunctionalInterface
    private interface SqlCallback<T> {
        T execute(Connection connection) throws SQLException;
    }
}
