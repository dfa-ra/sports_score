package com.studentleague.common.exception;

import java.time.Instant;
import java.util.List;

public record ApiError(
        String code,
        String message,
        List<FieldErrorDetail> details,
        Instant timestamp,
        String path
) {
    public record FieldErrorDetail(String field, String message) {
    }
}
