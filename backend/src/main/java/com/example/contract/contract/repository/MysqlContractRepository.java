package com.example.contract.contract.repository;

import com.example.contract.common.DatabaseProperties;
import com.example.contract.contract.model.Attachment;
import com.example.contract.contract.model.Contract;
import com.example.contract.contract.model.ContractProcess;
import com.example.contract.contract.model.ContractStatus;
import com.example.contract.contract.model.ContractVersion;
import com.example.contract.contract.model.ProcessState;
import com.example.contract.contract.model.ProcessType;
import com.example.contract.contract.model.SignRecord;
import jakarta.annotation.PostConstruct;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("db")
public class MysqlContractRepository implements ContractRepository {

    private final DatabaseProperties databaseProperties;
    private final AtomicInteger contractSequence = new AtomicInteger();
    private final AtomicInteger processSequence = new AtomicInteger();
    private String pendingContractNum;

    public MysqlContractRepository(DatabaseProperties databaseProperties) {
        this.databaseProperties = databaseProperties;
    }

    @PostConstruct
    public void init() {
        withConnection(connection -> {
            ensureContractAiReviewColumn(connection);
            ensureContractProcessCreatedAtColumn(connection);
            ensureContractProcessSignatureColumns(connection);
            createVersionTables(connection);
            repairMissingFinalizeProcesses(connection);
            contractSequence.set(maxContractNumber(connection));
            processSequence.set(maxIntId(connection, "contract_process"));
            return null;
        });
    }

    private void createVersionTables(Connection connection) throws SQLException {
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
                      uploadTime datetime null,
                      foreign key (versionId) references contract_version(id) on delete cascade
                    ) engine=InnoDB default charset=utf8mb4
                    """);
        }
    }

    @Override
    public List<Contract> findAllContracts() {
        return withConnection(connection -> {
            List<Contract> contracts = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "select num, name, customer, beginTime, endTime, content, userName, aiReview from contract order by num desc");
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Contract contract = mapContract(connection, resultSet);
                    contract.setAttachments(findAttachments(connection, contract.getId()));
                    contracts.add(contract);
                }
            }
            return contracts;
        });
    }

    @Override
    public Optional<Contract> findContractById(String id) {
        return withConnection(connection -> findContractById(connection, id));
    }

    @Override
    public Contract saveContract(Contract contract) {
        return withConnection(connection -> {
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement statement = connection.prepareStatement("""
                        insert into contract (num, name, customer, beginTime, endTime, content, userName, aiReview)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        on duplicate key update
                          name = values(name),
                          customer = values(customer),
                          beginTime = values(beginTime),
                          endTime = values(endTime),
                          content = values(content),
                          userName = values(userName),
                          aiReview = values(aiReview)
                        """)) {
                    statement.setString(1, contract.getNum());
                    statement.setString(2, contract.getName());
                    statement.setString(3, contract.getCustomerId());
                    statement.setDate(4, Date.valueOf(contract.getBeginTime()));
                    statement.setDate(5, Date.valueOf(contract.getEndTime()));
                    statement.setString(6, contract.getContent());
                    statement.setString(7, contract.getDrafterId());
                    statement.setString(8, contract.getAiReview());
                    statement.executeUpdate();
                }
                saveAttachments(connection, contract);
                saveStatus(connection, contract);
                connection.commit();
                return findContractById(contract.getNum()).orElse(contract);
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    @Override
    public void deleteProcessesByContractId(String contractId) {
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("delete from contract_process where conNum = ?")) {
                statement.setString(1, contractId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("delete from contract_state where conNum = ? and type > 1")) {
                statement.setString(1, contractId);
                statement.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void deleteContractById(String id) {
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("delete from contract_attachment where conNum = ?")) {
                statement.setString(1, id);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("delete from contract_process where conNum = ?")) {
                statement.setString(1, id);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("delete from contract_state where conNum = ?")) {
                statement.setString(1, id);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("delete from contract where num = ?")) {
                statement.setString(1, id);
                statement.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public List<ContractProcess> findProcessesByContractId(String contractId) {
        return withConnection(connection -> findProcessesByContractId(connection, contractId));
    }

    @Override
    public List<ContractProcess> findAllProcesses() {
        return withConnection(connection -> {
            List<ContractProcess> processes = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "select id, conNum, type, state, userName, content, time, createdAt from contract_process " +
                "order by case type when 1 then 1 when 4 then 2 when 2 then 3 when 3 then 4 end, id");
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    processes.add(mapProcess(resultSet));
                }
            }
            return processes;
        });
    }

    @Override
    public ContractProcess saveProcess(ContractProcess process) {
        return withConnection(connection -> saveProcess(connection, process));
    }

    @Override
    public List<ContractProcess> saveProcesses(List<ContractProcess> processes) {
        return withConnection(connection -> {
            for (ContractProcess process : processes) {
                saveProcess(connection, process);
            }
            return processes;
        });
    }

    @Override
    public void returnToFinalize(String contractId, String drafterId, List<String> approveUserIds) {
        withConnection(connection -> {
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement deleteStates = connection.prepareStatement("delete from contract_state where conNum = ? and type >= 3")) {
                    deleteStates.setString(1, contractId);
                    deleteStates.executeUpdate();
                }
                try (PreparedStatement resetFinalize = connection.prepareStatement("""
                        update contract_process
                        set state = 0, content = null, time = null, createdAt = ?
                        where conNum = ? and type = ?
                        """)) {
                    resetFinalize.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
                    resetFinalize.setString(2, contractId);
                    resetFinalize.setInt(3, toDbProcessType(ProcessType.FINALIZE));
                    resetFinalize.executeUpdate();
                }
                if (!hasAnyProcess(connection, contractId, ProcessType.FINALIZE)) {
                    ContractProcess finalizeProcess = new ContractProcess();
                    finalizeProcess.setContractId(contractId);
                    finalizeProcess.setType(ProcessType.FINALIZE);
                    finalizeProcess.setUserId(drafterId);
                    finalizeProcess.setState(ProcessState.PENDING);
                    saveProcess(connection, finalizeProcess);
                }
                for (String approveUserId : approveUserIds) {
                    ContractProcess approveProcess = new ContractProcess();
                    approveProcess.setContractId(contractId);
                    approveProcess.setType(ProcessType.APPROVE);
                    approveProcess.setUserId(approveUserId);
                    approveProcess.setState(ProcessState.PENDING);
                    saveProcess(connection, approveProcess);
                }
                connection.commit();
                return null;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    @Override
    public ContractVersion saveContractVersion(Contract contract, String approverId, String approvalResult, String approvalOpinion) {
        return withConnection(connection -> {
            try {
                connection.setAutoCommit(false);
                int versionNo = nextVersionNo(connection, contract.getId());
                long versionId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        insert into contract_version
                          (conNum, versionNo, num, name, customer, beginTime, endTime, content, userName,
                           approverName, approvalResult, approvalOpinion, createdAt)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, contract.getId());
                    statement.setInt(2, versionNo);
                    statement.setString(3, contract.getNum());
                    statement.setString(4, contract.getName());
                    statement.setString(5, contract.getCustomerId());
                    statement.setDate(6, Date.valueOf(contract.getBeginTime()));
                    statement.setDate(7, Date.valueOf(contract.getEndTime()));
                    statement.setString(8, contract.getContent());
                    statement.setString(9, contract.getDrafterId());
                    statement.setString(10, approverId);
                    statement.setString(11, approvalResult);
                    statement.setString(12, approvalOpinion);
                    statement.setTimestamp(13, Timestamp.valueOf(LocalDateTime.now()));
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        keys.next();
                        versionId = keys.getLong(1);
                    }
                }
                saveVersionAttachments(connection, versionId, contract.getAttachments());
                connection.commit();
                return findVersionById(connection, versionId);
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    @Override
    public List<ContractVersion> findVersionsByContractId(String contractId) {
        return withConnection(connection -> {
            List<ContractVersion> versions = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    select id, conNum, versionNo, num, name, customer, beginTime, endTime, content, userName,
                           approverName, approvalResult, approvalOpinion, createdAt
                    from contract_version
                    where conNum = ?
                    order by versionNo desc
                    """)) {
                statement.setString(1, contractId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        versions.add(mapVersion(connection, resultSet));
                    }
                }
            }
            return versions;
        });
    }

    @Override
    public SignRecord saveSignRecord(SignRecord signRecord) {
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    update contract_process
                    set signerName = ?, signatureDataUrl = ?
                    where conNum = ? and type = 3 and state = 1 and time is not null
                    order by time desc, id desc
                    limit 1
                    """)) {
                statement.setString(1, signRecord.getSignerName());
                statement.setString(2, signRecord.getSignatureDataUrl());
                statement.setString(3, signRecord.getContractId());
                statement.executeUpdate();
            }
            return null;
        });
        return signRecord;
    }

    @Override
    public List<SignRecord> findSignRecordsByContractId(String contractId) {
        return withConnection(connection -> {
            List<SignRecord> records = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    select id, conNum, content, time, signerName, signatureDataUrl
                    from contract_process
                    where conNum = ? and type = 3 and state = 1
                    order by id
                    """)) {
                statement.setString(1, contractId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        SignRecord record = new SignRecord();
                        record.setId(String.valueOf(resultSet.getInt("id")));
                        record.setContractId(resultSet.getString("conNum"));
                        Timestamp time = resultSet.getTimestamp("time");
                        record.setSignDate(time == null ? LocalDate.now() : time.toLocalDateTime().toLocalDate());
                        record.setMethod(resultSet.getString("content"));
                        record.setSignerName(resultSet.getString("signerName"));
                        record.setSignatureDataUrl(resultSet.getString("signatureDataUrl"));
                        records.add(record);
                    }
                }
            }
            return records;
        });
    }

    @Override
    public synchronized String nextContractId() {
        pendingContractNum = generateContractNum();
        return pendingContractNum;
    }

    @Override
    public synchronized String nextContractNum() {
        if (pendingContractNum != null) {
            String value = pendingContractNum;
            pendingContractNum = null;
            return value;
        }
        return generateContractNum();
    }

    @Override
    public String nextProcessId() {
        return String.valueOf(processSequence.incrementAndGet());
    }

    @Override
    public String nextSignRecordId() {
        return nextProcessId();
    }

    private String generateContractNum() {
        return "HT-" + LocalDate.now().getYear() + "-" + String.format("%03d", contractSequence.incrementAndGet());
    }

    private Optional<Contract> findContractById(Connection connection, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select num, name, customer, beginTime, endTime, content, userName, aiReview from contract where num = ?")) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                Contract contract = mapContract(connection, resultSet);
                contract.setAttachments(findAttachments(connection, id));
                return Optional.of(contract);
            }
        }
    }

    private ContractProcess saveProcess(Connection connection, ContractProcess process) throws SQLException {
        Integer id = parseInt(process.getId());
        if (id != null && processExists(connection, id)) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    update contract_process
                    set conNum = ?, type = ?, state = ?, userName = ?, content = ?, time = ?, createdAt = ?
                    where id = ?
                    """)) {
                fillProcessStatement(statement, process);
                statement.setInt(8, id);
                statement.executeUpdate();
            }
            return process;
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                insert into contract_process (conNum, type, state, userName, content, time, createdAt)
                values (?, ?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            fillProcessStatement(statement, process);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    process.setId(String.valueOf(keys.getInt(1)));
                }
            }
        }
        return process;
    }

    private void fillProcessStatement(PreparedStatement statement, ContractProcess process) throws SQLException {
        statement.setString(1, process.getContractId());
        statement.setInt(2, toDbProcessType(process.getType()));
        statement.setInt(3, toDbProcessState(process.getState()));
        statement.setString(4, process.getUserId());
        statement.setString(5, process.getContent());
        statement.setTimestamp(6, process.getTime() == null ? null : Timestamp.valueOf(process.getTime()));
        statement.setTimestamp(7, Timestamp.valueOf(process.getCreatedAt() == null ? LocalDateTime.now() : process.getCreatedAt()));
    }

    private Contract mapContract(Connection connection, ResultSet resultSet) throws SQLException {
        Contract contract = new Contract();
        String num = resultSet.getString("num");
        contract.setId(num);
        contract.setNum(num);
        contract.setName(resultSet.getString("name"));
        contract.setCustomerId(resultSet.getString("customer"));
        contract.setBeginTime(resultSet.getDate("beginTime").toLocalDate());
        contract.setEndTime(resultSet.getDate("endTime").toLocalDate());
        contract.setContent(resultSet.getString("content"));
        contract.setDrafterId(resultSet.getString("userName"));
        contract.setCreatedAt(createdAt(connection, num));
        contract.setStatus(resolveStatus(connection, num));
        contract.setAiReview(resultSet.getString("aiReview"));
        return contract;
    }

    private ContractProcess mapProcess(ResultSet resultSet) throws SQLException {
        ContractProcess process = new ContractProcess();
        process.setId(String.valueOf(resultSet.getInt("id")));
        process.setContractId(resultSet.getString("conNum"));
        process.setType(fromDbProcessType(resultSet.getInt("type")));
        process.setState(fromDbProcessState(resultSet.getInt("state")));
        process.setUserId(resultSet.getString("userName"));
        process.setContent(resultSet.getString("content"));
        Timestamp createdAt = resultSet.getTimestamp("createdAt");
        process.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
        Timestamp time = resultSet.getTimestamp("time");
        process.setTime(time == null ? null : time.toLocalDateTime());
        return process;
    }

    private List<ContractProcess> findProcessesByContractId(Connection connection, String contractId) throws SQLException {
        List<ContractProcess> processes = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "select id, conNum, type, state, userName, content, time, createdAt from contract_process where conNum = ? " +
                "order by case type when 1 then 1 when 4 then 2 when 2 then 3 when 3 then 4 end, id")) {
            statement.setString(1, contractId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    processes.add(mapProcess(resultSet));
                }
            }
        }
        return processes;
    }

    private List<Attachment> findAttachments(Connection connection, String contractId) throws SQLException {
        List<Attachment> attachments = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "select fileName, path, type, uploadTime from contract_attachment where conNum = ? order by id")) {
            statement.setString(1, contractId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Timestamp uploadTime = resultSet.getTimestamp("uploadTime");
                    attachments.add(new Attachment(
                            resultSet.getString("fileName"),
                            resultSet.getString("type"),
                            resultSet.getString("path"),
                            uploadTime == null ? null : uploadTime.toLocalDateTime()));
                }
            }
        }
        return attachments;
    }

    private void saveAttachments(Connection connection, Contract contract) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("delete from contract_attachment where conNum = ?")) {
            delete.setString(1, contract.getNum());
            delete.executeUpdate();
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                insert into contract_attachment (conNum, fileName, path, type, uploadTime)
                values (?, ?, ?, ?, ?)
                """)) {
            for (Attachment attachment : contract.getAttachments()) {
                insert.setString(1, contract.getNum());
                insert.setString(2, attachment.getName());
                insert.setString(3, attachment.getPath());
                insert.setString(4, attachment.getType());
                insert.setTimestamp(5, Timestamp.valueOf(attachment.getUploadTime() == null ? LocalDateTime.now() : attachment.getUploadTime()));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void saveVersionAttachments(Connection connection, long versionId, List<Attachment> attachments) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                insert into contract_version_attachment (versionId, fileName, path, type, uploadTime)
                values (?, ?, ?, ?, ?)
                """)) {
            for (Attachment attachment : attachments) {
                insert.setLong(1, versionId);
                insert.setString(2, attachment.getName());
                insert.setString(3, attachment.getPath());
                insert.setString(4, attachment.getType());
                insert.setTimestamp(5, attachment.getUploadTime() == null ? null : Timestamp.valueOf(attachment.getUploadTime()));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private int nextVersionNo(Connection connection, String contractId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select coalesce(max(versionNo), 0) + 1 from contract_version where conNum = ?")) {
            statement.setString(1, contractId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private ContractVersion findVersionById(Connection connection, long versionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select id, conNum, versionNo, num, name, customer, beginTime, endTime, content, userName,
                       approverName, approvalResult, approvalOpinion, createdAt
                from contract_version
                where id = ?
                """)) {
            statement.setLong(1, versionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return mapVersion(connection, resultSet);
            }
        }
    }

    private ContractVersion mapVersion(Connection connection, ResultSet resultSet) throws SQLException {
        ContractVersion version = new ContractVersion();
        long id = resultSet.getLong("id");
        version.setId(String.valueOf(id));
        version.setContractId(resultSet.getString("conNum"));
        version.setVersionNo(resultSet.getInt("versionNo"));
        version.setNum(resultSet.getString("num"));
        version.setName(resultSet.getString("name"));
        version.setCustomerId(resultSet.getString("customer"));
        version.setBeginTime(resultSet.getDate("beginTime").toLocalDate());
        version.setEndTime(resultSet.getDate("endTime").toLocalDate());
        version.setContent(resultSet.getString("content"));
        version.setDrafterId(resultSet.getString("userName"));
        version.setApproverId(resultSet.getString("approverName"));
        version.setApprovalResult(resultSet.getString("approvalResult"));
        version.setApprovalOpinion(resultSet.getString("approvalOpinion"));
        version.setCreatedAt(resultSet.getTimestamp("createdAt").toLocalDateTime());
        version.setAttachments(findVersionAttachments(connection, id));
        return version;
    }

    private List<Attachment> findVersionAttachments(Connection connection, long versionId) throws SQLException {
        List<Attachment> attachments = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "select fileName, path, type, uploadTime from contract_version_attachment where versionId = ? order by id")) {
            statement.setLong(1, versionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Timestamp uploadTime = resultSet.getTimestamp("uploadTime");
                    attachments.add(new Attachment(
                            resultSet.getString("fileName"),
                            resultSet.getString("type"),
                            resultSet.getString("path"),
                            uploadTime == null ? null : uploadTime.toLocalDateTime()));
                }
            }
        }
        return attachments;
    }

    private void saveStatus(Connection connection, Contract contract) throws SQLException {
        ensureState(connection, contract.getNum(), 1);
        switch (contract.getStatus()) {
            case FINALIZING -> ensureState(connection, contract.getNum(), 2);
            case APPROVING -> {
                ensureState(connection, contract.getNum(), 2);
                ensureState(connection, contract.getNum(), 3);
            }
            case SIGNING -> {
                ensureState(connection, contract.getNum(), 2);
                ensureState(connection, contract.getNum(), 3);
                ensureState(connection, contract.getNum(), 4);
            }
            case COMPLETED -> {
                ensureState(connection, contract.getNum(), 2);
                ensureState(connection, contract.getNum(), 3);
                ensureState(connection, contract.getNum(), 4);
                ensureState(connection, contract.getNum(), 5);
            }
            default -> {
            }
        }
    }

    private void ensureState(Connection connection, String contractId, int type) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into contract_state (conNum, type, time)
                values (?, ?, ?)
                on duplicate key update time = values(time)
                """)) {
            statement.setString(1, contractId);
            statement.setInt(2, type);
            statement.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            statement.executeUpdate();
        }
    }

    private ContractStatus resolveStatus(Connection connection, String contractId) throws SQLException {
        if (hasState(connection, contractId, 5)) return ContractStatus.COMPLETED;
        if (hasState(connection, contractId, 4)) return ContractStatus.SIGNING;
        if (hasState(connection, contractId, 3)) return ContractStatus.APPROVING;
        if (hasState(connection, contractId, 2)) return ContractStatus.FINALIZING;
        if (hasAnyProcess(connection, contractId, ProcessType.COUNTERSIGN)) return ContractStatus.COUNTERSIGNING;
        return ContractStatus.DRAFTING;
    }

    private boolean hasRejectedApproval(Connection connection, String contractId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select 1 from contract_process where conNum = ? and type = ? and state = ? limit 1")) {
            statement.setString(1, contractId);
            statement.setInt(2, toDbProcessType(ProcessType.APPROVE));
            statement.setInt(3, toDbProcessState(ProcessState.REJECTED));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean hasState(Connection connection, String contractId, int type) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select 1 from contract_state where conNum = ? and type = ? limit 1")) {
            statement.setString(1, contractId);
            statement.setInt(2, type);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean hasAnyProcess(Connection connection, String contractId, ProcessType type) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select 1 from contract_process where conNum = ? and type = ? limit 1")) {
            statement.setString(1, contractId);
            statement.setInt(2, toDbProcessType(type));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private LocalDateTime createdAt(Connection connection, String contractId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select time from contract_state where conNum = ? and type = 1")) {
            statement.setString(1, contractId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next() && resultSet.getTimestamp("time") != null) {
                    return resultSet.getTimestamp("time").toLocalDateTime();
                }
            }
        }
        return LocalDateTime.now();
    }

    private boolean processExists(Connection connection, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select 1 from contract_process where id = ?")) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private int toDbProcessType(ProcessType type) {
        return switch (type) {
            case COUNTERSIGN -> 1;
            case APPROVE -> 2;
            case SIGN -> 3;
            case FINALIZE -> 4;
        };
    }

    private ProcessType fromDbProcessType(int type) {
        return switch (type) {
            case 1 -> ProcessType.COUNTERSIGN;
            case 2 -> ProcessType.APPROVE;
            case 3 -> ProcessType.SIGN;
            case 4 -> ProcessType.FINALIZE;
            default -> throw new IllegalArgumentException("Unknown process type: " + type);
        };
    }

    private int toDbProcessState(ProcessState state) {
        return switch (state) {
            case PENDING -> 0;
            case DONE -> 1;
            case REJECTED -> 2;
        };
    }

    private ProcessState fromDbProcessState(int state) {
        return switch (state) {
            case 0 -> ProcessState.PENDING;
            case 1 -> ProcessState.DONE;
            case 2 -> ProcessState.REJECTED;
            default -> throw new IllegalArgumentException("Unknown process state: " + state);
        };
    }

    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private int maxContractNumber(Connection connection) throws SQLException {
        List<Integer> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("select num from contract");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String num = resultSet.getString("num");
                int dash = num == null ? -1 : num.lastIndexOf('-');
                if (dash >= 0 && dash + 1 < num.length()) {
                    try {
                        values.add(Integer.parseInt(num.substring(dash + 1)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return values.stream().max(Comparator.naturalOrder()).orElse(0);
    }

    private void ensureContractAiReviewColumn(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("alter table contract add column aiReview longtext null");
        } catch (SQLException exception) {
            if (!isDuplicateColumn(exception)) {
                throw exception;
            }
        }
    }

    private void ensureContractProcessCreatedAtColumn(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("alter table contract_process add column createdAt datetime null");
        } catch (SQLException exception) {
            if (!isDuplicateColumn(exception)) {
                throw exception;
            }
        }
    }

    private void ensureContractProcessSignatureColumns(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "alter table contract_process add column signerName varchar(100) null");
        addColumnIfMissing(connection, "alter table contract_process add column signatureDataUrl longtext null");
    }

    private void addColumnIfMissing(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            if (!isDuplicateColumn(exception)) {
                throw exception;
            }
        }
    }

    private boolean isDuplicateColumn(SQLException exception) {
        return exception.getErrorCode() == 1060;
    }

    private int maxIntId(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select coalesce(max(id), 0) from " + tableName)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private void repairMissingFinalizeProcesses(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    insert into contract_process (conNum, type, state, userName, content, time, createdAt)
                    select c.num,
                           4,
                           case when exists (
                             select 1 from contract_state s_done
                             where s_done.conNum = c.num and s_done.type >= 3
                           ) then 1 else 0 end,
                           c.userName,
                           case when exists (
                             select 1 from contract_state s_done
                             where s_done.conNum = c.num and s_done.type >= 3
                           ) then '定稿完成' else null end,
                           case when exists (
                             select 1 from contract_state s_done
                             where s_done.conNum = c.num and s_done.type >= 3
                           ) then now() else null end,
                           now()
                    from contract c
                    where exists (
                      select 1 from contract_state s
                      where s.conNum = c.num and s.type >= 2
                    )
                    and not exists (
                      select 1 from contract_process p
                      where p.conNum = c.num and p.type = 4
                    )
                    """);
        }
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
