package com.deeptier.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AnalyzeDisruptionRequest(
        @NotBlank(message = "alertText is required")
        @Size(max = 2000, message = "alertText must not exceed 2000 characters")
        String alertText,

        @Size(max = 64, message = "partCode must not exceed 64 characters")
        String partCode,

        @Size(max = 64, message = "scenarioId must not exceed 64 characters")
        String scenarioId,

        @Min(0)
        Integer unitsOnHand,

        @Min(0)
        Integer dailyDemand,

        @Min(0)
        @Max(3650)
        Integer leadTimeDays,

        @DecimalMin("0")
        BigDecimal annualSpendInr,

        @DecimalMin("0")
        @DecimalMax("1")
        BigDecimal costFraction,

        @DecimalMin("0")
        BigDecimal lateCostPerDayInr,

        Long seed
) {}
