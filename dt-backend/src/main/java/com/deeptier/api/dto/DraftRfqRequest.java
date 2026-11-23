package com.deeptier.api.dto;

import jakarta.validation.constraints.NotBlank;

public record DraftRfqRequest(
        @NotBlank(message = "supplierNodeId is required")
        String supplierNodeId,

        @NotBlank(message = "quantity is required")
        String quantity,

        @NotBlank(message = "deliveryOption is required")
        String deliveryOption,

        String scenarioId
) {}
