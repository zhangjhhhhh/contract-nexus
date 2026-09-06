package com.example.contract.notification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.contract.contract.model.Contract;
import com.example.contract.contract.model.ContractProcess;
import com.example.contract.contract.model.ProcessState;
import com.example.contract.contract.model.ProcessType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TaskReminderPolicyTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 6, 13, 10, 0);

    @Test
    void remindsWhenContractDueWithinOneDay() {
        Contract contract = contract(LocalDate.of(2026, 6, 14), now.minusHours(2));
        ContractProcess process = pendingProcess(now.minusHours(2));

        assertTrue(TaskReminderPolicy.needsReminder(contract, process, now));
    }

    @Test
    void remindsWhenPendingMoreThanOneDay() {
        Contract contract = contract(LocalDate.of(2026, 7, 1), now.minusDays(3));
        ContractProcess process = pendingProcess(now.minusDays(1).minusMinutes(1));

        assertTrue(TaskReminderPolicy.needsReminder(contract, process, now));
    }

    @Test
    void skipsCompletedAndFreshTasks() {
        Contract contract = contract(LocalDate.of(2026, 7, 1), now.minusHours(2));
        ContractProcess process = pendingProcess(now.minusHours(2));
        assertFalse(TaskReminderPolicy.needsReminder(contract, process, now));

        process.setState(ProcessState.DONE);
        assertFalse(TaskReminderPolicy.needsReminder(contract, process, now));
    }

    private Contract contract(LocalDate endTime, LocalDateTime createdAt) {
        Contract contract = new Contract();
        contract.setId("K001");
        contract.setNum("HT-2026-001");
        contract.setName("测试合同");
        contract.setEndTime(endTime);
        contract.setCreatedAt(createdAt);
        return contract;
    }

    private ContractProcess pendingProcess(LocalDateTime createdAt) {
        ContractProcess process = new ContractProcess();
        process.setId("P001");
        process.setContractId("K001");
        process.setType(ProcessType.APPROVE);
        process.setUserId("U001");
        process.setState(ProcessState.PENDING);
        process.setCreatedAt(createdAt);
        return process;
    }
}
