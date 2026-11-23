package com.deeptier.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MockRfqResponse(
        String id,
        String approvalRequestId,
        String targetSupplierId,
        String partCode,
        Integer requestedQty,
        BigDecimal quotedLandedCost,
        Integer expectedLeadTimeDays,
        String rfqStatus,
        String issuedBy,
        Instant issuedAt
) {}
