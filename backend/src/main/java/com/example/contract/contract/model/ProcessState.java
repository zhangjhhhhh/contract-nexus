package com.example.contract.contract.model;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum ProcessState {
    PENDING,
    DONE,
    REJECTED;

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
