package com.example.contract.contract.repository;

import com.example.contract.contract.model.Attachment;
import com.example.contract.contract.model.Contract;
import com.example.contract.contract.model.ContractProcess;
import com.example.contract.contract.model.ContractStatus;
import com.example.contract.contract.model.ContractVersion;
import com.example.contract.contract.model.ProcessState;
import com.example.contract.contract.model.ProcessType;
import com.example.contract.contract.model.SignRecord;
import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("memory")
public class InMemoryContractRepository implements ContractRepository {

    private final Map<String, Contract> contracts = new LinkedHashMap<>();
    private final Map<String, ContractProcess> processes = new LinkedHashMap<>();
    private final Map<String, ContractVersion> versions = new LinkedHashMap<>();
    private final Map<String, SignRecord> signRecords = new LinkedHashMap<>();
    private final AtomicInteger contractSequence = new AtomicInteger();
    private final AtomicInteger processSequence = new AtomicInteger();
    private final AtomicInteger signSequence = new AtomicInteger();

    @PostConstruct
    public void init() {
        seedContracts();
        seedProcesses();
        seedSignRecords();
    }

    @Override
    public synchronized List<Contract> findAllContracts() {
        return contracts.values()
                .stream()
                .sorted(Comparator.comparing(Contract::getCreatedAt).reversed())
                .map(this::copyContract)
                .toList();
    }

    @Override
    public synchronized Optional<Contract> findContractById(String id) {
        return Optional.ofNullable(contracts.get(id)).map(this::copyContract);
    }

    @Override
    public synchronized Contract saveContract(Contract contract) {
        contracts.put(contract.getId(), copyContract(contract));
        return copyContract(contract);
    }

    @Override
    public synchronized void deleteProcessesByContractId(String contractId) {
        processes.values().removeIf(process -> process.getContractId().equals(contractId));
    }

    @Override
    public synchronized void deleteContractById(String id) {
        contracts.remove(id);
        processes.values().removeIf(process -> process.getContractId().equals(id));
        signRecords.values().removeIf(record -> record.getContractId().equals(id));
    }

    @Override
    public synchronized List<ContractProcess> findProcessesByContractId(String contractId) {
        return processes.values()
                .stream()
                .filter(process -> process.getContractId().equals(contractId))
                .map(this::copyProcess)
                .toList();
    }

    @Override
    public synchronized List<ContractProcess> findAllProcesses() {
        return processes.values().stream().map(this::copyProcess).toList();
    }

    @Override
    public synchronized ContractProcess saveProcess(ContractProcess process) {
        processes.put(process.getId(), copyProcess(process));
        return copyProcess(process);
    }

    @Override
    public synchronized List<ContractProcess> saveProcesses(List<ContractProcess> items) {
        items.forEach(this::saveProcess);
        return items.stream().map(this::copyProcess).toList();
    }

    @Override
    public synchronized void returnToFinalize(String contractId, String drafterId, List<String> approveUserIds) {
        processes.values().stream()
                .filter(process -> process.getContractId().equals(contractId))
                .filter(process -> process.getType() == ProcessType.FINALIZE)
                .forEach(process -> {
                    process.setState(ProcessState.PENDING);
                    process.setContent(null);
                    process.setCreatedAt(LocalDateTime.now());
                    process.setTime(null);
                });

        boolean hasFinalize = processes.values().stream()
                .anyMatch(process -> process.getContractId().equals(contractId) && process.getType() == ProcessType.FINALIZE);
        if (!hasFinalize) {
            ContractProcess process = new ContractProcess();
            process.setId(nextProcessId());
            process.setContractId(contractId);
            process.setType(ProcessType.FINALIZE);
            process.setUserId(drafterId);
            process.setState(ProcessState.PENDING);
            process.setCreatedAt(LocalDateTime.now());
            processes.put(process.getId(), process);
        }

        for (String approveUserId : approveUserIds) {
            ContractProcess process = new ContractProcess();
            process.setId(nextProcessId());
            process.setContractId(contractId);
            process.setType(ProcessType.APPROVE);
            process.setUserId(approveUserId);
            process.setState(ProcessState.PENDING);
            process.setCreatedAt(LocalDateTime.now());
            processes.put(process.getId(), process);
        }
    }

    @Override
    public synchronized ContractVersion saveContractVersion(Contract contract, String approverId, String approvalResult, String approvalOpinion) {
        ContractVersion version = new ContractVersion();
        version.setId("V" + String.format("%03d", versions.size() + 1));
        version.setContractId(contract.getId());
        version.setVersionNo((int) versions.values().stream().filter(item -> item.getContractId().equals(contract.getId())).count() + 1);
        version.setNum(contract.getNum());
        version.setName(contract.getName());
        version.setCustomerId(contract.getCustomerId());
        version.setBeginTime(contract.getBeginTime());
        version.setEndTime(contract.getEndTime());
        version.setContent(contract.getContent());
        version.setDrafterId(contract.getDrafterId());
        version.setApproverId(approverId);
        version.setApprovalResult(approvalResult);
        version.setApprovalOpinion(approvalOpinion);
        version.setCreatedAt(LocalDateTime.now());
        version.setAttachments(contract.getAttachments().stream()
                .map(item -> new Attachment(item.getName(), item.getType(), item.getPath(), item.getUploadTime()))
                .toList());
        versions.put(version.getId(), version);
        return version;
    }

    @Override
    public synchronized List<ContractVersion> findVersionsByContractId(String contractId) {
        return versions.values()
                .stream()
                .filter(version -> version.getContractId().equals(contractId))
                .sorted(Comparator.comparing(ContractVersion::getVersionNo).reversed())
                .toList();
    }

    @Override
    public synchronized SignRecord saveSignRecord(SignRecord signRecord) {
        signRecords.put(signRecord.getId(), copySignRecord(signRecord));
        return copySignRecord(signRecord);
    }

    @Override
    public synchronized List<SignRecord> findSignRecordsByContractId(String contractId) {
        return signRecords.values()
                .stream()
                .filter(record -> record.getContractId().equals(contractId))
                .map(this::copySignRecord)
                .toList();
    }

    @Override
    public String nextContractId() {
        return "K" + String.format("%03d", contractSequence.incrementAndGet());
    }

    @Override
    public String nextContractNum() {
        return "HT-" + LocalDate.now().getYear() + "-" + String.format("%03d", contractSequence.get());
    }

    @Override
    public String nextProcessId() {
        return "P" + String.format("%03d", processSequence.incrementAndGet());
    }

    @Override
    public String nextSignRecordId() {
        return "S" + String.format("%03d", signSequence.incrementAndGet());
    }

    private void seedContracts() {
        saveSeedContract("K001", "HT-2026-001", "年度设备维护合同", "C001", "U002",
                ContractStatus.DRAFTING, "2026-06-01", "2027-05-31",
                "约定生产设备巡检、故障响应、备件更换、季度复盘与验收标准。");
        contracts.get("K001").setAttachments(List.of(new Attachment("maintenance.doc", "doc", "/mock/maintenance.doc", LocalDateTime.now())));

        saveSeedContract("K002", "HT-2026-002", "物流服务框架合同", "C002", "U002",
                ContractStatus.COUNTERSIGNING, "2026-07-01", "2027-06-30",
                "明确仓配服务范围、运费结算、异常处理、赔付标准与对账周期。");
        saveSeedContract("K003", "HT-2026-003", "数据平台采购合同", "C003", "U003",
                ContractStatus.FINALIZING, "2026-06-15", "2027-06-14",
                "采购数据治理平台授权、部署服务、培训支持与售后保障。");
        saveSeedContract("K004", "HT-2026-004", "新能源站点运维合同", "C004", "U004",
                ContractStatus.APPROVING, "2026-06-20", "2028-06-19",
                "覆盖站点巡检、远程监控、故障升级、备品备件和服务报告。");
        saveSeedContract("K005", "HT-2026-005", "园区弱电施工合同", "C005", "U003",
                ContractStatus.SIGNING, "2026-06-10", "2026-09-30",
                "约定弱电施工范围、材料标准、里程碑验收与质保责任。");
        saveSeedContract("K006", "HT-2026-006", "办公软件订阅合同", "C003", "U002",
                ContractStatus.COMPLETED, "2026-05-15", "2027-05-14",
                "订阅办公协作套件账号、技术支持与年度续费条款。");
    }

    private void seedProcesses() {
        saveSeedProcess("P001", "K002", ProcessType.COUNTERSIGN, "U003", ProcessState.PENDING, null);
        saveSeedProcess("P002", "K002", ProcessType.COUNTERSIGN, "U004", ProcessState.PENDING, null);
        saveSeedProcess("P003", "K002", ProcessType.APPROVE, "U005", ProcessState.PENDING, null);
        saveSeedProcess("P004", "K002", ProcessType.SIGN, "U006", ProcessState.PENDING, null);
        saveSeedProcess("P005", "K003", ProcessType.COUNTERSIGN, "U002", ProcessState.DONE, "商务条款完整，建议补充交付验收表。");
        saveSeedProcess("P006", "K003", ProcessType.COUNTERSIGN, "U004", ProcessState.DONE, "技术服务范围清晰，可以定稿。");
        saveSeedProcess("P007", "K003", ProcessType.APPROVE, "U005", ProcessState.PENDING, null);
        saveSeedProcess("P008", "K003", ProcessType.SIGN, "U006", ProcessState.PENDING, null);
        saveSeedProcess("P009", "K004", ProcessType.COUNTERSIGN, "U002", ProcessState.DONE, "运维响应时限已明确。");
        saveSeedProcess("P010", "K004", ProcessType.APPROVE, "U005", ProcessState.PENDING, null);
        saveSeedProcess("P011", "K004", ProcessType.SIGN, "U006", ProcessState.PENDING, null);
        saveSeedProcess("P012", "K005", ProcessType.COUNTERSIGN, "U002", ProcessState.DONE, "施工材料规格已确认。");
        saveSeedProcess("P013", "K005", ProcessType.APPROVE, "U005", ProcessState.DONE, "同意签订，注意验收资料归档。");
        saveSeedProcess("P014", "K005", ProcessType.SIGN, "U006", ProcessState.PENDING, null);
        saveSeedProcess("P015", "K006", ProcessType.COUNTERSIGN, "U004", ProcessState.DONE, "订阅数量与周期一致。");
        saveSeedProcess("P016", "K006", ProcessType.APPROVE, "U005", ProcessState.DONE, "通过。");
        saveSeedProcess("P017", "K006", ProcessType.SIGN, "U006", ProcessState.DONE, "电子签订完成。");
    }

    private void seedSignRecords() {
        SignRecord record = new SignRecord();
        record.setId("S001");
        record.setContractId("K006");
        record.setSignDate(LocalDate.parse("2026-04-29"));
        record.setMethod("电子签章");
        record.setRemark("已归档电子合同。");
        saveSignRecord(record);
        signSequence.incrementAndGet();
    }

    private void saveSeedContract(String id, String num, String name, String customerId, String drafterId,
                                  ContractStatus status, String beginTime, String endTime, String content) {
        Contract contract = new Contract();
        contract.setId(id);
        contract.setNum(num);
        contract.setName(name);
        contract.setCustomerId(customerId);
        contract.setDrafterId(drafterId);
        contract.setStatus(status);
        contract.setBeginTime(LocalDate.parse(beginTime));
        contract.setEndTime(LocalDate.parse(endTime));
        contract.setCreatedAt(LocalDateTime.of(2026, 5, contractSequence.incrementAndGet(), 9, 0));
        contract.setContent(content);
        contracts.put(id, contract);
    }

    private void saveSeedProcess(String id, String contractId, ProcessType type, String userId,
                                 ProcessState state, String content) {
        ContractProcess process = new ContractProcess();
        process.setId(id);
        process.setContractId(contractId);
        process.setType(type);
        process.setUserId(userId);
        process.setState(state);
        process.setContent(content);
        process.setCreatedAt(LocalDateTime.of(2026, 5, processSequence.get() + 1, 9, 0));
        if (state != ProcessState.PENDING) {
            process.setTime(LocalDateTime.of(2026, 5, processSequence.get() + 1, 10, 0));
        }
        processes.put(id, process);
        processSequence.incrementAndGet();
    }

    private Contract copyContract(Contract source) {
        Contract target = new Contract();
        target.setId(source.getId());
        target.setNum(source.getNum());
        target.setName(source.getName());
        target.setCustomerId(source.getCustomerId());
        target.setBeginTime(source.getBeginTime());
        target.setEndTime(source.getEndTime());
        target.setContent(source.getContent());
        target.setDrafterId(source.getDrafterId());
        target.setCreatedAt(source.getCreatedAt());
        target.setStatus(source.getStatus());
        target.setAiReview(source.getAiReview());
        List<Attachment> attachments = source.getAttachments()
                .stream()
                .map(item -> new Attachment(item.getName(), item.getType(), item.getPath(), item.getUploadTime()))
                .toList();
        target.setAttachments(new ArrayList<>(attachments));
        return target;
    }

    private ContractProcess copyProcess(ContractProcess source) {
        ContractProcess target = new ContractProcess();
        target.setId(source.getId());
        target.setContractId(source.getContractId());
        target.setType(source.getType());
        target.setUserId(source.getUserId());
        target.setState(source.getState());
        target.setContent(source.getContent());
        target.setCreatedAt(source.getCreatedAt());
        target.setTime(source.getTime());
        return target;
    }

    private SignRecord copySignRecord(SignRecord source) {
        SignRecord target = new SignRecord();
        target.setId(source.getId());
        target.setContractId(source.getContractId());
        target.setSignDate(source.getSignDate());
        target.setMethod(source.getMethod());
        target.setRemark(source.getRemark());
        target.setSignerName(source.getSignerName());
        target.setSignatureDataUrl(source.getSignatureDataUrl());
        return target;
    }
}
