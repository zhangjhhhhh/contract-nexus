package com.example.contract.contract.repository;

import com.example.contract.contract.model.Contract;
import com.example.contract.contract.model.ContractProcess;
import com.example.contract.contract.model.ContractVersion;
import com.example.contract.contract.model.SignRecord;
import java.util.List;
import java.util.Optional;

public interface ContractRepository {

    List<Contract> findAllContracts();

    Optional<Contract> findContractById(String id);

    Contract saveContract(Contract contract);

    void deleteProcessesByContractId(String contractId);

    List<ContractProcess> findProcessesByContractId(String contractId);

    List<ContractProcess> findAllProcesses();

    ContractProcess saveProcess(ContractProcess process);

    List<ContractProcess> saveProcesses(List<ContractProcess> processes);

    void returnToFinalize(String contractId, String drafterId, List<String> approveUserIds);

    ContractVersion saveContractVersion(Contract contract, String approverId, String approvalResult, String approvalOpinion);

    List<ContractVersion> findVersionsByContractId(String contractId);

    SignRecord saveSignRecord(SignRecord signRecord);

    List<SignRecord> findSignRecordsByContractId(String contractId);

    void deleteContractById(String id);

    String nextContractId();

    String nextContractNum();

    String nextProcessId();

    String nextSignRecordId();
}

