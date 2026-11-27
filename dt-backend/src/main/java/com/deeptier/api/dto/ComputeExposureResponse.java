package com.deeptier.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record ComputeExposureResponse(
        String tool,
        BigDecimal dutyExposureInr,
        String dataClass,
        List<String> evidenceIds,
        String formula,
        ExposureInputs inputs
) {
    public record ExposureInputs(
            BigDecimal annualSpendInr,
            BigDecimal costFraction,
            BigDecimal deltaPercentagePoints
    ) {}
}
