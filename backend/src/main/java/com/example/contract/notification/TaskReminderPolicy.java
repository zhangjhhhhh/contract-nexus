package com.example.contract.notification;

import com.example.contract.contract.model.Contract;
import com.example.contract.contract.model.ContractProcess;
import com.example.contract.contract.model.ProcessState;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class TaskReminderPolicy {

    private TaskReminderPolicy() {
    }

    public static boolean needsReminder(Contract contract, ContractProcess process, LocalDateTime now) {
        if (contract == null || process == null || process.getState() != ProcessState.PENDING) {
            return false;
        }
        return isContractDueWithinOneDay(contract.getEndTime(), now.toLocalDate())
                || isPendingMoreThanOneDay(pendingSince(contract, process), now);
    }

    public static boolean isContractDueWithinOneDay(LocalDate endTime, LocalDate today) {
        if (endTime == null) {
            return false;
        }
        return !endTime.isBefore(today) && !endTime.isAfter(today.plusDays(1));
    }

    public static boolean isPendingMoreThanOneDay(LocalDateTime pendingSince, LocalDateTime now) {
        return pendingSince != null && pendingSince.isBefore(now.minusDays(1));
    }

    public static LocalDateTime pendingSince(Contract contract, ContractProcess process) {
        if (process.getCreatedAt() != null) {
            return process.getCreatedAt();
        }
        return contract.getCreatedAt();
    }
}
