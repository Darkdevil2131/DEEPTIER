package com.deeptier.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

public record ComputeExposureRequest(
        @NotNull(message = "annualSpendInr is required")
        @Positive(message = "annualSpendInr must be positive")
        BigDecimal annualSpendInr,

        @NotNull(message = "costFraction is required")
        @DecimalMin(value = "0.0", message = "costFraction must be >= 0.0")
        @DecimalMax(value = "1.0", message = "costFraction must be <= 1.0")
        BigDecimal costFraction,

        @NotNull(message = "deltaPercentagePoints is required")
        @Positive(message = "deltaPercentagePoints must be positive")
        Integer deltaPercentagePoints,

        List<String> evidenceIds
) {}
