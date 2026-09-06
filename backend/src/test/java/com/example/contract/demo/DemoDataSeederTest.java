package com.example.contract.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.example.contract.common.DotenvLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class DemoDataSeederTest {

    private static final String DEFAULT_DB_URL =
            "jdbc:mysql://rm-2zekv7snw92jp09z5uo.mysql.rds.aliyuncs.com:3306/contract_system"
                    + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    private static final Pattern CONTRACT_LINE = Pattern.compile(
            "^(MNHT-\\d{4}-\\d{3})\\s*\\|\\s*([^|]+)\\s*\\|\\s*([^|]+)\\s*\\|\\s*([^|]+)\\s*\\|\\s*(.+)$");
    private static final List<String> CONFIRMED_LEGACY_CONTRACTS = List.of(
            "HT-2026-001", "HT-2026-002",
            "HT-2026-010", "HT-2026-011", "HT-2026-012", "HT-2026-013", "HT-2026-014",
            "HT-2026-015", "HT-2026-016", "HT-2026-017", "HT-2026-018", "HT-2026-019",
            "HT-2026-020", "HT-2026-021", "HT-2026-022", "HT-2026-023", "HT-2026-024",
            "HT-2026-025", "HT-2026-026", "HT-2026-027", "HT-2026-028", "HT-2026-029",
            "HT-2026-030", "HT-2026-031", "HT-2026-032", "HT-2026-033", "HT-2026-034",
            "HT-2026-035", "HT-2026-036", "HT-2026-037", "HT-2026-038", "HT-2026-039",
            "HT-2026-040", "HT-2026-041");
    private static final Stage[] STAGES = {
            Stage.COMPLETED, Stage.SIGNING, Stage.APPROVING, Stage.FINALIZING, Stage.COUNTERSIGNING,
            Stage.COMPLETED, Stage.COMPLETED, Stage.SIGNING, Stage.APPROVING, Stage.FINALIZING,
            Stage.DRAFTING, Stage.COMPLETED, Stage.SIGNING, Stage.APPROVING, Stage.COUNTERSIGNING,
            Stage.COMPLETED, Stage.APPROVING, Stage.SIGNING, Stage.COMPLETED, Stage.FINALIZING,
            Stage.APPROVING, Stage.COMPLETED, Stage.SIGNING, Stage.COUNTERSIGNING, Stage.COMPLETED,
            Stage.APPROVING, Stage.FINALIZING, Stage.SIGNING, Stage.COMPLETED, Stage.DRAFTING
    };
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Map<String, String> LOCAL_ENV = readLocalEnv();
    private static final String BULK_USER_EMAIL = "demo@example.com";

    @Test
    void seedDemoDataIntoConfiguredDatabase() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("seed.demo"), "Set -Dseed.demo=true to write demo data.");
        DotenvLoader.load();

        Path docsDir = resolveDocsDir();
        List<DemoContract> contracts = readContracts(docsDir.resolve("合同清单.txt"));
        assertEquals(30, contracts.size(), "模拟合同清单应包含 30 条合同");

        String url = setting("DB_URL", DEFAULT_DB_URL);
        String username = requireSetting("DB_USERNAME");
        String password = setting("DB_PASSWORD", "");

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            connection.setAutoCommit(false);
            try {
                ensureSchema(connection);
                List<String> users = findUsers(connection);
                assertFalse(users.isEmpty(), "数据库中没有可分配的用户");

                clearDemoContracts(connection);
                Map<String, String> customerIds = upsertCustomers(connection, companiesOf(contracts));
                insertContracts(connection, docsDir, contracts, customerIds, users);

                connection.commit();
                System.out.printf(Locale.ROOT,
                        "Seeded demo data: customers=%d, contracts=%d, users=%d%n",
                        customerIds.size(), contracts.size(), users.size());
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Test
    void cleanupNonMockContractsInConfiguredDatabase() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("cleanup.non-mock.contracts"),
                "Set -Dcleanup.non-mock.contracts=true to delete confirmed legacy contracts.");
        DotenvLoader.load();

        String url = setting("DB_URL", DEFAULT_DB_URL);
        String username = requireSetting("DB_USERNAME");
        String password = setting("DB_PASSWORD", "");

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            connection.setAutoCommit(false);
            try {
                ensureSchema(connection);
                List<String> deletingContracts = confirmedLegacyContractsInDatabase(connection);
                List<String> storedNames = legacyAttachmentStoredNames(connection, deletingContracts);

                deleteLegacyContractRows(connection, deletingContracts);
                deleteStoredFiles(connection, storedNames);

                int remainingLegacy = confirmedLegacyContractsInDatabase(connection).size();
                int remainingMock = countWhere(connection, "select count(*) from contract where num like 'MNHT-2026-%'");
                assertEquals(0, remainingLegacy, "确认删除列表中的合同应已清空");
                assertEquals(30, remainingMock, "mock 合同应保留 30 条");

                connection.commit();
                System.out.printf(Locale.ROOT,
                        "Cleaned non-mock contracts: deletedContracts=%d, deletedStoredFiles=%d, remainingMockContracts=%d%n",
                        deletingContracts.size(), storedNames.size(), remainingMock);
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Test
    void enhanceMockDataInConfiguredDatabase() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("enhance.mock.data"),
                "Set -Denhance.mock.data=true to add richer mock logs and versions.");
        DotenvLoader.load();

        String url = setting("DB_URL", DEFAULT_DB_URL);
        String username = requireSetting("DB_USERNAME");
        String password = setting("DB_PASSWORD", "");

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            connection.setAutoCommit(false);
            try {
                ensureSchema(connection);
                ensureVersionTables(connection);
                ensureLogTable(connection);
                List<String> users = findUsers(connection);
                assertFalse(users.isEmpty(), "数据库中没有可分配的用户");

                clearMockEnhancements(connection);
                int versionCount = insertMockVersions(connection, users);
                int logCount = insertMockLogs(connection, users);
                int signedCount = enrichMockSignRecords(connection);

                connection.commit();
                System.out.printf(Locale.ROOT,
                        "Enhanced mock data: versions=%d, logs=%d, signedProcesses=%d%n",
                        versionCount, logCount, signedCount);
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Test
    void previewNonMockContractsInConfiguredDatabase() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("preview.non-mock.contracts"),
                "Set -Dpreview.non-mock.contracts=true to preview non-mock contracts.");
        DotenvLoader.load();

        String url = setting("DB_URL", DEFAULT_DB_URL);
        String username = requireSetting("DB_USERNAME");
        String password = setting("DB_PASSWORD", "");

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            int mockCount = countWhere(connection, "select count(*) from contract where num like 'MNHT-2026-%'");
            List<String> nonMockContracts = queryStrings(
                    connection,
                    "select num from contract where num not like 'MNHT-2026-%' order by num",
                    "num");
            System.out.printf(Locale.ROOT,
                    "Non-mock contract preview: nonMockContracts=%d, mockContracts=%d%n",
                    nonMockContracts.size(), mockCount);
            for (String contractNum : nonMockContracts) {
                System.out.println("Candidate contract: " + contractNum);
            }
        }
    }

    @Test
    void updateDemoUserEmailsInConfiguredDatabase() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("update.demo.user.emails"),
                "Set -Dupdate.demo.user.emails=true to update non-exempt user emails.");
        DotenvLoader.load();

        String url = setting("DB_URL", DEFAULT_DB_URL);
        String username = requireSetting("DB_USERNAME");
        String password = setting("DB_PASSWORD", "");

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            connection.setAutoCommit(false);
            try {
                int updated;
                try (PreparedStatement statement = connection.prepareStatement("""
                        update `user`
                        set email = ?
                        where userName <> ?
                          and userName <> ?
                        """)) {
                    statement.setString(1, BULK_USER_EMAIL);
                    statement.setString(2, "admin");
                    statement.setString(3, "张三");
                    updated = statement.executeUpdate();
                }

                int unmatched = countWhere(connection, """
                        select count(*)
                        from `user`
                        where userName <> 'admin'
                          and userName <> '张三'
                          and coalesce(email, '') <> 'demo@example.com'
                        """);
                assertEquals(0, unmatched, "除 admin 和张三外，其余用户邮箱应已统一更新");

                connection.commit();
                System.out.printf(Locale.ROOT,
                        "Updated user emails: updatedUsers=%d, targetEmail=%s, excludedUsers=admin,张三%n",
                        updated, BULK_USER_EMAIL);
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static void ensureSchema(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "alter table contract add column aiReview longtext null");
        addColumnIfMissing(connection, "alter table contract_process add column createdAt datetime null");
        addColumnIfMissing(connection, "alter table contract_process add column signerName varchar(100) null");
        addColumnIfMissing(connection, "alter table contract_process add column signatureDataUrl longtext null");
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table if not exists file_storage (
                      storedName varchar(255) primary key,
                      originalName varchar(255) not null,
                      type varchar(30) not null,
                      contentType varchar(120) not null,
                      size bigint not null,
                      content longblob not null,
                      uploadTime datetime not null
                    ) engine=InnoDB default charset=utf8mb4
                    """);
        }
    }

    private static void ensureVersionTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table if not exists contract_version (
                      id bigint primary key auto_increment,
                      conNum varchar(50) not null,
                      versionNo int not null,
                      num varchar(50) not null,
                      name varchar(100) not null,
                      customer varchar(50) not null,
                      beginTime date not null,
                      endTime date not null,
                      content text not null,
                      userName varchar(50) not null,
                      approverName varchar(50) not null,
                      approvalResult varchar(20) not null,
                      approvalOpinion text not null,
                      createdAt datetime not null,
                      unique key uk_contract_version_no (conNum, versionNo)
                    ) engine=InnoDB default charset=utf8mb4
                    """);
            statement.execute("""
                    create table if not exists contract_version_attachment (
                      id bigint primary key auto_increment,
                      versionId bigint not null,
                      fileName varchar(255) not null,
                      path varchar(255) not null,
                      type varchar(30) not null,
                      uploadTime datetime null
                    ) engine=InnoDB default charset=utf8mb4
                    """);
        }
    }

    private static void ensureLogTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table if not exists `log` (
                      id bigint primary key auto_increment,
                      userName varchar(50) not null,
                      content varchar(255) not null,
                      time datetime not null
                    ) engine=InnoDB default charset=utf8mb4
                    """);
        }
    }

    private static void clearDemoContracts(Connection connection) throws SQLException {
        try (PreparedStatement deleteVersionAttachments = connection.prepareStatement("""
                delete va from contract_version_attachment va
                join contract_version v on v.id = va.versionId
                where v.conNum like 'MNHT-2026-%'
                """)) {
            deleteVersionAttachments.executeUpdate();
        } catch (SQLException exception) {
            ignoreMissingTable(exception);
        }
        deleteWhere(connection, "delete from contract_version where conNum like 'MNHT-2026-%'");
        deleteWhere(connection, "delete from contract_attachment where conNum like 'MNHT-2026-%'");
        deleteWhere(connection, "delete from contract_process where conNum like 'MNHT-2026-%'");
        deleteWhere(connection, "delete from contract_state where conNum like 'MNHT-2026-%'");
        deleteWhere(connection, "delete from contract where num like 'MNHT-2026-%'");
        deleteWhere(connection, "delete from file_storage where storedName like 'demo-MNHT-2026-%'");
    }

    private static void deleteLegacyContractRows(Connection connection, List<String> contractNums) throws SQLException {
        if (contractNums.isEmpty()) {
            return;
        }
        try (PreparedStatement deleteVersionAttachments = connection.prepareStatement("""
                delete va from contract_version_attachment va
                join contract_version v on v.id = va.versionId
                where v.conNum = ?
                """)) {
            for (String contractNum : contractNums) {
                deleteVersionAttachments.setString(1, contractNum);
                deleteVersionAttachments.addBatch();
            }
            deleteVersionAttachments.executeBatch();
        } catch (SQLException exception) {
            ignoreMissingTable(exception);
        }
        deleteRowsByContractNum(connection, "delete from contract_version where conNum = ?", contractNums);
        deleteRowsByContractNum(connection, "delete from contract_attachment where conNum = ?", contractNums);
        deleteRowsByContractNum(connection, "delete from contract_process where conNum = ?", contractNums);
        deleteRowsByContractNum(connection, "delete from contract_state where conNum = ?", contractNums);
        deleteRowsByContractNum(connection, "delete from contract where num = ?", contractNums);
    }

    private static List<String> legacyAttachmentStoredNames(Connection connection, List<String> contractNums) throws SQLException {
        Set<String> storedNames = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "select path from contract_attachment where conNum = ?")) {
            for (String contractNum : contractNums) {
                statement.setString(1, contractNum);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        storedNameFromPath(resultSet.getString("path")).ifPresent(storedNames::add);
                    }
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                select va.path
                from contract_version_attachment va
                join contract_version v on v.id = va.versionId
                where v.conNum = ?
                """)) {
            for (String contractNum : contractNums) {
                statement.setString(1, contractNum);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        storedNameFromPath(resultSet.getString("path")).ifPresent(storedNames::add);
                    }
                }
            }
        } catch (SQLException exception) {
            ignoreMissingTable(exception);
        }
        storedNames.removeIf(name -> name.startsWith("demo-MNHT-2026-"));
        return new ArrayList<>(storedNames);
    }

    private static List<String> confirmedLegacyContractsInDatabase(Connection connection) throws SQLException {
        List<String> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("select 1 from contract where num = ?")) {
            for (String contractNum : CONFIRMED_LEGACY_CONTRACTS) {
                statement.setString(1, contractNum);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        values.add(contractNum);
                    }
                }
            }
        }
        return values;
    }

    private static void deleteRowsByContractNum(Connection connection, String sql, List<String> contractNums) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (String contractNum : contractNums) {
                statement.setString(1, contractNum);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException exception) {
            ignoreMissingTable(exception);
        }
    }

    private static void clearMockEnhancements(Connection connection) throws SQLException {
        try (PreparedStatement deleteVersionAttachments = connection.prepareStatement("""
                delete va from contract_version_attachment va
                join contract_version v on v.id = va.versionId
                where v.conNum like 'MNHT-2026-%'
                """)) {
            deleteVersionAttachments.executeUpdate();
        } catch (SQLException exception) {
            ignoreMissingTable(exception);
        }
        deleteWhere(connection, "delete from contract_version where conNum like 'MNHT-2026-%'");
        deleteWhere(connection, "delete from `log` where content like '[MOCK]%'");
    }

    private static int insertMockVersions(Connection connection, List<String> users) throws SQLException {
        List<MockContractRow> contracts = mockContractRows(connection);
        int count = 0;
        for (int i = 0; i < contracts.size(); i++) {
            MockContractRow contract = contracts.get(i);
            if (i % 3 == 1) {
                continue;
            }
            long versionId = insertMockVersion(connection, contract, 1, users.get((i + 2) % users.size()),
                    "approved", "模拟审批通过：条款结构完整，建议留存交付资料。",
                    contract.content() + "\n\n版本说明：初版已完成业务确认。", contract.createdAt().plusDays(2));
            copyCurrentAttachmentsToVersion(connection, contract.num(), versionId);
            count++;
            if (i % 5 == 0) {
                long secondVersionId = insertMockVersion(connection, contract, 2, users.get((i + 3) % users.size()),
                        "approved", "模拟复核通过：已补充付款节点和验收描述。",
                        contract.content() + "\n\n版本说明：复核版补充付款和验收节点。", contract.createdAt().plusDays(4));
                copyCurrentAttachmentsToVersion(connection, contract.num(), secondVersionId);
                count++;
            }
        }
        return count;
    }

    private static long insertMockVersion(Connection connection, MockContractRow contract, int versionNo, String approver,
                                          String result, String opinion, String content, LocalDateTime createdAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into contract_version
                  (conNum, versionNo, num, name, customer, beginTime, endTime, content, userName,
                   approverName, approvalResult, approvalOpinion, createdAt)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, contract.num());
            statement.setInt(2, versionNo);
            statement.setString(3, contract.num());
            statement.setString(4, contract.name());
            statement.setString(5, contract.customer());
            statement.setDate(6, Date.valueOf(contract.beginTime()));
            statement.setDate(7, Date.valueOf(contract.endTime()));
            statement.setString(8, content);
            statement.setString(9, contract.userName());
            statement.setString(10, approver);
            statement.setString(11, result);
            statement.setString(12, opinion);
            statement.setTimestamp(13, Timestamp.valueOf(createdAt));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static void copyCurrentAttachmentsToVersion(Connection connection, String contractNum, long versionId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "select fileName, path, type, uploadTime from contract_attachment where conNum = ? order by id");
             PreparedStatement insert = connection.prepareStatement("""
                     insert into contract_version_attachment (versionId, fileName, path, type, uploadTime)
                     values (?, ?, ?, ?, ?)
                     """)) {
            select.setString(1, contractNum);
            try (ResultSet resultSet = select.executeQuery()) {
                while (resultSet.next()) {
                    insert.setLong(1, versionId);
                    insert.setString(2, resultSet.getString("fileName"));
                    insert.setString(3, resultSet.getString("path"));
                    insert.setString(4, resultSet.getString("type"));
                    insert.setTimestamp(5, resultSet.getTimestamp("uploadTime"));
                    insert.addBatch();
                }
            }
            insert.executeBatch();
        }
    }

    private static int insertMockLogs(Connection connection, List<String> users) throws SQLException {
        List<MockContractRow> contracts = mockContractRows(connection);
        int count = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into `log` (userName, content, time) values (?, ?, ?)")) {
            for (int i = 0; i < contracts.size(); i++) {
                MockContractRow contract = contracts.get(i);
                LocalDateTime base = contract.createdAt();
                addLog(statement, contract.userName(), "[MOCK] 起草合同：" + contract.name(), base.plusMinutes(12));
                count++;
                addLog(statement, "admin", "[MOCK] 分配合同流程：" + contract.name(), base.plusMinutes(35));
                count++;
                if (i % 2 == 0) {
                    addLog(statement, users.get((i + 1) % users.size()), "[MOCK] 提交会签意见：" + contract.name(), base.plusHours(4));
                    count++;
                }
                if (i % 3 != 1) {
                    addLog(statement, users.get((i + 2) % users.size()), "[MOCK] 提交审批意见：" + contract.name(), base.plusDays(2).plusHours(2));
                    count++;
                }
                if (i % 4 == 0) {
                    addLog(statement, users.get((i + 3) % users.size()), "[MOCK] 录入签订信息：" + contract.name(), base.plusDays(4).plusHours(1));
                    count++;
                }
            }
            statement.executeBatch();
        }
        return count;
    }

    private static void addLog(PreparedStatement statement, String userName, String content, LocalDateTime time) throws SQLException {
        statement.setString(1, userName);
        statement.setString(2, content);
        statement.setTimestamp(3, Timestamp.valueOf(time));
        statement.addBatch();
    }

    private static int enrichMockSignRecords(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                update contract_process
                set signerName = userName,
                    signatureDataUrl = 'mock-signature-data'
                where conNum like 'MNHT-2026-%'
                  and type = 3
                  and state = 1
                  and time is not null
                """)) {
            return statement.executeUpdate();
        }
    }

    private static List<MockContractRow> mockContractRows(Connection connection) throws SQLException {
        List<MockContractRow> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                select c.num, c.name, c.customer, c.beginTime, c.endTime, c.content, c.userName,
                       coalesce((select min(s.time) from contract_state s where s.conNum = c.num), now()) as createdAt
                from contract c
                where c.num like 'MNHT-2026-%'
                order by c.num
                """);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                values.add(new MockContractRow(
                        resultSet.getString("num"),
                        resultSet.getString("name"),
                        resultSet.getString("customer"),
                        resultSet.getDate("beginTime").toLocalDate(),
                        resultSet.getDate("endTime").toLocalDate(),
                        resultSet.getString("content"),
                        resultSet.getString("userName"),
                        resultSet.getTimestamp("createdAt").toLocalDateTime()));
            }
        }
        return values;
    }

    private static void deleteStoredFiles(Connection connection, List<String> storedNames) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "delete from file_storage where storedName = ? and storedName not like 'demo-MNHT-2026-%'")) {
            for (String storedName : storedNames) {
                statement.setString(1, storedName);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException exception) {
            ignoreMissingTable(exception);
        }
    }

    private static Map<String, String> upsertCustomers(Connection connection, List<String> companies) throws SQLException {
        Map<String, String> customerIds = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into customer (num, name, tel, address, fax, email, bank, account, remark)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on duplicate key update
                  name = values(name),
                  tel = values(tel),
                  address = values(address),
                  fax = values(fax),
                  email = values(email),
                  bank = values(bank),
                  account = values(account),
                  remark = values(remark)
                """)) {
            for (int i = 0; i < companies.size(); i++) {
                String company = companies.get(i);
                String id = "MNC" + String.format(Locale.ROOT, "%03d", i + 1);
                customerIds.put(company, id);
                statement.setString(1, id);
                statement.setString(2, company);
                statement.setString(3, phoneFor(i));
                statement.setString(4, addressFor(i));
                statement.setString(5, "010-" + String.format(Locale.ROOT, "%08d", 62000000 + i * 137));
                statement.setString(6, "contact" + String.format(Locale.ROOT, "%02d", i + 1) + "@demo.sihai.local");
                statement.setString(7, bankFor(i));
                statement.setString(8, "6222" + String.format(Locale.ROOT, "%015d", 20260000000L + i * 7919L));
                statement.setString(9, "演示客户，来源于 docs/模拟合同文件夹，用于系统演示、测试和培训。");
                statement.addBatch();
            }
            statement.executeBatch();
        }
        return customerIds;
    }

    private static void insertContracts(Connection connection, Path docsDir, List<DemoContract> contracts,
                                        Map<String, String> customerIds, List<String> users) throws Exception {
        for (int i = 0; i < contracts.size(); i++) {
            DemoContract contract = contracts.get(i);
            int displayIndex = i + 1;
            Stage stage = STAGES[i % STAGES.length];
            String drafter = users.get(i % users.size());
            LocalDate beginDate = LocalDate.of(2026, 1, 5).plusDays(i * 5L);
            LocalDate endDate = beginDate.plusMonths(12).minusDays(displayIndex % 11L);
            LocalDateTime createdAt = beginDate.atTime(9 + displayIndex % 7, 15);
            String customerId = customerIds.get(contract.secondParty());

            insertFile(connection, docsDir.resolve(contract.fileName()), contract);
            insertContract(connection, contract, customerId, drafter, beginDate, endDate);
            insertAttachment(connection, contract, createdAt);
            insertStates(connection, contract.num(), stage, createdAt);
            insertProcesses(connection, contract.num(), stage, users, i, drafter, createdAt);
        }
    }

    private static void insertContract(Connection connection, DemoContract contract, String customerId, String drafter,
                                       LocalDate beginDate, LocalDate endDate) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into contract (num, name, customer, beginTime, endTime, content, userName, aiReview)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, contract.num());
            statement.setString(2, contract.title());
            statement.setString(3, customerId);
            statement.setDate(4, Date.valueOf(beginDate));
            statement.setDate(5, Date.valueOf(endDate));
            statement.setString(6, contract.content());
            statement.setString(7, drafter);
            statement.setString(8, aiReviewFor(contract, beginDate, endDate));
            statement.executeUpdate();
        }
    }

    private static void insertFile(Connection connection, Path file, DemoContract contract) throws IOException, SQLException {
        byte[] content = Files.readAllBytes(file);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into file_storage (storedName, originalName, type, contentType, size, content, uploadTime)
                values (?, ?, ?, ?, ?, ?, ?)
                on duplicate key update
                  originalName = values(originalName),
                  type = values(type),
                  contentType = values(contentType),
                  size = values(size),
                  content = values(content),
                  uploadTime = values(uploadTime)
                """)) {
            statement.setString(1, contract.storedName());
            statement.setString(2, contract.fileName());
            statement.setString(3, contract.extension());
            statement.setString(4, contentType(contract.extension()));
            statement.setLong(5, content.length);
            statement.setBytes(6, content);
            statement.setTimestamp(7, Timestamp.valueOf(LocalDateTime.of(2026, 6, 14, 9, 33)));
            statement.executeUpdate();
        }
    }

    private static void insertAttachment(Connection connection, DemoContract contract, LocalDateTime createdAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into contract_attachment (conNum, fileName, path, type, uploadTime)
                values (?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, contract.num());
            statement.setString(2, contract.fileName());
            statement.setString(3, "/api/files/" + contract.storedName());
            statement.setString(4, contract.extension());
            statement.setTimestamp(5, Timestamp.valueOf(createdAt.plusMinutes(10)));
            statement.executeUpdate();
        }
    }

    private static void insertStates(Connection connection, String contractNum, Stage stage, LocalDateTime createdAt) throws SQLException {
        insertState(connection, contractNum, 1, createdAt);
        if (stage.ordinal() >= Stage.FINALIZING.ordinal()) {
            insertState(connection, contractNum, 2, createdAt.plusDays(1));
        }
        if (stage.ordinal() >= Stage.APPROVING.ordinal()) {
            insertState(connection, contractNum, 3, createdAt.plusDays(2));
        }
        if (stage.ordinal() >= Stage.SIGNING.ordinal()) {
            insertState(connection, contractNum, 4, createdAt.plusDays(3));
        }
        if (stage == Stage.COMPLETED) {
            insertState(connection, contractNum, 5, createdAt.plusDays(4));
        }
    }

    private static void insertProcesses(Connection connection, String contractNum, Stage stage, List<String> users,
                                        int index, String drafter, LocalDateTime createdAt) throws SQLException {
        if (stage == Stage.DRAFTING) {
            return;
        }

        String countersignerA = users.get((index + 1) % users.size());
        String countersignerB = users.get((index + 2) % users.size());
        String approverA = users.get((index + 3) % users.size());
        String approverB = users.get((index + 4) % users.size());
        String signer = users.get((index + 5) % users.size());

        boolean countersignDone = stage.ordinal() >= Stage.FINALIZING.ordinal();
        insertProcess(connection, contractNum, 1, 1, countersignerA,
                "会签意见：合同要素完整，可继续流转。", createdAt.plusHours(4), createdAt.plusHours(3));
        insertProcess(connection, contractNum, 1, countersignDone ? 1 : 0, countersignerB,
                countersignDone ? "会签意见：建议关注交付验收与违约责任。" : null,
                countersignDone ? createdAt.plusHours(6) : null, createdAt.plusHours(3));

        if (stage.ordinal() >= Stage.FINALIZING.ordinal()) {
            boolean finalizeDone = stage.ordinal() >= Stage.APPROVING.ordinal();
            insertProcess(connection, contractNum, 4, finalizeDone ? 1 : 0, drafter,
                    finalizeDone ? "定稿完成，已同步附件版本。" : null,
                    finalizeDone ? createdAt.plusDays(1).plusHours(2) : null, createdAt.plusDays(1));
        }

        if (stage.ordinal() >= Stage.APPROVING.ordinal()) {
            boolean approveDone = stage.ordinal() >= Stage.SIGNING.ordinal();
            insertProcess(connection, contractNum, 2, approveDone ? 1 : 1, approverA,
                    "审批意见：条款清晰，风险可控。", createdAt.plusDays(2).plusHours(2), createdAt.plusDays(2));
            insertProcess(connection, contractNum, 2, approveDone ? 1 : 0, approverB,
                    approveDone ? "审批意见：同意进入签订环节。" : null,
                    approveDone ? createdAt.plusDays(2).plusHours(5) : null, createdAt.plusDays(2));
        }

        if (stage.ordinal() >= Stage.SIGNING.ordinal()) {
            boolean signDone = stage == Stage.COMPLETED;
            insertProcess(connection, contractNum, 3, signDone ? 1 : 0, signer,
                    signDone ? "电子签章完成" : null,
                    signDone ? createdAt.plusDays(3).plusHours(4) : null, createdAt.plusDays(3));
        }
    }

    private static void insertState(Connection connection, String contractNum, int type, LocalDateTime time) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into contract_state (conNum, type, time)
                values (?, ?, ?)
                """)) {
            statement.setString(1, contractNum);
            statement.setInt(2, type);
            statement.setTimestamp(3, Timestamp.valueOf(time));
            statement.executeUpdate();
        }
    }

    private static void insertProcess(Connection connection, String contractNum, int type, int state, String username,
                                      String content, LocalDateTime time, LocalDateTime createdAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into contract_process (conNum, type, state, userName, content, time, createdAt)
                values (?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, contractNum);
            statement.setInt(2, type);
            statement.setInt(3, state);
            statement.setString(4, username);
            statement.setString(5, content);
            statement.setTimestamp(6, time == null ? null : Timestamp.valueOf(time));
            statement.setTimestamp(7, Timestamp.valueOf(createdAt));
            statement.executeUpdate();
        }
    }

    private static String aiReviewFor(DemoContract contract, LocalDate beginDate, LocalDate endDate) throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("contract_title", contract.title());
        summary.put("contract_type", contract.category());
        summary.put("party_a", contract.firstParty());
        summary.put("party_b", contract.secondParty());
        summary.put("subject_matter", contract.category() + "相关服务或标的");
        summary.put("total_amount", "详见合同附件");
        summary.put("payment_terms", "按合同约定节点结算");
        summary.put("performance_period", beginDate + " 至 " + endDate);
        summary.put("effective_date", beginDate.toString());
        summary.put("termination_date", endDate.toString());
        summary.put("dispute_resolution", "协商不成时按合同约定处理");
        root.put("contract_summary", summary);
        root.put("risk_alerts", List.of(
                Map.of(
                        "risk_id", contract.num() + "-R1",
                        "risk_level", "中",
                        "risk_category", "履约与验收",
                        "clause_reference", "交付/验收条款",
                        "risk_description", "建议在实际业务中明确验收标准、验收期限和不合格处理方式。",
                        "suggestion", "补充可量化验收指标，并约定逾期反馈视为验收或需书面确认。"
                ),
                Map.of(
                        "risk_id", contract.num() + "-R2",
                        "risk_level", "低",
                        "risk_category", "付款安排",
                        "clause_reference", "付款条款",
                        "risk_description", "付款节点与发票、验收资料的衔接需要保持一致。",
                        "suggestion", "将付款前置条件写入合同附件或补充协议。"
                )
        ));
        root.put("overall_assessment", Map.of(
                "overall_risk_level", "中",
                "missing_clauses", List.of("通知送达", "资料交接清单"),
                "summary", "该合同结构完整，适合演示系统流转；正式使用前仍需结合交易背景复核金额、期限、违约责任和争议解决条款。"
        ));
        return OBJECT_MAPPER.writeValueAsString(root);
    }

    private static List<String> findUsers(Connection connection) throws SQLException {
        List<String> users = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("select userName from `user` order by id");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String user = resultSet.getString("userName");
                if (user != null && !user.isBlank()) {
                    users.add(user);
                }
            }
        }
        return users;
    }

    private static List<DemoContract> readContracts(Path listFile) throws IOException {
        List<DemoContract> contracts = new ArrayList<>();
        for (String line : Files.readAllLines(listFile, StandardCharsets.UTF_8)) {
            Matcher matcher = CONTRACT_LINE.matcher(line.trim());
            if (!matcher.matches()) {
                continue;
            }
            String[] parties = matcher.group(3).split("\\s+-\\s+");
            if (parties.length != 2) {
                throw new IllegalArgumentException("合同双方格式不正确：" + line);
            }
            contracts.add(new DemoContract(
                    matcher.group(1).trim(),
                    matcher.group(2).trim(),
                    parties[0].trim(),
                    parties[1].trim(),
                    matcher.group(4).trim().toLowerCase(Locale.ROOT),
                    matcher.group(5).trim()));
        }
        return contracts;
    }

    private static List<String> companiesOf(List<DemoContract> contracts) {
        Set<String> companies = new LinkedHashSet<>();
        for (DemoContract contract : contracts) {
            companies.add(contract.firstParty());
            companies.add(contract.secondParty());
        }
        return new ArrayList<>(companies);
    }

    private static Path resolveDocsDir() {
        List<Path> candidates = List.of(
                Path.of("..", "docs", "模拟合同文件夹"),
                Path.of("docs", "模拟合同文件夹")
        );
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized)) {
                return normalized;
            }
        }
        throw new IllegalStateException("未找到 docs/模拟合同文件夹");
    }

    private static String phoneFor(int index) {
        String[] prefixes = {"010", "021", "020", "0755", "0571", "028", "029", "0532", "0592", "023"};
        return prefixes[index % prefixes.length] + "-" + String.format(Locale.ROOT, "%08d", 88000000 + index * 513);
    }

    private static String addressFor(int index) {
        String[] cities = {"北京市海淀区", "上海市浦东新区", "广州市天河区", "深圳市南山区", "杭州市余杭区",
                "成都市高新区", "西安市雁塔区", "青岛市市南区", "厦门市思明区", "重庆市渝北区"};
        return cities[index % cities.length] + "演示产业园 " + (index + 1) + " 号楼";
    }

    private static String bankFor(int index) {
        String[] banks = {"中国工商银行", "中国建设银行", "招商银行", "中国银行", "交通银行",
                "浦发银行", "中信银行", "兴业银行", "平安银行", "民生银行"};
        return banks[index % banks.length] + "演示支行";
    }

    private static String contentType(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
    }

    private static void addColumnIfMissing(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            if (exception.getErrorCode() != 1060) {
                throw exception;
            }
        }
    }

    private static void deleteWhere(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException exception) {
            ignoreMissingTable(exception);
        }
    }

    private static List<String> queryStrings(Connection connection, String sql, String column) throws SQLException {
        List<String> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String value = resultSet.getString(column);
                if (value != null && !value.isBlank()) {
                    values.add(value);
                }
            }
        } catch (SQLException exception) {
            ignoreMissingTable(exception);
        }
        return values;
    }

    private static int countWhere(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static java.util.Optional<String> storedNameFromPath(String path) {
        if (path == null || path.isBlank()) {
            return java.util.Optional.empty();
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String storedName = slash >= 0 ? path.substring(slash + 1) : path;
        return storedName.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(storedName);
    }

    private static void ignoreMissingTable(SQLException exception) throws SQLException {
        if (exception.getErrorCode() != 1146) {
            throw exception;
        }
    }

    private static String setting(String key, String fallback) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env;
        }
        String property = System.getProperty(key);
        if (property != null && !property.isBlank()) {
            return property;
        }
        String local = LOCAL_ENV.get(key);
        if (local != null && !local.isBlank()) {
            return local;
        }
        return fallback;
    }

    private static Map<String, String> readLocalEnv() {
        Map<String, String> values = new LinkedHashMap<>();
        List<Path> candidates = List.of(
                Path.of(".env"),
                Path.of("backend", ".env"),
                Path.of("..", ".env"),
                Path.of("..", "backend", ".env")
        );
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (!Files.isRegularFile(normalized)) {
                continue;
            }
            try {
                for (String line : Files.readAllLines(normalized, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    if (trimmed.isBlank() || trimmed.startsWith("#")) {
                        continue;
                    }
                    int split = trimmed.indexOf('=');
                    if (split <= 0) {
                        continue;
                    }
                    values.putIfAbsent(stripBom(trimmed.substring(0, split).trim()), unquote(trimmed.substring(split + 1).trim()));
                }
            } catch (IOException exception) {
                throw new IllegalStateException("读取 .env 文件失败：" + exception.getMessage(), exception);
            }
            break;
        }
        return values;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static String stripBom(String value) {
        return value == null ? "" : value.replace("\uFEFF", "");
    }

    private static String requireSetting(String key) {
        String value = setting(key, "");
        if (value.isBlank()) {
            throw new IllegalStateException("缺少数据库配置：" + key);
        }
        return value;
    }

    private enum Stage {
        DRAFTING,
        COUNTERSIGNING,
        FINALIZING,
        APPROVING,
        SIGNING,
        COMPLETED
    }

    private record DemoContract(String num, String category, String firstParty, String secondParty,
                                String type, String fileName) {

        String title() {
            String baseName = fileName.substring(0, fileName.length() - extension().length() - 1);
            String[] parts = baseName.split("-", 4);
            return parts.length >= 3 ? parts[2] : category + "合同";
        }

        String extension() {
            int dot = fileName.lastIndexOf('.');
            return dot >= 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : type;
        }

        String storedName() {
            return "demo-" + num + "." + extension();
        }

        String content() {
            return "模拟合同：" + title() + "\n"
                    + "合同类型：" + category + "\n"
                    + "甲方：" + firstParty + "\n"
                    + "乙方：" + secondParty + "\n"
                    + "正文详见附件：" + fileName + "\n"
                    + "用途：系统演示、测试和培训。";
        }
    }

    private record MockContractRow(String num, String name, String customer, LocalDate beginTime,
                                   LocalDate endTime, String content, String userName, LocalDateTime createdAt) {
    }
}
