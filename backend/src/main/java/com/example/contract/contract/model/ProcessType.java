package com.example.contract.contract.model;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum ProcessType {
    COUNTERSIGN,
    FINALIZE,
    APPROVE,
    SIGN;

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
