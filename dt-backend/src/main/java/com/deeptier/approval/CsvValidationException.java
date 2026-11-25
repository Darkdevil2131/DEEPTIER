package com.deeptier.approval;

import com.deeptier.api.dto.CsvRowValidationError;
import lombok.Getter;

import java.util.List;

@Getter
public class CsvValidationException extends RuntimeException {

    private final int totalRows;
    private final List<CsvRowValidationError> errors;

    public CsvValidationException(String message, int totalRows, List<CsvRowValidationError> errors) {
        super(message);
        this.totalRows = totalRows;
        this.errors = errors != null ? List.copyOf(errors) : List.of();
    }
}
