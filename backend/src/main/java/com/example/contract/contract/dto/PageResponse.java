package com.example.contract.contract.dto;

import java.util.List;

public record PageResponse<T>(List<T> list, long total, int page, int pageSize) {
}

