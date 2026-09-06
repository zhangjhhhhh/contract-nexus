package com.example.contract.contract.controller;

import com.example.contract.common.ApiResponse;
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
import com.example.contract.contract.service.ContractService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @GetMapping
    public ApiResponse<PageResponse<Contract>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate beginDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(contractService.list(keyword, status, beginDate, endDate, page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<Contract> detail(@PathVariable String id) {
        return ApiResponse.success(contractService.detail(id));
    }

    @PostMapping
    public ApiResponse<Contract> draft(@Valid @RequestBody DraftContractRequest request) {
        return ApiResponse.success(contractService.draft(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Contract> update(@PathVariable String id, @Valid @RequestBody UpdateContractRequest request) {
        return ApiResponse.success(contractService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        contractService.delete(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/assign")
    public ApiResponse<Contract> assign(@PathVariable String id, @Valid @RequestBody AssignContractRequest request) {
        return ApiResponse.success(contractService.assign(id, request));
    }

    @PostMapping("/{id}/retract")
    public ApiResponse<Contract> retract(@PathVariable String id, @RequestBody java.util.Map<String, String> body) {
        String userId = body.get("userId");
        if (userId == null || userId.isBlank()) {
            throw new com.example.contract.common.BusinessException("用户ID不能为空");
        }
        return ApiResponse.success(contractService.retract(id, userId));
    }

    @PostMapping("/{id}/countersign")
    public ApiResponse<Contract> countersign(@PathVariable String id, @Valid @RequestBody CountersignRequest request) {
        return ApiResponse.success(contractService.countersign(id, request));
    }

    @PostMapping("/{id}/finalize")
    public ApiResponse<Contract> finalizeContract(@PathVariable String id, @Valid @RequestBody FinalizeContractRequest request) {
        return ApiResponse.success(contractService.finalizeContract(id, request));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<Contract> approve(@PathVariable String id, @Valid @RequestBody ApproveContractRequest request) {
        return ApiResponse.success(contractService.approve(id, request));
    }

    @PostMapping("/{id}/sign")
    public ApiResponse<Contract> sign(@PathVariable String id, @Valid @RequestBody SignContractRequest request) {
        return ApiResponse.success(contractService.sign(id, request));
    }

    @GetMapping("/{id}/processes")
    public ApiResponse<List<ContractProcess>> processes(@PathVariable String id) {
        return ApiResponse.success(contractService.processes(id));
    }

    @GetMapping("/{id}/sign-records")
    public ApiResponse<List<SignRecord>> signRecords(@PathVariable String id) {
        return ApiResponse.success(contractService.signRecords(id));
    }

    @GetMapping("/{id}/versions")
    public ApiResponse<List<ContractVersion>> versions(@PathVariable String id) {
        return ApiResponse.success(contractService.versions(id));
    }
}

