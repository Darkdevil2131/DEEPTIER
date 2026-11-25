package com.deeptier.api.dto;

public record CsvRowValidationError(
        int line,
        String column,
        String value,
        String message
) {
}
