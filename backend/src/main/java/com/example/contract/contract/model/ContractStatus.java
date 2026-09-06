package com.example.contract.contract.model;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum ContractStatus {
    DRAFTING,
    COUNTERSIGNING,
    FINALIZING,
    APPROVING,
    SIGNING,
    COMPLETED,
    REJECTED;

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
