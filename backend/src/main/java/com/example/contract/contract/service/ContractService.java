package com.example.contract.contract.service;

import com.example.contract.contract.dto.ApproveContractRequest;
import com.example.contract.contract.dto.AssignContractRequest;
import com.example.contract.contract.dto.CountersignRequest;
import com.example.contract.contract.dto.DraftContractRequest;
import com.example.contract.contract.dto.FinalizeContractRequest;
import com.example.contract.contract.dto.PageResponse;
import com.example.contract.contract.dto.SignContractRequest;
import com.example.contract.contract.dto.UpdateContractRequest;
import com.example.contract.contract.model.Contract;
import com.example.contract.contract.model.ContractProcess;
import com.example.contract.contract.model.ContractVersion;
import com.example.contract.contract.model.SignRecord;
import java.time.LocalDate;
import java.util.List;

public interface ContractService {

    PageResponse<Contract> list(String keyword, String status, LocalDate beginDate, LocalDate endDate, int page, int pageSize);

    Contract detail(String id);

    Contract draft(DraftContractRequest request);

    Contract update(String id, UpdateContractRequest request);

    void delete(String id);

    Contract assign(String contractId, AssignContractRequest request);

    Contract retract(String id, String userId);

    Contract countersign(String contractId, CountersignRequest request);

    Contract finalizeContract(String contractId, FinalizeContractRequest request);

    Contract approve(String contractId, ApproveContractRequest request);

    Contract sign(String contractId, SignContractRequest request);

    List<ContractProcess> processes(String contractId);

    List<SignRecord> signRecords(String contractId);

    List<ContractVersion> versions(String contractId);
}

