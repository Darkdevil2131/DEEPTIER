package com.deeptier.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateApprovalRequest(
        @NotBlank(message = "partCode is required")
        String partCode,

        String sourceSupplierId,

        @NotBlank(message = "targetSupplierId is required")
        String targetSupplierId,

        @NotNull(message = "recommendedQty is required")
        @Positive(message = "recommendedQty must be positive")
        Integer recommendedQty,

        BigDecimal estimatedLandedCost,

        Integer estimatedLeadTimeDays,

        String requestedBy,

        String scenarioId
) {
    public CreateApprovalRequest(
            String partCode,
            String sourceSupplierId,
            String targetSupplierId,
            Integer recommendedQty,
            BigDecimal estimatedLandedCost,
            Integer estimatedLeadTimeDays,
            String requestedBy) {
        this(partCode, sourceSupplierId, targetSupplierId, recommendedQty,
                estimatedLandedCost, estimatedLeadTimeDays, requestedBy, null);
    }
}
