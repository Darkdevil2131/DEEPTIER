package com.deeptier.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record InternalTierMapResponse(
        String tool,
        List<TierNodeDto> nodes,
        List<TierLinkDto> links,
        String dataClass
) {
    public record TierNodeDto(
            String nodeId,
            int tier,
            String name,
            String country,
            String linkType,
            Double confidence
    ) {}

    public record TierLinkDto(
            String from,
            String to,
            String hsCode,
            BigDecimal annualSpendInr,
            BigDecimal costFraction,
            String linkType
    ) {}
}
