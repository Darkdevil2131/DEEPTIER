package com.deeptier.api.dto;

import java.time.Instant;

public record SapPushResponse(
        String mode,
        String rfqId,
        String purchaseRequisitionId,
        String status,
        Instant pushedAt
) {}
