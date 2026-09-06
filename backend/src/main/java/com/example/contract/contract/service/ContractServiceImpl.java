package com.example.contract.contract.service;

import com.example.contract.common.BusinessException;
import com.example.contract.contract.dto.ApproveContractRequest;
import com.example.contract.contract.dto.AssignContractRequest;
import com.example.contract.contract.dto.AttachmentRequest;
import com.example.contract.contract.dto.CountersignRequest;
import com.example.contract.contract.dto.DraftContractRequest;
import com.example.contract.contract.dto.FinalizeContractRequest;
import com.example.contract.contract.dto.PageResponse;
import com.example.contract.contract.dto.SignContractRequest;
import com.example.contract.contract.dto.UpdateContractRequest;
import com.example.contract.contract.model.Attachment;
import com.example.contract.contract.model.Contract;
import com.example.contract.contract.model.ContractProcess;
import com.example.contract.contract.model.ContractStatus;
import com.example.contract.contract.model.ContractVersion;
import com.example.contract.contract.model.ProcessState;
import com.example.contract.contract.model.ProcessType;
import com.example.contract.contract.model.SignRecord;
import com.example.contract.contract.repository.ContractRepository;
import com.example.contract.log.service.LogService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ContractServiceImpl implements ContractService {

    private static final Logger LOG = LoggerFactory.getLogger(ContractServiceImpl.class);
    private static final Set<String> ALLOWED_ATTACHMENT_TYPES = Set.of("doc", "docx", "pdf", "txt", "jpg", "jpeg", "png", "bmp", "gif");
    private static final Set<String> REVIEW_ATTACHMENT_TYPES = Set.of("docx", "pdf");

    private final ContractRepository repository;
    private final LogService logService;
    private final BailianContractReviewService bailianContractReviewService;

    public ContractServiceImpl(ContractRepository repository,
                               LogService logService,
                               BailianContractReviewService bailianContractReviewService) {
        this.repository = repository;
        this.logService = logService;
        this.bailianContractReviewService = bailianContractReviewService;
    }

    @Override
    public PageResponse<Contract> list(String keyword, String status, LocalDate beginDate, LocalDate endDate, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        ContractStatus targetStatus = parseStatus(status);

        List<Contract> filtered = repository.findAllContracts()
                .stream()
                .filter(contract -> normalizedKeyword.isBlank()
                        || contract.getName().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                        || contract.getNum().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .filter(contract -> targetStatus == null || contract.getStatus() == targetStatus)
                .filter(contract -> beginDate == null || !contract.getCreatedAt().toLocalDate().isBefore(beginDate))
                .filter(contract -> endDate == null || !contract.getCreatedAt().toLocalDate().isAfter(endDate))
                .toList();

        int fromIndex = Math.min((safePage - 1) * safePageSize, filtered.size());
        int toIndex = Math.min(fromIndex + safePageSize, filtered.size());
        return new PageResponse<>(filtered.subList(fromIndex, toIndex), filtered.size(), safePage, safePageSize);
    }

    @Override
    public Contract detail(String id) {
        return findContract(id);
    }

    @Override
    public Contract draft(DraftContractRequest request) {
        if (request.getEndTime().isBefore(request.getBeginTime())) {
            throw new BusinessException("结束时间不能早于开始时间");
        }
        requireReviewAttachment(request.getAttachments());

        Contract contract = new Contract();
        contract.setId(repository.nextContractId());
        contract.setNum(repository.nextContractNum());
        contract.setName(request.getName());
        contract.setCustomerId(request.getCustomerId());
        contract.setBeginTime(request.getBeginTime());
        contract.setEndTime(request.getEndTime());
        contract.setContent(request.getContent());
        contract.setDrafterId(request.getDrafterId());
        contract.setCreatedAt(LocalDateTime.now());
        contract.setStatus(ContractStatus.DRAFTING);
        contract.setAttachments(toAttachments(request.getAttachments()));

        Contract saved = repository.saveContract(contract);
        reviewDraftContractAsync(saved);
        logService.record(request.getDrafterId(), "起草合同：" + saved.getName());
        return saved;
    }

    @Override
    public Contract update(String id, UpdateContractRequest request) {
        Contract contract = findContract(id);
        if (request.getEndTime().isBefore(request.getBeginTime())) {
            throw new BusinessException("结束时间不能早于开始时间");
        }

        contract.setName(request.getName());
        contract.setCustomerId(request.getCustomerId());
        contract.setBeginTime(request.getBeginTime());
        contract.setEndTime(request.getEndTime());
        contract.setContent(request.getContent());
        if (request.getAttachments() != null) {
            contract.setAttachments(toAttachments(request.getAttachments()));
        }

        Contract saved = repository.saveContract(contract);
        logService.record("admin", "修改合同：" + saved.getName());
        return saved;
    }

    @Override
    public void delete(String id) {
        findContract(id);
        repository.deleteContractById(id);
        logService.record("admin", "删除合同：" + id);
    }

    @Override
    public Contract assign(String contractId, AssignContractRequest request) {
        Contract contract = findContract(contractId);
        requireStatus(contract, ContractStatus.DRAFTING, "只有起草状态的合同可以分配人员");

        repository.deleteProcessesByContractId(contractId);
        List<ContractProcess> processes = new ArrayList<>();
        request.getCountersignUserIds().forEach(userId -> processes.add(newPendingProcess(contractId, ProcessType.COUNTERSIGN, userId)));
        processes.add(newPendingProcess(contractId, ProcessType.FINALIZE, contract.getDrafterId()));
        request.getApproveUserIds().forEach(userId -> processes.add(newPendingProcess(contractId, ProcessType.APPROVE, userId)));
        request.getSignUserIds().forEach(userId -> processes.add(newPendingProcess(contractId, ProcessType.SIGN, userId)));
        repository.saveProcesses(processes);
        ensureAssignedProcessesSaved(contractId);

        contract.setStatus(ContractStatus.COUNTERSIGNING);
        Contract saved = repository.saveContract(contract);
        logService.record("admin", "分配合同流程：" + saved.getName());
        return saved;
    }

    @Override
    public Contract retract(String id, String userId) {
        Contract contract = findContract(id);
        requireStatus(contract, ContractStatus.DRAFTING, "只有起草状态的合同可以撤回");

        if (!contract.getDrafterId().equals(userId)) {
            throw new BusinessException("只有合同起草人可以撤回");
        }

        logService.record(userId, "撤回起草合同：" + contract.getName());
        return contract;
    }

    @Override
    public Contract countersign(String contractId, CountersignRequest request) {
        Contract contract = findContract(contractId);
        requireStatus(contract, ContractStatus.COUNTERSIGNING, "当前合同不处于会签阶段");

        ContractProcess process = findPendingProcess(contractId, ProcessType.COUNTERSIGN, request.getUserId());
        process.setState(ProcessState.DONE);
        process.setContent(request.getContent());
        process.setTime(LocalDateTime.now());
        repository.saveProcess(process);

        if (allProcessesDone(contractId, ProcessType.COUNTERSIGN)) {
            contract.setStatus(ContractStatus.FINALIZING);
            repository.saveContract(contract);
        }

        logService.record(request.getUserId(), "提交会签意见：" + contract.getName());
        return findContract(contractId);
    }

    @Override
    public Contract finalizeContract(String contractId, FinalizeContractRequest request) {
        Contract contract = findContract(contractId);
        requireStatus(contract, ContractStatus.FINALIZING, "当前合同不处于定稿阶段");
        if (!contract.getDrafterId().equals(request.getUserId())) {
            throw new BusinessException("只有合同起草人可以定稿");
        }

        ContractProcess process = findPendingProcess(contractId, ProcessType.FINALIZE, request.getUserId());
        process.setState(ProcessState.DONE);
        process.setTime(LocalDateTime.now());
        repository.saveProcess(process);

        contract.setContent(request.getContent());
        if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
            contract.setAttachments(toAttachments(request.getAttachments()));
        }
        contract.setStatus(ContractStatus.APPROVING);
        Contract saved = repository.saveContract(contract);
        logService.record(request.getUserId(), "定稿合同：" + saved.getName());
        return saved;
    }

    @Override
    public Contract approve(String contractId, ApproveContractRequest request) {
        Contract contract = findContract(contractId);
        requireStatus(contract, ContractStatus.APPROVING, "当前合同不处于审批阶段");

        ContractProcess process = findPendingProcess(contractId, ProcessType.APPROVE, request.getUserId());
        boolean rejected = "rejected".equals(request.getResult());
        process.setState(rejected ? ProcessState.REJECTED : ProcessState.DONE);
        process.setContent(request.getContent());
        process.setTime(LocalDateTime.now());
        repository.saveProcess(process);
        repository.saveContractVersion(contract, request.getUserId(), request.getResult(), request.getContent());

        if (rejected) {
            List<String> approveUserIds = repository.findProcessesByContractId(contractId)
                    .stream()
                    .filter(item -> item.getType() == ProcessType.APPROVE)
                    .map(ContractProcess::getUserId)
                    .distinct()
                    .toList();
            repository.returnToFinalize(contractId, contract.getDrafterId(), approveUserIds);
            contract.setStatus(ContractStatus.FINALIZING);
        } else if (allProcessesDone(contractId, ProcessType.APPROVE)) {
            contract.setStatus(ContractStatus.SIGNING);
        }
        Contract saved = repository.saveContract(contract);
        logService.record(request.getUserId(), "提交审批意见：" + saved.getName());
        return saved;
    }

    @Override
    public Contract sign(String contractId, SignContractRequest request) {
        Contract contract = findContract(contractId);
        requireStatus(contract, ContractStatus.SIGNING, "当前合同不处于签订阶段");

        ContractProcess process = findPendingProcess(contractId, ProcessType.SIGN, request.getUserId());
        process.setState(ProcessState.DONE);
        process.setContent(request.getMethod() + "；" + (request.getRemark() == null || request.getRemark().isBlank() ? "无备注" : request.getRemark()));
        process.setTime(LocalDateTime.now());
        process.setContent(buildSignProcessContent(request));
        repository.saveProcess(process);

        SignRecord signRecord = new SignRecord();
        signRecord.setId(repository.nextSignRecordId());
        signRecord.setContractId(contractId);
        signRecord.setSignDate(request.getSignDate());
        signRecord.setMethod(request.getMethod());
        signRecord.setRemark(request.getRemark());
        signRecord.setSignerName(request.getSignerName());
        signRecord.setSignatureDataUrl(request.getSignatureDataUrl());
        repository.saveSignRecord(signRecord);

        if (allProcessesDone(contractId, ProcessType.SIGN)) {
            contract.setStatus(ContractStatus.COMPLETED);
            repository.saveContract(contract);
        }

        logService.record(request.getUserId(), "录入签订信息：" + contract.getName());
        return findContract(contractId);
    }

    private String buildSignProcessContent(SignContractRequest request) {
        String remark = request.getRemark() == null || request.getRemark().isBlank() ? "none" : request.getRemark().trim();
        String signerName = request.getSignerName() == null || request.getSignerName().isBlank() ? request.getUserId() : request.getSignerName().trim();
        String signatureState = request.getSignatureDataUrl() == null || request.getSignatureDataUrl().isBlank() ? "signature=missing" : "signature=submitted";
        return "method=%s; signer=%s; %s; remark=%s".formatted(request.getMethod(), signerName, signatureState, remark);
    }

    @Override
    public List<ContractProcess> processes(String contractId) {
        findContract(contractId);
        return repository.findProcessesByContractId(contractId);
    }

    @Override
    public List<SignRecord> signRecords(String contractId) {
        findContract(contractId);
        return repository.findSignRecordsByContractId(contractId);
    }

    @Override
    public List<ContractVersion> versions(String contractId) {
        findContract(contractId);
        return repository.findVersionsByContractId(contractId);
    }

    private Contract findContract(String id) {
        return repository.findContractById(id)
                .orElseThrow(() -> new BusinessException(404, "合同不存在"));
    }

    private ContractStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ContractStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("合同状态不正确");
        }
    }

    private void requireStatus(Contract contract, ContractStatus status, String message) {
        if (contract.getStatus() != status) {
            throw new BusinessException(message);
        }
    }

    private ContractProcess newPendingProcess(String contractId, ProcessType type, String userId) {
        ContractProcess process = new ContractProcess();
        process.setId(repository.nextProcessId());
        process.setContractId(contractId);
        process.setType(type);
        process.setUserId(userId);
        process.setState(ProcessState.PENDING);
        process.setCreatedAt(LocalDateTime.now());
        return process;
    }

    private ContractProcess findPendingProcess(String contractId, ProcessType type, String userId) {
        return repository.findProcessesByContractId(contractId)
                .stream()
                .filter(process -> process.getType() == type)
                .filter(process -> process.getUserId().equals(userId))
                .filter(process -> process.getState() == ProcessState.PENDING)
                .findFirst()
                .orElseThrow(() -> new BusinessException("当前用户没有该合同的待办流程"));
    }

    private void ensureAssignedProcessesSaved(String contractId) {
        List<ContractProcess> saved = repository.findProcessesByContractId(contractId);
        boolean hasCountersign = saved.stream().anyMatch(process -> process.getType() == ProcessType.COUNTERSIGN);
        boolean hasFinalize = saved.stream().anyMatch(process -> process.getType() == ProcessType.FINALIZE);
        boolean hasApprove = saved.stream().anyMatch(process -> process.getType() == ProcessType.APPROVE);
        boolean hasSign = saved.stream().anyMatch(process -> process.getType() == ProcessType.SIGN);
        if (!hasCountersign || !hasFinalize || !hasApprove || !hasSign) {
            throw new BusinessException("合同流程分配不完整，请重新选择会签、审批和签订人员");
        }
    }

    private boolean allProcessesDone(String contractId, ProcessType type) {
        List<ContractProcess> processes = repository.findProcessesByContractId(contractId)
                .stream()
                .filter(process -> process.getType() == type)
                .filter(process -> process.getState() != ProcessState.REJECTED)
                .toList();
        return !processes.isEmpty() && processes.stream().allMatch(process -> process.getState() == ProcessState.DONE);
    }

    private void reviewDraftContractAsync(Contract contract) {
        LOG.info("[AI审查] 已提交异步审查任务：contractId={}，contractNum={}，contractName={}",
                contract.getId(), contract.getNum(), contract.getName());
        CompletableFuture.runAsync(() -> {
            LOG.info("[AI审查] 异步审查任务开始：contractId={}，contractNum={}", contract.getId(), contract.getNum());
            try {
                bailianContractReviewService.review(contract).ifPresentOrElse(reviewJson -> {
                    Contract latest = repository.findContractById(contract.getId()).orElse(contract);
                    latest.setAiReview(reviewJson);
                    repository.saveContract(latest);
                    LOG.info("[AI审查] 审查结果已保存：contractId={}，contractNum={}，jsonLength={}",
                            contract.getId(), contract.getNum(), reviewJson.length());
                }, () -> LOG.warn("[AI审查] 审查结果未生成：contractId={}，contractNum={}，请查看前面的百炼配置/文字提取/调用日志",
                        contract.getId(), contract.getNum()));
            } catch (RuntimeException exception) {
                LOG.error("[AI审查] 异步审查任务异常结束：contractId={}，contractNum={}，reason={}",
                        contract.getId(), contract.getNum(), exception.toString(), exception);
            }
            LOG.info("[AI审查] 异步审查任务结束：contractId={}，contractNum={}", contract.getId(), contract.getNum());
        }).exceptionally(exception -> {
            LOG.error("[AI审查] 异步审查任务提交后异常：contractId={}，contractNum={}，reason={}",
                    contract.getId(), contract.getNum(), exception.toString(), exception);
            return null;
        });
    }

    private List<Attachment> toAttachments(List<AttachmentRequest> requests) {
        return requests.stream().map(request -> {
            String type = request.getType().toLowerCase(Locale.ROOT);
            if (!ALLOWED_ATTACHMENT_TYPES.contains(type)) {
                throw new BusinessException("附件仅支持 doc、docx、pdf、txt、jpg、jpeg、png、bmp 或 gif 格式");
            }
            return new Attachment(request.getName(), type, request.getPath(), LocalDateTime.now());
        }).toList();
    }

    private void requireReviewAttachment(List<AttachmentRequest> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            throw new BusinessException("请上传合同文件用于AI审查");
        }
        boolean hasReviewableFile = attachments.stream()
                .map(AttachmentRequest::getType)
                .filter(type -> type != null)
                .map(type -> type.toLowerCase(Locale.ROOT))
                .anyMatch(REVIEW_ATTACHMENT_TYPES::contains);
        if (!hasReviewableFile) {
            String supported = REVIEW_ATTACHMENT_TYPES.stream()
                    .sorted(Comparator.naturalOrder())
                    .toList()
                    .toString();
            throw new BusinessException("请上传可审查的合同文件，支持格式：" + supported);
        }
    }
}

